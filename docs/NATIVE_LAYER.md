# Native Kotlin Layer

All printing logic lives in native Kotlin. React Native only handles the UI.

## Package Structure

```
com.akprint/
├── MainActivity.kt              # App entry, splash → main theme switch
├── MainApplication.kt           # RN Application host
├── bridge/
│   ├── PrinterModule.kt         # NativeModule — RN ↔ Kotlin bridge
│   └── PrinterPackage.kt        # Registers PrinterModule with RN
├── drivers/
│   ├── EscPosDriver.kt          # Interface: connect/send/disconnect/isConnected
│   ├── BluetoothEscPosDriver.kt # Bluetooth SPP (RFCOMM) driver
│   └── LanEscPosDriver.kt       # TCP socket driver
├── escpos/
│   ├── EscPosCommands.kt        # ESC/POS byte constants
│   └── EscPosConverter.kt       # PDF/bitmap → ESC/POS conversion
├── intents/
│   ├── BaseIntentPrintActivity.kt # Shared intent print logic
│   ├── IntentPdfActivity.kt      # Share PDF → print
│   ├── IntentImageActivity.kt    # Share Image → print
│   └── IntentTextActivity.kt     # Share Text → print
└── printservice/
    ├── AkPrintService.kt         # Android PrintService implementation
    ├── AkPrinterDiscoverySession.kt # Printer discovery for system dialog
    └── PrintJobProcessor.kt      # Core print job execution & persistence
```

## PrinterModule (NativeModule Bridge)

The bridge between React Native and native Kotlin. Registered via legacy `NativeModules` pattern (not TurboModules).

### Exposed Methods

| Method | Description |
|--------|-------------|
| `scanBluetooth()` | Discover Bluetooth devices (bonded + new, 12s scan) |
| `addBluetoothPrinter(data)` | Add a Bluetooth printer to the registry |
| `addLanPrinter(data)` | Add a LAN/IP printer to the registry |
| `getPrinters()` | Get all registered printers |
| `deletePrinter(id)` | Remove a printer |
| `setDefaultPrinter(id)` | Set a printer as default |
| `testPrint(printerId)` | Send a test page to a printer |
| `getSettings()` | Get app settings |
| `saveSettings(data)` | Save app settings |
| `getLogs()` | Get log entries |
| `clearLogs()` | Clear all logs |
| `getPrintHistory()` | Get print history |
| `clearPrintHistory()` | Clear all history |
| `checkPrinterStatus(id)` | Probe printer connectivity (connect/disconnect) |
| `isPrintServiceEnabled()` | Check if print service is bound by system |
| `openPrintServiceSettings()` | Open Android print settings |
| `getPendingJobs()` | Get queued pending jobs |
| `deletePendingJob(id)` | Delete a pending job |
| `printPendingJob(jobId, printerId)` | Print a pending job to a specific printer |

### Events Emitted

| Event | Payload | Trigger |
|-------|---------|---------|
| `PrintJobStarted` | `{jobId, printerName}` | Print job begins |
| `PrintJobCompleted` | `{jobId, printerName, pageCount, duration}` | Print job succeeds |
| `PrintJobFailed` | `{jobId, error}` | Print job fails |
| `PrinterStatusChanged` | `{printerId, status}` | Printer status changes |
| `PendingJobAdded` | `{jobId}` | New pending job saved |

## EscPosDriver Interface

```kotlin
interface EscPosDriver {
    val printerId: String
    fun connect(): Boolean
    fun send(data: ByteArray): Boolean
    fun disconnect()
    fun isConnected(): Boolean
}
```

### BluetoothEscPosDriver

- Uses Bluetooth SPP (Serial Port Profile) via RFCOMM
- UUID: `00001101-0000-1000-8000-00805F9B34FB`
- Sends in **512-byte chunks** with **20ms inter-chunk delay** (prevents buffer overflow on cheap printers)
- Synchronized methods to prevent concurrent access issues

### LanEscPosDriver

- Raw TCP socket to printer IP:port (default port 9100)
- Connect timeout: 5 seconds
- Socket timeout: 10 seconds
- Sends in **4096-byte chunks** (no delay needed — network buffers are larger)

## EscPosConverter

Converts documents to ESC/POS printer commands.

### PDF Conversion Pipeline

