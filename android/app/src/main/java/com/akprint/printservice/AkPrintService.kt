package com.akprint.printservice

import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class AkPrintService : PrintService() {

    companion object {
        private const val TAG = "AkPrintService"

        // Pending events queue — consumed by PrinterModule when RN is active
        val pendingEvents = ConcurrentLinkedQueue<Pair<String, JSONObject>>()
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track active jobs so we can cancel them from onRequestCancelPrintJob
    private val activeJobs = ConcurrentHashMap<String, Job>()

    override fun onConnected() {
        Log.d(TAG, "onConnected — print service bound by system")
    }

    override fun onDisconnected() {
        Log.d(TAG, "onDisconnected — print service unbound by system")
        // Cancel all in-flight jobs since the service is being disconnected
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        Log.d(TAG, "onCreatePrinterDiscoverySession")
        return AkPrinterDiscoverySession(this)
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        Log.d(TAG, "onPrintJobQueued: ${printJob.id}")

        // Transition to STARTED — we are actively processing the job
        printJob.start()

        val jobIdStr = printJob.id.toString()
        val job = scope.launch {
            try {
                // Print immediately to the target printer, just like a real print service
                PrintJobProcessor.processJob(this@AkPrintService, printJob)
            } catch (e: CancellationException) {
                Log.d(TAG, "Job cancelled: $jobIdStr")
                try { printJob.cancel() } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "Error processing job", e)
                try { printJob.fail(e.message ?: "Unknown error") } catch (_: Exception) {}
            } finally {
                activeJobs.remove(jobIdStr)
            }
        }
        activeJobs[jobIdStr] = job
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        Log.d(TAG, "onRequestCancelPrintJob: ${printJob.id}")
        val jobIdStr = printJob.id.toString()

        // Cancel any in-flight coroutine for this job
        activeJobs[jobIdStr]?.cancel()
        activeJobs.remove(jobIdStr)

        // Cancel the system print job
        printJob.cancel()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        activeJobs.clear()
        scope.cancel()
        super.onDestroy()
    }
}
