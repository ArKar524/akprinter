import {useState, useEffect, useCallback, useRef} from 'react';
import type {PrinterStatus} from '../types/printer';
import {PrinterBridge} from '../services/PrinterBridge';
import {STATUS_POLL_INTERVAL_MS} from '../utils/constants';

export function usePrinterStatus(
  printerId: string | null,
  interval = STATUS_POLL_INTERVAL_MS,
) {
  const [status, setStatus] = useState<PrinterStatus>('unknown');
  const [checking, setChecking] = useState(false);
  const [lastChecked, setLastChecked] = useState<Date | null>(null);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const checkNow = useCallback(async () => {
    if (!printerId) {
      return;
    }
    setChecking(true);
    try {
      const s = await PrinterBridge.checkPrinterStatus(printerId);
      setStatus(s);
      setLastChecked(new Date());
    } catch {
      setStatus('offline');
    } finally {
      setChecking(false);
    }
  }, [printerId]);

  useEffect(() => {
    if (!printerId) {
      return;
    }
    checkNow();
    timerRef.current = setInterval(checkNow, interval);
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, [printerId, interval, checkNow]);

  useEffect(() => {
    const sub = PrinterBridge.onPrinterStatusChanged(data => {
      if (data.printerId === printerId) {
        setStatus(data.status);
        setLastChecked(new Date());
      }
    });
    return () => sub.remove();
  }, [printerId]);

  return {status, checking, lastChecked, checkNow};
}
