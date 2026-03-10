import {useState, useEffect, useCallback, useMemo} from 'react';
import type {LogEntry, LogLevel} from '../types/logs';
import {PrinterBridge} from '../services/PrinterBridge';

type LogFilter = LogLevel | 'all';

export function useLogs() {
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<LogFilter>('all');

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const entries = await PrinterBridge.getLogs();
      setLogs(entries.reverse());
    } catch {
      setLogs([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const clearLogs = useCallback(async () => {
    await PrinterBridge.clearLogs();
    setLogs([]);
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const filteredLogs = useMemo(
    () => (filter === 'all' ? logs : logs.filter(l => l.level === filter)),
    [logs, filter],
  );

  return {logs, filteredLogs, loading, filter, setFilter, refresh, clearLogs};
}
