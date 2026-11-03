import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Eraser, Radio } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { endpoints } from '../../api/endpoints';
import type { LiveEvent, LiveEventSource, LogLevelFilter } from '../../api/types';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { formatNumber } from '../../lib/format';
import { LogTerminal } from './LogTerminal';
import { LogToolbar } from './LogToolbar';
import { downloadText, filterLiveEvents, formatLogTimestamp, linesToText } from './logModel';

const SOURCES: LiveEventSource[] = ['LOG', 'MEMBERSHIP', 'LIFECYCLE', 'MIGRATION', 'DISTRIBUTED_OBJECT'];

interface LiveStreamTabProps {
  wrap: boolean;
  onWrapChange: (wrap: boolean) => void;
}

/**
 * Live cluster events and the Hazelcast log stream: the server keeps the last events in a ring
 * buffer; this view loads them once and then asks every 3 seconds only for newer ones. Level,
 * source and text filters apply instantly in the browser.
 */
export function LiveStreamTab({ wrap, onWrapChange }: LiveStreamTabProps) {
  const { t, i18n } = useTranslation();
  const [events, setEvents] = useState<LiveEvent[]>([]);
  const [capacity, setCapacity] = useState(1000);
  const [dropped, setDropped] = useState(0);
  const [level, setLevel] = useState<LogLevelFilter>('ALL');
  const [search, setSearch] = useState('');
  const [sources, setSources] = useState<LiveEventSource[]>(SOURCES);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const [scrollSignal, setScrollSignal] = useState(0);
  const lastSeq = useRef(0);

  const poll = useCallback(async () => {
    setLoading(true);
    try {
      const page = await endpoints.logs.live(lastSeq.current, 'ALL');
      setCapacity(page.capacity);
      setDropped(page.dropped);
      if (page.lastSeq < lastSeq.current) {
        // the server restarted: start over
        lastSeq.current = 0;
        setEvents([]);
        return;
      }
      lastSeq.current = page.lastSeq;
      if (page.events.length > 0) {
        setEvents((current) => [...current, ...page.events].slice(-page.capacity));
      }
      setError(null);
    } catch (failure) {
      setError(failure);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void poll();
  }, [poll]);

  useEffect(() => {
    if (!autoRefresh) {
      return undefined;
    }
    const timer = window.setInterval(() => void poll(), 3000);
    return () => window.clearInterval(timer);
  }, [autoRefresh, poll]);

  const clear = async () => {
    await endpoints.logs.clearLive();
    setEvents([]);
  };

  const visible = useMemo(
    () => filterLiveEvents(events.filter((event) => sources.includes(event.source)), level, search),
    [events, sources, level, search],
  );
  const counts = useMemo(() => {
    const result: Partial<Record<LiveEventSource, number>> = {};
    events.forEach((event) => (result[event.source] = (result[event.source] ?? 0) + 1));
    return result;
  }, [events]);

  const toggleSource = (source: LiveEventSource) =>
    setSources((current) => (current.includes(source) ? current.filter((value) => value !== source) : [...current, source]));

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <LogToolbar
        source={
          <span className="flex items-center gap-1.5 text-xs font-medium text-gk-cyan">
            <Radio className={`h-4 w-4 ${autoRefresh ? 'animate-pulse' : ''}`} aria-hidden />
            {t('logs.live.source')}
          </span>
        }
        level={level}
        onLevelChange={setLevel}
        search={search}
        onSearchChange={setSearch}
        autoRefresh={autoRefresh}
        onAutoRefreshChange={setAutoRefresh}
        onRefresh={() => void poll()}
        refreshing={loading}
        wrap={wrap}
        onWrapChange={onWrapChange}
        onScrollToBottom={() => setScrollSignal((signal) => signal + 1)}
        onDownload={() => downloadText(`hazelcast-live-${formatLogTimestamp(Date.now()).replace(/[: ]/g, '-')}.log`, linesToText(visible))}
        downloadDisabled={visible.length === 0}
        actions={
          <button type="button" className="gk-btn-ghost px-2 py-1" onClick={() => void clear()} title={t('logs.live.clearHint')}>
            <Eraser className="h-4 w-4" aria-hidden />
            <span className="hidden xl:inline">{t('logs.live.clear')}</span>
          </button>
        }
      />
      <div className="flex flex-wrap items-center gap-1.5 border-b border-gk-border bg-gk-bg px-4 py-1.5 text-xs">
        <span className="mr-1 text-gk-muted">{t('logs.live.sources')}</span>
        {SOURCES.map((source) => {
          const active = sources.includes(source);
          return (
            <button
              key={source}
              type="button"
              aria-pressed={active}
              onClick={() => toggleSource(source)}
              title={t(`logs.live.sourceHints.${source}`)}
              className={`rounded-full border px-2.5 py-0.5 transition-colors ${
                active ? 'border-gk-purple/50 bg-gk-purple/15 text-gk-text' : 'border-gk-border text-gk-subtle line-through'
              }`}
            >
              {t(`logs.live.sourceNames.${source}`)} <span className="gk-mono text-gk-muted">{counts[source] ?? 0}</span>
            </button>
          );
        })}
        <span className="ml-auto text-gk-subtle">
          {t('logs.live.buffer', {
            count: events.length,
            buffered: formatNumber(events.length, i18n.language),
            capacity: formatNumber(capacity, i18n.language),
          })}
          {dropped > 0 && ` · ${t('logs.live.dropped', { count: dropped, dropped: formatNumber(dropped, i18n.language) })}`}
        </span>
      </div>
      {error !== null && (
        <div className="border-b border-gk-border p-2">
          <ErrorMessage error={error} onRetry={() => void poll()} showDetails={false} />
        </div>
      )}
      <LogTerminal
        lines={visible}
        search={search}
        wrap={wrap}
        scrollSignal={scrollSignal}
        empty={events.length === 0 ? t('logs.live.empty') : t('logs.noMatches')}
      />
    </div>
  );
}
