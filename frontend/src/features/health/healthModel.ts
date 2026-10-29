import { AlertOctagon, AlertTriangle, CheckCircle2, Info, type LucideIcon } from 'lucide-react';
import type { HealthCheckIssue, HealthCheckReport, HealthSeverity, HealthStatus } from '../../api/types';
import { structureRoute } from '../../components/layout/structureRoutes';
import { configureSchemaRoute } from '../schemas/schemaLinks';

interface Tone {
  /** Tailwind classes of a badge / chip. */
  badge: string;
  /** Text color class. */
  text: string;
  /** Border color class of cards. */
  border: string;
  /** Hex color for SVG strokes. */
  hex: string;
  icon: LucideIcon;
}

/** Severity colors (status palette: critical red, warning amber, info cyan), always with an icon. */
export const SEVERITY_TONE: Record<HealthSeverity, Tone> = {
  CRITICAL: { badge: 'bg-gk-danger/15 text-gk-danger', text: 'text-gk-danger', border: 'border-gk-danger/40', hex: '#E74C3C', icon: AlertOctagon },
  WARNING: { badge: 'bg-gk-warning/15 text-gk-warning', text: 'text-gk-warning', border: 'border-gk-warning/40', hex: '#F39C12', icon: AlertTriangle },
  INFO: { badge: 'bg-gk-cyan/10 text-gk-cyan', text: 'text-gk-cyan', border: 'border-gk-cyan/30', hex: '#00E5FF', icon: Info },
};

export const STATUS_TONE: Record<HealthStatus, Tone> = {
  HEALTHY: { badge: 'bg-gk-green/15 text-gk-green', text: 'text-gk-green', border: 'border-gk-green/40', hex: '#2ECC71', icon: CheckCircle2 },
  DEGRADED: SEVERITY_TONE.WARNING,
  CRITICAL: SEVERITY_TONE.CRITICAL,
};

/** Top bar badge: the most severe active finding, or the score when nothing needs attention. */
export function badgeSummary(report: HealthCheckReport): { key: string; count?: number; score: number; status: HealthStatus } {
  if (!report.clusterAvailable) {
    return { key: 'health.badge.unavailable', score: report.healthScore, status: 'CRITICAL' };
  }
  if (report.counts.critical > 0) {
    return { key: 'health.badge.critical', count: report.counts.critical, score: report.healthScore, status: report.status };
  }
  if (report.counts.warning > 0) {
    return { key: 'health.badge.warnings', count: report.counts.warning, score: report.healthScore, status: report.status };
  }
  return { key: 'health.badge.healthy', score: report.healthScore, status: report.status };
}

const numberDetail = (issue: HealthCheckIssue, key: string) => {
  const value = issue.details[key];
  return typeof value === 'number' ? value : undefined;
};

/** Where the target of an issue lives in the UI. */
export function targetRoute(issue: HealthCheckIssue): string {
  switch (issue.targetType) {
    case 'IMAP':
      return structureRoute('MAP', issue.targetName);
    case 'IQUEUE':
      return structureRoute('QUEUE', issue.targetName);
    case 'DTO_SCHEMA': {
      const factoryId = numberDetail(issue, 'factoryId');
      const classId = numberDetail(issue, 'classId');
      const className = typeof issue.details.className === 'string' ? issue.details.className : undefined;
      return factoryId !== undefined && classId !== undefined ? configureSchemaRoute({ factoryId, classId, className }) : '/schemas';
    }
    case 'CLUSTER':
      return '/';
  }
}

/** Contextual action of an issue card: translation key of the label and the route. */
export function issueAction(issue: HealthCheckIssue): { labelKey: string; route: string } {
  switch (issue.checkType) {
    case 'DTO_UNREGISTERED_SCHEMA':
      return { labelKey: 'health.actions.configureDto', route: targetRoute(issue) };
    case 'MAP_UNBOUNDED_NO_TTL':
      return { labelKey: 'health.actions.inspectMap', route: targetRoute(issue) };
    case 'MAP_ZOMBIE_WRITE_ONLY':
    case 'MAP_HIGH_LATENCY':
    case 'MAP_BACKUP_DISABLED_OR_BROKEN':
      return { labelKey: 'health.actions.openStats', route: `${targetRoute(issue)}?tab=stats` };
    case 'QUEUE_BACKLOG_GROWTH':
      return { labelKey: 'health.actions.openQueue', route: targetRoute(issue) };
    case 'CLUSTER_SINGLE_MEMBER':
      return { labelKey: 'health.actions.openOverview', route: '/' };
  }
}

/** Ignore list id that hides every issue of the rule. */
export const wholeRuleId = (issue: HealthCheckIssue) => `${issue.checkType}:*`;

/** Check type of an ignore list entry (`MAP_UNBOUNDED_NO_TTL:orders` → `MAP_UNBOUNDED_NO_TTL`). */
export function ruleOf(ignoreId: string): string {
  const colon = ignoreId.indexOf(':');
  return colon < 0 ? ignoreId : ignoreId.substring(0, colon);
}

/** Whether an ignore list entry hides a whole rule. */
export const isWholeRule = (ignoreId: string) => !ignoreId.includes(':') || ignoreId.endsWith(':*');
