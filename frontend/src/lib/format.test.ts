import { describe, expect, it } from 'vitest';
import type { TFunction } from 'i18next';
import { formatBytes, formatDuration, formatNumber } from './format';

const t = ((key: string, options?: { count?: number }) =>
  `${options?.count}${key.replace('time.', '').charAt(0)}`) as unknown as TFunction;

describe('format', () => {
  it('formats bytes with base 1024 units', () => {
    expect(formatBytes(0)).toBe('0 B');
    expect(formatBytes(1023)).toBe('1023 B');
    expect(formatBytes(1536)).toBe('1.50 KB');
    expect(formatBytes(5 * 1024 * 1024)).toBe('5.00 MB');
    expect(formatBytes(300 * 1024 * 1024 * 1024)).toBe('300 GB');
    expect(formatBytes(undefined)).toBe('—');
  });

  it('formats numbers with grouping', () => {
    expect(formatNumber(1234567, 'en')).toBe('1,234,567');
  });

  it('formats durations with the two most significant units', () => {
    expect(formatDuration(0, t)).toBe('0s');
    expect(formatDuration(65_000, t)).toBe('1m 5s');
    expect(formatDuration(3_600_000, t)).toBe('1h');
    expect(formatDuration(2 * 86_400_000 + 4 * 3_600_000 + 60_000, t)).toBe('2d 4h');
  });
});
