import type { ReactNode } from 'react';
import { ArrowDown, ArrowUp, ArrowUpDown, Flame, Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { EntryDTO, EntrySortBy } from '../../api/types';
import { TypeBadge, ValuePreview } from '../../components/ui/ValueBadges';
import { formatBytes, formatDateTime, formatDuration, formatNumber } from '../../lib/format';
import { hitIntensity, pageMaxHits, type EntrySort, type HitTone } from './entrySort';

interface EntryTableProps {
  entries: EntryDTO[];
  onDelete: (entry: EntryDTO) => void;
  /** Opens the entry inspector; rows are clickable when provided. */
  onOpen?: (entry: EntryDTO) => void;
  /** Applied order; the Key, Cost, Hits and TTL / Last Access headers sort when `onSort` is provided. */
  sort?: EntrySort;
  onSort?: (column: EntrySortBy) => void;
  /** Highest hit count of the ranked sample; the page maximum is used otherwise. */
  maxHits?: number;
}

/**
 * IMap entries: key, key type, value preview, memory cost, hits (with an intensity bar relative to
 * the most popular entry), TTL / last access, actions.
 */
export function EntryTable({ entries, onDelete, onOpen, sort, onSort, maxHits }: EntryTableProps) {
  const { t, i18n } = useTranslation();
  const highest = maxHits ?? pageMaxHits(entries);
  const header = (column: EntrySortBy, label: string, align: 'left' | 'right' = 'left') => (
    <SortableHeader column={column} label={label} align={align} sort={sort} onSort={onSort} />
  );
  return (
    <div className="overflow-x-auto">
      <table className="w-full table-fixed text-left">
        <colgroup>
          <col className="w-[22%]" />
          <col className="w-[110px]" />
          <col />
          <col className="w-[130px]" />
          <col className="w-[120px]" />
          <col className="w-[180px]" />
          <col className="w-[70px]" />
        </colgroup>
        <thead className="sticky top-0 z-10 bg-gk-panel text-xs uppercase tracking-wide text-gk-muted">
          <tr>
            {header('KEY', t('browser.columns.key'))}
            <th className="px-3 py-2 font-medium">{t('browser.columns.keyType')}</th>
            <th className="px-3 py-2 font-medium">{t('browser.columns.value')}</th>
            {header('COST', t('browser.columns.cost'), 'right')}
            {header('HITS', t('browser.columns.hits'), 'right')}
            {header('LAST_ACCESS', t('browser.columns.ttl'))}
            <th className="px-3 py-2 text-right font-medium">
              <span className="sr-only">{t('browser.columns.actions')}</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry) => (
            <tr
              key={entry.keyRef}
              className={`group border-t border-gk-border transition-colors hover:bg-gk-card ${onOpen ? 'cursor-pointer' : ''}`}
              onClick={onOpen ? () => onOpen(entry) : undefined}
              title={onOpen ? t('browser.open') : undefined}
            >
              <td className="px-3 py-2">
                {entry.key.deserialized ? (
                  <span className="gk-mono block truncate text-gk-cyan" title={entry.key.preview}>
                    {entry.key.preview}
                  </span>
                ) : (
                  <ValuePreview value={entry.key} />
                )}
              </td>
              <td className="px-3 py-2">
                <TypeBadge value={entry.key} />
              </td>
              <td className="px-3 py-2">
                <div className="flex min-w-0 items-center gap-2">
                  {entry.value.deserialized && <TypeBadge value={entry.value} />}
                  <div className="min-w-0 flex-1">
                    <ValuePreview value={entry.value} />
                  </div>
                </div>
              </td>
              <td className="gk-mono px-3 py-2 text-right" title={formatBytes(entry.metadata?.cost)}>
                {formatNumber(entry.metadata?.cost, i18n.language)}
              </td>
              <td className="px-3 py-2 text-right">
                <Hits hits={entry.metadata?.hits} maxHits={highest} />
              </td>
              <td className="px-3 py-2 text-xs">
                <Expiration expirationTime={entry.metadata?.expirationTime} ttl={entry.metadata?.ttl} />
                <LastAccess lastAccessTime={entry.metadata?.lastAccessTime} />
              </td>
              <td className="px-3 py-2 text-right">
                <button
                  type="button"
                  className="rounded p-1.5 text-gk-subtle opacity-60 hover:bg-gk-danger/15 hover:text-gk-danger group-hover:opacity-100"
                  onClick={(event) => {
                    event.stopPropagation();
                    onDelete(entry);
                  }}
                  title={t('browser.delete')}
                  aria-label={t('browser.delete')}
                >
                  <Trash2 className="h-3.5 w-3.5" aria-hidden />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

interface SortableHeaderProps {
  column: EntrySortBy;
  label: string;
  align: 'left' | 'right';
  sort?: EntrySort;
  onSort?: (column: EntrySortBy) => void;
}

/** Column header that sorts by its metric; the active one shows its direction in cyan. */
function SortableHeader({ column, label, align, sort, onSort }: SortableHeaderProps) {
  const { t } = useTranslation();
  const active = sort?.sortBy === column;
  let content: ReactNode = label;
  if (onSort) {
    const Icon = !active ? ArrowUpDown : sort?.direction === 'DESC' ? ArrowDown : ArrowUp;
    content = (
      <button
        type="button"
        onClick={() => onSort(column)}
        title={t('browser.sort.byColumn', { column: label })}
        className={`group/sort inline-flex items-center gap-1 font-medium uppercase tracking-wide transition-colors ${
          align === 'right' ? 'flex-row-reverse' : ''
        } ${active ? 'text-gk-cyan' : 'hover:text-gk-text'}`}
      >
        <span>{label}</span>
        <Icon
          className={`h-3.5 w-3.5 shrink-0 ${active ? 'text-[#00E5FF]' : 'opacity-40 group-hover/sort:opacity-100'}`}
          aria-hidden
        />
      </button>
    );
  }
  return (
    <th
      className={`px-3 py-2 font-medium ${align === 'right' ? 'text-right' : ''}`}
      aria-sort={!onSort ? undefined : !active ? 'none' : sort?.direction === 'DESC' ? 'descending' : 'ascending'}
    >
      {content}
    </th>
  );
}

const HIT_TONES: Record<HitTone, { bar: string; text: string }> = {
  hot: { bar: 'bg-gk-cyan', text: 'text-gk-cyan' },
  warm: { bar: 'bg-gk-cyan/70', text: 'text-gk-text' },
  cool: { bar: 'bg-gk-cyan/40', text: 'text-gk-text' },
  cold: { bar: 'bg-transparent', text: 'text-gk-subtle' },
};

/** Hit count with a bar relative to the most popular entry; the hottest entries get a flame. */
function Hits({ hits, maxHits }: { hits?: number; maxHits?: number }) {
  const { t, i18n } = useTranslation();
  const intensity = hitIntensity(hits, maxHits);
  if (!intensity) {
    return (
      <span className="gk-mono text-gk-subtle" title={hits === undefined ? t('browser.sort.hitsUnknown') : undefined}>
        {formatNumber(hits, i18n.language)}
      </span>
    );
  }
  const tone = HIT_TONES[intensity.tone];
  const percent = Math.round(intensity.ratio * 100);
  return (
    <div className="flex flex-col items-end gap-1" title={t('browser.sort.hitsShare', { percent })}>
      <span className={`gk-mono inline-flex items-center gap-1 ${tone.text}`}>
        {intensity.tone === 'hot' && <Flame className="h-3.5 w-3.5 text-gk-warning" aria-label={t('browser.sort.hot')} />}
        {formatNumber(hits, i18n.language)}
      </span>
      <span className="block h-1 w-full max-w-[84px] overflow-hidden rounded-full bg-gk-border" aria-hidden>
        <span className={`block h-full rounded-full ${tone.bar}`} style={{ width: `${hits ? Math.max(percent, 4) : 0}%` }} />
      </span>
    </div>
  );
}

/** "Accessed 5 min ago" below the expiration. */
function LastAccess({ lastAccessTime }: { lastAccessTime?: number }) {
  const { t, i18n } = useTranslation();
  if (!lastAccessTime) {
    return <span className="block text-gk-subtle">{t('browser.neverAccessed')}</span>;
  }
  return (
    <span className="block text-gk-muted" title={formatDateTime(lastAccessTime, i18n.language)}>
      {t('browser.lastAccess', { time: formatDuration(Math.max(0, Date.now() - lastAccessTime), t) })}
    </span>
  );
}

/** Remaining lifetime (or "never expires") with the absolute expiration time as tooltip. */
function Expiration({ expirationTime, ttl }: { expirationTime?: number; ttl?: number }) {
  const { t, i18n } = useTranslation();
  if (!expirationTime) {
    return <span className="block text-gk-subtle">{t('browser.noExpiry')}</span>;
  }
  const remaining = expirationTime - Date.now();
  return (
    <span title={formatDateTime(expirationTime, i18n.language)} className="flex flex-col">
      <span className={remaining <= 0 ? 'text-gk-danger' : 'text-gk-warning'}>
        {remaining <= 0 ? t('browser.expired') : t('browser.expiresIn', { time: formatDuration(remaining, t) })}
      </span>
      {ttl !== undefined && <span className="text-gk-subtle">{t('browser.ttlValue', { time: formatDuration(ttl, t) })}</span>}
    </span>
  );
}
