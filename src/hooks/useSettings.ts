import {useState, useEffect, useCallback} from 'react';
import type {AppSettings} from '../types/settings';
import {DEFAULT_SETTINGS} from '../types/settings';
import {PrinterBridge} from '../services/PrinterBridge';

export function useSettings() {
  const [settings, setSettings] = useState<AppSettings>(DEFAULT_SETTINGS);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const loadSettings = useCallback(async () => {
    setLoading(true);
    try {
      const s = await PrinterBridge.getSettings();
      setSettings({...DEFAULT_SETTINGS, ...s});
    } catch {
      setSettings(DEFAULT_SETTINGS);
    } finally {
      setLoading(false);
    }
  }, []);

  const updateSettings = useCallback(
    async (partial: Partial<AppSettings>) => {
      const merged = {...settings, ...partial};
      setSaving(true);
      try {
        await PrinterBridge.saveSettings(merged);
        setSettings(merged);
      } finally {
        setSaving(false);
      }
    },
    [settings],
  );

  useEffect(() => {
    loadSettings();
  }, [loadSettings]);

  return {settings, loading, saving, updateSettings, reload: loadSettings};
}
