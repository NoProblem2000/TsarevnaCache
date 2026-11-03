import { AlertTriangle, ArrowDown, ArrowUp, Info } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { ENTRY_SORTS, type EntryPageDTO, type EntrySortBy } from '../../api/types';
import { formatNumber } from '../../lib/format';
import { SCAN_LIMITS, SORT_PRESETS, defaultDirection, isSorted, presetOf, type EntrySort } from './entrySort';

interface SortToolbarProps {
  sort: EntrySort;
  onSortChange: (sort: EntrySort) => void;
  scanLimit: number;
  onScanLimitChange: (limit: number) => void;
  /** The current page, for the sampling summary. */
  page?: EntryPageDTO;
  /** Exact key lookups are never sorted. */
  disabled?: boolean;
}

/**
 * Hot keys analyzer controls: quick presets (Default Order, Most Popular, Cold Keys, Largest in
 * Memory), any order with its direction, the scan limit and a summary of how many entries the
 * ranking was built from.
 */
export function SortToolbar({ sort, onSortChange, scanLimit, onScanLimitChange, page, disabled }: SortToolbarProps) {
  const { t, i18n } = useTranslation();
  const active = presetOf(sort);
  const sorted = isSorted(sort) && !disabled;
  const ranked = sorted && page?.mode === 'SORTED' ? page : undefined;

  return (
    <div className="space-y-2 border-b border-gk-border px-4 py-2">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-xs font-medium uppercase tracking-wide text-gk-muted">{t('browser.sort.title')}</span>
        {SORT_PRESETS.map((preset) => {
          const selected = !disabled && active === preset.id;
          return (
            <button
              key={preset.id}
              type="button"
              disabled={disabled}
              aria-pressed={selected}
              onClick={() => onSortChange(preset.sort)}
              title={t(`browser.sort.presets.${preset.id}Hint`)}
              className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors disabled:opacity-40 ${
                selected
                  ? 'border-gk-cyan/60 bg-gk-cyan/10 text-gk-cyan shadow-[0_0_10px_rgba(0,229,255,0.15)]'
                  : 'border-gk-border bg-gk-bg text-gk-muted hover:border-gk-subtle hover:text-gk-text'
              }`}
            >
              {t(`browser.sort.presets.${preset.id}`)}
            </button>
          );
        })}

        <div className="ml-auto flex items-center gap-1.5">
          <label className="flex items-center gap-1.5 text-xs text-gk-muted">
            {t('browser.sort.orderBy')}
            <select
              className="gk-input w-auto py-1 text-xs"
              value={disabled ? 'NONE' : sort.sortBy}
              disabled={disabled}
              onChange={(event) => {
                const sortBy = event.target.value as EntrySortBy;
                onSortChange({ sortBy, direction: defaultDirection(sortBy) });
              }}
            >
              {ENTRY_SORTS.map((sortBy) => (
                <option key={sortBy} value={sortBy}>
                  {t(`browser.sort.fields.${sortBy}`)}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="gk-btn-ghost px-2 py-1"
            disabled={!sorted}
            onClick={() => onSortChange({ ...sort, direction: sort.direction === 'DESC' ? 'ASC' : 'DESC' })}
            title={t(sort.direction === 'DESC' ? 'browser.sort.desc' : 'browser.sort.asc')}
            aria-label={t(sort.direction === 'DESC' ? 'browser.sort.desc' : 'browser.sort.asc')}
          >
            {sort.direction === 'DESC' ? (
              <ArrowDown className={`h-4 w-4 ${sorted ? 'text-gk-cyan' : ''}`} aria-hidden />
            ) : (
              <ArrowUp className={`h-4 w-4 ${sorted ? 'text-gk-cyan' : ''}`} aria-hidden />
            )}
          </button>
          <label className="flex items-center gap-1.5 text-xs text-gk-muted" title={t('browser.sort.scanLimitHint')}>
            {t('browser.sort.scanLimit')}
            <select
              className="gk-input w-auto py-1 text-xs"
              value={scanLimit}
              disabled={!sorted}
              onChange={(event) => onScanLimitChange(Number(event.target.value))}
            >
              {SCAN_LIMITS.map((limit) => (
                <option key={limit} value={limit}>
                  {formatNumber(limit, i18n.language)}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      {ranked && (
        <div className="flex flex-wrap items-start gap-x-4 gap-y-1 text-xs">
          <p className={`flex items-start gap-1.5 ${ranked.sampled ? 'text-gk-warning' : 'text-gk-muted'}`}>
            {ranked.sampled ? (
              <AlertTriangle className="mt-0.5 h-3 w-3 shrink-0" aria-hidden />
            ) : (
              <Info className="mt-0.5 h-3 w-3 shrink-0" aria-hidden />
            )}
            {t(
              !ranked.sampled ? 'browser.sort.complete' : ranked.total === undefined ? 'browser.sort.sampledFiltered' : 'browser.sort.sampled',
              {
                count: ranked.scannedCount ?? 0,
                scanned: formatNumber(ranked.scannedCount, i18n.language),
                total: formatNumber(ranked.total, i18n.language),
                field: t(`browser.sort.fields.${sort.sortBy}`),
              },
            )}
          </p>
          {sort.sortBy === 'HITS' && ranked.hitsAvailable === false && (
            <p className="flex items-start gap-1.5 text-gk-warning">
              <AlertTriangle className="mt-0.5 h-3 w-3 shrink-0" aria-hidden />
              {t('browser.sort.hitsDisabled')}
            </p>
          )}
        </div>
      )}
    </div>
  );
}
