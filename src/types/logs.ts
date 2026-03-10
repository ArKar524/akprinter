import type {PaperWidth} from './printer';

export type LogLevel = 'info' | 'warn' | 'error' | 'debug';

export interface LogEntry {
  id: string;
  timestamp: string;
  level: LogLevel;
  message: string;
  printerId?: string;
  jobId?: string;
}

export interface PendingJob {
  id: string;
  documentName: string;
  pageCount: number;
  createdAt: string;
  status: 'pending' | 'printing';
}

export interface PrintHistoryEntry {
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
