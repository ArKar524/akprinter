package com.akprint.escpos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor

object EscPosConverter {

    fun targetWidthDots(paperWidthMm: Int, dpi: Int): Int {
        return if (dpi == 180) {
            when {
                paperWidthMm >= 100 -> EscPosCommands.DOTS_104MM_180DPI
                paperWidthMm >= 70  -> EscPosCommands.DOTS_80MM_180DPI
                else                -> EscPosCommands.DOTS_58MM_180DPI
            }
        } else {
            when {
                paperWidthMm >= 100 -> EscPosCommands.DOTS_104MM
                paperWidthMm >= 70  -> EscPosCommands.DOTS_80MM
                else                -> EscPosCommands.DOTS_58MM
            }
        }
    }

    fun pdfToEscPos(
        pfd: ParcelFileDescriptor,
        paperWidthMm: Int,
        copies: Int = 1,
        autoCutMode: String = "partial",
        cashDrawerMode: String = "none",
        linesBeforeCut: Int = 4,
        dpi: Int = 203,
        useDither: Boolean = false,
        onPageProgress: ((pageIndex: Int, totalPages: Int) -> Unit)? = null
    ): ByteArray {
        val result = mutableListOf<ByteArray>()
        val pdfRenderer = PdfRenderer(pfd)
        val widthDots = targetWidthDots(paperWidthMm, dpi)

        try {
            val totalPages = pdfRenderer.pageCount
            repeat(copies) { copyIndex ->
                result.add(EscPosCommands.INIT)

                for (pageIndex in 0 until totalPages) {
                    onPageProgress?.invoke(pageIndex, totalPages)
                    val page = pdfRenderer.openPage(pageIndex)
                    try {
                        val bitmap = renderPageToBitmap(page, widthDots)
                        result.add(bitmapToEscPosRaster(bitmap, widthDots, useDither))
                        bitmap.recycle()
                    } finally {
                        page.close()
                    }
                }

                if (linesBeforeCut > 0) result.add(EscPosCommands.feedLines(linesBeforeCut))
                when (autoCutMode) {
                    "full"    -> result.add(EscPosCommands.CUT_FULL)
                    "partial" -> result.add(EscPosCommands.CUT_PARTIAL)
                }
                when (cashDrawerMode) {
                    "drawer1" -> result.add(EscPosCommands.CASH_DRAWER_PIN2)
                    "drawer2" -> result.add(EscPosCommands.CASH_DRAWER_PIN5)
                }

                // Feed lines between copies (not after last)
                if (copyIndex < copies - 1) result.add(EscPosCommands.feedLines(3))
            }
        } finally {
            pdfRenderer.close()
        }

        return mergeByteArrays(result)
    }

    private fun renderPageToBitmap(page: PdfRenderer.Page, targetWidthDots: Int): Bitmap {
        // Page size is in points (1/72 inch). Convert to pixels at printer DPI.
        val pageWidthPts = page.width.toFloat()
        val pageHeightPts = page.height.toFloat()

        val widthPx = targetWidthDots
        val heightPx = (pageHeightPts / pageWidthPts * widthPx).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        // Fill white background before rendering
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

        // Crop trailing whitespace so short content doesn't waste paper
        val cropped = cropBottomWhitespace(bitmap)
        if (cropped !== bitmap) bitmap.recycle()
        return cropped
    }

