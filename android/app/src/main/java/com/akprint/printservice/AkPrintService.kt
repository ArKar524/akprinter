package com.akprint.printservice

import android.content.Context
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class AkPrintService : PrintService() {

    companion object {
        private const val TAG = "AkPrintService"
        const val PREF_SERVICE_ENABLED = "service_enabled"

        // Pending events queue — consumed by PrinterModule when RN is active
        val pendingEvents = ConcurrentLinkedQueue<Pair<String, JSONObject>>()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track active background print jobs so we can cancel them
    private val activeJobs = ConcurrentHashMap<String, Job>()

    override fun onConnected() {
        Log.d(TAG, "onConnected — print service bound by system")
        getSharedPreferences(PrintJobProcessor.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_SERVICE_ENABLED, true).apply()
    }

    override fun onDisconnected() {
        Log.d(TAG, "onDisconnected — print service unbound by system")
        getSharedPreferences(PrintJobProcessor.PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_SERVICE_ENABLED, false).apply()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        Log.d(TAG, "onCreatePrinterDiscoverySession")
        return AkPrinterDiscoverySession(this)
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        Log.d(TAG, "onPrintJobQueued: ${printJob.id}")
        printJob.start()

        val jobIdStr = printJob.id.toString()
        val printerLocalId = printJob.info.printerId?.localId
        val pageCount = printJob.document.info?.pageCount ?: 1
        val copies = printJob.info.copies.coerceAtLeast(1)
        val startTime = System.currentTimeMillis()

        if (printerLocalId == null) {
            Log.e(TAG, "No printer ID in job $jobIdStr")
            printJob.fail("No printer ID")
            return
        }

        // Save the PDF to disk NOW, while document data is accessible.
        // We complete() immediately (like the ESC app) so Android never shows
        // "Print service not enabled" error notifications. Actual printing
        // happens in a background coroutine; failures are reported via events.
        val pdfFile = File(filesDir, "print_jobs/$jobIdStr.pdf")
        pdfFile.parentFile?.mkdirs()

        val pfd = printJob.document.data
        if (pfd == null) {
            Log.e(TAG, "No document data for job $jobIdStr")
            printJob.fail("No document data")
            return
        }

        val saved = try {
            FileInputStream(pfd.fileDescriptor).use { input ->
                FileOutputStream(pdfFile).use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save PDF for job $jobIdStr", e)
            false
        } finally {
            pfd.close()
        }

        if (!saved) {
            printJob.fail("Failed to save print data")
            return
        }

        // Mark job complete from system's perspective — this prevents any
        // "Print service not enabled" or "Printer error" system notifications.
        printJob.complete()

        // Print in background; errors are emitted as PrintJobFailed events.
        val job = scope.launch {
            try {
                PrintJobProcessor.processJobFromFile(
                    context = this@AkPrintService,
                    jobId = jobIdStr,
                    printerLocalId = printerLocalId,
                    pdfFile = pdfFile,
                    pageCount = pageCount,
                    copies = copies,
                    startTime = startTime
                )
            } catch (e: CancellationException) {
                Log.d(TAG, "Job cancelled: $jobIdStr")
                pdfFile.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled error in job $jobIdStr", e)
                pdfFile.delete()
            } finally {
                activeJobs.remove(jobIdStr)
            }
        }
        activeJobs[jobIdStr] = job
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        Log.d(TAG, "onRequestCancelPrintJob: ${printJob.id}")
        val jobIdStr = printJob.id.toString()
        activeJobs[jobIdStr]?.cancel()
        activeJobs.remove(jobIdStr)
        // Clean up temp file; job is already complete() so no state change needed
        File(filesDir, "print_jobs/$jobIdStr.pdf").delete()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        scope.cancel()
        super.onDestroy()
    }
}
