package com.akprint.drivers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BluetoothEscPosDriver(
    private val address: String,
    override val printerId: String
) : EscPosDriver {

    companion object {
        private const val TAG = "BtEscPosDriver"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val CHUNK_SIZE = 4096
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    @Synchronized
    override fun connect(): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
                ?: throw IOException("Bluetooth not available")
            val device = adapter.getRemoteDevice(address)
            adapter.cancelDiscovery()

            val btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            btSocket.connect()
            socket = btSocket
            outputStream = btSocket.outputStream
            Log.d(TAG, "Connected to $address")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Connect failed for $address", e)
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
            Log.e(TAG, "Send failed for $address", e)
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

    override fun isConnected(): Boolean = socket?.isConnected == true
}
