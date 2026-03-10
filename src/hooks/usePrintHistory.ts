import {useState, useEffect, useCallback} from 'react';
import type {PrintHistoryEntry} from '../types/logs';
import {PrinterBridge} from '../services/PrinterBridge';

export function usePrintHistory() {
  const [history, setHistory] = useState<PrintHistoryEntry[]>([]);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const entries = await PrinterBridge.getPrintHistory();
      setHistory(entries.reverse());
    } catch {
      setHistory([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const clearHistory = useCallback(async () => {
    await PrinterBridge.clearPrintHistory();
    setHistory([]);
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return {history, loading, refresh, clearHistory};
}
