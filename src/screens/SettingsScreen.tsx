import React, {useCallback, useEffect, useState} from 'react';
import {ScrollView, View, Text, Switch, TouchableOpacity, StyleSheet, AppState} from 'react-native';
import {useSettings} from '../hooks/useSettings';
import {ListItem} from '../components/ListItem';
import {SectionHeader} from '../components/SectionHeader';
import {AlertIcn, CheckCircleIcn} from '../components/Icons';
import {PrinterBridge} from '../services/PrinterBridge';
import type {PaperWidth} from '../types/printer';
import {PAPER_WIDTHS} from '../utils/constants';

export function SettingsScreen() {
  const {settings, loading, updateSettings} = useSettings();
  const [serviceEnabled, setServiceEnabled] = useState<boolean | null>(null);

  const checkService = useCallback(() => {
    PrinterBridge.isPrintServiceEnabled().then(setServiceEnabled).catch(() => {});
  }, []);

  useEffect(() => {
    checkService();
    const sub = AppState.addEventListener('change', state => {
      if (state === 'active') {
        checkService();
      }
    });
    return () => sub.remove();
  }, [checkService]);

  if (loading) {
    return <View style={styles.loading} />;
  }

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
      {/* Print Service */}
      <SectionHeader title="Print Service" />
      <View style={styles.section}>
        <View style={styles.serviceRow}>
          <View style={styles.serviceInfo}>
            {serviceEnabled ? (
              <CheckCircleIcn size={20} color="#16a34a" />
            ) : (
              <AlertIcn size={20} color="#d97706" />
            )}
            <View style={styles.serviceText}>
              <Text style={styles.serviceTitle}>
                {serviceEnabled ? 'AkPrint is enabled' : 'AkPrint is not enabled'}
              </Text>
              {!serviceEnabled && (
                <Text style={styles.serviceDesc}>
                  Enable AkPrint in Android print settings to print from any app
                </Text>
              )}
            </View>
          </View>
          {!serviceEnabled && (
            <TouchableOpacity
              style={styles.serviceBtn}
              onPress={() => PrinterBridge.openPrintServiceSettings()}
              activeOpacity={0.7}>
              <Text style={styles.serviceBtnText}>Enable</Text>
            </TouchableOpacity>
          )}
        </View>
      </View>

      {/* Print Defaults */}
      <SectionHeader title="Print Defaults" />
      <View style={styles.section}>
        <View style={styles.row}>
          <Text style={styles.label}>Paper Width</Text>
          <View style={styles.segmented}>
            {PAPER_WIDTHS.map((w: PaperWidth) => (
              <TouchableOpacity
                key={w}
                style={[
                  styles.segBtn,
                  settings.paperWidth === w && styles.segBtnActive,
                ]}
                onPress={() => updateSettings({paperWidth: w})}
                activeOpacity={0.7}>
                <Text
                  style={[
                    styles.segBtnText,
                    settings.paperWidth === w && styles.segBtnTextActive,
                  ]}>
                  {w}mm
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        </View>

        <View style={styles.row}>
          <Text style={styles.label}>Copies</Text>
          <View style={styles.stepper}>
            <TouchableOpacity
              style={styles.stepBtn}
              onPress={() =>
                settings.copies > 1 && updateSettings({copies: settings.copies - 1})
              }
              activeOpacity={0.7}>
              <Text style={styles.stepBtnText}>–</Text>
            </TouchableOpacity>
            <Text style={styles.stepValue}>{settings.copies}</Text>
            <TouchableOpacity
              style={styles.stepBtn}
              onPress={() =>
                settings.copies < 10 && updateSettings({copies: settings.copies + 1})
              }
              activeOpacity={0.7}>
              <Text style={styles.stepBtnText}>+</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>

      {/* Printing Behavior */}
      <SectionHeader title="Behavior" />
      <View style={styles.section}>
        <ListItem
          label="Auto Cut After Print"
          rightElement={
            <Switch
              value={settings.autoCut}
              onValueChange={v => updateSettings({autoCut: v})}
              trackColor={{false: '#d1d5db', true: '#93c5fd'}}
              thumbColor={settings.autoCut ? '#2563eb' : '#9ca3af'}
            />
          }
        />
        <ListItem
          label="Open Cash Drawer"
          rightElement={
            <Switch
              value={settings.openCashDrawer}
              onValueChange={v => updateSettings({openCashDrawer: v})}
              trackColor={{false: '#d1d5db', true: '#93c5fd'}}
              thumbColor={settings.openCashDrawer ? '#2563eb' : '#9ca3af'}
            />
          }
        />
        <ListItem
          label="Retry on Failure"
          rightElement={
            <Switch
              value={settings.retryOnFailure}
              onValueChange={v => updateSettings({retryOnFailure: v})}
              trackColor={{false: '#d1d5db', true: '#93c5fd'}}
              thumbColor={settings.retryOnFailure ? '#2563eb' : '#9ca3af'}
            />
          }
        />
        {settings.retryOnFailure && (
          <View style={styles.row}>
            <Text style={styles.label}>Retry Count</Text>
            <View style={styles.stepper}>
              <TouchableOpacity
                style={styles.stepBtn}
                onPress={() =>
                  settings.retryCount > 1 &&
                  updateSettings({retryCount: settings.retryCount - 1})
                }
                activeOpacity={0.7}>
                <Text style={styles.stepBtnText}>–</Text>
              </TouchableOpacity>
              <Text style={styles.stepValue}>{settings.retryCount}</Text>
              <TouchableOpacity
                style={styles.stepBtn}
                onPress={() =>
                  settings.retryCount < 5 &&
                  updateSettings({retryCount: settings.retryCount + 1})
                }
                activeOpacity={0.7}>
                <Text style={styles.stepBtnText}>+</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      </View>

      {/* Debug */}
      <SectionHeader title="Debug" />
      <View style={styles.section}>
        <ListItem
          label="Debug Logging"
          rightElement={
            <Switch
              value={settings.debugLogging}
              onValueChange={v => updateSettings({debugLogging: v})}
              trackColor={{false: '#d1d5db', true: '#93c5fd'}}
              thumbColor={settings.debugLogging ? '#2563eb' : '#9ca3af'}
            />
          }
        />
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {flex: 1},
  content: {paddingBottom: 40},
  loading: {flex: 1, backgroundColor: '#f3f4f6'},
  section: {
    marginHorizontal: 16,
    backgroundColor: '#ffffff',
    borderRadius: 10,
    overflow: 'hidden',
  },
  serviceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: 16,
  },
  serviceInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  serviceText: {
    marginLeft: 10,
    flex: 1,
  },
  serviceTitle: {
    fontSize: 15,
    fontWeight: '600',
    color: '#111827',
  },
  serviceDesc: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  serviceBtn: {
    backgroundColor: '#2563eb',
    paddingHorizontal: 16,
    paddingVertical: 8,
    borderRadius: 8,
    marginLeft: 12,
  },
  serviceBtnText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#ffffff',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e5e7eb',
  },
  label: {
    fontSize: 15,
    color: '#111827',
    flex: 1,
  },
  segmented: {
    flexDirection: 'row',
    borderRadius: 8,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: '#d1d5db',
  },
  segBtn: {
    paddingHorizontal: 16,
    paddingVertical: 6,
    backgroundColor: '#ffffff',
  },
  segBtnActive: {
    backgroundColor: '#2563eb',
  },
  segBtnText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#6b7280',
  },
  segBtnTextActive: {
    color: '#ffffff',
  },
  stepper: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  stepBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: '#f3f4f6',
    alignItems: 'center',
    justifyContent: 'center',
  },
  stepBtnText: {
    fontSize: 20,
    fontWeight: '600',
    color: '#374151',
    lineHeight: 24,
  },
  stepValue: {
    fontSize: 16,
    fontWeight: '700',
    color: '#111827',
    minWidth: 32,
    textAlign: 'center',
  },
});
