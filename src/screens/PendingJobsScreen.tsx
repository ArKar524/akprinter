import React, {useCallback, useEffect, useState} from 'react';
import {
  View,
  Text,
  FlatList,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ActivityIndicator,
} from 'react-native';
import {FileIcn, PrinterIcn} from '../components/Icons';
import {EmptyState} from '../components/EmptyState';
import {PrinterBridge} from '../services/PrinterBridge';
import {usePrinters} from '../hooks/usePrinters';
import {formatDate} from '../utils/formatters';
import type {PendingJob} from '../types/logs';

export function PendingJobsScreen() {
  const [jobs, setJobs] = useState<PendingJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [printingId, setPrintingId] = useState<string | null>(null);
  const {printers, defaultPrinter} = usePrinters();

  const loadJobs = useCallback(() => {
    setLoading(true);
    PrinterBridge.getPendingJobs()
      .then(data => setJobs(data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    loadJobs();
    const sub = PrinterBridge.onPendingJobAdded(() => loadJobs());
    return () => sub.remove();
  }, [loadJobs]);

  const handlePrint = (job: PendingJob) => {
    if (printers.length === 0) {
      Alert.alert('No Printers', 'Please add a printer first.');
      return;
    }

    if (printers.length === 1) {
      doPrint(job.id, printers[0].id, printers[0].name);
      return;
    }

    if (defaultPrinter) {
      doPrint(job.id, defaultPrinter.id, defaultPrinter.name);
      return;
    }

    // Multiple printers, no default — let user choose
    Alert.alert(
      'Select Printer',
      'Which printer should be used?',
      printers.map(p => ({
        text: p.name,
        onPress: () => doPrint(job.id, p.id, p.name),
      })),
    );
  };

  const doPrint = async (jobId: string, printerId: string, printerName: string) => {
    setPrintingId(jobId);
    try {
      await PrinterBridge.printPendingJob(jobId, printerId);
      Alert.alert('Success', `Printed to ${printerName}`);
      loadJobs();
    } catch (e: any) {
      Alert.alert('Print Failed', e?.message || 'Could not print');
    } finally {
      setPrintingId(null);
    }
  };

  const handleDelete = (job: PendingJob) => {
    Alert.alert('Delete Job', `Delete "${job.documentName}"?`, [
      {text: 'Cancel', style: 'cancel'},
      {
        text: 'Delete',
        style: 'destructive',
        onPress: async () => {
          await PrinterBridge.deletePendingJob(job.id);
          loadJobs();
        },
      },
    ]);
  };

  const renderItem = ({item}: {item: PendingJob}) => {
    const isPrinting = printingId === item.id;
    return (
      <View style={styles.row}>
        <View style={styles.docIcon}>
          <FileIcn size={22} color="#2563eb" />
        </View>
        <View style={styles.info}>
          <Text style={styles.docName} numberOfLines={2}>
            {item.documentName}
          </Text>
          <Text style={styles.meta}>
            {item.pageCount > 0
              ? `${item.pageCount} page${item.pageCount !== 1 ? 's' : ''} · `
              : ''}
            {formatDate(item.createdAt)}
          </Text>
        </View>
        <View style={styles.actions}>
          {isPrinting ? (
            <ActivityIndicator size="small" color="#2563eb" />
          ) : (
            <TouchableOpacity
              style={styles.printBtn}
              onPress={() => handlePrint(item)}
              activeOpacity={0.7}>
              <PrinterIcn size={16} color="#ffffff" />
              <Text style={styles.printBtnText}>Print</Text>
            </TouchableOpacity>
          )}
          <TouchableOpacity
            style={styles.deleteBtn}
            onPress={() => handleDelete(item)}
            activeOpacity={0.7}>
            <Text style={styles.deleteBtnText}>Delete</Text>
          </TouchableOpacity>
        </View>
      </View>
    );
  };

  return (
    <FlatList
      style={styles.list}
      data={jobs}
      keyExtractor={item => item.id}
      renderItem={renderItem}
      contentContainerStyle={
        jobs.length === 0 ? styles.emptyContainer : styles.content
      }
      ListHeaderComponent={
        jobs.length > 0 ? (
          <View style={styles.header}>
            <Text style={styles.headerText}>
              {jobs.length} pending job{jobs.length !== 1 ? 's' : ''}
            </Text>
          </View>
        ) : null
      }
      ListEmptyComponent={
        loading ? null : (
          <EmptyState
            icon={<PrinterIcn size={48} color="#9ca3af" />}
            message={'No pending print jobs.\nPrint from any app to see jobs here.'}
          />
        )
      }
      refreshing={loading}
      onRefresh={loadJobs}
    />
  );
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
  header: {
    marginBottom: 12,
  },
  headerText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#6b7280',
  },
  row: {
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
  docIcon: {
    width: 40,
    height: 40,
    borderRadius: 10,
    backgroundColor: '#eff6ff',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  info: {
    flex: 1,
    marginRight: 10,
  },
  docName: {
    fontSize: 14,
    fontWeight: '600',
    color: '#111827',
  },
  meta: {
    fontSize: 12,
    color: '#6b7280',
    marginTop: 3,
  },
  actions: {
    alignItems: 'center',
    gap: 6,
  },
  printBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#2563eb',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 8,
    gap: 5,
  },
  printBtnText: {
    fontSize: 13,
    fontWeight: '600',
    color: '#ffffff',
  },
  deleteBtn: {
    paddingHorizontal: 8,
    paddingVertical: 4,
  },
  deleteBtnText: {
    fontSize: 12,
    color: '#9ca3af',
  },
});
