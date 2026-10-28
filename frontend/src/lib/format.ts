import type { TFunction } from 'i18next';

const UNITS = ['B', 'KB', 'MB', 'GB', 'TB', 'PB'];

/** Formats a byte count with base-1024 units, e.g. `1.50 MB`. */
export function formatBytes(bytes: number | undefined | null): string {
  if (bytes === undefined || bytes === null || Number.isNaN(bytes)) {
    return '—';
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value >= 100 ? value.toFixed(0) : value.toFixed(2)} ${UNITS[unit]}`;
}

/** Formats an integer with locale specific grouping. */
export function formatNumber(value: number | undefined | null, locale: string): string {
  if (value === undefined || value === null || Number.isNaN(value)) {
    return '—';
  }
  return new Intl.NumberFormat(locale).format(value);
}

/** Formats an epoch-millis timestamp as a local date and time. */
export function formatDateTime(epochMillis: number | undefined | null, locale: string): string {
  if (!epochMillis) {
    return '—';
  }
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(epochMillis));
}

/** Formats a time of day for "updated at" hints. */
export function formatTime(epochMillis: number | undefined | null, locale: string): string {
  if (!epochMillis) {
    return '—';
  }
  return new Intl.DateTimeFormat(locale, { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(
    new Date(epochMillis),
  );
}

/** Formats a duration as the two most significant units, e.g. `2d 4h` or `5m 12s`. */
export function formatDuration(millis: number | undefined | null, t: TFunction): string {
  if (millis === undefined || millis === null || millis < 0) {
    return '—';
  }
  const totalSeconds = Math.floor(millis / 1000);
  const parts: [string, number][] = [
    ['time.days', Math.floor(totalSeconds / 86400)],
    ['time.hours', Math.floor((totalSeconds % 86400) / 3600)],
    ['time.minutes', Math.floor((totalSeconds % 3600) / 60)],
    ['time.seconds', totalSeconds % 60],
  ];
  const first = parts.findIndex(([, value]) => value > 0);
  if (first < 0) {
    return t('time.seconds', { count: 0 });
  }
  return parts
    .slice(first, first + 2)
    .filter(([, value]) => value > 0)
    .map(([key, value]) => t(key, { count: value }))
    .join(' ');
}
