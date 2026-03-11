package com.akprint.intents

import android.content.Intent
import android.os.Bundle
import com.akprint.escpos.EscPosCommands

class IntentTextActivity : BaseIntentPrintActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text: String? = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: run {
                    finish()
                    return
                }
                try {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                } catch (_: Exception) { null }
            }
            else -> null
        }

        if (text.isNullOrEmpty()) {
            android.widget.Toast.makeText(this, "No text to print", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val printer = findDefaultPrinter()
        if (printer == null) {
            android.widget.Toast.makeText(this, "No printer configured. Add a printer in AkPrint first.", android.widget.Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val settings = loadSettings()
        val autoCut = settings.optBoolean("autoCut", true)
        val openCashDrawer = settings.optBoolean("openCashDrawer", false)
        val printerName = printer.optString("name", "Printer")

        startPrinting(printerName) {
            val parts = mutableListOf<ByteArray>()
            parts.add(EscPosCommands.INIT)
            parts.add(EscPosCommands.SET_LINE_SPACING_DEFAULT)
            // Ensure text ends with a newline
            val printText = if (text.endsWith("\n")) text else "$text\n"
            parts.add(printText.toByteArray(Charsets.UTF_8))
            parts.add(EscPosCommands.feedLines(4))
            if (autoCut) parts.add(EscPosCommands.CUT_PARTIAL)
            if (openCashDrawer) parts.add(EscPosCommands.CASH_DRAWER_PIN2)

            val total = parts.sumOf { it.size }
            val merged = ByteArray(total)
            var offset = 0
            for (arr in parts) { arr.copyInto(merged, offset); offset += arr.size }
            merged
        }
    }
}
