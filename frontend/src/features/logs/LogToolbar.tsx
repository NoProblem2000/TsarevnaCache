import type { ReactNode } from 'react';
import { ArrowDownToLine, Download, RefreshCw, Search, WrapText, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { LOG_LEVELS, type LogLevelFilter } from '../../api/types';
import { Segmented } from '../../components/ui/Segmented';

/** Lines offered for the tail of server files and local files. */
export const LINE_LIMITS = [500, 1000, 2000, 5000] as const;

interface LogToolbarProps {
  /** Source selector on the left (file select, open button, ...). */
  source?: ReactNode;
  level: LogLevelFilter;
  onLevelChange: (level: LogLevelFilter) => void;
  search: string;
  onSearchChange: (search: string) => void;
  lines?: number;
  onLinesChange?: (lines: number) => void;
  /** Auto-refresh (Live Tail 3s); hidden when undefined. */
  autoRefresh?: boolean;
  onAutoRefreshChange?: (enabled: boolean) => void;
  onRefresh?: () => void;
  refreshing?: boolean;
  wrap: boolean;
  onWrapChange: (wrap: boolean) => void;
  onScrollToBottom: () => void;
  /** Download Log: a link (server files) or a click handler (live stream, local file). */
  downloadHref?: string;
  onDownload?: () => void;
  downloadDisabled?: boolean;
  /** Extra buttons on the right. */
  actions?: ReactNode;
}

/** Controls of a log view: source, level, search, lines, live tail, wrap, scroll and download. */
export function LogToolbar(props: LogToolbarProps) {
  const { t } = useTranslation();
  const {
    source,
    level,
    onLevelChange,
    search,
    onSearchChange,
    lines,
    onLinesChange,
    autoRefresh,
    onAutoRefreshChange,
    onRefresh,
    refreshing,
    wrap,
    onWrapChange,
    onScrollToBottom,
    downloadHref,
    onDownload,
    downloadDisabled,
    actions,
  } = props;
  const levels: LogLevelFilter[] = ['ALL', ...LOG_LEVELS.filter((value) => value !== 'DEBUG')];

  return (
    <div className="flex flex-wrap items-center gap-2 border-b border-gk-border bg-gk-panel px-4 py-2">
      {source}
      <div title={t('logs.levelHint')}>
        <Segmented<LogLevelFilter>
          ariaLabel={t('logs.level')}
          value={level}
          onChange={onLevelChange}
          options={levels.map((value) => ({ value, label: t(`logs.levels.${value}`) }))}
        />
      </div>
      <label className="relative min-w-[200px] flex-1">
        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-gk-subtle" aria-hidden />
        <input
          type="search"
          className="gk-input gk-mono py-1.5 pl-8 pr-8 text-xs"
          value={search}
          onChange={(event) => onSearchChange(event.target.value)}
          placeholder={t('logs.searchPlaceholder')}
          aria-label={t('logs.searchPlaceholder')}
        />
        {search && (
          <button
            type="button"
            className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded p-1 text-gk-subtle hover:text-gk-text"
            onClick={() => onSearchChange('')}
            aria-label={t('logs.clearSearch')}
            title={t('logs.clearSearch')}
          >
            <X className="h-3 w-3" aria-hidden />
          </button>
        )}
      </label>
      {lines !== undefined && onLinesChange && (
        <label className="flex items-center gap-1.5 text-xs text-gk-muted">
          {t('logs.lines')}
          <select className="gk-input w-auto py-1 text-xs" value={lines} onChange={(event) => onLinesChange(Number(event.target.value))}>
            {LINE_LIMITS.map((limit) => (
              <option key={limit} value={limit}>
                {limit}
              </option>
            ))}
          </select>
        </label>
      )}
      {autoRefresh !== undefined && onAutoRefreshChange && (
        <label
          className={`flex cursor-pointer items-center gap-2 rounded-md border px-2.5 py-1 text-xs transition-colors ${
            autoRefresh ? 'border-gk-cyan/50 bg-gk-cyan/10 text-gk-cyan' : 'border-gk-border text-gk-muted hover:text-gk-text'
          }`}
          title={t('logs.autoRefreshHint')}
        >
          <input type="checkbox" className="accent-gk-cyan" checked={autoRefresh} onChange={(event) => onAutoRefreshChange(event.target.checked)} />
          <span className={`h-1.5 w-1.5 rounded-full ${autoRefresh ? 'animate-pulse bg-gk-cyan' : 'bg-gk-subtle'}`} aria-hidden />
          {t('logs.autoRefresh')}
        </label>
      )}
      {onRefresh && (
        <button type="button" className="gk-btn-ghost px-2 py-1" onClick={onRefresh} title={t('logs.refresh')} aria-label={t('logs.refresh')}>
          <RefreshCw className={`h-4 w-4 ${refreshing ? 'animate-spin' : ''}`} aria-hidden />
        </button>
      )}
      <button
        type="button"
        className={`gk-btn-ghost px-2 py-1 ${wrap ? 'text-gk-cyan' : ''}`}
        onClick={() => onWrapChange(!wrap)}
        aria-pressed={wrap}
        title={t('logs.wrap')}
      >
        <WrapText className="h-4 w-4" aria-hidden />
        <span className="hidden xl:inline">{t('logs.wrap')}</span>
      </button>
      <button type="button" className="gk-btn-ghost px-2 py-1" onClick={onScrollToBottom} title={t('logs.scrollToBottom')}>
        <ArrowDownToLine className="h-4 w-4" aria-hidden />
        <span className="hidden xl:inline">{t('logs.scrollToBottom')}</span>
      </button>
      {downloadHref ? (
        <a className={`gk-btn-secondary px-2.5 py-1 ${downloadDisabled ? 'pointer-events-none opacity-50' : ''}`} href={downloadHref} download title={t('logs.downloadHint')}>
          <Download className="h-4 w-4" aria-hidden />
          {t('logs.download')}
        </a>
      ) : (
        onDownload && (
          <button type="button" className="gk-btn-secondary px-2.5 py-1" onClick={onDownload} disabled={downloadDisabled}>
            <Download className="h-4 w-4" aria-hidden />
            {t('logs.download')}
          </button>
        )
      )}
      {actions}
    </div>
  );
}
