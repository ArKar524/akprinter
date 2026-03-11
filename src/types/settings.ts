import type {PaperWidth} from './printer';

export type AutoCutMode = 'none' | 'partial' | 'full';
export type CashDrawerMode = 'none' | 'drawer1' | 'drawer2';

export interface AppSettings {
  defaultPrinterId: string | null;
  paperWidth: PaperWidth;
  copies: number;
  autoCutMode: AutoCutMode;
  cashDrawerMode: CashDrawerMode;
  linesBeforeCut: number;
  disconnectDelay: number;
  dpi: 180 | 203;
  retryOnFailure: boolean;
  retryCount: number;
  debugLogging: boolean;
}

export const DEFAULT_SETTINGS: AppSettings = {
  defaultPrinterId: null,
  paperWidth: 80,
  copies: 1,
  autoCutMode: 'partial',
  cashDrawerMode: 'none',
  linesBeforeCut: 4,
  disconnectDelay: 3,
  dpi: 203,
  retryOnFailure: true,
  retryCount: 3,
  debugLogging: false,
};
