import {useState, useCallback} from 'react';
import type {BluetoothDevice} from '../types/printer';
import {PrinterBridge} from '../services/PrinterBridge';

export function useBluetoothScan() {
  const [devices, setDevices] = useState<BluetoothDevice[]>([]);
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const startScan = useCallback(async () => {
    setScanning(true);
    setError(null);
    setDevices([]);
    try {
      const found = await PrinterBridge.scanBluetooth();
      setDevices(found);
    } catch (e: any) {
      setError(e.message ?? 'Bluetooth scan failed');
    } finally {
      setScanning(false);
    }
  }, []);

  return {devices, scanning, error, startScan};
}
