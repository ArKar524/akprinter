import React, {useState} from 'react';
import {ScrollView, View, Alert, StyleSheet} from 'react-native';
import {useNavigation, useRoute} from '@react-navigation/native';
import type {PrinterDetailNavProp, PrinterDetailRouteProp} from '../navigation/types';
import {usePrinters} from '../hooks/usePrinters';
import {usePrinterStatus} from '../hooks/usePrinterStatus';
import {Button} from '../components/Button';
import {ListItem} from '../components/ListItem';
import {StatusBadge} from '../components/StatusBadge';
import {SectionHeader} from '../components/SectionHeader';
import {PrinterBridge} from '../services/PrinterBridge';
import {formatDate} from '../utils/formatters';

export function PrinterDetailScreen() {
  const navigation = useNavigation<PrinterDetailNavProp>();
  const route = useRoute<PrinterDetailRouteProp>();
  const {printerId} = route.params;

  const {printers, deletePrinter, setDefault} = usePrinters();
  const printer = printers.find(p => p.id === printerId) ?? null;

  const {status, checking, lastChecked, checkNow} = usePrinterStatus(printerId);
  const [testLoading, setTestLoading] = useState(false);

  if (!printer) {
    return null;
  }

  const handleTestPrint = async () => {
    setTestLoading(true);
    try {
      await PrinterBridge.testPrint(printerId);
      Alert.alert('Success', 'Test page sent successfully');
    } catch (e: any) {
      Alert.alert('Test Print Failed', e.message ?? 'Could not send test page');
    } finally {
      setTestLoading(false);
    }
  };

  const handleSetDefault = async () => {
    await setDefault(printerId);
  };

  const handleDelete = () => {
    Alert.alert('Delete Printer', `Remove "${printer.name}"?`, [
      {text: 'Cancel', style: 'cancel'},
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          await deletePrinter(printerId);
          navigation.goBack();
        },
      },
    ]);
  };

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
      {/* Status */}
      <SectionHeader title="Status" />
      <View style={styles.section}>
        <ListItem
          label="Connection"
          rightElement={<StatusBadge status={status} />}
        />
        {lastChecked && (
          <ListItem label="Last Checked" value={formatDate(lastChecked.toISOString())} />
        )}
        <View style={styles.checkBtn}>
          <Button
            title={checking ? 'Checking...' : 'Check Now'}
            onPress={checkNow}
            variant="secondary"
            loading={checking}
          />
        </View>
      </View>

      {/* Printer Info */}
      <SectionHeader title="Printer Info" />
      <View style={styles.section}>
        <ListItem label="Name" value={printer.name} />
        <ListItem label="Type" value={printer.type === 'bluetooth' ? 'Bluetooth' : 'LAN / IP'} />
        {printer.type === 'bluetooth' && (
          <ListItem label="Address" value={printer.address} />
        )}
        {printer.type === 'lan' && (
          <>
            <ListItem label="Host" value={printer.host} />
            <ListItem label="Port" value={String(printer.port)} />
          </>
        )}
        <ListItem label="Paper Width" value={`${printer.paperWidth}mm`} />
        <ListItem label="Added" value={formatDate(printer.createdAt)} />
        <ListItem label="Default" value={printer.isDefault ? 'Yes' : 'No'} />
      </View>

      {/* Actions */}
      <SectionHeader title="Actions" />
      <View style={styles.section}>
        <Button
          title="Test Print"
          onPress={handleTestPrint}
          loading={testLoading}
          style={styles.actionBtn}
        />
        {!printer.isDefault && (
          <Button
            title="Set as Default"
            onPress={handleSetDefault}
            variant="secondary"
            style={styles.actionBtn}
          />
        )}
        <Button
          title="Delete Printer"
          onPress={handleDelete}
          variant="danger"
          style={styles.actionBtn}
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {flex: 1},
  content: {paddingBottom: 40},
  section: {
    marginHorizontal: 16,
    backgroundColor: '#ffffff',
    borderRadius: 10,
    overflow: 'hidden',
  },
  checkBtn: {
    padding: 12,
  },
  actionBtn: {
    marginHorizontal: 16,
    marginBottom: 10,
  },
});
