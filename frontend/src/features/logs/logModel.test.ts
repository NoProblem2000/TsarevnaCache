import { describe, expect, it } from 'vitest';
import type { LiveEvent } from '../../api/types';
import { filterLines, filterLiveEvents, highlight, isErrorLine, linesToText, liveEventLines, parseLog } from './logModel';

const SPRING_LOG = [
  '2026-11-03T10:00:00.001+03:00  INFO 4242 --- [main] c.h.c.i.c.ClientConnectionManager : Connected',
  '2026-11-03T10:00:01.002+03:00  WARN 4242 --- [hz.client.event-1] c.h.c.i.s.ClientInvocation : Slow call',
  '2026-11-03T10:00:02.003+03:00 ERROR 4242 --- [http-nio-8080-exec-1] i.g.n.t.i.Inspector : Cannot read value',
  'com.hazelcast.nio.serialization.HazelcastSerializationException: No DataSerializerFactory registered',
  '\tat com.hazelcast.internal.serialization.impl.DataSerializableSerializer.read(DataSerializableSerializer.java:1)',
  'Caused by: java.lang.IllegalStateException: boom',
  '2026-11-03T10:00:03.004+03:00 DEBUG 4242 --- [main] c.h.c.i.s.ClientPartitionService : Partition table',
  '2026-11-03T10:00:04.005+03:00  INFO 4242 --- [main] i.g.n.t.TsarevnaCacheApplication : Started',
].join('\r\n');

describe('parsing log files', () => {
  it('groups stack traces with their record like the server', () => {
    const lines = parseLog(`${SPRING_LOG}\r\n`);
    expect(lines).toHaveLength(8);
    expect(lines.map((line) => line.level)).toEqual(['INFO', 'WARN', 'ERROR', 'ERROR', 'ERROR', 'ERROR', 'DEBUG', 'INFO']);
    expect(lines[3].continuation).toBe(true);
    expect(lines[2].continuation).toBe(false);
    expect(lines[7].text.endsWith('Started')).toBe(true);

    expect(filterLines(lines, 'ERROR', '', 500).map((line) => line.number)).toEqual([3, 4, 5, 6]);
    expect(filterLines(lines, 'WARN', '', 500).map((line) => line.number)).toEqual([2, 3, 4, 5, 6]);
    expect(filterLines(lines, 'INFO', '', 500).map((line) => line.number)).not.toContain(7);
    expect(filterLines(lines, 'ALL', 'caused BY', 500).map((line) => line.number)).toEqual([6]);
    expect(filterLines(lines, 'ALL', '', 2).map((line) => line.number)).toEqual([7, 8]);
  });

  it('understands the two-line java.util.logging format', () => {
    const lines = parseLog(
      [
        'Nov 03, 2026 10:00:00 AM com.hazelcast.internal.cluster.ClusterService',
        'INFO: [127.0.0.1]:5701 [dev] [5.5.0] Members {size:1}',
        'Nov 03, 2026 10:00:05 AM com.hazelcast.spi.impl.operationservice.impl.Invocation',
        'SEVERE: [127.0.0.1]:5701 [dev] [5.5.0] Operation failed',
        'java.lang.IllegalStateException: boom',
        '\tat com.hazelcast.Example.run(Example.java:1)',
      ].join('\n'),
    );
    expect(filterLines(lines, 'ERROR', '', 500).map((line) => line.number)).toEqual([3, 4, 5, 6]);
    expect(lines[0].level).toBe('INFO');

    const russian = parseLog(
      [
        'нояб. 03, 2026 4:03:48 PM com.hazelcast.internal.partition.impl.PartitionStateManagerImpl',
        'SEVERE: [192.168.3.29]:5701 [dev] [5.5.0] Partition table broken',
        'нояб. 03, 2026 4:03:49 PM com.hazelcast.core.LifecycleService',
        'INFO: [192.168.3.29]:5701 [dev] [5.5.0] is STARTED',
      ].join('\n'),
    );
    expect(filterLines(russian, 'ERROR', '', 500).map((line) => line.number)).toEqual([1, 2]);
    expect(highlight(russian[0].text)[0]).toMatchObject({ kind: 'timestamp', text: 'нояб. 03, 2026 4:03:48 PM' });
  });

  it('keeps plain text without levels for ALL only', () => {
    const lines = parseLog('first\nsecond\n');
    expect(lines.map((line) => [line.level, line.continuation])).toEqual([
      [undefined, false],
      [undefined, false],
    ]);
    expect(filterLines(lines, 'ALL', '', 10)).toHaveLength(2);
    expect(filterLines(lines, 'INFO', '', 10)).toHaveLength(0);
    expect(parseLog('')).toEqual([]);
  });
});

