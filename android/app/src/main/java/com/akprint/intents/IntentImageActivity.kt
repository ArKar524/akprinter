package com.akprint.intents

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.akprint.escpos.EscPosCommands
import com.akprint.escpos.EscPosConverter

class IntentImageActivity : BaseIntentPrintActivity() {

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
            android.widget.Toast.makeText(this, "No image provided", android.widget.Toast.LENGTH_SHORT).show()
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
        val paperWidth = printer.optInt("paperWidth", 80)
        val targetWidthDots = when {
            paperWidth >= 100 -> EscPosCommands.DOTS_104MM
            paperWidth >= 70  -> EscPosCommands.DOTS_80MM
            else              -> EscPosCommands.DOTS_58MM
        }
        val autoCut = settings.optBoolean("autoCut", true)
        val openCashDrawer = settings.optBoolean("openCashDrawer", false)
        val printerName = printer.optString("name", "Printer")

        startPrinting(printerName) {
            val stream = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Cannot open image")
            val bitmap = BitmapFactory.decodeStream(stream)
            stream.close()

            if (bitmap == null) throw IllegalStateException("Cannot decode image")

            try {
                val parts = mutableListOf<ByteArray>()
                parts.add(EscPosCommands.INIT)
                parts.add(EscPosConverter.bitmapToEscPosRaster(bitmap, targetWidthDots))
                parts.add(EscPosCommands.feedLines(4))
                if (autoCut) parts.add(EscPosCommands.CUT_PARTIAL)
                if (openCashDrawer) parts.add(EscPosCommands.CASH_DRAWER_PIN2)

                val total = parts.sumOf { it.size }
                val merged = ByteArray(total)
                var offset = 0
                for (arr in parts) { arr.copyInto(merged, offset); offset += arr.size }
                merged
            } finally {
                bitmap.recycle()
            }
        }
    }
}
