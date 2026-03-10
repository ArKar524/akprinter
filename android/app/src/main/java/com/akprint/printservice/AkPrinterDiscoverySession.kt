package com.akprint.printservice

import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrinterDiscoverySession
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray

class AkPrinterDiscoverySession(
    private val service: AkPrintService
) : PrinterDiscoverySession() {

    companion object {
        private const val TAG = "AkDiscoverySession"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Track multiple printers simultaneously — keyed by localId
    private val trackingJobs = mutableMapOf<String, Job>()

    override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
        Log.d(TAG, "onStartPrinterDiscovery, priorityList=${priorityList.size}")
        val printers = PrintJobProcessor.loadPrinters(service)

        // Report priority printers first (ones the system wants us to find quickly)
        if (priorityList.isNotEmpty()) {
            val priorityIds = priorityList.map { it.localId }.toSet()
            val priorityInfoList = mutableListOf<PrinterInfo>()
            for (i in 0 until printers.length()) {
                val p = printers.getJSONObject(i)
                if (p.getString("id") in priorityIds) {
                    val printerId = service.generatePrinterId(p.getString("id"))
                    priorityInfoList.add(buildPrinterInfo(printerId, p, PrinterInfo.STATUS_IDLE, withCapabilities = true))
                }
            }
            if (priorityInfoList.isNotEmpty()) {
                addPrinters(priorityInfoList)
            }
        }

        // Then report all printers
        val allPrinterInfoList = buildPrinterInfoList(printers, withCapabilities = true)
        if (allPrinterInfoList.isNotEmpty()) {
            addPrinters(allPrinterInfoList)
        }
    }

    override fun onStopPrinterDiscovery() {
        Log.d(TAG, "onStopPrinterDiscovery")
        // No ongoing discovery tasks to stop for our static list approach
    }

    override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
        Log.d(TAG, "onValidatePrinters: ${printerIds.size}")
        // Validate printers by checking if they still exist in our registry
        val printers = PrintJobProcessor.loadPrinters(service)
        val result = mutableListOf<PrinterInfo>()
        for (pid in printerIds) {
            val data = findPrinterData(printers, pid.localId)
            if (data != null) {
                result.add(buildPrinterInfo(pid, data, PrinterInfo.STATUS_IDLE, withCapabilities = true))
            }
            // Printers not found are implicitly invalid — not added to the list
        }
        if (result.isNotEmpty()) addPrinters(result)
    }

    override fun onStartPrinterStateTracking(printerId: PrinterId) {
        Log.d(TAG, "onStartPrinterStateTracking: ${printerId.localId}")
        val localId = printerId.localId

        // Cancel existing tracking for this printer if any
        trackingJobs[localId]?.cancel()

        trackingJobs[localId] = scope.launch {
            val printers = PrintJobProcessor.loadPrinters(service)
            val printerData = findPrinterData(printers, localId) ?: return@launch

            // Immediately report printer with capabilities (status IDLE as placeholder)
            val initialInfo = buildPrinterInfo(printerId, printerData, PrinterInfo.STATUS_IDLE, withCapabilities = true)
            withContext(Dispatchers.Main) {
                addPrinters(listOf(initialInfo))
            }

            // Probe actual connection status
            val driver = PrintJobProcessor.buildDriver(printerData)
            val status = try {
                if (driver.connect()) {
                    driver.disconnect()
                    PrinterInfo.STATUS_IDLE
                } else {
                    PrinterInfo.STATUS_UNAVAILABLE
                }
            } catch (e: Exception) {
                Log.w(TAG, "Probe failed for ${printerId.localId}", e)
                PrinterInfo.STATUS_UNAVAILABLE
            }

            ensureActive()

            val updatedInfo = buildPrinterInfo(printerId, printerData, status, withCapabilities = true)
            withContext(Dispatchers.Main) {
                addPrinters(listOf(updatedInfo))
            }
        }
    }

    override fun onStopPrinterStateTracking(printerId: PrinterId) {
        Log.d(TAG, "onStopPrinterStateTracking: ${printerId.localId}")
        trackingJobs.remove(printerId.localId)?.cancel()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        trackingJobs.values.forEach { it.cancel() }
        trackingJobs.clear()
        scope.cancel()
    }

    private fun buildPrinterInfoList(printers: JSONArray, withCapabilities: Boolean): List<PrinterInfo> {
        val result = mutableListOf<PrinterInfo>()
        for (i in 0 until printers.length()) {
            val p = printers.getJSONObject(i)
            val printerId = service.generatePrinterId(p.getString("id"))
            result.add(buildPrinterInfo(printerId, p, PrinterInfo.STATUS_IDLE, withCapabilities))
        }
        return result
    }

    private fun buildPrinterInfo(
        printerId: PrinterId,
        printerData: org.json.JSONObject,
        status: Int,
        withCapabilities: Boolean
    ): PrinterInfo {
        val name = printerData.optString("name", "AkPrint Printer")
        val paperWidth = printerData.optInt("paperWidth", 80)
        val description = when (printerData.optString("type")) {
            "bluetooth" -> "Bluetooth (${printerData.optString("address", "")})"
            "lan" -> "LAN (${printerData.optString("host", "")}:${printerData.optInt("port", 9100)})"
            else -> "ESC/POS Printer"
        }

        val builder = PrinterInfo.Builder(printerId, name, status)
            .setDescription(description)

        if (withCapabilities) {
            builder.setCapabilities(buildCapabilities(printerId, paperWidth))
        }

        return builder.build()
    }

    private fun buildCapabilities(printerId: PrinterId, paperWidthMm: Int): PrinterCapabilitiesInfo {
        val mediaSize = if (paperWidthMm <= 58) {
            PrintAttributes.MediaSize("receipt_58mm", "Receipt 58mm", 2283, 11693)
        } else {
            PrintAttributes.MediaSize("receipt_80mm", "Receipt 80mm", 3150, 11693)
        }

        val resolution = PrintAttributes.Resolution("203dpi", "203 DPI", 203, 203)

        return PrinterCapabilitiesInfo.Builder(printerId)
            .addMediaSize(mediaSize, true)
            .addResolution(resolution, true)
            .setColorModes(
                PrintAttributes.COLOR_MODE_MONOCHROME,
                PrintAttributes.COLOR_MODE_MONOCHROME
            )
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
    }

    private fun findPrinterData(printers: JSONArray, localId: String): org.json.JSONObject? {
        for (i in 0 until printers.length()) {
            val p = printers.getJSONObject(i)
            if (p.getString("id") == localId) return p
        }
        return null
    }
}