    private fun cropBottomWhitespace(bitmap: Bitmap, paddingDots: Int = 16): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var lastContentRow = 0
        outer@ for (y in h - 1 downTo 0) {
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                val lum = (0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)).toInt()
                if (lum < 250) {
                    lastContentRow = y
                    break@outer
                }
            }
        }

        val newH = (lastContentRow + 1 + paddingDots).coerceAtMost(h)
        if (newH >= h) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, w, newH)
    }

    fun bitmapToEscPosRaster(bitmap: Bitmap, targetWidthDots: Int, useDither: Boolean = false): ByteArray {
        val scaledBitmap = if (bitmap.width != targetWidthDots) {
            val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
            val targetHeight = (targetWidthDots * aspectRatio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetWidthDots, targetHeight, true)
        } else bitmap

        val widthDots = scaledBitmap.width
        val heightDots = scaledBitmap.height
        val widthBytes = (widthDots + 7) / 8

        val header = EscPosCommands.rasterImageHeader(widthBytes, heightDots)
        val pixelData = if (useDither) {
            ditherPixelData(scaledBitmap, widthDots, heightDots, widthBytes)
        } else {
            thresholdPixelData(scaledBitmap, widthDots, heightDots, widthBytes)
        }

        if (scaledBitmap !== bitmap) scaledBitmap.recycle()

        return header + pixelData
    }

    // Simple luminance threshold: dark pixels (< 128) → print dot
    private fun thresholdPixelData(
        bitmap: Bitmap,
        widthDots: Int,
        heightDots: Int,
        widthBytes: Int
    ): ByteArray {
        val data = ByteArray(widthBytes * heightDots)
        for (y in 0 until heightDots) {
            for (byteIndex in 0 until widthBytes) {
                var b = 0
                for (bit in 0 until 8) {
                    val x = byteIndex * 8 + bit
                    if (x < widthDots) {
                        val pixel = bitmap.getPixel(x, y)
                        val lum = (0.299 * Color.red(pixel) +
                                   0.587 * Color.green(pixel) +
                                   0.114 * Color.blue(pixel)).toInt()
                        if (lum < 128) b = b or (0x80 shr bit)
                    }
                }
                data[y * widthBytes + byteIndex] = b.toByte()
            }
        }
        return data
    }

    // Floyd-Steinberg dithering — better quality for photos and gradients
    private fun ditherPixelData(
        bitmap: Bitmap,
        widthDots: Int,
        heightDots: Int,
        widthBytes: Int
    ): ByteArray {
        // Copy luminance values into a float buffer for error diffusion
        val lum = FloatArray(widthDots * heightDots)
        for (y in 0 until heightDots) {
            for (x in 0 until widthDots) {
                val pixel = bitmap.getPixel(x, y)
                lum[y * widthDots + x] = (0.299f * Color.red(pixel) +
                                          0.587f * Color.green(pixel) +
                                          0.114f * Color.blue(pixel))
            }
        }

        val data = ByteArray(widthBytes * heightDots)
        for (y in 0 until heightDots) {
            for (byteIndex in 0 until widthBytes) {
                var b = 0
                for (bit in 0 until 8) {
                    val x = byteIndex * 8 + bit
                    if (x < widthDots) {
                        val idx = y * widthDots + x
                        val old = lum[idx]
                        val newVal = if (old < 128f) 0f else 255f
                        val err = old - newVal
                        lum[idx] = newVal

                        // Distribute error to neighbors (Floyd-Steinberg weights)
                        if (x + 1 < widthDots)                        lum[idx + 1]            += err * 7f / 16f
                        if (y + 1 < heightDots) {
                            if (x - 1 >= 0)                            lum[idx + widthDots - 1] += err * 3f / 16f
                                                                       lum[idx + widthDots]     += err * 5f / 16f
                            if (x + 1 < widthDots)                     lum[idx + widthDots + 1] += err * 1f / 16f
                        }

                        if (newVal == 0f) b = b or (0x80 shr bit)
                    }
                }
                data[y * widthBytes + byteIndex] = b.toByte()
            }
        }
        return data
    }

    fun buildTestPage(printerName: String, paperWidthMm: Int, dpi: Int = 203): ByteArray {
        val result = mutableListOf<ByteArray>()
        val separator = "--------------------------------\n"

        result.add(EscPosCommands.INIT)
        result.add(EscPosCommands.TEXT_ALIGN_CENTER)
        result.add(EscPosCommands.TEXT_BOLD_ON)
        result.add(EscPosCommands.TEXT_DOUBLE_HEIGHT_ON)
        result.add("Pico Printer\n".toByteArray(Charsets.UTF_8))
        result.add(EscPosCommands.TEXT_SIZE_NORMAL)
        result.add(EscPosCommands.TEXT_BOLD_OFF)
        result.add("Test Page\n".toByteArray(Charsets.UTF_8))
        result.add(separator.toByteArray(Charsets.UTF_8))
        result.add(EscPosCommands.TEXT_ALIGN_LEFT)
        result.add("Printer: $printerName\n".toByteArray(Charsets.UTF_8))
        result.add("Paper: ${paperWidthMm}mm\n".toByteArray(Charsets.UTF_8))
        result.add("DPI: $dpi\n".toByteArray(Charsets.UTF_8))
        result.add(separator.toByteArray(Charsets.UTF_8))
        result.add(EscPosCommands.TEXT_ALIGN_CENTER)
        result.add("Print OK\n".toByteArray(Charsets.UTF_8))
        result.add(EscPosCommands.feedLines(4))
        result.add(EscPosCommands.CUT_PARTIAL)

        return mergeByteArrays(result)
    }

    private fun mergeByteArrays(arrays: List<ByteArray>): ByteArray {
        val totalSize = arrays.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (arr in arrays) {
            arr.copyInto(result, offset)
            offset += arr.size
        }
        return result
    }
}
