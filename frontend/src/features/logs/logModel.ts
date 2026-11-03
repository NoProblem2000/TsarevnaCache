import type { LiveEvent, LogLevel, LogLevelFilter, LogLine } from '../../api/types';

/** A row of the terminal view. */
export interface TerminalLine {
  key: string;
  /** Shown in the gutter; continuation rows of live events have none. */
  number?: number;
  text: string;
  level?: LogLevel;
  continuation: boolean;
}

const SEVERITY: Record<LogLevel, number> = { DEBUG: 10, INFO: 20, WARN: 30, ERROR: 40 };

/** Same rule as the backend: a level keeps itself and the more severe levels; ALL keeps everything. */
export function acceptsLevel(filter: LogLevelFilter, level: LogLevel | undefined): boolean {
  return filter === 'ALL' || (level !== undefined && SEVERITY[level] >= SEVERITY[filter]);
}

const LEVEL_TOKEN = /(?<![\w.$-])(FATAL|SEVERE|ERROR|WARNING|WARN|INFO|CONFIG|DEBUG|FINEST|FINER|FINE|TRACE)(?![\w$-])/;
/** Level tokens are only searched near the start of a line. */
const HEADER_LENGTH = 200;
const TIMESTAMP = /^\s*\[?(\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}(?::\d{2}(?:[.,]\d{1,9})?)?(?:Z|[+-]\d{2}:?\d{2})?|\d{2}:\d{2}:\d{2}[.,]\d{3}|[^\s\d]{3,6}\.? \d{1,2}, \d{4} \d{1,2}:\d{2}:\d{2}(?: [AP]M)?)\]?/;

/** Maps level names of Logback, Log4j and java.util.logging. */
export function levelOf(name: string): LogLevel | undefined {
  switch (name.toUpperCase()) {
    case 'FATAL':
    case 'SEVERE':
    case 'ERROR':
      return 'ERROR';
    case 'WARNING':
    case 'WARN':
      return 'WARN';
    case 'INFO':
    case 'CONFIG':
      return 'INFO';
    case 'DEBUG':
    case 'TRACE':
    case 'FINE':
    case 'FINER':
    case 'FINEST':
      return 'DEBUG';
    default:
      return undefined;
  }
}

/** Level named near the start of a line. */
export function detectLevel(line: string): LogLevel | undefined {
  const match = LEVEL_TOKEN.exec(line.slice(0, HEADER_LENGTH));
  return match ? levelOf(match[1]) : undefined;
}

export const startsWithTimestamp = (line: string) => TIMESTAMP.test(line);

/**
 * Splits a log file into lines grouped into records, exactly like the server side viewer: a line
 * naming a level or starting with a timestamp opens a record, other lines (stack traces) continue
 * it and inherit its level; the date line of the two-line java.util.logging format takes the level
 * of the `INFO: ...` line below it.
 */
export function parseLog(text: string, firstNumber = 1): LogLine[] {
  const raw = text.split('\n');
  if (raw.length > 0 && raw[raw.length - 1] === '') {
    raw.pop();
  }
  const lines: LogLine[] = raw.map((line, index) => ({
    number: firstNumber + index,
    text: line.endsWith('\r') ? line.slice(0, -1) : line,
    continuation: false,
  }));
  let record: LogLine[] = [];
  let recordLevel: LogLevel | undefined;
  let timestampOnly = false;
  let seenHeader = false;
  for (const line of lines) {
    const detected = detectLevel(line.text);
    const timestamp = startsWithTimestamp(line.text);
    if (detected === undefined && !timestamp) {
      line.level = recordLevel;
      line.continuation = seenHeader;
      record.push(line);
      continue;
    }
    if (detected !== undefined && !timestamp && timestampOnly) {
      // "INFO: ..." below "Nov 03, 2026 10:00:00 AM com.hazelcast...": the date record gets the level
      record.forEach((previous) => (previous.level = detected));
    }
    seenHeader = true;
    recordLevel = detected;
    timestampOnly = detected === undefined;
    line.level = recordLevel;
    record = [line];
  }
  return lines;
}

/**
 * The last `limit` lines that pass the level filter and contain `search` (case-insensitive).
 */
export function filterLines(lines: LogLine[], level: LogLevelFilter, search: string, limit: number): LogLine[] {
  const needle = search.trim().toLowerCase();
  const result: LogLine[] = [];
  for (let i = lines.length - 1; i >= 0 && result.length < limit; i--) {
    const line = lines[i];
    if (acceptsLevel(level, line.level) && (!needle || line.text.toLowerCase().includes(needle))) {
      result.push(line);
    }
  }
  return result.reverse();
}

const pad = (value: number, length = 2) => String(value).padStart(length, '0');

