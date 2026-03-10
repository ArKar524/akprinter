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
import type {AddLanNavProp} from '../navigation/types';
import {Button} from '../components/Button';
import {Input} from '../components/Input';
import {SectionHeader} from '../components/SectionHeader';
import {PrinterBridge} from '../services/PrinterBridge';
import type {PaperWidth} from '../types/printer';
import {PAPER_WIDTHS, DEFAULT_LAN_PORT} from '../utils/constants';

const IP_REGEX = /^(\d{1,3}\.){3}\d{1,3}$/;
const PORT_MIN = 1;
const PORT_MAX = 65535;

export function AddLanPrinterScreen() {
  const navigation = useNavigation<AddLanNavProp>();

  const [host, setHost] = useState('');
  const [port, setPort] = useState(String(DEFAULT_LAN_PORT));
  const [name, setName] = useState('');
  const [paperWidth, setPaperWidth] = useState<PaperWidth>(80);
  const [isDefault, setIsDefault] = useState(false);
  const [saving, setSaving] = useState(false);
  const [errors, setErrors] = useState<{host?: string; port?: string; name?: string}>({});

  const validate = () => {
    const newErrors: typeof errors = {};
    if (!host.trim()) {
      newErrors.host = 'IP address is required';
    } else if (!IP_REGEX.test(host.trim())) {
      newErrors.host = 'Enter a valid IP address (e.g. 192.168.1.100)';
    }
    const portNum = Number(port);
    if (!port.trim() || isNaN(portNum) || portNum < PORT_MIN || portNum > PORT_MAX) {
      newErrors.port = `Port must be ${PORT_MIN}–${PORT_MAX}`;
    }
    if (!name.trim()) {
      newErrors.name = 'Printer name is required';
    }
    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  };

  const handleAdd = async () => {
    if (!validate()) {
      return;
    }
    setSaving(true);
    try {
      await PrinterBridge.addLanPrinter({
        name: name.trim(),
        host: host.trim(),
        port: Number(port),
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
    <ScrollView
      style={styles.scroll}
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled">
      <SectionHeader title="Network Details" />
      <View style={styles.section}>
        <Input
          label="IP Address"
          value={host}
          onChangeText={setHost}
          placeholder="192.168.1.100"
          keyboardType="numeric"
          error={errors.host}
        />
        <Input
          label="Port"
          value={port}
          onChangeText={setPort}
          placeholder={String(DEFAULT_LAN_PORT)}
          keyboardType="numeric"
          error={errors.port}
        />
      </View>

      <SectionHeader title="Printer Settings" />
      <View style={styles.section}>
        <Input
          label="Printer Name"
          value={name}
          onChangeText={setName}
          placeholder="e.g. Kitchen Printer"
          autoCapitalize="words"
          error={errors.name}
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
