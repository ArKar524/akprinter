import React, {useState} from 'react';
import {
  ScrollView,
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Switch,
  Alert,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import type {AddBluetoothNavProp} from '../navigation/types';
import {useBluetoothScan} from '../hooks/useBluetoothScan';
import {Button} from '../components/Button';
import {Input} from '../components/Input';
import {SectionHeader} from '../components/SectionHeader';
import {PrinterBridge} from '../services/PrinterBridge';
import type {BluetoothDevice, PaperWidth} from '../types/printer';
import {PAPER_WIDTHS} from '../utils/constants';

export function AddBluetoothPrinterScreen() {
  const navigation = useNavigation<AddBluetoothNavProp>();
  const {devices, scanning, error: scanError, startScan} = useBluetoothScan();

  const [selected, setSelected] = useState<BluetoothDevice | null>(null);
  const [name, setName] = useState('');
  const [paperWidth, setPaperWidth] = useState<PaperWidth>(80);
  const [isDefault, setIsDefault] = useState(false);
  const [saving, setSaving] = useState(false);

  const handleDeviceSelect = (device: BluetoothDevice) => {
    setSelected(device);
    if (!name) {
      setName(device.name);
    }
  };

  const validate = () => {
    if (!selected) {
      Alert.alert('Error', 'Please select a Bluetooth device');
      return false;
    }
    if (!name.trim()) {
      Alert.alert('Error', 'Please enter a printer name');
      return false;
    }
    return true;
  };

  const handleAdd = async () => {
    if (!validate() || !selected) {
      return;
    }
    setSaving(true);
    try {
      await PrinterBridge.addBluetoothPrinter({
        name: name.trim(),
        address: selected.address,
        paperWidth,
        isDefault,
      });
      navigation.goBack();
    } catch (e: any) {
      Alert.alert('Error', e.message ?? 'Failed to add printer');
    } finally {
      setSaving(false);
    }
  };

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
      {/* Scan */}
      <SectionHeader title="1. Discover Devices" />
      <View style={styles.section}>
        <Button
          title={scanning ? 'Scanning...' : 'Scan for Devices'}
          onPress={startScan}
          variant="secondary"
          loading={scanning}
        />
        {scanError && <Text style={styles.errorText}>{scanError}</Text>}
      </View>

      {/* Device list */}
      {devices.length > 0 && (
        <>
          <SectionHeader title="2. Select Device" />
          <View style={styles.section}>
            {devices.map(device => (
              <TouchableOpacity
                key={device.address}
                style={[
                  styles.deviceRow,
                  selected?.address === device.address && styles.deviceSelected,
                ]}
                onPress={() => handleDeviceSelect(device)}
                activeOpacity={0.7}>
                <View style={styles.deviceInfo}>
                  <Text style={styles.deviceName}>{device.name}</Text>
                  <Text style={styles.deviceAddress}>{device.address}</Text>
                </View>
                {device.bondState === 'bonded' && (
                  <Text style={styles.bondedBadge}>Paired</Text>
                )}
                {selected?.address === device.address && (
                  <Text style={styles.checkmark}>✓</Text>
                )}
              </TouchableOpacity>
            ))}
          </View>
        </>
      )}

      {/* Config form */}
      <SectionHeader title={devices.length > 0 ? '3. Configure' : 'Configure'} />
      <View style={styles.section}>
        <Input
          label="Printer Name"
          value={name}
          onChangeText={setName}
          placeholder="e.g. Receipt Printer"
          autoCapitalize="words"
        />

        <Text style={styles.fieldLabel}>Paper Width</Text>
        <View style={styles.paperWidthRow}>
          {PAPER_WIDTHS.map(w => (
            <TouchableOpacity
              key={w}
              style={[styles.paperBtn, paperWidth === w && styles.paperBtnActive]}
              onPress={() => setPaperWidth(w)}
              activeOpacity={0.7}>
              <Text
                style={[styles.paperBtnText, paperWidth === w && styles.paperBtnTextActive]}>
                {w}mm
              </Text>
            </TouchableOpacity>
          ))}
        </View>

        <View style={styles.switchRow}>
          <Text style={styles.switchLabel}>Set as Default Printer</Text>
          <Switch
            value={isDefault}
            onValueChange={setIsDefault}
            trackColor={{false: '#d1d5db', true: '#93c5fd'}}
            thumbColor={isDefault ? '#2563eb' : '#9ca3af'}
          />
        </View>

        <Button
          title="Add Printer"
          onPress={handleAdd}
          loading={saving}
          disabled={!selected || !name.trim()}
          style={styles.addBtn}
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {flex: 1},
  content: {paddingBottom: 40},
  section: {marginHorizontal: 16},
  errorText: {
    color: '#dc2626',
    fontSize: 13,
    marginTop: 8,
  },
  deviceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    borderRadius: 8,
    padding: 12,
    marginBottom: 6,
    borderWidth: 1.5,
    borderColor: 'transparent',
  },
  deviceSelected: {
    borderColor: '#2563eb',
    backgroundColor: '#eff6ff',
  },
  deviceInfo: {flex: 1},
  deviceName: {
    fontSize: 15,
    fontWeight: '600',
    color: '#111827',
  },
  deviceAddress: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  bondedBadge: {
    fontSize: 11,
    fontWeight: '600',
    color: '#16a34a',
    backgroundColor: '#dcfce7',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    marginRight: 8,
  },
  checkmark: {
    fontSize: 18,
    color: '#2563eb',
    fontWeight: '700',
  },
  fieldLabel: {
    fontSize: 14,
    fontWeight: '600',
    color: '#374151',
    marginBottom: 8,
  },
  paperWidthRow: {
    flexDirection: 'row',
    gap: 10,
    marginBottom: 20,
  },
  paperBtn: {
    flex: 1,
    paddingVertical: 10,
    borderRadius: 8,
    borderWidth: 1.5,
    borderColor: '#d1d5db',
    alignItems: 'center',
    backgroundColor: '#ffffff',
  },
  paperBtnActive: {
    borderColor: '#2563eb',
    backgroundColor: '#eff6ff',
  },
  paperBtnText: {
    fontSize: 15,
    fontWeight: '600',
    color: '#6b7280',
  },
  paperBtnTextActive: {
    color: '#2563eb',
  },
  switchRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#ffffff',
    borderRadius: 8,
    padding: 14,
    marginBottom: 20,
  },
  switchLabel: {
    fontSize: 15,
    color: '#111827',
  },
  addBtn: {
    marginTop: 4,
  },
});
