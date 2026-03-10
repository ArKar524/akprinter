package com.akprint.drivers

interface EscPosDriver {
    val printerId: String
    fun connect(): Boolean
    fun send(data: ByteArray): Boolean
    fun disconnect()
    fun isConnected(): Boolean
}
