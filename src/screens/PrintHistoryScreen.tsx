import React from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
} from 'react-native';
import {CheckCircleIcn, CancelIcn, HistoryIcn} from '../components/Icons';
import {usePrintHistory} from '../hooks/usePrintHistory';
import {EmptyState} from '../components/EmptyState';
import {formatDate, formatDuration} from '../utils/formatters';
import type {PrintHistoryEntry} from '../types/logs';

function HistoryRow({entry}: {entry: PrintHistoryEntry}) {
  const success = entry.success;
  return (
    <View style={styles.row}>
      <View style={[styles.statusIcon, {backgroundColor: success ? '#dcfce7' : '#fee2e2'}]}>
        {success ? (
          <CheckCircleIcn size={18} color="#16a34a" />
        ) : (
          <CancelIcn size={18} color="#dc2626" />
        )}
      </View>
      <View style={styles.info}>
        <Text style={styles.printerName} numberOfLines={1}>
          {entry.printerName}
        </Text>
        <Text style={styles.meta}>
          {entry.pageCount} page{entry.pageCount !== 1 ? 's' : ''} ·{' '}
          {entry.copies > 1 ? `${entry.copies} copies · ` : ''}
          {entry.paperWidth}mm · {formatDuration(entry.durationMs)}
        </Text>
        <Text style={styles.time}>{formatDate(entry.timestamp)}</Text>
        {entry.errorMessage && (
          <Text style={styles.errorMsg} numberOfLines={2}>
            {entry.errorMessage}
          </Text>
        )}
      </View>
      {!success && <Text style={styles.failBadge}>FAILED</Text>}
    </View>
  );
}

export function PrintHistoryScreen() {
  const {history, loading, refresh, clearHistory} = usePrintHistory();

  const totalJobs = history.length;
  const successCount = history.filter(h => h.success).length;
  const failCount = totalJobs - successCount;

  const handleClear = () => {
    Alert.alert('Clear History', 'Delete all print history?', [
      {text: 'Cancel', style: 'cancel'},
      {text: 'Clear', style: 'destructive', onPress: clearHistory},
    ]);
  };

  const Header = () => (
    <View style={styles.summary}>
      <SummaryItem label="Total" value={String(totalJobs)} />
      <SummaryDivider />
      <SummaryItem label="Success" value={String(successCount)} color="#16a34a" />
      <SummaryDivider />
      <SummaryItem label="Failed" value={String(failCount)} color={failCount > 0 ? '#dc2626' : '#9ca3af'} />
      <TouchableOpacity style={styles.clearBtn} onPress={handleClear} activeOpacity={0.7}>
        <Text style={styles.clearBtnText}>Clear All</Text>
      </TouchableOpacity>
    </View>
  );

  return (
    <FlatList
      style={styles.list}
      data={history}
      keyExtractor={item => item.id}
      renderItem={({item}) => <HistoryRow entry={item} />}
      contentContainerStyle={
        history.length === 0 ? styles.emptyContainer : styles.content
      }
      ListHeaderComponent={totalJobs > 0 ? <Header /> : null}
      ListEmptyComponent={
        loading ? null : (
          <EmptyState icon={<HistoryIcn size={48} color="#9ca3af" />} message="No print history yet." />
        )
      }
      refreshing={loading}
      onRefresh={refresh}
    />
  );
}

function SummaryItem({
  label,
  value,
  color = '#111827',
}: {
  label: string;
  value: string;
  color?: string;
}) {
  return (
    <View style={styles.summaryItem}>
      <Text style={[styles.summaryValue, {color}]}>{value}</Text>
      <Text style={styles.summaryLabel}>{label}</Text>
    </View>
  );
}

function SummaryDivider() {
  return <View style={styles.divider} />;
}

const styles = StyleSheet.create({
  list: {flex: 1},
  content: {
    padding: 12,
    paddingBottom: 32,
  },
  emptyContainer: {
    flex: 1,
  },
  summary: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    borderRadius: 10,
    padding: 16,
    marginBottom: 12,
  },
  summaryItem: {
    flex: 1,
    alignItems: 'center',
  },
  summaryValue: {
    fontSize: 22,
    fontWeight: '700',
  },
  summaryLabel: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 2,
  },
  divider: {
    width: StyleSheet.hairlineWidth,
    height: 36,
    backgroundColor: '#e5e7eb',
  },
  clearBtn: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 6,
    backgroundColor: '#fee2e2',
  },
  clearBtnText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#dc2626',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    backgroundColor: '#ffffff',
    borderRadius: 8,
    padding: 12,
    marginBottom: 6,
  },
  statusIcon: {
    width: 32,
    height: 32,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
    marginTop: 2,
  },
  info: {
    flex: 1,
  },
  printerName: {
    fontSize: 14,
    fontWeight: '600',
    color: '#111827',
  },
  meta: {
    fontSize: 12,
    color: '#374151',
    marginTop: 2,
  },
  time: {
    fontSize: 11,
    color: '#9ca3af',
    marginTop: 2,
  },
  errorMsg: {
    fontSize: 12,
    color: '#dc2626',
    marginTop: 4,
  },
  failBadge: {
    fontSize: 10,
    fontWeight: '700',
    color: '#dc2626',
    backgroundColor: '#fee2e2',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    alignSelf: 'flex-start',
    marginTop: 2,
  },
});
