# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**AkPrint** — A React Native 0.84.1 Android receipt printer app that implements a native Android `PrintService` (appears in the system print dialog), drives Bluetooth and LAN ESC/POS thermal receipt printers, and provides a React Native UI for printer setup and management.

- All printing logic is **native Kotlin** (ESC/POS commands, PDF-to-bitmap conversion, Bluetooth/TCP drivers)
- React Native handles **UI, navigation, and persistence** (AsyncStorage)
- Uses **New Architecture** (Fabric) with Hermes JS engine, React 19, TypeScript

## Commands

- **Start Metro dev server:** `npm start`
- **Run on Android:** `npm run android` (requires JAVA_HOME set)
- **Lint:** `npm run lint`
- **Run tests:** `npm test`
- **Clean Android build:** `cd android && ./gradlew clean && cd ..`
- **Production APK:** `cd android && ./gradlew assembleRelease && cd ..`

## Project Structure

```
src/
├── components/       # Shared UI components
│   ├── Icons.tsx     # CENTRALIZED icon wrappers (only file importing @hugeicons)
│   ├── PrinterCard.tsx, StatusBadge.tsx, EmptyState.tsx
│   ├── Button.tsx, Input.tsx, ListItem.tsx, SectionHeader.tsx
├── screens/          # 8 screens (Home, PrintersList, AddBluetooth/Lan, PrinterDetail, Settings, Logs, PrintHistory)
├── navigation/       # React Navigation v7 native-stack (AppNavigator.tsx, types.ts)
├── hooks/            # Custom hooks (usePrinters, usePrinterStatus, useBluetoothScan, useLogs, usePrintHistory, useSettings)
├── services/         # PrinterBridge (NativeModules bridge), StorageService
├── types/            # TypeScript types (printer.ts, logs.ts, settings.ts)
└── utils/            # Constants, formatters, storage helpers

android/app/src/main/java/com/akprint/
├── MainActivity.kt, MainApplication.kt
├── bridge/           # PrinterModule.kt (NativeModule), PrinterPackage.kt
├── drivers/          # EscPosDriver interface, BluetoothEscPosDriver, LanEscPosDriver
├── escpos/           # EscPosCommands (byte sequences), EscPosConverter (PDF→ESC/POS via PdfRenderer)
└── printservice/     # AkPrintService, AkPrinterDiscoverySession, PrintJobProcessor
```

## Key Architecture Decisions

- **Icons:** All hugeicons imports are centralized in `src/components/Icons.tsx`. No other file should import from `@hugeicons/react-native` or `@hugeicons/core-free-icons` directly.
- **Native bridge:** Uses legacy NativeModules pattern (manually registered via `PrinterPackage`), not TurboModules.
- **Persistence:** React Native side uses AsyncStorage; native Kotlin side uses SharedPreferences (`akprint_prefs`). Both store printers/settings/logs/history as JSON strings.
- **PrintService:** `AkPrintService` is an Android system print service. Must be enabled in Android Settings → Printing. Uses `PrintJobProcessor` for job execution with retry logic.
- **ESC/POS:** `EscPosConverter` renders PDF pages to bitmaps at 203 DPI via Android's `PdfRenderer`, then converts to ESC/POS raster commands.
- **Splash screen:** Native Android splash using `SplashTheme` (blue background + app icon) in `AndroidManifest.xml`, switched to `AppTheme` in `MainActivity.onCreate()`.

## Key Dependencies

- `react-native` 0.84.1, `react` 19.2.3
- `@react-navigation/native` + `@react-navigation/native-stack` v7
- `@react-native-async-storage/async-storage` ^2.2.0 (v3.x has build issues)
- `@hugeicons/react-native` + `@hugeicons/core-free-icons` (SVG icons)
- `react-native-svg` ^15.15.3 (required as direct dep for native linking)
- `react-native-safe-area-context`, `react-native-screens`

## Android Configuration

- **Namespace:** `com.akprint`
- **minSdk:** 24, **targetSdk:** 35, **Kotlin:** 2.1.20
- **New Architecture:** enabled (`newArchEnabled=true` in `gradle.properties`)
- **Permissions:** INTERNET, BLUETOOTH_CONNECT, BLUETOOTH_SCAN, ACCESS_FINE_LOCATION (runtime-requested before BT scan)

## Known Issues & Gotchas

- `@react-native-async-storage/async-storage` v3.x breaks Android build — stay on v2.x
- `react-native-svg` must be a **direct** dependency (not just transitive via hugeicons) for autolinking to compile native views
- `@hugeicons/core-free-icons` v4.0.0 has `"type": "module"` which can cause Metro issues; icons may appear as `undefined` at runtime if not resolved correctly
- After adding any package with native code, **full native rebuild required** (`npx react-native run-android`), not just Metro reload
- PrintService must be manually enabled by user in Android Settings → Printing
- `FileTextIcon` does NOT exist in `@hugeicons/core-free-icons` — use `File01Icon` instead

## Git & Deployment

- **Remote:** `git@github-arkar524:ArKar524/akprinter.git` (uses SSH host alias for ArKar524 GitHub account)
- **SSH config:** `~/.ssh/config` has `github-arkar524` host alias pointing to `~/.ssh/id_ed25519_arkar524`

## Node Requirement

Node >= 22.11.0 (specified in `package.json` engines)
