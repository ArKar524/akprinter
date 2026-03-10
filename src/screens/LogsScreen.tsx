import React from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
} from 'react-native';
import {FileIcn} from '../components/Icons';
import {useLogs} from '../hooks/useLogs';
import {EmptyState} from '../components/EmptyState';
import {formatDate} from '../utils/formatters';
import type {LogEntry, LogLevel} from '../types/logs';

type FilterOption = LogLevel | 'all';

const FILTERS: {label: string; value: FilterOption}[] = [
  {label: 'All', value: 'all'},
  {label: 'Info', value: 'info'},
  {label: 'Warn', value: 'warn'},
  {label: 'Error', value: 'error'},
  {label: 'Debug', value: 'debug'},
];

const LEVEL_COLORS: Record<LogLevel, string> = {
  info: '#2563eb',
  warn: '#d97706',
  error: '#dc2626',
  debug: '#6b7280',
};

function LogRow({entry}: {entry: LogEntry}) {
  const color = LEVEL_COLORS[entry.level];
  return (
    <View style={styles.logRow}>
      <View style={[styles.levelBadge, {backgroundColor: color + '20'}]}>
        <Text style={[styles.levelText, {color}]}>{entry.level.toUpperCase()}</Text>
      </View>
      <View style={styles.logContent}>
        <Text style={styles.logMessage}>{entry.message}</Text>
        <Text style={styles.logTime}>{formatDate(entry.timestamp)}</Text>
      </View>
    </View>
  );
}

export function LogsScreen() {
  const {filteredLogs, loading, filter, setFilter, refresh, clearLogs} = useLogs();

  const handleClear = () => {
    Alert.alert('Clear Logs', 'Delete all log entries?', [
      {text: 'Cancel', style: 'cancel'},
      {text: 'Clear', style: 'destructive', onPress: clearLogs},
    ]);
  };

  return (
    <View style={styles.container}>
      {/* Filter chips */}
      <View style={styles.filterRow}>
        {FILTERS.map(f => (
          <TouchableOpacity
            key={f.value}
            style={[styles.chip, filter === f.value && styles.chipActive]}
            onPress={() => setFilter(f.value)}
            activeOpacity={0.7}>
            <Text style={[styles.chipText, filter === f.value && styles.chipTextActive]}>
              {f.label}
            </Text>
          </TouchableOpacity>
        ))}
        <TouchableOpacity style={styles.clearChip} onPress={handleClear} activeOpacity={0.7}>
          <Text style={styles.clearChipText}>Clear</Text>
        </TouchableOpacity>
      </View>

      <FlatList
        data={filteredLogs}
        keyExtractor={item => item.id}
        renderItem={({item}) => <LogRow entry={item} />}
        contentContainerStyle={
          filteredLogs.length === 0 ? styles.emptyContainer : styles.list
        }
        ListEmptyComponent={
          loading ? null : (
            <EmptyState icon={<FileIcn size={48} color="#9ca3af" />} message="No log entries to display." />
          )
        }
        refreshing={loading}
        onRefresh={refresh}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {flex: 1},
  filterRow: {
    flexDirection: 'row',
    paddingHorizontal: 16,
    paddingVertical: 10,
    gap: 6,
    backgroundColor: '#ffffff',
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#e5e7eb',
    flexWrap: 'wrap',
  },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#f3f4f6',
    borderWidth: 1,
    borderColor: 'transparent',
  },
  chipActive: {
    backgroundColor: '#eff6ff',
    borderColor: '#2563eb',
  },
  chipText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#6b7280',
  },
  chipTextActive: {
    color: '#2563eb',
  },
  clearChip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
    backgroundColor: '#fee2e2',
  },
  clearChipText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#dc2626',
  },
  list: {
    padding: 12,
  },
  emptyContainer: {
    flex: 1,
  },
  logRow: {
    flexDirection: 'row',
    backgroundColor: '#ffffff',
    borderRadius: 8,
    padding: 10,
    marginBottom: 6,
    alignItems: 'flex-start',
  },
  levelBadge: {
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 4,
    marginRight: 10,
    marginTop: 1,
  },
  levelText: {
    fontSize: 10,
    fontWeight: '700',
  },
  logContent: {
    flex: 1,
  },
  logMessage: {
    fontSize: 13,
    color: '#111827',
    lineHeight: 18,
  },
  logTime: {
    fontSize: 11,
    color: '#9ca3af',
    marginTop: 3,
  },
});
