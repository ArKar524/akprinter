import React from 'react';
import {NavigationContainer} from '@react-navigation/native';
import {createNativeStackNavigator} from '@react-navigation/native-stack';
import type {RootStackParamList} from './types';
import {HomeScreen} from '../screens/HomeScreen';
import {PrintersListScreen} from '../screens/PrintersListScreen';
import {AddBluetoothPrinterScreen} from '../screens/AddBluetoothPrinterScreen';
import {AddLanPrinterScreen} from '../screens/AddLanPrinterScreen';
import {PrinterDetailScreen} from '../screens/PrinterDetailScreen';
import {SettingsScreen} from '../screens/SettingsScreen';
import {LogsScreen} from '../screens/LogsScreen';
import {PrintHistoryScreen} from '../screens/PrintHistoryScreen';
import {PendingJobsScreen} from '../screens/PendingJobsScreen';

const Stack = createNativeStackNavigator<RootStackParamList>();

export function AppNavigator() {
  return (
    <NavigationContainer>
      <Stack.Navigator
        initialRouteName="Home"
        screenOptions={{
          headerStyle: {backgroundColor: '#ffffff'},
          headerTintColor: '#111827',
          headerTitleStyle: {fontWeight: '700'},
          headerShadowVisible: false,
          contentStyle: {backgroundColor: '#f3f4f6'},
        }}>
        <Stack.Screen name="Home" component={HomeScreen} options={{title: 'Pico Printer'}} />
        <Stack.Screen
          name="PrintersList"
          component={PrintersListScreen}
          options={{title: 'Printers'}}
        />
        <Stack.Screen
          name="AddBluetoothPrinter"
          component={AddBluetoothPrinterScreen}
          options={{title: 'Add Bluetooth Printer'}}
        />
        <Stack.Screen
          name="AddLanPrinter"
          component={AddLanPrinterScreen}
          options={{title: 'Add LAN Printer'}}
        />
        <Stack.Screen
          name="PrinterDetail"
          component={PrinterDetailScreen}
          options={{title: 'Printer'}}
        />
        <Stack.Screen
          name="PendingJobs"
          component={PendingJobsScreen}
          options={{title: 'Pending Jobs'}}
        />
        <Stack.Screen
          name="Settings"
          component={SettingsScreen}
          options={{title: 'Settings'}}
        />
        <Stack.Screen name="Logs" component={LogsScreen} options={{title: 'Logs'}} />
        <Stack.Screen
          name="PrintHistory"
          component={PrintHistoryScreen}
          options={{title: 'Print History'}}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
