import {NativeModules, NativeEventEmitter} from 'react-native';
import type {BluetoothDevice, Printer, PrinterStatus} from '../types/printer';
import type {AppSettings} from '../types/settings';
import type {LogEntry, PrintHistoryEntry} from '../types/logs';

const {PrinterModule} = NativeModules;
const emitter = new NativeEventEmitter(PrinterModule);

export const PrinterBridge = {
  scanBluetooth(): Promise<BluetoothDevice[]> {
    return PrinterModule.scanBluetooth();
  },

  addBluetoothPrinter(printer: {
    name: string;
    address: string;
    paperWidth: number;
    isDefault: boolean;
  }): Promise<void> {
    return PrinterModule.addBluetoothPrinter(printer);
  },

  addLanPrinter(printer: {
    name: string;
    host: string;
    port: number;
    paperWidth: number;
    isDefault: boolean;
  }): Promise<void> {
    return PrinterModule.addLanPrinter(printer);
  },

  getPrinters(): Promise<Printer[]> {
    return PrinterModule.getPrinters();
  },

  deletePrinter(id: string): Promise<void> {
    return PrinterModule.deletePrinter(id);
  },

  testPrint(printerId: string): Promise<void> {
    return PrinterModule.testPrint(printerId);
  },

  setDefaultPrinter(id: string): Promise<void> {
    return PrinterModule.setDefaultPrinter(id);
  },

  getSettings(): Promise<AppSettings> {
    return PrinterModule.getSettings();
  },

  saveSettings(settings: AppSettings): Promise<void> {
    return PrinterModule.saveSettings(settings);
  },

  getLogs(): Promise<LogEntry[]> {
    return PrinterModule.getLogs();
  },

  clearLogs(): Promise<void> {
    return PrinterModule.clearLogs();
  },

  getPrintHistory(): Promise<PrintHistoryEntry[]> {
    return PrinterModule.getPrintHistory();
  },

  clearPrintHistory(): Promise<void> {
    return PrinterModule.clearPrintHistory();
  },

  checkPrinterStatus(printerId: string): Promise<PrinterStatus> {
    return PrinterModule.checkPrinterStatus(printerId);
  },

  isPrintServiceEnabled(): Promise<boolean> {
    return PrinterModule.isPrintServiceEnabled();
  },

  openPrintServiceSettings(): Promise<void> {
    return PrinterModule.openPrintServiceSettings();
  },

  onPrintJobStarted(
    callback: (data: {jobId: string; printerName: string}) => void,
  ) {
    return emitter.addListener('PrintJobStarted', callback);
  },

  onPrintJobCompleted(
    callback: (data: {
      jobId: string;
      printerName: string;
      pageCount: number;
      duration: number;
    }) => void,
  ) {
    return emitter.addListener('PrintJobCompleted', callback);
  },

  onPrintJobFailed(
    callback: (data: {jobId: string; error: string}) => void,
  ) {
    return emitter.addListener('PrintJobFailed', callback);
  },

  onPrinterStatusChanged(
    callback: (data: {printerId: string; status: PrinterStatus}) => void,
  ) {
    return emitter.addListener('PrinterStatusChanged', callback);
  },
};
