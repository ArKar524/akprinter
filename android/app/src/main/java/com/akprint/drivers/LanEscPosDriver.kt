package com.akprint.drivers

import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class LanEscPosDriver(
    private val host: String,
    private val port: Int,
    override val printerId: String
) : EscPosDriver {

    companion object {
        private const val TAG = "LanEscPosDriver"
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val SO_TIMEOUT_MS = 10000
        private const val CHUNK_SIZE = 4096
    }

    private var socket: Socket? = null
    private var outputStream: OutputStream? = null

    @Synchronized
    override fun connect(): Boolean {
        return try {
            val sock = Socket()
            sock.soTimeout = SO_TIMEOUT_MS
            sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket = sock
            outputStream = sock.getOutputStream()
            Log.d(TAG, "Connected to $host:$port")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Connect failed for $host:$port", e)
            disconnect()
            false
        }
    }

    @Synchronized
    override fun send(data: ByteArray): Boolean {
        val os = outputStream ?: return false
        return try {
            var offset = 0
            while (offset < data.size) {
                val length = minOf(CHUNK_SIZE, data.size - offset)
                os.write(data, offset, length)
                os.flush()
                offset += length
            }
            true
        } catch (e: IOException) {
            Log.e(TAG, "Send failed for $host:$port", e)
            false
        }
    }

    @Synchronized
    override fun disconnect() {
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        outputStream = null
        socket = null
    }

    override fun isConnected(): Boolean {
        val s = socket ?: return false
        return s.isConnected && !s.isClosed
    }
}