/** `2026-11-03 10:00:00.123` in local time. */
export function formatLogTimestamp(epochMillis: number): string {
  const date = new Date(epochMillis);
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(
    date.getMinutes(),
  )}:${pad(date.getSeconds())}.${pad(date.getMilliseconds(), 3)}`;
}

/**
 * A live event as log lines: the header line with the first line of the message, then the further
 * message lines (Hazelcast prints member lists over several lines) and the stack trace.
 */
export function liveEventLines(event: LiveEvent): TerminalLine[] {
  const [first = '', ...more] = (event.message ?? '').replace(/\r/g, '').trimEnd().split('\n');
  const header = `${formatLogTimestamp(event.timestamp)} ${event.level.padEnd(5)} [${event.source}]${
    event.thread ? ` [${event.thread}]` : ''
  } ${event.logger ?? ''} : ${first}`;
  const lines: TerminalLine[] = [
    { key: `${event.seq}`, number: event.seq, text: header, level: event.level, continuation: false },
  ];
  const trace = event.stackTrace?.replace(/\r/g, '').trimEnd();
  [...more, ...(trace ? trace.split('\n') : [])].forEach((text, index) => {
    lines.push({ key: `${event.seq}.${index}`, text, level: event.level, continuation: true });
  });
  return lines;
}

/** Live events that pass the filters (an event matches if any of its lines contains `search`). */
export function filterLiveEvents(events: LiveEvent[], level: LogLevelFilter, search: string): TerminalLine[] {
  const needle = search.trim().toLowerCase();
  const result: TerminalLine[] = [];
  for (const event of events) {
    if (!acceptsLevel(level, event.level)) {
      continue;
    }
    const lines = liveEventLines(event);
    if (!needle || lines.some((line) => line.text.toLowerCase().includes(needle))) {
      result.push(...lines);
    }
  }
  return result;
}

export const toTerminalLines = (lines: LogLine[]): TerminalLine[] =>
  lines.map((line) => ({ key: String(line.number), ...line }));

/** The rows as a text file (Download Log of the live stream and of local files). */
export const linesToText = (lines: { text: string }[]) => lines.map((line) => line.text).join('\n') + (lines.length ? '\n' : '');

// ------------------------------------------------------------------ syntax highlighting

export type SegmentKind = 'text' | 'timestamp' | 'level' | 'thread' | 'logger' | 'exception';

export interface Segment {
  text: string;
  kind: SegmentKind;
  level?: LogLevel;
  /** Part of a search match. */
  match: boolean;
}

interface Range {
  start: number;
  end: number;
  kind: SegmentKind;
  level?: LogLevel;
}

const THREAD = /\[[^\]\n]{1,160}\]/g;
const QUALIFIED_NAME = /(?<![\w$.])(?:[a-zA-Z_$][\w$]*\.){2,}[A-Za-z_$][\w$]*(?![\w$])/g;
const EXCEPTION = /Caused by:|Suppressed:|\b[\w$.]*(?:Exception|Error|Throwable)\b(?=[:\s]|$)/g;

/**
 * Splits a line into highlighted parts: the leading timestamp, the level badge, bracketed thread
 * or member names, qualified class names and exceptions; then marks every occurrence of `search`.
 */
export function highlight(text: string, search = ''): Segment[] {
  const ranges: Range[] = [];
  const claim = (start: number, end: number, kind: SegmentKind, level?: LogLevel) => {
    if (end > start && !ranges.some((range) => start < range.end && end > range.start)) {
      ranges.push({ start, end, kind, level });
    }
  };

  const timestamp = TIMESTAMP.exec(text);
  if (timestamp) {
    const start = timestamp.index + timestamp[0].length - timestamp[0].trimStart().length;
    claim(start, timestamp.index + timestamp[0].length, 'timestamp');
  }
  const head = text.slice(0, HEADER_LENGTH);
  const level = LEVEL_TOKEN.exec(head);
  if (level) {
    claim(level.index, level.index + level[0].length, 'level', levelOf(level[1]));
  }
  for (const match of text.matchAll(EXCEPTION)) {
    claim(match.index, match.index + match[0].length, 'exception');
  }
  for (const match of text.matchAll(THREAD)) {
    claim(match.index, match.index + match[0].length, 'thread');
  }
  for (const match of text.matchAll(QUALIFIED_NAME)) {
    claim(match.index, match.index + match[0].length, 'logger');
  }
  ranges.sort((left, right) => left.start - right.start);

  const segments: Segment[] = [];
  let position = 0;
  for (const range of ranges) {
    if (range.start > position) {
      segments.push({ text: text.slice(position, range.start), kind: 'text', match: false });
    }
    segments.push({ text: text.slice(range.start, range.end), kind: range.kind, level: range.level, match: false });
    position = range.end;
  }
  if (position < text.length) {
    segments.push({ text: text.slice(position), kind: 'text', match: false });
  }
  return markMatches(segments, search);
}

function markMatches(segments: Segment[], search: string): Segment[] {
  const needle = search.trim().toLowerCase();
  if (!needle) {
    return segments;
  }
  const text = segments.map((segment) => segment.text).join('');
  const lower = text.toLowerCase();
  const matches: [number, number][] = [];
  for (let index = lower.indexOf(needle); index >= 0; index = lower.indexOf(needle, index + needle.length)) {
    matches.push([index, index + needle.length]);
  }
  if (matches.length === 0) {
    return segments;
  }
  const result: Segment[] = [];
  let offset = 0;
  for (const segment of segments) {
    const start = offset;
    const end = offset + segment.text.length;
    let cursor = start;
    for (const [from, to] of matches) {
      if (to <= cursor || from >= end) {
        continue;
      }
      const matchStart = Math.max(from, cursor);
      const matchEnd = Math.min(to, end);
      if (matchStart > cursor) {
        result.push({ ...segment, text: text.slice(cursor, matchStart), match: false });
      }
      result.push({ ...segment, text: text.slice(matchStart, matchEnd), match: true });
      cursor = matchEnd;
    }
    if (cursor < end) {
      result.push({ ...segment, text: text.slice(cursor, end), match: false });
    }
    offset = end;
  }
  return result;
}

/** Whether a row is highlighted as an error: its record is an ERROR or it shows an exception. */
export function isErrorLine(line: { text: string; level?: LogLevel }): boolean {
  return line.level === 'ERROR' || /Caused by:|^\s+at [\w$.]+\(|\b[\w$.]*Exception\b/.test(line.text);
}

/** Saves text as a file (Download Log of the live stream and of filtered local files). */
export function downloadText(fileName: string, text: string) {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}
