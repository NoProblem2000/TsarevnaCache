import { describe, expect, it } from 'vitest';
import type { HealthCheckIssue, HealthCheckReport } from '../../api/types';
import { badgeSummary, isWholeRule, issueAction, ruleOf, targetRoute, wholeRuleId } from './healthModel';

const issue = (overrides: Partial<HealthCheckIssue>): HealthCheckIssue => ({
  issueId: 'MAP_UNBOUNDED_NO_TTL:user sessions',
  checkType: 'MAP_UNBOUNDED_NO_TTL',
  severity: 'WARNING',
  targetType: 'IMAP',
  targetName: 'user sessions',
  currentValue: 'entries=1200',
  recommendationCode: 'MAP_UNBOUNDED_NO_TTL',
  detectedAt: '2026-10-29T10:00:00Z',
  details: {},
  ...overrides,
});

const report = (overrides: Partial<HealthCheckReport>): HealthCheckReport => ({
  healthScore: 100,
  status: 'HEALTHY',
  durationMillis: 1,
  clusterAvailable: true,
  counts: { critical: 0, warning: 0, info: 0, ignored: 0 },
  activeIssues: [],
  ignoredIssues: [],
  ignores: [],
  checkedMaps: 0,
  sampledMaps: 0,
  intervalMillis: 60_000,
  ...overrides,
});

describe('healthModel', () => {
  it('summarizes the most severe finding for the top bar', () => {
    expect(badgeSummary(report({ healthScore: 92 }))).toMatchObject({ key: 'health.badge.healthy', score: 92 });
    expect(badgeSummary(report({ status: 'DEGRADED', counts: { critical: 0, warning: 2, info: 1, ignored: 0 } }))).toMatchObject({
      key: 'health.badge.warnings',
      count: 2,
    });
    expect(badgeSummary(report({ status: 'CRITICAL', counts: { critical: 1, warning: 2, info: 0, ignored: 0 } }))).toMatchObject({
      key: 'health.badge.critical',
      count: 1,
    });
    expect(badgeSummary(report({ clusterAvailable: false, healthScore: 0 }))).toMatchObject({
      key: 'health.badge.unavailable',
      status: 'CRITICAL',
    });
  });

  it('links targets and contextual actions', () => {
    expect(targetRoute(issue({}))).toBe('/maps/user%20sessions');
    expect(issueAction(issue({ checkType: 'MAP_HIGH_LATENCY' }))).toEqual({
      labelKey: 'health.actions.openStats',
      route: '/maps/user%20sessions?tab=stats',
    });
    expect(targetRoute(issue({ targetType: 'IQUEUE', targetName: 'jobs' }))).toBe('/structures/queue/jobs');
    expect(
      issueAction(
        issue({
          checkType: 'DTO_UNREGISTERED_SCHEMA',
          targetType: 'DTO_SCHEMA',
          targetName: 'UnknownDTO',
          details: { factoryId: 1, classId: 3, className: 'UnknownDTO' },
        }),
      ),
    ).toEqual({ labelKey: 'health.actions.configureDto', route: '/schemas?configure=1&factoryId=1&classId=3&className=UnknownDTO' });
    expect(targetRoute(issue({ targetType: 'CLUSTER', checkType: 'CLUSTER_SINGLE_MEMBER' }))).toBe('/');
  });

  it('understands ignore list ids', () => {
    expect(wholeRuleId(issue({}))).toBe('MAP_UNBOUNDED_NO_TTL:*');
    expect(ruleOf('MAP_UNBOUNDED_NO_TTL:a:b')).toBe('MAP_UNBOUNDED_NO_TTL');
    expect(ruleOf('CLUSTER_SINGLE_MEMBER')).toBe('CLUSTER_SINGLE_MEMBER');
    expect(isWholeRule('MAP_UNBOUNDED_NO_TTL:*')).toBe(true);
    expect(isWholeRule('CLUSTER_SINGLE_MEMBER')).toBe(true);
    expect(isWholeRule('MAP_UNBOUNDED_NO_TTL:orders')).toBe(false);
  });
});
