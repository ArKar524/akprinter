import React from 'react';
import {View, Text, StyleSheet} from 'react-native';
import type {PrinterStatus} from '../types/printer';
import {PRINTER_STATUS_LABELS, PRINTER_STATUS_COLORS} from '../utils/constants';

interface StatusBadgeProps {
  status: PrinterStatus;
}

export function StatusBadge({status}: StatusBadgeProps) {
  const color = PRINTER_STATUS_COLORS[status];
  const label = PRINTER_STATUS_LABELS[status];

  return (
    <View style={[styles.badge, {backgroundColor: color + '20'}]}>
      <View style={[styles.dot, {backgroundColor: color}]} />
      <Text style={[styles.text, {color}]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 12,
  },
  dot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    marginRight: 5,
  },
  text: {
    fontSize: 12,
    fontWeight: '600',
  },
});
