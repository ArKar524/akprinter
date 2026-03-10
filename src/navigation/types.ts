import type {NativeStackNavigationProp} from '@react-navigation/native-stack';
import type {RouteProp} from '@react-navigation/native';

export type RootStackParamList = {
  Home: undefined;
  PrintersList: undefined;
  AddBluetoothPrinter: undefined;
  AddLanPrinter: undefined;
  PrinterDetail: {printerId: string};
  Settings: undefined;
  Logs: undefined;
  PrintHistory: undefined;
};

declare global {
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}

export type HomeNavProp = NativeStackNavigationProp<RootStackParamList, 'Home'>;
export type PrintersListNavProp = NativeStackNavigationProp<RootStackParamList, 'PrintersList'>;
export type AddBluetoothNavProp = NativeStackNavigationProp<RootStackParamList, 'AddBluetoothPrinter'>;
export type AddLanNavProp = NativeStackNavigationProp<RootStackParamList, 'AddLanPrinter'>;
export type PrinterDetailNavProp = NativeStackNavigationProp<RootStackParamList, 'PrinterDetail'>;
export type PrinterDetailRouteProp = RouteProp<RootStackParamList, 'PrinterDetail'>;
