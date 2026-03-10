import React from 'react';
import {HugeiconsIcon} from '@hugeicons/react-native';
import {
  PrinterIcon,
  BluetoothIcon,
  WifiIcon,
  Settings02Icon,
  WorkHistoryIcon,
  File01Icon,
  CheckmarkCircle02Icon,
  Cancel01Icon,
} from '@hugeicons/core-free-icons';

interface IconProps {
  size?: number;
  color?: string;
}

export function PrinterIcn({size = 24, color = '#000'}: IconProps) {
  return <HugeiconsIcon icon={PrinterIcon} size={size} color={color} />;
}

export function BluetoothIcn({size = 24, color = '#000'}: IconProps) {
  return <HugeiconsIcon icon={BluetoothIcon} size={size} color={color} />;
}

export function WifiIcn({size = 24, color = '#000'}: IconProps) {
  return <HugeiconsIcon icon={WifiIcon} size={size} color={color} />;
}

export function SettingsIcn({size = 24, color = '#000'}: IconProps) {
  return <HugeiconsIcon icon={Settings02Icon} size={size} color={color} />;
}

export function HistoryIcn({size = 24, color = '#000'}: IconProps) {
  return <HugeiconsIcon icon={WorkHistoryIcon} size={size} color={color} />;
}

export function FileIcn({size = 24, color = '#000'}: IconProps) {
  return <HugeiconsIcon icon={File01Icon} size={size} color={color} />;
}

export function CheckCircleIcn({size = 24, color = '#000'}: IconProps) {
  return <HugeiconsIcon icon={CheckmarkCircle02Icon} size={size} color={color} />;
}

export function CancelIcn({size = 24, color = '#000'}: IconProps) {
  return <HugeiconsIcon icon={Cancel01Icon} size={size} color={color} />;
}
