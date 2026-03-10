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
                PrintJobProcessor.processJob(this@AkPrintService, printJob)
            } catch (e: Exception) {
                Log.e(TAG, "Unhandled error in print job", e)
                try {
                    printJob.fail(e.message ?: "Unknown error")
                } catch (_: Exception) {}
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
