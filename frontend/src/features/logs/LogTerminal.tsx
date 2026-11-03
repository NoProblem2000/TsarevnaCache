import { memo, useEffect, useLayoutEffect, useRef, type ReactNode } from 'react';
import { TerminalSquare } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import type { LogLevel } from '../../api/types';
import { highlight, isErrorLine, type Segment, type TerminalLine } from './logModel';

interface LogTerminalProps {
  lines: TerminalLine[];
  search: string;
  wrap: boolean;
  /** Changes whenever the view should jump to the last line (button, new file, ...). */
  scrollSignal: number;
  /** Shown instead of the lines when there are none. */
  empty?: ReactNode;
}

/**
 * Terminal style log view (GitKraken dark terminal): line numbers, level badges, error and warning
 * rows, muted timestamps and thread names, search matches. While the view is scrolled to the
 * bottom it follows new lines; scrolled up, it stays where the reader is.
 */
export function LogTerminal({ lines, search, wrap, scrollSignal, empty }: LogTerminalProps) {
  const container = useRef<HTMLDivElement>(null);
  const atBottom = useRef(true);
  const lastKey = lines.length > 0 ? lines[lines.length - 1].key : '';

  useLayoutEffect(() => {
    const element = container.current;
    if (element && atBottom.current) {
      element.scrollTop = element.scrollHeight;
    }
  }, [lastKey, lines.length, wrap]);

  useEffect(() => {
    const element = container.current;
    if (element) {
      element.scrollTop = element.scrollHeight;
      atBottom.current = true;
    }
  }, [scrollSignal]);

  const onScroll = () => {
    const element = container.current;
    if (element) {
      atBottom.current = element.scrollHeight - element.scrollTop - element.clientHeight < 24;
    }
  };

  const gutter = lines.reduce((width, line) => Math.max(width, String(line.number ?? '').length), 3);

  return (
    <div
      ref={container}
      onScroll={onScroll}
      className="min-h-0 flex-1 overflow-auto bg-[#0D1117] font-mono text-[12.5px] leading-5 text-[#C9D1D9]"
      role="log"
      aria-live="off"
    >
      {lines.length === 0 ? (
        <div className="flex h-full min-h-[200px] flex-col items-center justify-center gap-2 p-6 text-center font-sans text-sm text-gk-muted">
          <TerminalSquare className="h-8 w-8 text-gk-subtle" aria-hidden />
          {empty}
        </div>
      ) : (
        <div className={wrap ? '' : 'w-max min-w-full'}>
          {lines.map((line) => (
            <TerminalRow key={line.key} line={line} search={search} wrap={wrap} gutter={gutter} />
          ))}
        </div>
      )}
    </div>
  );
}

const LEVEL_BADGE: Record<LogLevel, string> = {
  ERROR: 'bg-[#E74C3C]/20 text-[#E74C3C] ring-1 ring-[#E74C3C]/40',
  WARN: 'bg-[#F39C12]/15 text-[#F39C12] ring-1 ring-[#F39C12]/35',
  INFO: 'bg-[#00E5FF]/10 text-[#00E5FF] ring-1 ring-[#00E5FF]/30',
  DEBUG: 'bg-gk-subtle/15 text-gk-muted ring-1 ring-gk-subtle/30',
};

const SEGMENT_CLASS: Record<Segment['kind'], string> = {
  text: '',
  timestamp: 'text-[#8C7BB8]',
  level: '',
  thread: 'text-[#9B87C9]/75',
  logger: 'text-[#7D8590]',
  exception: 'font-semibold text-[#E74C3C]',
};

const TerminalRow = memo(function TerminalRow({
  line,
  search,
  wrap,
  gutter,
}: {
  line: TerminalLine;
  search: string;
  wrap: boolean;
  gutter: number;
}) {
  const { t } = useTranslation();
  const error = isErrorLine(line);
  const warning = !error && line.level === 'WARN';
  const row = error
    ? 'bg-[#E74C3C]/[0.08] shadow-[inset_2px_0_0_#E74C3C] hover:bg-[#E74C3C]/[0.14]'
    : warning
      ? 'bg-[#F39C12]/[0.06] shadow-[inset_2px_0_0_#F39C12] hover:bg-[#F39C12]/[0.11]'
      : 'hover:bg-white/[0.03]';
  return (
    <div className={`flex ${row}`}>
      <span
        className="sticky left-0 shrink-0 select-none border-r border-[#21262D] bg-[#0D1117] px-2 text-right text-[#484F58]"
        style={{ minWidth: `${gutter + 2}ch` }}
        title={line.number !== undefined && line.number < 0 ? t('logs.fromEnd') : undefined}
      >
        {line.number ?? ''}
      </span>
      <span className={`min-w-0 flex-1 px-3 ${wrap ? 'whitespace-pre-wrap break-all' : 'whitespace-pre'} ${line.continuation ? 'text-[#B1BAC4]' : ''}`}>
        {highlight(line.text, search).map((segment, index) => (
          <span
            key={index}
            className={`${
              segment.kind === 'level' && segment.level ? `rounded px-1 font-semibold ${LEVEL_BADGE[segment.level]}` : SEGMENT_CLASS[segment.kind]
            } ${segment.match ? 'rounded-sm bg-[#00E5FF]/35 text-white' : ''}`}
          >
            {segment.text}
          </span>
        ))}
        {line.text === '' && ' '}
      </span>
    </div>
  );
});