describe('highlighting', () => {
  it('splits timestamps, levels, threads, class names and exceptions', () => {
    const segments = highlight(
      '2026-11-03T10:00:02.003+03:00 ERROR 4242 --- [hz.client.event-1] c.h.c.i.Inspector : HazelcastSerializationException: boom',
    );
    const kinds = segments.filter((segment) => segment.kind !== 'text').map((segment) => [segment.kind, segment.text]);
    expect(kinds).toEqual([
      ['timestamp', '2026-11-03T10:00:02.003+03:00'],
      ['level', 'ERROR'],
      ['thread', '[hz.client.event-1]'],
      ['logger', 'c.h.c.i.Inspector'],
      ['exception', 'HazelcastSerializationException'],
    ]);
    expect(segments.find((segment) => segment.kind === 'level')?.level).toBe('ERROR');
    expect(segments.map((segment) => segment.text).join('')).toContain(' : HazelcastSerializationException: boom');
  });

  it('marks every search match, also across highlighted parts', () => {
    const segments = highlight('WARN [main] slow call, call again', 'CALL');
    expect(segments.filter((segment) => segment.match).map((segment) => segment.text)).toEqual(['call', 'call']);
    const across = highlight('INFO [main] x', 'o [ma');
    expect(across.filter((segment) => segment.match).map((segment) => [segment.kind, segment.text])).toEqual([
      ['level', 'O'],
      ['text', ' '],
      ['thread', '[ma'],
    ]);
    expect(highlight('plain', '').every((segment) => !segment.match)).toBe(true);
  });

  it('flags error rows', () => {
    expect(isErrorLine({ text: '\tat com.example.Foo.bar(Foo.java:1)' })).toBe(true);
    expect(isErrorLine({ text: 'Caused by: x' })).toBe(true);
    expect(isErrorLine({ text: 'all good', level: 'INFO' })).toBe(false);
    expect(isErrorLine({ text: 'whatever', level: 'ERROR' })).toBe(true);
  });
});

describe('live stream', () => {
  const event = (seq: number, level: LiveEvent['level'], message: string, stackTrace?: string): LiveEvent => ({
    seq,
    timestamp: new Date(2026, 10, 3, 10, 0, 0, 7).getTime(),
    level,
    source: 'LOG',
    logger: 'com.hazelcast.Test',
    thread: 'main',
    message,
    stackTrace,
  });

  it('renders events as log lines and filters them', () => {
    const lines = liveEventLines(event(7, 'ERROR', 'boom', 'java.lang.IllegalStateException: boom\n\tat x.y(Z.java:1)\n'));
    expect(lines).toHaveLength(3);
    expect(lines[0].text).toBe('2026-11-03 10:00:00.007 ERROR [LOG] [main] com.hazelcast.Test : boom');
    expect(lines[0].number).toBe(7);
    expect(lines[2].continuation).toBe(true);
    expect(lines[2].number).toBeUndefined();

    const events = [event(1, 'INFO', 'member joined'), event(2, 'WARN', 'slow'), event(3, 'ERROR', 'boom', 'trace Needle')];
    expect(filterLiveEvents(events, 'WARN', '').map((line) => line.key)).toEqual(['2', '3', '3.0']);
    expect(filterLiveEvents(events, 'ALL', 'needle').map((line) => line.key)).toEqual(['3', '3.0']);
    const multiLine = liveEventLines(event(8, 'INFO', 'Client Connectivity [1] {\r\n\tMember [127.0.0.1]:5701 - connected\r\n}\r\n'));
    expect(multiLine.map((line) => [line.text.endsWith('Client Connectivity [1] {'), line.continuation])).toEqual([
      [true, false],
      [false, true],
      [false, true],
    ]);
    expect(multiLine[1].text).toBe('\tMember [127.0.0.1]:5701 - connected');
    expect(linesToText(lines.slice(0, 1))).toBe(`${lines[0].text}\n`);
    expect(linesToText([])).toBe('');
  });
});
