import React from 'react';
import {TouchableOpacity, View, Text, StyleSheet} from 'react-native';

interface ListItemProps {
  label: string;
  value?: string;
  chevron?: boolean;
  rightElement?: React.ReactNode;
  onPress?: () => void;
  destructive?: boolean;
}

export function ListItem({
  label,
  value,
  chevron = false,
  rightElement,
  onPress,
  destructive = false,
}: ListItemProps) {
  const Container = onPress ? TouchableOpacity : View;

  return (
    <Container
      style={styles.row}
      onPress={onPress}
      activeOpacity={0.7}
    >
      <Text style={[styles.label, destructive && styles.destructive]}>{label}</Text>
      <View style={styles.right}>
        {value !== undefined && <Text style={styles.value}>{value}</Text>}
        {rightElement}
        {chevron && <Text style={styles.chevron}>&rsaquo;</Text>}
      </View>
    </Container>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#ffffff',
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
  destructive: {
    color: '#dc2626',
  },
  right: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  value: {
    fontSize: 15,
    color: '#6b7280',
    marginRight: 4,
  },
  chevron: {
    fontSize: 22,
    color: '#9ca3af',
    marginLeft: 4,
  },
});
