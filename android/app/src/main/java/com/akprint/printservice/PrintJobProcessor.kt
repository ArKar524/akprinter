package com.akprint.printservice

import android.content.Context
import android.print.PrintJobInfo
import android.printservice.PrintJob
import android.util.Log
import com.akprint.drivers.BluetoothEscPosDriver
import com.akprint.drivers.EscPosDriver
import com.akprint.drivers.LanEscPosDriver
import com.akprint.escpos.EscPosConverter
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object PrintJobProcessor {

    private const val TAG = "PrintJobProcessor"
    private const val PREFS_NAME = "akprint_prefs"

    fun processJob(context: Context, printJob: PrintJob) {
        val startTime = System.currentTimeMillis()
        val printerIdLocal = printJob.info.printerId.localId
        val printerData = findPrinter(context, printerIdLocal)

        if (printerData == null) {
            printJob.fail("Printer not found in registry")
            appendLog(context, "error", "Printer not found: $printerIdLocal")
            return
        }

        val settings = loadSettings(context)
        val driver = buildDriver(printerData)

        if (!driver.connect()) {
            printJob.fail("Could not connect to printer")
            appendLog(context, "error", "Connect failed: ${printerData.optString("name")}")
            appendHistory(context, printerData, 0, startTime, false, "Connection failed")
            return
        }

        try {
            printJob.start()
            emitEvent(context, "PrintJobStarted", JSONObject().apply {
                put("jobId", printJob.id.toString())
                put("printerName", printerData.optString("name"))
            })

            val pfd = printJob.document.data
            if (pfd == null) {
                printJob.fail("No document data")
                return
            }

            val paperWidth = printerData.optInt("paperWidth", settings.optInt("paperWidth", 80))
            val copies = printJob.info.copies.coerceAtLeast(1).let {
                if (it == 1) settings.optInt("copies", 1) else it
            }
            val autoCut = settings.optBoolean("autoCut", true)
            val openCashDrawer = settings.optBoolean("openCashDrawer", false)

            val escPosData = EscPosConverter.pdfToEscPos(
                pfd = pfd,
                paperWidthMm = paperWidth,
                copies = copies,
                autoCut = autoCut,
                openCashDrawer = openCashDrawer
            )

            val sendOk = driver.send(escPosData)
            pfd.close()

            if (sendOk) {
                printJob.complete()
                val duration = System.currentTimeMillis() - startTime
                appendLog(context, "info", "Job completed: ${printerData.optString("name")}")
                appendHistory(context, printerData, printJob.document.info?.pageCount ?: 1, startTime, true, null)
                emitEvent(context, "PrintJobCompleted", JSONObject().apply {
                    put("jobId", printJob.id.toString())
                    put("printerName", printerData.optString("name"))
                    put("pageCount", printJob.document.info?.pageCount ?: 1)
                    put("duration", duration)
                })
            } else {
                // Retry logic
                val retryOnFailure = settings.optBoolean("retryOnFailure", true)
                val retryCount = settings.optInt("retryCount", 3)

                if (retryOnFailure) {
                    var retried = false
                    for (attempt in 1..retryCount) {
                        Log.d(TAG, "Retry attempt $attempt/$retryCount")
                        driver.disconnect()
                        Thread.sleep(1000L * attempt)
                        if (driver.connect() && driver.send(escPosData)) {
                            retried = true
                            break
                        }
                    }
                    if (retried) {
                        printJob.complete()
                        appendLog(context, "info", "Job completed after retry: ${printerData.optString("name")}")
                        appendHistory(context, printerData, printJob.document.info?.pageCount ?: 1, startTime, true, null)
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
}
