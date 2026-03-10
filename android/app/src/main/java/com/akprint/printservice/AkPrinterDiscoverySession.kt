package com.akprint.printservice

import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrinterDiscoverySession
import android.util.Log
import com.akprint.drivers.BluetoothEscPosDriver
import com.akprint.drivers.LanEscPosDriver
import kotlinx.coroutines.*
import org.json.JSONArray

class AkPrinterDiscoverySession(
    private val service: AkPrintService
) : PrinterDiscoverySession() {

    companion object {
        private const val TAG = "AkDiscoverySession"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trackingJob: Job? = null

    override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
        Log.d(TAG, "onStartPrinterDiscovery")
        val printers = PrintJobProcessor.loadPrinters(service)
        val printerInfoList = buildPrinterInfoList(printers, withCapabilities = false)
        addPrinters(printerInfoList)
    }

    override fun onStopPrinterDiscovery() {
        Log.d(TAG, "onStopPrinterDiscovery")
    }

    override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
        Log.d(TAG, "onValidatePrinters: ${printerIds.size}")
    }

    override fun onStartPrinterStateTracking(printerId: PrinterId) {
        Log.d(TAG, "onStartPrinterStateTracking: ${printerId.localId}")
        trackingJob?.cancel()
        trackingJob = scope.launch {
            val printers = PrintJobProcessor.loadPrinters(service)
            val printerData = findPrinterData(printers, printerId.localId) ?: return@launch

            // Build full capabilities for the tracked printer
            val info = buildPrinterInfoWithCapabilities(printerId, printerData)
            withContext(Dispatchers.Main) {
                addPrinters(listOf(info))
            }

            // Probe connection status
            val driver = PrintJobProcessor.buildDriver(printerData)
            val status = if (driver.connect()) {
                driver.disconnect()
                PrinterInfo.STATUS_IDLE
            } else {
                PrinterInfo.STATUS_UNAVAILABLE
            }

            val updatedInfo = buildPrinterInfo(printerId, printerData, status, withCapabilities = true)
            withContext(Dispatchers.Main) {
                addPrinters(listOf(updatedInfo))
            }
        }
    }

    override fun onStopPrinterStateTracking(printerId: PrinterId) {
        Log.d(TAG, "onStopPrinterStateTracking: ${printerId.localId}")
        trackingJob?.cancel()
        trackingJob = null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
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

    private fun buildPrinterInfoWithCapabilities(printerId: PrinterId, printerData: org.json.JSONObject): PrinterInfo {
        return buildPrinterInfo(printerId, printerData, PrinterInfo.STATUS_IDLE, withCapabilities = true)
    }

    private fun buildPrinterInfo(
        printerId: PrinterId,
        printerData: org.json.JSONObject,
        status: Int,
        withCapabilities: Boolean
    ): PrinterInfo {
        val name = printerData.optString("name", "AkPrint Printer")
        val paperWidth = printerData.optInt("paperWidth", 80)

        val builder = PrinterInfo.Builder(printerId, name, status)

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
