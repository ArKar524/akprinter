import React from 'react';
import {FlatList, View, StyleSheet, TouchableOpacity, Text} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {HugeiconsIcon} from '@hugeicons/react-native';
import {PrinterIcon} from '@hugeicons/core-free-icons';
import type {PrintersListNavProp} from '../navigation/types';
import {usePrinters} from '../hooks/usePrinters';
import {usePrinterStatus} from '../hooks/usePrinterStatus';
import {PrinterCard} from '../components/PrinterCard';
import {EmptyState} from '../components/EmptyState';
import type {Printer} from '../types/printer';

function PrinterRow({
  printer,
  onPress,
}: {
  printer: Printer;
  onPress: () => void;
}) {
  const {status} = usePrinterStatus(printer.id);
  return <PrinterCard printer={printer} status={status} onPress={onPress} />;
}

export function PrintersListScreen() {
  const navigation = useNavigation<PrintersListNavProp>();
  const {printers, loading, refresh} = usePrinters();

  return (
    <View style={styles.container}>
      <FlatList
        data={printers}
        keyExtractor={item => item.id}
        renderItem={({item}) => (
          <PrinterRow
            printer={item}
            onPress={() =>
              navigation.navigate('PrinterDetail', {printerId: item.id})
            }
          />
        )}
        contentContainerStyle={
          printers.length === 0 ? styles.emptyContainer : styles.list
        }
        ListEmptyComponent={
          loading ? null : (
            <EmptyState
              icon={<HugeiconsIcon icon={PrinterIcon} size={48} color="#9ca3af" />}
              message="No printers added yet. Add a Bluetooth or LAN printer to get started."
            />
          )
        }
        refreshing={loading}
        onRefresh={refresh}
      />
      {/* FAB row */}
      <View style={styles.fabRow}>
        <TouchableOpacity
          style={styles.fab}
          onPress={() => navigation.navigate('AddBluetoothPrinter')}
          activeOpacity={0.8}>
          <Text style={styles.fabText}>+ Bluetooth</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[styles.fab, styles.fabSecondary]}
          onPress={() => navigation.navigate('AddLanPrinter')}
          activeOpacity={0.8}>
          <Text style={[styles.fabText, styles.fabSecondaryText]}>+ LAN / IP</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  list: {
    padding: 16,
    paddingBottom: 80,
  },
  emptyContainer: {
    flex: 1,
    paddingBottom: 80,
  },
  fabRow: {
    position: 'absolute',
    bottom: 20,
    left: 16,
    right: 16,
    flexDirection: 'row',
    gap: 10,
  },
  fab: {
    flex: 1,
    backgroundColor: '#2563eb',
    borderRadius: 10,
    paddingVertical: 14,
    alignItems: 'center',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 2},
    shadowOpacity: 0.15,
    shadowRadius: 4,
  },
  fabSecondary: {
    backgroundColor: '#ffffff',
    borderWidth: 1.5,
    borderColor: '#2563eb',
  },
  fabText: {
    color: '#ffffff',
    fontWeight: '700',
    fontSize: 15,
  },
  fabSecondaryText: {
    color: '#2563eb',
  },
});
