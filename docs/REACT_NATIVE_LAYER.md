# React Native Layer

The React Native layer handles all UI, navigation, and user interaction. It communicates with native Kotlin via the `PrinterBridge` service.

## Directory Structure

```
src/
├── components/       # Shared, reusable UI components
├── screens/          # Full-screen views (9 screens)
├── navigation/       # React Navigation setup + type definitions
├── hooks/            # Custom React hooks for data fetching
├── services/         # Native bridge + storage abstraction
├── types/            # TypeScript type definitions
└── utils/            # Constants, formatters, storage helpers
```

## Navigation

Uses **React Navigation v7** with a single `NativeStackNavigator`.

```
Home (initial)
├── PrintersList
│   ├── AddBluetoothPrinter
│   └── AddLanPrinter
├── PrinterDetail (params: {printerId})
├── PendingJobs
├── Settings
├── Logs
└── PrintHistory
```

### Route Parameters

| Route | Params | Description |
|-------|--------|-------------|
| `Home` | none | Dashboard with default printer, quick actions, recent jobs |
| `PrintersList` | none | All registered printers |
| `AddBluetoothPrinter` | none | Bluetooth scan + add form |
| `AddLanPrinter` | none | IP/port entry + add form |
| `PrinterDetail` | `{printerId: string}` | Single printer info, status, test print, delete |
| `PendingJobs` | none | Jobs saved but not yet printed |
| `Settings` | none | App-wide print defaults and behavior |
| `Logs` | none | Debug/info/warn/error log viewer |
| `PrintHistory` | none | All past print jobs with success/failure status |

## Screens

### HomeScreen
The main dashboard. Displays:
- **Print service warning banner** — if the service isn't enabled in Android settings
- **Default printer card** — with live connection status (polled every 30s)
- **Quick actions** — Pending, Printers, Settings, History (with badge counts)
- **Recent jobs** — last 3 print history entries with success/fail indicators

Listens for `PrintJobCompleted` and `PendingJobAdded` events to auto-refresh.

### PrintersListScreen
FlatList of all registered printers. Each row shows printer name, type, status badge. Pull-to-refresh. Two FABs at bottom: "+ Bluetooth" and "+ LAN / IP".

### AddBluetoothPrinterScreen
Three-step flow:
1. **Discover** — scans for BT devices (12 seconds), requests runtime permissions
2. **Select** — tap a discovered device (bonded devices shown first, with "Paired" badge)
3. **Configure** — set name, paper width (58/80/104mm), default toggle

### AddLanPrinterScreen
Form-based:
- IP address (validated with regex)
- Port (default 9100, validated 1-65535)
- Printer name
- Paper width selector
- Default toggle

### PrinterDetailScreen
Shows full printer info:
- Live connection status with "Check Now" button
- Printer metadata (name, type, address/host, paper width, created date)
- Actions: Test Print, Set as Default, Delete (with confirmation dialog)

### SettingsScreen
App-wide print configuration:
- **Print Service status** — enabled/disabled indicator with "Enable" button
- **Print Defaults** — paper width, copies, DPI (180/203), image mode (threshold/dither)
- **Behavior** — auto cut (none/partial/full), cash drawer (off/drawer1/drawer2), lines before cut, disconnect delay, retry on failure + retry count
- **Debug** — debug logging toggle

### PendingJobsScreen
Lists jobs that were saved when no printer was available. Each job shows document name, page count, creation time. Actions: print to a specific printer, or delete.

### LogsScreen
Filterable log viewer (all/info/warn/error/debug). Shows timestamp, level badge, message. Pull-to-refresh. Clear all button.

### PrintHistoryScreen
Chronological list of all print attempts. Each entry shows printer name, page count, duration, timestamp, and success/failure status with error messages.

## Hooks

### `usePrinters()`
Manages the printer list. Loads on mount, auto-refreshes on `PrinterStatusChanged` events.

```typescript
const {
  printers,         // Printer[]
  defaultPrinter,   // Printer | null
  loading,          // boolean
  error,            // string | null
  refresh,          // () => Promise<void>
  deletePrinter,    // (id: string) => Promise<void>
  setDefault,       // (id: string) => Promise<void>
} = usePrinters();
```

### `usePrinterStatus(printerId)`
Polls printer connectivity status every 30 seconds. Also listens for `PrinterStatusChanged` events.

```typescript
const {
  status,       // 'online' | 'offline' | 'connecting' | 'unknown'
  checking,     // boolean
  lastChecked,  // Date | null
  checkNow,     // () => Promise<void>
} = usePrinterStatus(printerId);
```

### `useBluetoothScan()`
Handles Bluetooth device discovery with permission requests.

```typescript
const {
  devices,    // BluetoothDevice[]
  scanning,   // boolean
  error,      // string | null
  startScan,  // () => Promise<void>
} = useBluetoothScan();
```

### `useSettings()`
Loads and saves app settings, merging with defaults.

