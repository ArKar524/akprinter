import type {PaperWidth, PrinterStatus} from '../types/printer';

export const PAPER_WIDTHS: PaperWidth[] = [58, 80];
export const DEFAULT_LAN_PORT = 9100;
export const BLUETOOTH_SCAN_DURATION_MS = 12000;
export const STATUS_POLL_INTERVAL_MS = 30000;
export const MAX_LOG_ENTRIES = 500;
export const MAX_HISTORY_ENTRIES = 1000;

export const PAPER_WIDTH_DOTS: Record<PaperWidth, number> = {
  58: 384,
  80: 576,
};

export const PRINTER_STATUS_LABELS: Record<PrinterStatus, string> = {
  online: 'Online',
  offline: 'Offline',
  connecting: 'Connecting...',
  unknown: 'Unknown',
};

export const PRINTER_STATUS_COLORS: Record<PrinterStatus, string> = {
  online: '#22c55e',
  offline: '#9ca3af',
  connecting: '#eab308',
  unknown: '#9ca3af',
};
