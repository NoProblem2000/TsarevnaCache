import { ChevronLeft, ChevronRight, ChevronsLeft } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { formatNumber } from '../../lib/format';

export const PAGE_SIZES = [10, 20, 50, 100];

interface PaginationProps {
  page: number;
  pageSize: number;
  shown: number;
  hasNext: boolean;
  total?: number;
  onFirst: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onPageSizeChange: (size: number) => void;
  busy?: boolean;
}

/** Previous/next paging (pages are cursor based, so there is no direct jump to page N). */
export function Pagination({ page, pageSize, shown, hasNext, total, onFirst, onPrevious, onNext, onPageSizeChange, busy }: PaginationProps) {
  const { t, i18n } = useTranslation();
  const from = shown === 0 ? 0 : page * pageSize + 1;
  const to = page * pageSize + shown;
  return (
    <div className="flex flex-wrap items-center gap-3 border-t border-gk-border bg-gk-panel px-4 py-2 text-xs text-gk-muted">
      <label className="flex items-center gap-2">
        {t('browser.pageSize')}
        <select
          className="gk-input w-auto py-1 text-xs"
          value={pageSize}
          onChange={(event) => onPageSizeChange(Number(event.target.value))}
        >
          {PAGE_SIZES.map((size) => (
            <option key={size} value={size}>
              {size}
            </option>
          ))}
        </select>
      </label>
      {total !== undefined && <span>{t('browser.total', { total: formatNumber(total, i18n.language) })}</span>}
      <span className="ml-auto gk-mono">
        {t('browser.pageInfo', { page: page + 1 })} · {t('browser.pageRange', { from, to })}
      </span>
      <div className="flex items-center gap-1">
        <button type="button" className="gk-btn-ghost px-2 py-1" onClick={onFirst} disabled={page === 0 || busy} title={t('browser.first')} aria-label={t('browser.first')}>
          <ChevronsLeft className="h-4 w-4" aria-hidden />
        </button>
        <button type="button" className="gk-btn-ghost px-2 py-1" onClick={onPrevious} disabled={page === 0 || busy}>
          <ChevronLeft className="h-4 w-4" aria-hidden />
          {t('browser.previous')}
        </button>
        <button type="button" className="gk-btn-ghost px-2 py-1" onClick={onNext} disabled={!hasNext || busy}>
          {t('browser.next')}
          <ChevronRight className="h-4 w-4" aria-hidden />
        </button>
      </div>
    </div>
  );
}