```typescript
const {
  settings,       // AppSettings
  loading,        // boolean
  saving,         // boolean
  updateSettings, // (partial: Partial<AppSettings>) => Promise<void>
  reload,         // () => Promise<void>
} = useSettings();
```

### `useLogs()`
Log management with filtering.

```typescript
const {
  logs,          // LogEntry[] (all)
  filteredLogs,  // LogEntry[] (by current filter)
  loading,       // boolean
  filter,        // 'all' | 'info' | 'warn' | 'error' | 'debug'
  setFilter,     // (f: LogFilter) => void
  refresh,       // () => Promise<void>
  clearLogs,     // () => Promise<void>
} = useLogs();
```

### `usePrintHistory()`
Print history management.

```typescript
const {
  history,      // PrintHistoryEntry[]
  loading,      // boolean
  refresh,      // () => Promise<void>
  clearHistory, // () => Promise<void>
} = usePrintHistory();
```

## Services

### PrinterBridge
Thin wrapper around `NativeModules.PrinterModule`. Provides typed TypeScript methods for every native function, plus typed event listeners:

- `onPrintJobStarted(callback)` — job started printing
- `onPrintJobCompleted(callback)` — job finished successfully
- `onPrintJobFailed(callback)` — job failed
- `onPrinterStatusChanged(callback)` — status change detected
- `onPendingJobAdded(callback)` — new pending job queued

### StorageService
AsyncStorage wrapper for RN-side log and history caching. Uses JSON serialization. Auto-trims logs to 500 entries and history to 1000 entries.

## Components

### Icons (`Icons.tsx`)
**Centralized icon wrapper** — the only file that imports from `@hugeicons`. All other files import from here. This prevents bundling issues and ensures consistent icon usage.

### Other Components
| Component | Purpose |
|-----------|---------|
| `PrinterCard` | Printer info card with status badge |
| `StatusBadge` | Colored status indicator (online/offline/connecting/unknown) |
| `EmptyState` | Icon + message for empty lists |
| `Button` | Styled button with loading/disabled states and variants (primary/secondary/danger) |
| `Input` | Text input with label and error display |
| `ListItem` | Key-value row for detail screens |
| `SectionHeader` | Section title with optional right action |

## Type Definitions

### Printer Types
```typescript
type PrinterType = 'bluetooth' | 'lan';
type PrinterStatus = 'online' | 'offline' | 'connecting' | 'unknown';
type PaperWidth = 58 | 80 | 104;

interface Printer {
  id: string;
  name: string;
  type: PrinterType;
  paperWidth: PaperWidth;
  address?: string;   // Bluetooth MAC
  host?: string;      // LAN IP
  port?: number;      // LAN port (default 9100)
  isDefault: boolean;
  createdAt: string;   // ISO 8601
}
```

### Settings Types
```typescript
interface AppSettings {
  defaultPrinterId: string | null;
  paperWidth: PaperWidth;          // Default: 80
  copies: number;                   // Default: 1
  autoCutMode: 'none' | 'partial' | 'full';  // Default: 'partial'
  cashDrawerMode: 'none' | 'drawer1' | 'drawer2'; // Default: 'none'
  linesBeforeCut: number;          // Default: 4
  disconnectDelay: number;         // Default: 3 (seconds)
  dpi: 180 | 203;                  // Default: 203
  imageMode: 'threshold' | 'dither'; // Default: 'threshold'
  retryOnFailure: boolean;         // Default: true
  retryCount: number;              // Default: 3
  debugLogging: boolean;           // Default: false
}
```

### Log/History Types
```typescript
interface LogEntry {
  id: string;
  timestamp: string;
  level: 'info' | 'warn' | 'error' | 'debug';
  message: string;
  printerId?: string;
  jobId?: string;
}

interface PrintHistoryEntry {
  id: string;
  timestamp: string;
  printerName: string;
  printerId: string;
  pageCount: number;
  copies: number;
  paperWidth: PaperWidth;
  durationMs: number;
  success: boolean;
  errorMessage?: string;
}
```

## Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| `PAPER_WIDTHS` | `[58, 80, 104]` | Supported paper sizes |
| `DEFAULT_LAN_PORT` | `9100` | Standard RAW printing port |
| `BLUETOOTH_SCAN_DURATION_MS` | `12000` | BT discovery timeout |
| `STATUS_POLL_INTERVAL_MS` | `30000` | Status check interval |
| `MAX_LOG_ENTRIES` | `500` | Log retention limit |
| `MAX_HISTORY_ENTRIES` | `1000` | History retention limit |

## Styling

- Clean, minimal design with white cards on light gray (`#f3f4f6`) background
- Blue primary color (`#2563eb`) throughout
- Consistent border radius (`8-10px`), subtle shadows
- Segmented controls for settings (paper width, DPI, cut mode, etc.)
- Stepper controls for numeric values (copies, lines, delay)
