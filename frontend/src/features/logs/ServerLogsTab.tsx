import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { AlertTriangle, FileText, FolderOpen } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { endpoints } from '../../api/endpoints';
import type { LogLevelFilter } from '../../api/types';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { CenteredSpinner } from '../../components/ui/Spinner';
import { formatBytes, formatDateTime, formatNumber } from '../../lib/format';
import { LogTerminal } from './LogTerminal';
import { LogToolbar } from './LogToolbar';
import { toTerminalLines } from './logModel';
import { useDebounced } from './useDebounced';

interface ServerLogsTabProps {
  wrap: boolean;
  onWrapChange: (wrap: boolean) => void;
}

/**
 * Log files on the server (admin.logs.paths): the end of the selected file, filtered by level and
 * text on the server, optionally refreshed every 3 seconds (Live Tail), and the whole file as a
 * download.
 */
export function ServerLogsTab({ wrap, onWrapChange }: ServerLogsTabProps) {
  const { t, i18n } = useTranslation();
  const [selected, setSelected] = useState<string>();
  const [level, setLevel] = useState<LogLevelFilter>('ALL');
  const [search, setSearch] = useState('');
  const [lines, setLines] = useState<number>();
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [scrollSignal, setScrollSignal] = useState(0);
  const debouncedSearch = useDebounced(search);

  const files = useQuery({ queryKey: ['logs', 'files'], queryFn: endpoints.logs.files, refetchInterval: 30_000 });
  const existing = files.data?.files.filter((file) => file.exists) ?? [];
  const file = existing.find((candidate) => candidate.id === selected) ?? existing[0];
  const lineLimit = lines ?? files.data?.defaultLines ?? 500;

  const tail = useQuery({
    queryKey: ['logs', 'tail', file?.id, lineLimit, level, debouncedSearch],
    queryFn: () => endpoints.logs.tail({ file: file?.id, lines: lineLimit, level, search: debouncedSearch || undefined }),
    enabled: file !== undefined,
    refetchInterval: autoRefresh ? 3000 : false,
    placeholderData: keepPreviousData,
  });

  const selectFile = (id: string) => {
    setSelected(id);
    setScrollSignal((signal) => signal + 1);
  };

  if (files.isPending) {
    return <CenteredSpinner />;
  }
  if (files.isError) {
    return (
      <div className="p-4">
        <ErrorMessage error={files.error} onRetry={() => void files.refetch()} />
      </div>
    );
  }

  const missing = files.data.files.filter((candidate) => !candidate.exists);
  const data = tail.data;
  const source = (
    <label className="flex min-w-[220px] max-w-[360px] items-center gap-1.5">
      <FileText className="h-4 w-4 shrink-0 text-gk-cyan" aria-hidden />
      <select
        className="gk-input gk-mono w-full py-1 text-xs"
        value={file?.id ?? ''}
        onChange={(event) => selectFile(event.target.value)}
        aria-label={t('logs.server.file')}
        disabled={existing.length === 0}
      >
        {existing.length === 0 && <option value="">{t('logs.server.noFiles')}</option>}
        {existing.map((candidate) => (
          <option key={candidate.id} value={candidate.id} title={candidate.id}>
            {candidate.name}
            {candidate.applicationLog ? ` · ${t('logs.server.applicationLog')}` : ''} · {formatBytes(candidate.size)}
          </option>
        ))}
      </select>
    </label>
  );

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <LogToolbar
        source={source}
        level={level}
        onLevelChange={setLevel}
        search={search}
        onSearchChange={setSearch}
        lines={lineLimit}
        onLinesChange={setLines}
        autoRefresh={autoRefresh}
        onAutoRefreshChange={setAutoRefresh}
        onRefresh={() => {
          void files.refetch();
          void tail.refetch();
        }}
        refreshing={tail.isFetching}
        wrap={wrap}
        onWrapChange={onWrapChange}
        onScrollToBottom={() => setScrollSignal((signal) => signal + 1)}
        downloadHref={file ? endpoints.logs.downloadUrl(file.id) : undefined}
        downloadDisabled={!file}
      />

      {file && (
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-gk-border bg-gk-bg px-4 py-1.5 text-xs text-gk-muted">
          <span className="gk-mono truncate text-gk-text" title={file.id}>
            {file.id}
          </span>
          {data && (
            <>
              <span>{formatBytes(data.file.size)}</span>
              <span>{t('logs.server.modified', { time: formatDateTime(data.file.lastModified, i18n.language) })}</span>
              {data.totalLines !== undefined && <span>{t('logs.server.totalLines', { count: data.totalLines, total: formatNumber(data.totalLines, i18n.language) })}</span>}
              <span>
                {t('logs.shown', { count: data.lines.length, shown: formatNumber(data.lines.length, i18n.language) })}
                {data.reachedStart ? '' : ` · ${t('logs.server.tailOnly')}`}
              </span>
              {data.scanLimitReached && (
                <span className="flex items-center gap-1 text-gk-warning">
                  <AlertTriangle className="h-3 w-3" aria-hidden />
                  {t('logs.server.scanLimit', { size: formatBytes(data.scannedBytes) })}
                </span>
              )}
            </>
          )}
        </div>
      )}

      {tail.isError ? (
        <div className="p-4">
          <ErrorMessage error={tail.error} onRetry={() => void tail.refetch()} />
        </div>
      ) : (
        <LogTerminal
          lines={data ? toTerminalLines(data.lines) : []}
          search={debouncedSearch}
          wrap={wrap}
          scrollSignal={scrollSignal}
          empty={
            existing.length === 0 ? (
              <>
                <span>{t('logs.server.noFilesHint')}</span>
                <span className="gk-mono text-xs text-gk-subtle">{files.data.configuredPaths.join(', ') || '—'}</span>
              </>
            ) : tail.isPending ? (
              t('logs.loading')
            ) : level !== 'ALL' || debouncedSearch ? (
              t('logs.noMatches')
            ) : (
              t('logs.emptyFile')
            )
          }
        />
      )}

      {missing.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 border-t border-gk-border bg-gk-panel px-4 py-1.5 text-xs text-gk-subtle">
          <FolderOpen className="h-3.5 w-3.5" aria-hidden />
          {t('logs.server.missing')}
          {missing.map((candidate) => (
            <span key={candidate.id} className="gk-mono" title={candidate.id}>
              {candidate.configuredPath}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
