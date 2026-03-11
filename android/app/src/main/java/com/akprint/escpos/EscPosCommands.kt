package com.akprint.escpos

object EscPosCommands {
    const val ESC: Byte = 0x1B
    const val GS: Byte = 0x1D
    const val FS: Byte = 0x1C
    const val DLE: Byte = 0x10

    val INIT: ByteArray = byteArrayOf(ESC, 0x40)
    val FEED_LINE: ByteArray = byteArrayOf(0x0A)
    val SET_LINE_SPACING_DEFAULT: ByteArray = byteArrayOf(ESC, 0x32)
    val CUT_FULL: ByteArray = byteArrayOf(GS, 0x56, 0x00)
    val CUT_PARTIAL: ByteArray = byteArrayOf(GS, 0x56, 0x01)

    // Open cash drawer on pin 2
    val CASH_DRAWER_PIN2: ByteArray = byteArrayOf(ESC, 0x70, 0x00, 0x19, 0x19)
    // Open cash drawer on pin 5
    val CASH_DRAWER_PIN5: ByteArray = byteArrayOf(ESC, 0x70, 0x01, 0x19, 0x19)

    fun feedLines(n: Int): ByteArray = byteArrayOf(ESC, 0x64, n.coerceIn(0, 255).toByte())

    // GS v 0 raster image header: m=0 (normal), xL, xH, yL, yH
    fun rasterImageHeader(widthBytes: Int, heightDots: Int): ByteArray = byteArrayOf(
        GS, 0x76, 0x30, 0x00,
        (widthBytes and 0xFF).toByte(),
        ((widthBytes shr 8) and 0xFF).toByte(),
        (heightDots and 0xFF).toByte(),
        ((heightDots shr 8) and 0xFF).toByte()
    )

    // Dot widths for paper sizes at 203 DPI (printable area, ~48mm/72mm/104mm)
    const val DOTS_58MM  = 384   // 58mm paper, ~48mm printable
    const val DOTS_80MM  = 576   // 80mm paper, ~72mm printable
    const val DOTS_104MM = 832   // 104mm paper, full width

    // Dot widths for paper sizes at 180 DPI
    const val DOTS_58MM_180DPI  = 340   // 58mm paper at 180 DPI
    const val DOTS_80MM_180DPI  = 510   // 80mm paper at 180 DPI
    const val DOTS_104MM_180DPI = 737   // 104mm paper at 180 DPI

    // Test page header bytes (ESC/POS text mode, 8 spaces per tab alignment)
    val TEXT_ALIGN_CENTER: ByteArray = byteArrayOf(ESC, 0x61, 0x01)
    val TEXT_ALIGN_LEFT: ByteArray = byteArrayOf(ESC, 0x61, 0x00)
    val TEXT_BOLD_ON: ByteArray = byteArrayOf(ESC, 0x45, 0x01)
    val TEXT_BOLD_OFF: ByteArray = byteArrayOf(ESC, 0x45, 0x00)
    val TEXT_DOUBLE_HEIGHT_ON: ByteArray = byteArrayOf(GS, 0x21, 0x11)
    val TEXT_SIZE_NORMAL: ByteArray = byteArrayOf(GS, 0x21, 0x00)
}
