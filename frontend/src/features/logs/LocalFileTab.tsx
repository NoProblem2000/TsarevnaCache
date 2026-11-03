import { useMemo, useRef, useState, type DragEvent } from 'react';
import { AlertTriangle, FileUp, Upload, X } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { LogLevelFilter, LogLine } from '../../api/types';
import { Spinner } from '../../components/ui/Spinner';
import { formatBytes, formatDateTime, formatNumber } from '../../lib/format';
import { LogTerminal } from './LogTerminal';
import { LogToolbar } from './LogToolbar';
import { downloadText, filterLines, linesToText, parseLog, toTerminalLines } from './logModel';
import { useDebounced } from './useDebounced';

/** Files are read in the browser; larger ones are cut to their last 50 MB. */
export const MAX_LOCAL_LOG_BYTES = 50 * 1024 * 1024;

const ACCEPT = '.log,.txt,.out,text/plain';

interface LoadedFile {
  name: string;
  size: number;
  lastModified: number;
  lines: LogLine[];
  /** Only the last {@link MAX_LOCAL_LOG_BYTES} were read. */
  truncated: boolean;
}

interface LocalFileTabProps {
  wrap: boolean;
  onWrapChange: (wrap: boolean) => void;
}

/**
 * A log file from the developer's disk (e.g. downloaded from a member), opened in the browser with
 * the same parsing, highlighting, level filter and search as the server files. Nothing is uploaded.
 */
export function LocalFileTab({ wrap, onWrapChange }: LocalFileTabProps) {
  const { t, i18n } = useTranslation();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<LoadedFile>();
  const [reading, setReading] = useState(false);
  const [readError, setReadError] = useState<string>();
  const [dragging, setDragging] = useState(false);
  const [level, setLevel] = useState<LogLevelFilter>('ALL');
  const [search, setSearch] = useState('');
  const [lines, setLines] = useState(5000);
  const [scrollSignal, setScrollSignal] = useState(0);
  const debouncedSearch = useDebounced(search, 200);

  const open = async (picked: File | undefined) => {
    if (!picked) {
      return;
    }
    setReading(true);
    setReadError(undefined);
    try {
      const truncated = picked.size > MAX_LOCAL_LOG_BYTES;
      let text = await (truncated ? picked.slice(picked.size - MAX_LOCAL_LOG_BYTES) : picked).text();
      if (truncated) {
        // the cut may fall inside a line: start with the next complete one
        text = text.slice(text.indexOf('\n') + 1);
      }
      setFile({ name: picked.name, size: picked.size, lastModified: picked.lastModified, lines: parseLog(text), truncated });
      setScrollSignal((signal) => signal + 1);
    } catch (failure) {
      setReadError(failure instanceof Error ? failure.message : String(failure));
    } finally {
      setReading(false);
    }
  };

  const visible = useMemo(
    () => (file ? toTerminalLines(filterLines(file.lines, level, debouncedSearch, lines)) : []),
    [file, level, debouncedSearch, lines],
  );
  const matching = useMemo(
    () => (file && (level !== 'ALL' || debouncedSearch) ? filterLines(file.lines, level, debouncedSearch, Number.MAX_SAFE_INTEGER).length : file?.lines.length ?? 0),
    [file, level, debouncedSearch],
  );

  const onDrop = (event: DragEvent) => {
    event.preventDefault();
    setDragging(false);
    void open(event.dataTransfer.files[0]);
  };
  const dragHandlers = {
    onDragOver: (event: DragEvent) => {
      event.preventDefault();
      setDragging(true);
    },
    onDragLeave: (event: DragEvent) => {
      if (event.currentTarget === event.target) {
        setDragging(false);
      }
    },
    onDrop,
  };

  const picker = (
    <>
      <input
        ref={input}
        type="file"
        accept={ACCEPT}
        className="hidden"
        data-testid="local-log-input"
        onChange={(event) => {
          void open(event.target.files?.[0]);
          event.target.value = '';
        }}
      />
      <button type="button" className="gk-btn-primary px-2.5 py-1" onClick={() => input.current?.click()}>
        <FileUp className="h-4 w-4" aria-hidden />
        {t('logs.local.open')}
      </button>
    </>
  );

  if (!file) {
    return (
      <div className="flex min-h-0 flex-1 flex-col bg-[#0D1117] p-6" {...dragHandlers}>
        <button
          type="button"
          onClick={() => input.current?.click()}
          className={`flex flex-1 flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed p-8 text-center transition-colors ${
            dragging ? 'border-gk-cyan bg-gk-cyan/5 text-gk-cyan' : 'border-gk-border text-gk-muted hover:border-gk-subtle hover:text-gk-text'
          }`}
        >
          {reading ? <Spinner /> : <Upload className="h-10 w-10" aria-hidden />}
          <span className="text-sm font-medium">{dragging ? t('logs.local.dropNow') : t('logs.local.dropZone')}</span>
          <span className="max-w-lg text-xs text-gk-subtle">{t('logs.local.privacy')}</span>
        </button>
        {readError && <p className="mt-3 text-center text-xs text-gk-danger">{t('logs.local.readFailed', { error: readError })}</p>}
        <input
          ref={input}
          type="file"
          accept={ACCEPT}
          className="hidden"
          data-testid="local-log-input"
          onChange={(event) => {
            void open(event.target.files?.[0]);
            event.target.value = '';
          }}
        />
      </div>
    );
  }

  return (
    <div className="relative flex min-h-0 flex-1 flex-col" {...dragHandlers}>
      <LogToolbar
        source={picker}
        level={level}
        onLevelChange={setLevel}
        search={search}
        onSearchChange={setSearch}
        lines={lines}
        onLinesChange={setLines}
        wrap={wrap}
        onWrapChange={onWrapChange}
        onScrollToBottom={() => setScrollSignal((signal) => signal + 1)}
        onDownload={() => downloadText(file.name.replace(/(\.\w+)?$/, '.filtered$1'), linesToText(visible))}
        downloadDisabled={visible.length === 0}
        actions={
          <button type="button" className="gk-btn-ghost px-2 py-1" onClick={() => setFile(undefined)} title={t('logs.local.close')}>
            <X className="h-4 w-4" aria-hidden />
          </button>
        }
      />
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-b border-gk-border bg-gk-bg px-4 py-1.5 text-xs text-gk-muted">
        <span className="gk-mono text-gk-text">{file.name}</span>
        <span>{formatBytes(file.size)}</span>
        <span>{t('logs.server.modified', { time: formatDateTime(file.lastModified, i18n.language) })}</span>
        <span>{t('logs.server.totalLines', { count: file.lines.length, total: formatNumber(file.lines.length, i18n.language) })}</span>
        <span>
          {t('logs.local.matching', {
            count: matching,
            matching: formatNumber(matching, i18n.language),
            shown: formatNumber(visible.length, i18n.language),
          })}
        </span>
        {file.truncated && (
          <span className="flex items-center gap-1 text-gk-warning">
            <AlertTriangle className="h-3 w-3" aria-hidden />
            {t('logs.local.truncated', { size: formatBytes(MAX_LOCAL_LOG_BYTES) })}
          </span>
        )}
      </div>
      <LogTerminal
        lines={visible}
        search={debouncedSearch}
        wrap={wrap}
        scrollSignal={scrollSignal}
        empty={file.lines.length === 0 ? t('logs.emptyFile') : t('logs.noMatches')}
      />
      {dragging && (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center border-2 border-dashed border-gk-cyan bg-gk-bg/80 text-sm font-medium text-gk-cyan">
          {t('logs.local.dropNow')}
        </div>
      )}
    </div>
  );
}
