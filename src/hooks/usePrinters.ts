import {useState, useEffect, useCallback} from 'react';
import type {Printer} from '../types/printer';
import {PrinterBridge} from '../services/PrinterBridge';

export function usePrinters() {
  const [printers, setPrinters] = useState<Printer[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await PrinterBridge.getPrinters();
      setPrinters(list);
    } catch (e: any) {
      setError(e.message ?? 'Failed to load printers');
    } finally {
      setLoading(false);
    }
  }, []);

  const deletePrinter = useCallback(
    async (id: string) => {
      await PrinterBridge.deletePrinter(id);
      await refresh();
    },
    [refresh],
  );

  const setDefault = useCallback(
    async (id: string) => {
      await PrinterBridge.setDefaultPrinter(id);
      await refresh();
    },
    [refresh],
  );

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    const sub = PrinterBridge.onPrinterStatusChanged(() => {
      refresh();
    });
    return () => sub.remove();
  }, [refresh]);

  const defaultPrinter = printers.find(p => p.isDefault) ?? null;

  return {printers, defaultPrinter, loading, error, refresh, deletePrinter, setDefault};
}
