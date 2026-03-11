package com.akprint.intents

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.akprint.escpos.EscPosConverter

class IntentPdfActivity : BaseIntentPrintActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }

        if (uri == null) {
            android.widget.Toast.makeText(this, "No PDF file provided", android.widget.Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val printer = findDefaultPrinter()
        if (printer == null) {
            android.widget.Toast.makeText(this, "No printer configured. Add a printer in Pico Printer first.", android.widget.Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val settings = loadSettings()
        val paperWidth = printer.optInt("paperWidth", 80)
        val copies = settings.optInt("copies", 1).coerceAtLeast(1)
        val autoCutMode = settings.optString("autoCutMode", "partial")
        val cashDrawerMode = settings.optString("cashDrawerMode", "none")
        val linesBeforeCut = settings.optInt("linesBeforeCut", 4)
        val dpi = settings.optInt("dpi", 203)
        val useDither = settings.optString("imageMode", "threshold") == "dither"
        val printerName = printer.optString("name", "Printer")

        startPrinting(printerName) {
            val pfd = contentResolver.openFileDescriptor(uri, "r")
                ?: throw IllegalStateException("Cannot open PDF")
            try {
                EscPosConverter.pdfToEscPos(
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
        }
    }
}