```
PDF file
  ↓ PdfRenderer (Android API)
  ↓ Render each page to Bitmap at target DPI
  ↓ Crop trailing whitespace (saves paper)
  ↓ Scale to target width in dots
  ↓ Convert to 1-bit monochrome:
  │   ├─ Threshold mode: pixel < 128 luminance → black
  │   └─ Dither mode: Floyd-Steinberg error diffusion
  ↓ Pack into ESC/POS raster image commands (GS v 0)
  ↓ Add cut/feed/cash drawer commands
  → Final ByteArray ready to send
```

### Paper Width → Dot Width Mapping

| Paper | 203 DPI | 180 DPI |
|-------|---------|---------|
| 58mm  | 384 dots | 340 dots |
| 80mm  | 576 dots | 510 dots |
| 104mm | 832 dots | 737 dots |

### ESC/POS Commands Used

| Command | Bytes | Purpose |
|---------|-------|---------|
| Initialize | `ESC @` | Reset printer state |
| Feed lines | `ESC d n` | Feed n lines |
| Raster image | `GS v 0` | Print raster bitmap |
| Partial cut | `GS V 1` | Partial paper cut |
| Full cut | `GS V 0` | Full paper cut |
| Cash drawer | `ESC p 0/1` | Open cash drawer pin 2/5 |
| Text align | `ESC a 0/1` | Left/center alignment |
| Bold on/off | `ESC E 0/1` | Bold text toggle |
| Double height | `GS ! 0x11` | Double width+height text |

## Android Print Service

### AkPrintService

Registered in `AndroidManifest.xml` as a system print service. Must be manually enabled by the user in **Settings → Printing**.

**Lifecycle:**
- `onConnected()` — System binds the service; sets `service_enabled` flag
- `onDisconnected()` — System unbinds; clears flag
- `onPrintJobQueued()` — Receives print jobs from system dialog
- `onRequestCancelPrintJob()` — Cancels in-flight background jobs

**Job Processing Strategy:**
1. Save PDF to disk immediately (while document data is accessible)
2. Call `printJob.complete()` immediately (prevents system error notifications)
3. Background coroutine handles actual printing
4. Failures reported via event queue → React Native UI

### AkPrinterDiscoverySession

Manages printer discovery for the system print dialog.

- `onStartPrinterDiscovery()` — Reports all registered printers from SharedPreferences
- `onStartPrinterStateTracking()` — Probes actual connectivity (connect/disconnect test)
- Supports multiple simultaneous printer tracking via coroutine jobs
- Builds `PrinterCapabilitiesInfo` with correct media size based on paper width + DPI

### PrintJobProcessor

Central orchestration object. Handles:
- Loading/saving printers and settings from SharedPreferences
- Building the correct driver for a printer type
- PDF → ESC/POS conversion with settings
- Retry logic (configurable: 1-5 retries with exponential backoff)
- Pending job management (save PDF to disk, queue metadata, print later)
- Log and history persistence (capped at 500 logs, 1000 history entries)
- Event emission to React Native via `AkPrintService.pendingEvents` queue

## Intent Activities

Three transparent dialog activities for Android Share intents:

| Activity | Handles | MIME Types |
|----------|---------|------------|
| `IntentPdfActivity` | PDF files | `application/pdf` |
| `IntentImageActivity` | Images | `image/*` |
| `IntentTextActivity` | Plain text | `text/plain` |

All extend `BaseIntentPrintActivity` which provides:
- Default printer lookup
- Settings loading
- Progress dialog UI
- Printing coroutine with cancel support
- Error dialog handling

**Flow:** Find default printer → convert content → connect → send → toast/finish

## Permissions

| Permission | Purpose | When Requested |
|-----------|---------|----------------|
| `INTERNET` | LAN printer TCP connections | Always granted |
| `BLUETOOTH_CONNECT` | Connect to paired BT printers | Android 12+ runtime |
| `BLUETOOTH_SCAN` | Discover new BT printers | Android 12+ runtime |
| `ACCESS_FINE_LOCATION` | BT discovery on Android < 12 | Runtime |
| `WAKE_LOCK` | Keep alive during printing | Always granted |
| `POST_NOTIFICATIONS` | Print status notifications | Android 13+ |

## Android Configuration

- **Namespace:** `com.akprint`
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 35 (Android 15)
- **Kotlin:** 2.1.20
- **New Architecture:** enabled (Fabric)
- **Hermes:** enabled
