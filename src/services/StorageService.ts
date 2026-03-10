import type {LogEntry, PrintHistoryEntry} from '../types/logs';
import {getItemWithDefault, setItem} from '../utils/storage';

const KEYS = {
  LOGS: '@akprint:logs',
  HISTORY: '@akprint:history',
} as const;

export const StorageService = {
  async getLogs(): Promise<LogEntry[]> {
    return getItemWithDefault<LogEntry[]>(KEYS.LOGS, []);
  },

  async appendLog(entry: LogEntry): Promise<void> {
    const logs = await this.getLogs();
    logs.push(entry);
    const trimmed = logs.length > 500 ? logs.slice(-500) : logs;
    await setItem(KEYS.LOGS, trimmed);
  },

  async clearLogs(): Promise<void> {
    await setItem(KEYS.LOGS, []);
  },

  async getHistory(): Promise<PrintHistoryEntry[]> {
    return getItemWithDefault<PrintHistoryEntry[]>(KEYS.HISTORY, []);
  },

  async appendHistory(entry: PrintHistoryEntry): Promise<void> {
    const history = await this.getHistory();
    history.push(entry);
    const trimmed = history.length > 1000 ? history.slice(-1000) : history;
    await setItem(KEYS.HISTORY, trimmed);
  },

  async clearHistory(): Promise<void> {
    await setItem(KEYS.HISTORY, []);
  },
};
