import React from 'react';
import {TouchableOpacity, View, Text, StyleSheet} from 'react-native';
import {HugeiconsIcon} from '@hugeicons/react-native';
import {BluetoothIcon, WifiIcon} from '@hugeicons/core-free-icons';
import type {Printer, PrinterStatus} from '../types/printer';
import {StatusBadge} from './StatusBadge';

interface PrinterCardProps {
  printer: Printer;
  status?: PrinterStatus;
  onPress: () => void;
}

export function PrinterCard({printer, status = 'unknown', onPress}: PrinterCardProps) {
  const icon = printer.type === 'bluetooth' ? BluetoothIcon : WifiIcon;

  return (
    <TouchableOpacity style={styles.card} onPress={onPress} activeOpacity={0.7}>
      <View style={styles.iconContainer}>
        <HugeiconsIcon icon={icon} size={20} color="#2563eb" />
      </View>
      <View style={styles.info}>
        <View style={styles.nameRow}>
          <Text style={styles.name} numberOfLines={1}>
            {printer.name}
          </Text>
          {printer.isDefault && (
            <View style={styles.defaultBadge}>
              <Text style={styles.defaultText}>DEFAULT</Text>
            </View>
          )}
        </View>
        <Text style={styles.detail}>
          {printer.type === 'bluetooth' ? printer.address : `${printer.host}:${printer.port}`}
          {'  '}
          {printer.paperWidth}mm
        </Text>
      </View>
      <StatusBadge status={status} />
      <Text style={styles.chevron}>&rsaquo;</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    borderRadius: 10,
    padding: 14,
    marginBottom: 8,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.05,
    shadowRadius: 2,
  },
  iconContainer: {
    width: 40,
    height: 40,
    borderRadius: 20,
    backgroundColor: '#eff6ff',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  info: {
    flex: 1,
    marginRight: 8,
  },
  nameRow: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  name: {
    fontSize: 15,
    fontWeight: '600',
    color: '#111827',
    flexShrink: 1,
  },
  defaultBadge: {
    backgroundColor: '#2563eb',
    paddingHorizontal: 6,
    paddingVertical: 1,
    borderRadius: 4,
    marginLeft: 6,
  },
  defaultText: {
    color: '#ffffff',
    fontSize: 9,
    fontWeight: '700',
  },
  detail: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  chevron: {
    fontSize: 22,
    color: '#9ca3af',
    marginLeft: 4,
  },
});
