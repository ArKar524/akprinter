import {useState, useCallback} from 'react';
import {PermissionsAndroid, Platform} from 'react-native';
import type {BluetoothDevice} from '../types/printer';
import {PrinterBridge} from '../services/PrinterBridge';

async function requestBluetoothPermissions(): Promise<boolean> {
  if (Platform.OS !== 'android') {
    return true;
  }

  const apiLevel = Platform.Version;

  if (apiLevel >= 31) {
    // Android 12+: need BLUETOOTH_SCAN and BLUETOOTH_CONNECT
    const result = await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
    ]);
    return (
      result[PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN] ===
        PermissionsAndroid.RESULTS.GRANTED &&
      result[PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT] ===
        PermissionsAndroid.RESULTS.GRANTED
    );
  }

  // Android < 12: need ACCESS_FINE_LOCATION for BT discovery
  const granted = await PermissionsAndroid.request(
    PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
    {
      title: 'Location Permission',
      message:
        'AkPrint needs location access to discover nearby Bluetooth printers.',
      buttonPositive: 'Allow',
      buttonNegative: 'Deny',
    },
  );
  return granted === PermissionsAndroid.RESULTS.GRANTED;
}

export function useBluetoothScan() {
  const [devices, setDevices] = useState<BluetoothDevice[]>([]);
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const startScan = useCallback(async () => {
    setScanning(true);
    setError(null);
    setDevices([]);
    try {
      const hasPermission = await requestBluetoothPermissions();
      if (!hasPermission) {
        setError('Bluetooth permissions are required to scan for printers');
        return;
      }
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
