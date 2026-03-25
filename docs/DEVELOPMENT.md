# Development Guide

## Prerequisites

- Node.js >= 22.11.0
- Java / JDK (for Android builds)
- Android SDK (API 35)
- Android device or emulator (minSdk 24)

## Setup

```bash
# Clone the repo
git clone git@github.com:ArKar524/akprinter.git
cd akprinter

# Install JS dependencies
npm install

# Start Metro bundler
npm start

# Build and run on Android
npm run android
```

## Commands

| Command | Description |
|---------|-------------|
| `npm start` | Start Metro dev server |
| `npm run android` | Build and run on Android device/emulator |
| `npm run ios` | Build and run on iOS (not actively used) |
| `npm run lint` | ESLint check |
| `npm test` | Run Jest tests |
| `cd android && ./gradlew clean` | Clean Android build artifacts |
| `cd android && ./gradlew assembleRelease` | Build release APK |

## Adding a New Screen

1. Create the screen component in `src/screens/NewScreen.tsx`
2. Add the route to `src/navigation/types.ts`:
   ```typescript
   export type RootStackParamList = {
     // ... existing routes
     NewScreen: { someParam?: string };
   };
   ```
3. Register it in `src/navigation/AppNavigator.tsx`:
   ```tsx
   <Stack.Screen name="NewScreen" component={NewScreen} options={{title: 'New Screen'}} />
   ```

## Adding a New Native Method

1. Add the method in `PrinterModule.kt`:
   ```kotlin
   @ReactMethod
   fun myNewMethod(param: String, promise: Promise) {
       scope.launch {
           try {
               // ... implementation
               promise.resolve(result)
           } catch (e: Exception) {
               promise.reject("ERROR_CODE", e.message, e)
           }
       }
   }
   ```

2. Add the TypeScript binding in `src/services/PrinterBridge.ts`:
   ```typescript
   myNewMethod(param: string): Promise<SomeType> {
     return PrinterModule.myNewMethod(param);
   }
   ```

## Adding Icons

**All icon imports must go through `src/components/Icons.tsx`** — no other file should import from `@hugeicons` directly.

```tsx
// In src/components/Icons.tsx
import { SomeNewIcon as SomeNewIconBase } from '@hugeicons/core-free-icons';

export function SomeNewIcn({ size = 24, color = '#000' }: IconProps) {
  return <HugeIcon icon={SomeNewIconBase} size={size} color={color} />;
}
```

> ⚠️ `FileTextIcon` does NOT exist in `@hugeicons/core-free-icons` — use `File01Icon` instead.

## Known Gotchas

### AsyncStorage Version
`@react-native-async-storage/async-storage` **v3.x breaks Android build**. Stay on v2.x.

### react-native-svg
Must be a **direct** dependency in `package.json` (not just transitive via hugeicons). Required for native autolinking to compile SVG views.

### HugeIcons Module Issues
`@hugeicons/core-free-icons` v4.0.0 has `"type": "module"` which can cause Metro resolution issues. Icons may appear as `undefined` at runtime if not resolved correctly.

### Native Rebuild Required
After adding any package with native code, do a **full native rebuild**:
```bash
npx react-native run-android
```
Hot reload / Metro restart is NOT sufficient.

### Print Service Activation
The Android Print Service must be **manually enabled** by the user:
**Settings → Printing → Pico Printer → Enable**

The app shows a warning banner when the service is not enabled and provides a button to open the settings.

## Persistence & Data

All persistent data lives in `SharedPreferences` under `akprint_prefs`:

| Key | Type | Description |
|-----|------|-------------|
| `printers` | JSON array | Registered printer configurations |
| `settings` | JSON object | App settings |
| `logs` | JSON array | Log entries (max 500) |
| `history` | JSON array | Print history (max 1000) |
| `pending_jobs` | JSON array | Pending job metadata |
| `service_enabled` | boolean | Whether print service is bound |

Pending job PDF files are stored in `filesDir/pending_jobs/{jobId}.pdf`.
Active print job PDFs are stored in `filesDir/print_jobs/{jobId}.pdf` (deleted after printing).

## Git & SSH

- **Remote:** `git@github-arkar524:ArKar524/akprinter.git` (SSH host alias)
- **SSH config:** Uses `github-arkar524` host alias pointing to a dedicated SSH key

## Release Build

```bash
cd android
./gradlew assembleRelease
```

APK output: `android/app/build/outputs/apk/release/app-release.apk`

> ⚠️ Currently uses the debug keystore for release signing. For production, generate a proper release keystore.
