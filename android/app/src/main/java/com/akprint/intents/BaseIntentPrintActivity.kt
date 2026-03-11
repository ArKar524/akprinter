package com.akprint.intents

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.akprint.printservice.PrintJobProcessor
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

abstract class BaseIntentPrintActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var printJob: Job? = null
    private var progressDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No layout — we display only dialogs
    }

    /**
     * Find the default printer (or first printer if no default is set).
     * Returns null if no printers are configured.
     */
    protected fun findDefaultPrinter(): JSONObject? {
        val printers = PrintJobProcessor.loadPrinters(this)
        for (i in 0 until printers.length()) {
            val p = printers.getJSONObject(i)
            if (p.optBoolean("isDefault")) return p
        }
        return if (printers.length() > 0) printers.getJSONObject(0) else null
    }

    protected fun loadSettings(): JSONObject = PrintJobProcessor.loadSettings(this)

    /**
     * Start printing. [convertData] runs on IO thread — return ESC/POS bytes to send,
     * or null to abort with an error message.
     */
    protected fun startPrinting(
        printerName: String,
        convertData: suspend () -> ByteArray?
    ) {
        progressDialog = AlertDialog.Builder(this)
            .setTitle("AkPrint")
            .setMessage("Preparing...")
            .setCancelable(false)
            .setNegativeButton("Cancel") { _, _ ->
                printJob?.cancel()
                finish()
            }
            .show()

        printJob = scope.launch {
            try {
                updateMessage("Converting document...")
                val escPosData = convertData()
                if (escPosData == null) {
                    showError("No data to print")
                    return@launch
                }

                updateMessage("Connecting to $printerName...")
                val printers = PrintJobProcessor.loadPrinters(this@BaseIntentPrintActivity)
                val printerData = findDefaultPrinterInArray(printers)
                if (printerData == null) {
                    showError("No printer configured.\nAdd a printer in AkPrint first.")
                    return@launch
                }

                val driver = PrintJobProcessor.buildDriver(printerData)
                if (!driver.connect()) {
                    showError("Could not connect to $printerName.\nCheck that the printer is on and in range.")
                    return@launch
                }

                try {
                    updateMessage("Printing to $printerName...")
                    if (driver.send(escPosData)) {
                        val disconnectDelayMs = PrintJobProcessor.loadSettings(this@BaseIntentPrintActivity)
                            .optInt("disconnectDelay", 3) * 1000L
                        if (disconnectDelayMs > 0) delay(disconnectDelayMs)
                        withContext(Dispatchers.Main) {
                            progressDialog?.dismiss()
                            Toast.makeText(this@BaseIntentPrintActivity, "Sent to $printerName", Toast.LENGTH_SHORT).show()
                            PrintJobProcessor.appendLog(this@BaseIntentPrintActivity, "info", "Intent print OK: $printerName")
                            finish()
                        }
                    } else {
                        showError("Failed to send data to printer.")
                    }
                } finally {
                    driver.disconnect()
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) { finish() }
            } catch (e: Exception) {
                showError(e.message ?: "Print error")
            }
        }
    }

    private fun findDefaultPrinterInArray(printers: JSONArray): JSONObject? {
        for (i in 0 until printers.length()) {
            val p = printers.getJSONObject(i)
            if (p.optBoolean("isDefault")) return p
        }
        return if (printers.length() > 0) printers.getJSONObject(0) else null
    }

    private suspend fun updateMessage(msg: String) = withContext(Dispatchers.Main) {
        progressDialog?.setMessage(msg)
    }

    protected suspend fun showError(msg: String) = withContext(Dispatchers.Main) {
        progressDialog?.dismiss()
        if (!isFinishing) {
            AlertDialog.Builder(this@BaseIntentPrintActivity)
                .setTitle("Print Error")
                .setMessage(msg)
                .setPositiveButton("OK") { _, _ -> finish() }
                .setOnCancelListener { finish() }
                .show()
        }
    }

    override fun onDestroy() {
        printJob?.cancel()
        scope.cancel()
        progressDialog?.dismiss()
        super.onDestroy()
    }
}
