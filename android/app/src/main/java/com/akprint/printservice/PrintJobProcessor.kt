package com.akprint.printservice

import android.content.Context
import android.printservice.PrintJob
import android.util.Log
import com.akprint.drivers.BluetoothEscPosDriver
import com.akprint.drivers.EscPosDriver
import com.akprint.drivers.LanEscPosDriver
import com.akprint.escpos.EscPosConverter
import org.json.JSONArray
import org.json.JSONObject
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object PrintJobProcessor {

    private const val TAG = "PrintJobProcessor"
    const val PREFS_NAME = "akprint_prefs"

    fun processJob(context: Context, printJob: PrintJob) {
        val startTime = System.currentTimeMillis()
        val printerIdLocal = printJob.info.printerId?.localId ?: run {
            printJob.fail("No printer ID")
            return
        }
        val printerData = findPrinter(context, printerIdLocal)

        if (printerData == null) {
            printJob.fail("Printer not found in registry")
            appendLog(context, "error", "Printer not found: $printerIdLocal")
            return
        }

        val printerName = printerData.optString("name")
        val settings = loadSettings(context)

        // Read document data and page count BEFORE any state transitions
        // (document data may become unavailable after certain state changes)
        val pfd = printJob.document.data
        if (pfd == null) {
            printJob.fail("No document data")
            return
        }

        val pageCount = printJob.document.info?.pageCount ?: 1

        printJob.setStatus("Connecting to $printerName...")
        val driver = buildDriver(printerData)

        if (!driver.connect()) {
            pfd.close()
            printJob.fail("Could not connect to printer")
            appendLog(context, "error", "Connect failed: $printerName")
            appendHistory(context, printerData, 0, startTime, false, "Connection failed")
            return
        }

        try {
            emitEvent(context, "PrintJobStarted", JSONObject().apply {
                put("jobId", printJob.id.toString())
                put("printerName", printerName)
            })

            printJob.setStatus("Converting document...")

            val escPosData: ByteArray
            try {
                val paperWidth = printerData.optInt("paperWidth", settings.optInt("paperWidth", 80))
                val copies = printJob.info.copies.coerceAtLeast(1).let {
                    if (it == 1) settings.optInt("copies", 1) else it
                }
                val autoCutMode = settings.optString("autoCutMode", "partial")
                val cashDrawerMode = settings.optString("cashDrawerMode", "none")
                val linesBeforeCut = settings.optInt("linesBeforeCut", 4)
                val dpi = settings.optInt("dpi", 203)
                val useDither = settings.optString("imageMode", "threshold") == "dither"

                escPosData = EscPosConverter.pdfToEscPos(
                    pfd = pfd,
                    paperWidthMm = paperWidth,
                    copies = copies,
                    autoCutMode = autoCutMode,
                    cashDrawerMode = cashDrawerMode,
                    linesBeforeCut = linesBeforeCut,
                    dpi = dpi,
                    useDither = useDither,
                    onPageProgress = { pageIndex, totalPages ->
                        printJob.setStatus("Converting page ${pageIndex + 1}/$totalPages...")
                    }
                )
            } finally {
                pfd.close()
            }

            printJob.setStatus("Sending to printer...")
            val sendOk = driver.send(escPosData)

            if (sendOk) {
                printJob.complete()
                val duration = System.currentTimeMillis() - startTime
                appendLog(context, "info", "Job completed: $printerName")
                appendHistory(context, printerData, pageCount, startTime, true, null)
                emitEvent(context, "PrintJobCompleted", JSONObject().apply {
                    put("jobId", printJob.id.toString())
                    put("printerName", printerName)
                    put("pageCount", pageCount)
                    put("duration", duration)
                })
            } else {
                // Retry logic
                val retryOnFailure = settings.optBoolean("retryOnFailure", true)
                val retryCount = settings.optInt("retryCount", 3)

                if (retryOnFailure) {
                    // Transition to BLOCKED — temporarily unable to print, will retry
                    printJob.block("Retrying...")
                    var retried = false
                    for (attempt in 1..retryCount) {
                        Log.d(TAG, "Retry attempt $attempt/$retryCount")
                        printJob.setStatus("Retry $attempt/$retryCount...")
                        driver.disconnect()
                        Thread.sleep(1000L * attempt)
                        if (driver.connect() && driver.send(escPosData)) {
                            retried = true
                            break
                        }
                    }
                    if (retried) {
                        printJob.complete()
                        appendLog(context, "info", "Job completed after retry: $printerName")
                        appendHistory(context, printerData, pageCount, startTime, true, null)
                    } else {
                        failJob(context, printJob, printerData, startTime, "Send failed after $retryCount retries")
                    }
                } else {
                    failJob(context, printJob, printerData, startTime, "Send failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing job", e)
            failJob(context, printJob, printerData, startTime, e.message ?: "Unknown error")
        } finally {
            val disconnectDelayMs = settings.optInt("disconnectDelay", 3) * 1000L
            if (disconnectDelayMs > 0) {
                try { Thread.sleep(disconnectDelayMs) } catch (_: InterruptedException) {}
            }
            driver.disconnect()
        }
    }

    private fun failJob(context: Context, printJob: PrintJob, printerData: JSONObject, startTime: Long, reason: String) {
        printJob.fail(reason)
        appendLog(context, "error", "Job failed: $reason")
        appendHistory(context, printerData, 0, startTime, false, reason)
        emitEvent(context, "PrintJobFailed", JSONObject().apply {
            put("jobId", printJob.id.toString())
            put("error", reason)
        })
    }

    fun buildDriver(printerData: JSONObject): EscPosDriver {
        val id = printerData.getString("id")
        return when (printerData.getString("type")) {
            "bluetooth" -> BluetoothEscPosDriver(printerData.getString("address"), id)
            "lan" -> LanEscPosDriver(
                printerData.getString("host"),
                printerData.optInt("port", 9100),
                id
            )
            else -> throw IllegalArgumentException("Unknown printer type")
        }
    }

    private fun findPrinter(context: Context, printerId: String): JSONObject? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val printersJson = prefs.getString("printers", "[]") ?: "[]"
        val arr = JSONArray(printersJson)
        for (i in 0 until arr.length()) {
            val p = arr.getJSONObject(i)
            if (p.getString("id") == printerId) return p
        }
        return null
    }

    fun loadSettings(context: Context): JSONObject {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("settings", "{}") ?: "{}"
        return JSONObject(json)
    }

    fun loadPrinters(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("printers", "[]") ?: "[]"
        return JSONArray(json)
    }

    fun savePrinters(context: Context, printers: JSONArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("printers", printers.toString()).apply()
    }

    fun saveSettings(context: Context, settings: JSONObject) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("settings", settings.toString()).apply()
    }

    fun appendLog(context: Context, level: String, message: String, printerId: String? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logsJson = prefs.getString("logs", "[]") ?: "[]"
        val logs = JSONArray(logsJson)
        val entry = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
            put("level", level)
            put("message", message)
            if (printerId != null) put("printerId", printerId)
        }
        logs.put(entry)
        // Keep last 500 entries
        val trimmed = if (logs.length() > 500) {
            val arr = JSONArray()
            for (i in (logs.length() - 500) until logs.length()) arr.put(logs.getJSONObject(i))
            arr
        } else logs
        prefs.edit().putString("logs", trimmed.toString()).apply()
    }

    private fun appendHistory(context: Context, printerData: JSONObject, pageCount: Int, startTime: Long, success: Boolean, error: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val historyJson = prefs.getString("history", "[]") ?: "[]"
        val history = JSONArray(historyJson)
        val entry = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("timestamp", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
            put("printerName", printerData.optString("name"))
            put("printerId", printerData.optString("id"))
            put("pageCount", pageCount)
            put("copies", 1)
            put("paperWidth", printerData.optInt("paperWidth", 80))
            put("durationMs", System.currentTimeMillis() - startTime)
            put("success", success)
            if (error != null) put("errorMessage", error)
        }
        history.put(entry)
        // Keep last 1000 entries
        val trimmed = if (history.length() > 1000) {
            val arr = JSONArray()
            for (i in (history.length() - 1000) until history.length()) arr.put(history.getJSONObject(i))
            arr
        } else history
        prefs.edit().putString("history", trimmed.toString()).apply()
    }

    private fun emitEvent(context: Context, eventName: String, data: JSONObject) {
        // Events are emitted via a static reference set by PrinterModule
        AkPrintService.pendingEvents.add(Pair(eventName, data))
    }

    /**
     * Print from a previously saved PDF file. Called after the PrintJob has already been
     * completed from the system's perspective (ESC app pattern: complete immediately, print
     * in background). Errors are emitted as events, not via printJob.fail().
     */
    fun processJobFromFile(
        context: Context,
        jobId: String,
        printerLocalId: String,
        pdfFile: File,
        pageCount: Int,
        copies: Int,
        startTime: Long
    ) {
        val printerData = findPrinter(context, printerLocalId)
        if (printerData == null) {
            appendLog(context, "error", "Printer not found: $printerLocalId")
            emitEvent(context, "PrintJobFailed", JSONObject().apply {
                put("jobId", jobId)
                put("error", "Printer not found")
            })
            pdfFile.delete()
            return
        }

        val printerName = printerData.optString("name")
        val settings = loadSettings(context)

        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val escPosData: ByteArray
        try {
            val paperWidth = printerData.optInt("paperWidth", settings.optInt("paperWidth", 80))
            val actualCopies = if (copies == 1) settings.optInt("copies", 1) else copies
            val autoCutMode = settings.optString("autoCutMode", "partial")
            val cashDrawerMode = settings.optString("cashDrawerMode", "none")
            val linesBeforeCut = settings.optInt("linesBeforeCut", 4)
            val dpi = settings.optInt("dpi", 203)
            val useDither = settings.optString("imageMode", "threshold") == "dither"

            escPosData = EscPosConverter.pdfToEscPos(
                pfd = pfd,
                paperWidthMm = paperWidth,
                copies = actualCopies,
                autoCutMode = autoCutMode,
                cashDrawerMode = cashDrawerMode,
                linesBeforeCut = linesBeforeCut,
                dpi = dpi,
                useDither = useDither
            )
        } finally {
            pfd.close()
            pdfFile.delete()
        }

        emitEvent(context, "PrintJobStarted", JSONObject().apply {
            put("jobId", jobId)
            put("printerName", printerName)
        })

        val driver = buildDriver(printerData)
        if (!driver.connect()) {
            appendLog(context, "error", "Connect failed: $printerName")
            appendHistory(context, printerData, 0, startTime, false, "Connection failed")
            emitEvent(context, "PrintJobFailed", JSONObject().apply {
                put("jobId", jobId)
                put("error", "Could not connect to printer")
            })
            return
        }

        try {
            val retryOnFailure = settings.optBoolean("retryOnFailure", true)
            val retryCount = settings.optInt("retryCount", 3)
            var sendOk = driver.send(escPosData)

            if (!sendOk && retryOnFailure) {
                for (attempt in 1..retryCount) {
                    Log.d(TAG, "Retry $attempt/$retryCount for $jobId")
                    driver.disconnect()
                    Thread.sleep(1000L * attempt)
                    if (driver.connect() && driver.send(escPosData)) {
                        sendOk = true
                        break
                    }
                }
            }

            val duration = System.currentTimeMillis() - startTime
            if (sendOk) {
                appendLog(context, "info", "Job completed: $printerName")
                appendHistory(context, printerData, pageCount, startTime, true, null)
                emitEvent(context, "PrintJobCompleted", JSONObject().apply {
                    put("jobId", jobId)
                    put("printerName", printerName)
                    put("pageCount", pageCount)
                    put("duration", duration)
                })
            } else {
                val reason = if (retryOnFailure) "Send failed after $retryCount retries" else "Send failed"
                appendLog(context, "error", "Job failed: $reason")
                appendHistory(context, printerData, 0, startTime, false, reason)
                emitEvent(context, "PrintJobFailed", JSONObject().apply {
                    put("jobId", jobId)
                    put("error", reason)
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error printing job $jobId", e)
            val msg = e.message ?: "Unknown error"
            appendLog(context, "error", "Job error: $msg")
            appendHistory(context, printerData, 0, startTime, false, msg)
            emitEvent(context, "PrintJobFailed", JSONObject().apply {
                put("jobId", jobId)
                put("error", msg)
            })
        } finally {
            val disconnectDelayMs = settings.optInt("disconnectDelay", 3) * 1000L
            if (disconnectDelayMs > 0) {
                try { Thread.sleep(disconnectDelayMs) } catch (_: InterruptedException) {}
            }
            driver.disconnect()
        }
    }

    // --- Pending Jobs ---

    fun savePendingJob(context: Context, printJob: PrintJob): Boolean {
        val jobId = UUID.randomUUID().toString()
        val pendingDir = File(context.filesDir, "pending_jobs")
        if (!pendingDir.exists()) pendingDir.mkdirs()

        val pdfFile = File(pendingDir, "$jobId.pdf")

        val pfd = printJob.document.data ?: return false
        try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(pdfFile).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            pfd.close()
        }

        val pageCount = printJob.document.info?.pageCount ?: 0
        val documentName = printJob.info.label ?: "Untitled"

        val metadata = JSONObject().apply {
            put("id", jobId)
            put("documentName", documentName)
            put("pageCount", pageCount)
            put("createdAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date()))
            put("status", "pending")
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jobsJson = prefs.getString("pending_jobs", "[]") ?: "[]"
        val jobs = JSONArray(jobsJson)
        jobs.put(metadata)
        prefs.edit().putString("pending_jobs", jobs.toString()).apply()

        return true
    }

    fun getPendingJobs(context: Context): JSONArray {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jobsJson = prefs.getString("pending_jobs", "[]") ?: "[]"
        return JSONArray(jobsJson)
    }

    fun deletePendingJob(context: Context, jobId: String) {
        // Delete the PDF file
        val pdfFile = File(context.filesDir, "pending_jobs/$jobId.pdf")
        if (pdfFile.exists()) pdfFile.delete()

        // Remove from SharedPreferences
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jobsJson = prefs.getString("pending_jobs", "[]") ?: "[]"
        val jobs = JSONArray(jobsJson)
        val filtered = JSONArray()
        for (i in 0 until jobs.length()) {
            val job = jobs.getJSONObject(i)
            if (job.getString("id") != jobId) filtered.put(job)
        }
        prefs.edit().putString("pending_jobs", filtered.toString()).apply()
    }

    fun printPendingJob(context: Context, jobId: String, printerData: JSONObject, settings: JSONObject): Boolean {
        val pdfFile = File(context.filesDir, "pending_jobs/$jobId.pdf")
        if (!pdfFile.exists()) throw IllegalStateException("PDF file not found for job: $jobId")

        val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val escPosData: ByteArray
        try {
            val paperWidth = printerData.optInt("paperWidth", settings.optInt("paperWidth", 80))
            val copies = settings.optInt("copies", 1)
            val autoCutMode = settings.optString("autoCutMode", "partial")
            val cashDrawerMode = settings.optString("cashDrawerMode", "none")
            val linesBeforeCut = settings.optInt("linesBeforeCut", 4)
            val dpi = settings.optInt("dpi", 203)
            val useDither = settings.optString("imageMode", "threshold") == "dither"

            escPosData = EscPosConverter.pdfToEscPos(
                pfd = pfd,
                paperWidthMm = paperWidth,
                copies = copies,
                autoCutMode = autoCutMode,
                cashDrawerMode = cashDrawerMode,
                linesBeforeCut = linesBeforeCut,
                dpi = dpi,
                useDither = useDither
            )
        } finally {
            pfd.close()
        }

        val driver = buildDriver(printerData)
        if (!driver.connect()) throw IllegalStateException("Could not connect to printer")

        try {
            val sendOk = driver.send(escPosData)
            if (sendOk) {
                deletePendingJob(context, jobId)
                return true
            }
            return false
        } finally {
            val disconnectDelayMs = settings.optInt("disconnectDelay", 3) * 1000L
            if (disconnectDelayMs > 0) {
                try { Thread.sleep(disconnectDelayMs) } catch (_: InterruptedException) {}
            }
            driver.disconnect()
        }
    }
}
