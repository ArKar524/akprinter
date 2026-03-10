import React, {useEffect} from 'react';
import {
  ScrollView,
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';
import {useNavigation} from '@react-navigation/native';
import {HugeiconsIcon} from '@hugeicons/react-native';
import {
  PrinterIcon,
  Settings02Icon,
  WorkHistoryIcon,
  FileTextIcon,
  CheckmarkCircle02Icon,
  Cancel01Icon,
} from '@hugeicons/core-free-icons';
import type {IconSvgElement} from '@hugeicons/core-free-icons';
import type {HomeNavProp} from '../navigation/types';
import {usePrinters} from '../hooks/usePrinters';
import {usePrintHistory} from '../hooks/usePrintHistory';
import {usePrinterStatus} from '../hooks/usePrinterStatus';
import {PrinterCard} from '../components/PrinterCard';
import {SectionHeader} from '../components/SectionHeader';
import {PrinterBridge} from '../services/PrinterBridge';
import {formatDate, formatDuration} from '../utils/formatters';

export function HomeScreen() {
  const navigation = useNavigation<HomeNavProp>();
  const {printers, defaultPrinter, refresh} = usePrinters();
  const {history} = usePrintHistory();
  const {status} = usePrinterStatus(defaultPrinter?.id ?? null);

  useEffect(() => {
    const sub = PrinterBridge.onPrintJobCompleted(() => refresh());
    return () => sub.remove();
  }, [refresh]);

  const recentHistory = history.slice(0, 3);

  return (
    <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
      {/* Default Printer Card */}
      <SectionHeader title="Default Printer" />
      <View style={styles.section}>
        {defaultPrinter ? (
          <PrinterCard
            printer={defaultPrinter}
            status={status}
            onPress={() =>
              navigation.navigate('PrinterDetail', {printerId: defaultPrinter.id})
            }
          />
        ) : (
          <Text style={styles.empty}>No default printer set</Text>
        )}
      </View>

      {/* Quick Actions */}
      <SectionHeader title="Quick Actions" />
      <View style={styles.actionsRow}>
        <QuickAction
          label="Printers"
          icon={PrinterIcon}
          color="#2563eb"
          onPress={() => navigation.navigate('PrintersList')}
          badge={printers.length > 0 ? String(printers.length) : undefined}
        />
        <QuickAction
          label="Settings"
          icon={Settings02Icon}
          color="#6b7280"
          onPress={() => navigation.navigate('Settings')}
        />
        <QuickAction
          label="History"
          icon={WorkHistoryIcon}
          color="#d97706"
          onPress={() => navigation.navigate('PrintHistory')}
        />
        <QuickAction
          label="Logs"
          icon={FileTextIcon}
          color="#16a34a"
          onPress={() => navigation.navigate('Logs')}
        />
      </View>

      {/* Recent Print Jobs */}
      {recentHistory.length > 0 && (
        <>
          <SectionHeader
            title="Recent Jobs"
            rightAction={
              <TouchableOpacity onPress={() => navigation.navigate('PrintHistory')}>
                <Text style={styles.seeAll}>See all</Text>
              </TouchableOpacity>
            }
          />
          <View style={styles.section}>
            {recentHistory.map(entry => (
              <View key={entry.id} style={styles.historyRow}>
                <View style={[styles.historyLeft, {backgroundColor: entry.success ? '#dcfce7' : '#fee2e2'}]}>
                  <HugeiconsIcon
                    icon={entry.success ? CheckmarkCircle02Icon : Cancel01Icon}
                    size={16}
                    color={entry.success ? '#16a34a' : '#dc2626'}
                  />
                </View>
                <View style={styles.historyInfo}>
                  <Text style={styles.historyPrinter} numberOfLines={1}>
                    {entry.printerName}
                  </Text>
                  <Text style={styles.historyMeta}>
                    {entry.pageCount}p · {formatDuration(entry.durationMs)} ·{' '}
                    {formatDate(entry.timestamp)}
                  </Text>
                </View>
                {!entry.success && (
                  <Text style={styles.failBadge}>FAILED</Text>
                )}
              </View>
            ))}
          </View>
        </>
      )}
    </ScrollView>
  );
}

function QuickAction({
  label,
  icon,
  color,
  onPress,
  badge,
}: {
  label: string;
  icon: IconSvgElement;
  color: string;
  onPress: () => void;
  badge?: string;
}) {
  return (
    <TouchableOpacity style={styles.action} onPress={onPress} activeOpacity={0.7}>
      <View style={styles.actionIcon}>
        <HugeiconsIcon icon={icon} size={26} color={color} />
        {badge && (
          <View style={styles.badge}>
            <Text style={styles.badgeText}>{badge}</Text>
          </View>
        )}
      </View>
      <Text style={styles.actionLabel}>{label}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  scroll: {
    flex: 1,
  },
  content: {
    paddingBottom: 32,
  },
  section: {
    marginHorizontal: 16,
  },
  empty: {
    fontSize: 15,
    color: '#9ca3af',
    textAlign: 'center',
    paddingVertical: 20,
    backgroundColor: '#ffffff',
    borderRadius: 10,
  },
  actionsRow: {
    flexDirection: 'row',
    marginHorizontal: 16,
    gap: 10,
  },
  action: {
    flex: 1,
    backgroundColor: '#ffffff',
    borderRadius: 10,
    alignItems: 'center',
    paddingVertical: 16,
    elevation: 1,
    shadowColor: '#000',
    shadowOffset: {width: 0, height: 1},
    shadowOpacity: 0.05,
    shadowRadius: 2,
  },
  actionIcon: {
    position: 'relative',
    marginBottom: 6,
  },
  actionLabel: {
    fontSize: 12,
    color: '#374151',
    fontWeight: '600',
  },
  badge: {
    position: 'absolute',
    top: -4,
    right: -8,
    backgroundColor: '#2563eb',
    borderRadius: 8,
    minWidth: 16,
    paddingHorizontal: 3,
    alignItems: 'center',
  },
  badgeText: {
    fontSize: 9,
    color: '#ffffff',
    fontWeight: '700',
  },
  seeAll: {
    fontSize: 13,
    color: '#2563eb',
    fontWeight: '600',
  },
  historyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    borderRadius: 8,
    padding: 12,
    marginBottom: 6,
  },
  historyLeft: {
    width: 28,
    height: 28,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
  },
  historyInfo: {
    flex: 1,
  },
  historyPrinter: {
    fontSize: 14,
    fontWeight: '600',
    color: '#111827',
  },
  historyMeta: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  failBadge: {
    fontSize: 10,
    fontWeight: '700',
    color: '#dc2626',
    backgroundColor: '#fee2e2',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
  },
});
