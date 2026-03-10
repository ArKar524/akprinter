import type {PaperWidth} from './printer';

export interface AppSettings {
  defaultPrinterId: string | null;
  paperWidth: PaperWidth;
  copies: number;
  autoCut: boolean;
  openCashDrawer: boolean;
  retryOnFailure: boolean;
  retryCount: number;
  debugLogging: boolean;
}

export const DEFAULT_SETTINGS: AppSettings = {
  defaultPrinterId: null,
  paperWidth: 80,
  copies: 1,
  autoCut: true,
  openCashDrawer: false,
  retryOnFailure: true,
  retryCount: 3,
  debugLogging: false,
};
