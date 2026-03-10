package com.akprint.printservice

import android.print.PrinterId
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

class AkPrintService : PrintService() {

    companion object {
        private const val TAG = "AkPrintService"

        // Pending events queue — consumed by PrinterModule when RN is active
        val pendingEvents = ConcurrentLinkedQueue<Pair<String, JSONObject>>()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        Log.d(TAG, "onCreatePrinterDiscoverySession")
        return AkPrinterDiscoverySession(this)
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        Log.d(TAG, "onPrintJobQueued: ${printJob.id}")
        printJob.start()

        scope.launch {
            try {
                val saved = PrintJobProcessor.savePendingJob(this@AkPrintService, printJob)
                if (saved) {
                    printJob.complete()
                    pendingEvents.add(Pair("PendingJobAdded", JSONObject().apply {
                        put("jobId", printJob.id.toString())
                    }))
                    PrintJobProcessor.appendLog(this@AkPrintService, "info", "Print job saved as pending: ${printJob.info.label}")
                } else {
                    printJob.fail("Failed to save print job")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving pending job", e)
                try { printJob.fail(e.message ?: "Unknown error") } catch (_: Exception) {}
            }
        }
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        Log.d(TAG, "onRequestCancelPrintJob: ${printJob.id}")
        printJob.cancel()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        scope.cancel()
        super.onDestroy()
    }
}
