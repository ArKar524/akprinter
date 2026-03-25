# Architecture Overview

## What is Pico Printer (AkPrint)?

Pico Printer is an Android app that turns your phone into a thermal receipt printer hub. It connects to **Bluetooth** and **LAN/IP** ESC/POS thermal printers and exposes them as a native **Android Print Service** — meaning any app on your phone (Chrome, PDF viewers, gallery, etc.) can print to your thermal printers via the standard system print dialog.

The app also handles direct printing via Android Share intents (share a PDF, image, or text → print immediately to default printer) and provides a full management UI for adding printers, configuring settings, viewing logs, and tracking print history.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI Framework | React Native 0.84.1 (New Architecture / Fabric) |
| JS Engine | Hermes |
| Language (UI) | TypeScript + React 19 |
| Language (Native) | Kotlin |
| Navigation | React Navigation v7 (native-stack) |
| Persistence (RN) | AsyncStorage v2 |
| Persistence (Native) | SharedPreferences (`akprint_prefs`) |
| Icons | HugeIcons (SVG via react-native-svg) |
| Concurrency (Native) | Kotlin Coroutines |

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    React Native UI                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐ │
│  │  Screens  │ │Components│ │  Hooks   │ │ Navigation │ │
│  └─────┬────┘ └──────────┘ └─────┬────┘ └────────────┘ │
│        │                         │                       │
│        └─────────┬───────────────┘                       │
│                  ▼                                        │
│         PrinterBridge (NativeModules)                     │
├──────────────────┬──────────────────────────────────────┤
│                  ▼            Native Kotlin               │
│          PrinterModule ◄──── Event Poller                 │
│            │       │                                      │
│     ┌──────┘       └──────┐                               │
│     ▼                     ▼                               │
│  PrintJobProcessor   SharedPreferences                    │
│     │       │                                             │
│     ▼       ▼                                             │
│  Drivers   EscPosConverter                                │
│  ┌─────┐ ┌─────┐    │                                    │
│  │ BT  │ │ LAN │    ├─ PDF → Bitmap (PdfRenderer)        │
│  └─────┘ └─────┘    ├─ Bitmap → ESC/POS raster           │
│                      └─ Floyd-Steinberg dithering         │
│                                                           │
│  ┌───────────────────────────────────────────┐            │
│  │         Android Print Service              │            │
│  │  AkPrintService ← System print dialog      │            │
│  │  AkPrinterDiscoverySession                  │            │
│  │  PrintJobProcessor.processJobFromFile()     │            │
│  └───────────────────────────────────────────┘            │
│                                                           │
│  ┌───────────────────────────────────────────┐            │
│  │         Intent Activities                  │            │
│  │  IntentPdfActivity   (Share PDF → Print)   │            │
│  │  IntentImageActivity (Share Image → Print) │            │
│  │  IntentTextActivity  (Share Text → Print)  │            │
│  └───────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────┘
```

## Data Flow

### Normal Print (via System Print Dialog)

1. User selects "Print" in any Android app
2. System shows print dialog → discovers Pico Printer printers via `AkPrinterDiscoverySession`
3. User selects printer → system queues print job
4. `AkPrintService.onPrintJobQueued()` receives the job
5. PDF is saved to disk, job is immediately `complete()`'d (prevents system error notifications)
6. Background coroutine: PDF → bitmap → ESC/POS bytes via `EscPosConverter`
7. Driver (Bluetooth or LAN) sends bytes to printer
8. Events emitted to React Native UI via event queue

### Share Intent Print (Direct)

1. User shares PDF/image/text to "Pico Printer"
2. Corresponding `IntentActivity` opens as a dialog
3. Finds default printer, converts content to ESC/POS
4. Sends directly to printer via driver
5. Shows toast on success, dialog on error

### React Native Test Print

1. User taps "Test Print" in the app UI
2. `PrinterBridge.testPrint()` → `PrinterModule.testPrint()` (native)
3. Builds text-mode ESC/POS test page
4. Connects driver, sends, disconnects

## Key Design Decisions

### Why complete() immediately?

The `AkPrintService` calls `printJob.complete()` before actual printing starts. This is the "ESC app pattern" — it prevents Android from showing annoying "Print service error" notifications when printing takes a while or the service is temporarily unresponsive. Actual failures are reported via events to the React Native UI.

### Dual Persistence

- **React Native side**: Uses AsyncStorage for any RN-only data (logs cache, history cache)
- **Native Kotlin side**: Uses SharedPreferences (`akprint_prefs`) as the source of truth for printers, settings, logs, and history — because the Print Service runs independently of React Native

### Event Bridge

The Print Service is a system service that runs independently. It can't directly call React Native JS. Instead:
- `AkPrintService` pushes events to a static `ConcurrentLinkedQueue`
- `PrinterModule` polls this queue every 200ms when RN is active
- Events are forwarded to JS via `RCTDeviceEventEmitter`

### Chunked Sending

Cheap Bluetooth thermal printers have tiny receive buffers. The `BluetoothEscPosDriver` sends data in 512-byte chunks with 20ms delays between chunks to prevent buffer overflow and garbled output. LAN printers use 4KB chunks with no delay.
