package com.akprint.bridge

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.print.PrintManager
import android.provider.Settings
import android.util.Log
import com.akprint.drivers.BluetoothEscPosDriver
import com.akprint.drivers.LanEscPosDriver
import com.akprint.escpos.EscPosConverter
import com.akprint.printservice.AkPrintService
import com.akprint.printservice.PrintJobProcessor
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class PrinterModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val TAG = "PrinterModule"
        private const val PREFS_NAME = "akprint_prefs"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var eventPoller: Job? = null

    override fun getName(): String = "PrinterModule"

    override fun initialize() {
        super.initialize()
        startEventPoller()
    }

    override fun invalidate() {
        scope.cancel()
        super.invalidate()
    }

    private fun startEventPoller() {
        eventPoller = scope.launch {
            while (isActive) {
                val event = AkPrintService.pendingEvents.poll()
                if (event != null) {
                    val (name, data) = event
                    val params = jsonToWritableMap(data)
                    sendEvent(name, params)
                }
                delay(200)
            }
        }
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactApplicationContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    // --- Bluetooth Scanning ---

    @SuppressLint("MissingPermission")
    @ReactMethod
    fun scanBluetooth(promise: Promise) {
        scope.launch {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                if (adapter == null) {
                    promise.reject("BT_UNAVAILABLE", "Bluetooth is not available")
                    return@launch
                }
                if (!adapter.isEnabled) {
                    promise.reject("BT_DISABLED", "Bluetooth is disabled")
                    return@launch
                }

                val devices = mutableListOf<WritableMap>()

                // Add already bonded devices
                adapter.bondedDevices?.forEach { device ->
                    devices.add(bluetoothDeviceToMap(device))
                }

                // Discover new devices
                val discoveredDevices = mutableListOf<WritableMap>()
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == BluetoothDevice.ACTION_FOUND) {
                            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                            device?.let {
                                val map = bluetoothDeviceToMap(it)
                                discoveredDevices.add(map)
                            }
                        }
                    }
                }

                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                reactApplicationContext.registerReceiver(receiver, filter)
                adapter.startDiscovery()

                delay(12000)

                adapter.cancelDiscovery()
                try { reactApplicationContext.unregisterReceiver(receiver) } catch (_: Exception) {}

                // Merge bonded + discovered, deduplicate by address
                val seen = mutableSetOf<String>()
                val result = Arguments.createArray()
                for (d in devices + discoveredDevices) {
                    val addr = d.getString("address") ?: continue
                    if (seen.add(addr)) result.pushMap(d)
                }

                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("BT_SCAN_ERROR", e.message, e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothDeviceToMap(device: BluetoothDevice): WritableMap {
        val map = Arguments.createMap()
        map.putString("name", device.name ?: "Unknown")
        map.putString("address", device.address)
        val bondState = when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> "bonded"
            BluetoothDevice.BOND_BONDING -> "bonding"
            else -> "none"
        }
        map.putString("bondState", bondState)
        return map
    }

    // --- Printer CRUD ---

    @ReactMethod
    fun addBluetoothPrinter(data: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val printers = PrintJobProcessor.loadPrinters(reactApplicationContext)
                val printer = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("name", data.getString("name"))
                    put("type", "bluetooth")
                    put("address", data.getString("address"))
                    put("paperWidth", if (data.hasKey("paperWidth")) data.getInt("paperWidth") else 80)
                    put("isDefault", data.hasKey("isDefault") && data.getBoolean("isDefault"))
                    put("createdAt", isoNow())
                }

                if (printer.getBoolean("isDefault")) clearDefaultFlags(printers)
                printers.put(printer)
                PrintJobProcessor.savePrinters(reactApplicationContext, printers)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("ADD_BT_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun addLanPrinter(data: ReadableMap, promise: Promise) {
        scope.launch {
            try {
                val printers = PrintJobProcessor.loadPrinters(reactApplicationContext)
                val printer = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("name", data.getString("name"))
                    put("type", "lan")
                    put("host", data.getString("host"))
                    put("port", if (data.hasKey("port")) data.getInt("port") else 9100)
                    put("paperWidth", if (data.hasKey("paperWidth")) data.getInt("paperWidth") else 80)
                    put("isDefault", data.hasKey("isDefault") && data.getBoolean("isDefault"))
                    put("createdAt", isoNow())
                }

                if (printer.getBoolean("isDefault")) clearDefaultFlags(printers)
                printers.put(printer)
                PrintJobProcessor.savePrinters(reactApplicationContext, printers)
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("ADD_LAN_ERROR", e.message, e)
            }
        }
    }

    @ReactMethod
    fun getPrinters(promise: Promise) {
        try {
            val printers = PrintJobProcessor.loadPrinters(reactApplicationContext)
            promise.resolve(jsonArrayToWritableArray(printers))
        } catch (e: Exception) {
            promise.reject("GET_PRINTERS_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun deletePrinter(id: String, promise: Promise) {
        try {
            val printers = PrintJobProcessor.loadPrinters(reactApplicationContext)
            val filtered = JSONArray()
            for (i in 0 until printers.length()) {
                val p = printers.getJSONObject(i)
                if (p.getString("id") != id) filtered.put(p)
            }
            PrintJobProcessor.savePrinters(reactApplicationContext, filtered)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("DELETE_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun setDefaultPrinter(id: String, promise: Promise) {
        try {
            val printers = PrintJobProcessor.loadPrinters(reactApplicationContext)
            for (i in 0 until printers.length()) {
                val p = printers.getJSONObject(i)
                p.put("isDefault", p.getString("id") == id)
            }
            PrintJobProcessor.savePrinters(reactApplicationContext, printers)

            val settings = PrintJobProcessor.loadSettings(reactApplicationContext)
            settings.put("defaultPrinterId", id)
            PrintJobProcessor.saveSettings(reactApplicationContext, settings)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SET_DEFAULT_ERROR", e.message, e)
        }
    }

    // --- Test Print ---

    @ReactMethod
    fun testPrint(printerId: String, promise: Promise) {
        scope.launch {
            try {
                val printers = PrintJobProcessor.loadPrinters(reactApplicationContext)
                var printerData: JSONObject? = null
                for (i in 0 until printers.length()) {
                    val p = printers.getJSONObject(i)
                    if (p.getString("id") == printerId) { printerData = p; break }
                }

                if (printerData == null) {
                    promise.reject("NOT_FOUND", "Printer not found")
                    return@launch
                }

                val driver = PrintJobProcessor.buildDriver(printerData)
                if (!driver.connect()) {
                    promise.reject("CONNECT_FAIL", "Could not connect to printer")
                    return@launch
                }

                try {
                    val testData = EscPosConverter.buildTestPage(
                        printerData.getString("name"),
                        printerData.optInt("paperWidth", 80)
                    )
                    if (driver.send(testData)) {
                        PrintJobProcessor.appendLog(reactApplicationContext, "info", "Test print OK: ${printerData.getString("name")}", printerId)
                        promise.resolve(null)
                    } else {
                        promise.reject("SEND_FAIL", "Failed to send test page")
                    }
                } finally {
                    driver.disconnect()
                }
            } catch (e: Exception) {
                promise.reject("TEST_PRINT_ERROR", e.message, e)
            }
        }
    }

    // --- Settings ---

    @ReactMethod
    fun getSettings(promise: Promise) {
        try {
            val settings = PrintJobProcessor.loadSettings(reactApplicationContext)
            promise.resolve(jsonToWritableMap(settings))
        } catch (e: Exception) {
            promise.reject("GET_SETTINGS_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun saveSettings(data: ReadableMap, promise: Promise) {
        try {
            val settings = readableMapToJson(data)
            PrintJobProcessor.saveSettings(reactApplicationContext, settings)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SAVE_SETTINGS_ERROR", e.message, e)
        }
    }

    // --- Logs & History ---

    @ReactMethod
    fun getLogs(promise: Promise) {
        try {
            val prefs = reactApplicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val logsJson = prefs.getString("logs", "[]") ?: "[]"
            val logs = JSONArray(logsJson)
            promise.resolve(jsonArrayToWritableArray(logs))
        } catch (e: Exception) {
            promise.reject("GET_LOGS_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun clearLogs(promise: Promise) {
        try {
            val prefs = reactApplicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("logs", "[]").apply()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("CLEAR_LOGS_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getPrintHistory(promise: Promise) {
        try {
            val prefs = reactApplicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val historyJson = prefs.getString("history", "[]") ?: "[]"
            val history = JSONArray(historyJson)
            promise.resolve(jsonArrayToWritableArray(history))
        } catch (e: Exception) {
            promise.reject("GET_HISTORY_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun clearPrintHistory(promise: Promise) {
        try {
            val prefs = reactApplicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("history", "[]").apply()
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("CLEAR_HISTORY_ERROR", e.message, e)
        }
    }

    // --- Status Check ---

    @ReactMethod
    fun checkPrinterStatus(printerId: String, promise: Promise) {
        scope.launch {
            try {
                val printers = PrintJobProcessor.loadPrinters(reactApplicationContext)
                var printerData: JSONObject? = null
                for (i in 0 until printers.length()) {
                    val p = printers.getJSONObject(i)
                    if (p.getString("id") == printerId) { printerData = p; break }
                }

                if (printerData == null) {
                    promise.resolve("unknown")
                    return@launch
                }

                val driver = PrintJobProcessor.buildDriver(printerData)
                val status = if (driver.connect()) {
                    driver.disconnect()
                    "online"
                } else {
                    "offline"
                }
                promise.resolve(status)
            } catch (e: Exception) {
                promise.resolve("offline")
            }
        }
    }

    // --- Print Service Status ---

    @ReactMethod
    fun isPrintServiceEnabled(promise: Promise) {
        try {
            val printManager = reactApplicationContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                promise.resolve(false)
                return
            }
            val enabledServices = printManager.printServices
            val isEnabled = enabledServices?.any {
                it.id.flattenToString().startsWith("com.akprint/")
            } ?: false
            promise.resolve(isEnabled)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check print service status", e)
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun openPrintServiceSettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_PRINT_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            reactApplicationContext.startActivity(intent)
            promise.resolve(null)
        } catch (e: Exception) {
            promise.reject("SETTINGS_ERROR", "Could not open print settings", e)
        }
    }

    // --- Event Listener Registration (required by NativeEventEmitter) ---

    @ReactMethod
    fun addListener(eventName: String) {
        // Required for NativeEventEmitter
    }

    @ReactMethod
    fun removeListeners(count: Int) {
        // Required for NativeEventEmitter
    }

    // --- Helpers ---

    private fun clearDefaultFlags(printers: JSONArray) {
        for (i in 0 until printers.length()) {
            printers.getJSONObject(i).put("isDefault", false)
        }
    }

    private fun isoNow(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())

    private fun jsonToWritableMap(json: JSONObject): WritableMap {
        val map = Arguments.createMap()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val value = json.get(key)) {
                is String -> map.putString(key, value)
                is Int -> map.putInt(key, value)
                is Long -> map.putDouble(key, value.toDouble())
                is Double -> map.putDouble(key, value)
                is Boolean -> map.putBoolean(key, value)
                is JSONObject -> map.putMap(key, jsonToWritableMap(value))
                is JSONArray -> map.putArray(key, jsonArrayToWritableArray(value))
                JSONObject.NULL -> map.putNull(key)
                else -> map.putString(key, value.toString())
            }
        }
        return map
    }

    private fun jsonArrayToWritableArray(arr: JSONArray): WritableArray {
        val result = Arguments.createArray()
        for (i in 0 until arr.length()) {
            when (val value = arr.get(i)) {
                is String -> result.pushString(value)
                is Int -> result.pushInt(value)
                is Long -> result.pushDouble(value.toDouble())
                is Double -> result.pushDouble(value)
                is Boolean -> result.pushBoolean(value)
                is JSONObject -> result.pushMap(jsonToWritableMap(value))
                is JSONArray -> result.pushArray(jsonArrayToWritableArray(value))
                JSONObject.NULL -> result.pushNull()
                else -> result.pushString(value.toString())
            }
        }
        return result
    }

    private fun readableMapToJson(map: ReadableMap): JSONObject {
        val json = JSONObject()
        val iterator = map.keySetIterator()
        while (iterator.hasNextKey()) {
            val key = iterator.nextKey()
            when (map.getType(key)) {
                ReadableType.String -> json.put(key, map.getString(key))
                ReadableType.Number -> json.put(key, map.getDouble(key))
                ReadableType.Boolean -> json.put(key, map.getBoolean(key))
                ReadableType.Map -> json.put(key, readableMapToJson(map.getMap(key)!!))
                ReadableType.Array -> json.put(key, map.getArray(key).toString())
                ReadableType.Null -> json.put(key, JSONObject.NULL)
            }
        }
        return json
    }
}
