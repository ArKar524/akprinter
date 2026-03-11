export type PrinterType = 'bluetooth' | 'lan';
export type PrinterStatus = 'online' | 'offline' | 'connecting' | 'unknown';
export type PaperWidth = 58 | 80 | 104;

export interface BluetoothDevice {
  name: string;
  address: string;
  bondState: 'bonded' | 'bonding' | 'none';
}

export interface Printer {
  id: string;
  name: string;
  type: PrinterType;
  paperWidth: PaperWidth;
  address?: string;
  host?: string;
  port?: number;
  isDefault: boolean;
  createdAt: string;
}
