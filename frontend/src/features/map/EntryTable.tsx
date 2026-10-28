import { Trash2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { EntryDTO } from '../../api/types';
import { TypeBadge, ValuePreview } from '../../components/ui/ValueBadges';
import { formatBytes, formatDateTime, formatDuration, formatNumber } from '../../lib/format';

interface EntryTableProps {
  entries: EntryDTO[];
  onDelete: (entry: EntryDTO) => void;
  /** Opens the entry inspector; rows are clickable when provided. */
  onOpen?: (entry: EntryDTO) => void;
}

/** IMap entries: key, key type, value preview, memory cost, hits, TTL/expiration, actions. */
export function EntryTable({ entries, onDelete, onOpen }: EntryTableProps) {
  const { t, i18n } = useTranslation();
  return (
    <div className="overflow-x-auto">
      <table className="w-full table-fixed text-left">
        <colgroup>
          <col className="w-[22%]" />
          <col className="w-[110px]" />
          <col />
          <col className="w-[130px]" />
          <col className="w-[80px]" />
          <col className="w-[170px]" />
          <col className="w-[70px]" />
        </colgroup>
        <thead className="sticky top-0 z-10 bg-gk-panel text-xs uppercase tracking-wide text-gk-muted">
          <tr>
            <th className="px-3 py-2 font-medium">{t('browser.columns.key')}</th>
            <th className="px-3 py-2 font-medium">{t('browser.columns.keyType')}</th>
            <th className="px-3 py-2 font-medium">{t('browser.columns.value')}</th>
            <th className="px-3 py-2 text-right font-medium">{t('browser.columns.cost')}</th>
            <th className="px-3 py-2 text-right font-medium">{t('browser.columns.hits')}</th>
            <th className="px-3 py-2 font-medium">{t('browser.columns.ttl')}</th>
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
              <td className="gk-mono px-3 py-2 text-right">{formatNumber(entry.metadata?.hits, i18n.language)}</td>
              <td className="px-3 py-2 text-xs">
                <Expiration expirationTime={entry.metadata?.expirationTime} ttl={entry.metadata?.ttl} />
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

/** Remaining lifetime (or "never expires") with the absolute expiration time as tooltip. */
function Expiration({ expirationTime, ttl }: { expirationTime?: number; ttl?: number }) {
  const { t, i18n } = useTranslation();
  if (!expirationTime) {
    return <span className="text-gk-subtle">{t('browser.noExpiry')}</span>;
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
