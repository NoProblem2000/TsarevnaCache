import { useMemo, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Activity, CheckCircle2, EyeOff, HeartPulse, ListChecks, Play, RotateCcw, ServerOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router-dom';
import { endpoints } from '../api/endpoints';
import { queryKeys, useHealthReport } from '../api/queries';
import {
  HEALTH_SEVERITIES,
  HEALTH_TARGET_TYPES,
  type HealthCheckIgnore,
  type HealthCheckIssue,
  type HealthCheckReport,
  type HealthSeverity,
  type HealthTargetType,
} from '../api/types';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { CenteredSpinner } from '../components/ui/Spinner';
import { Tabs } from '../components/ui/Tabs';
import { useToast } from '../components/ui/Toast';
import { IgnoreIssueModal } from '../features/health/IgnoreIssueModal';
import { IssueCard } from '../features/health/IssueCard';
import { PrometheusModal } from '../features/health/PrometheusModal';
import { ScoreRing } from '../features/health/ScoreRing';
import { SEVERITY_TONE, isWholeRule, ruleOf } from '../features/health/healthModel';
import { formatDateTime, formatDuration } from '../lib/format';

type HealthTab = 'active' | 'ignored';

/**
 * Healthcheck & Advisor: health score, issue counters, last run, "Run Check Now", the Prometheus
 * exporter, active issues (filterable) and the ignore list. The tab is kept in the URL.
 */
export function HealthcheckPage() {
  const { t, i18n } = useTranslation();
  const toast = useToast();
  const queryClient = useQueryClient();
  const report = useHealthReport();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab: HealthTab = searchParams.get('tab') === 'ignored' ? 'ignored' : 'active';
  const [severities, setSeverities] = useState<HealthSeverity[]>(HEALTH_SEVERITIES);
  const [targetType, setTargetType] = useState<HealthTargetType | ''>('');
  const [running, setRunning] = useState(false);
  const [toIgnore, setToIgnore] = useState<HealthCheckIssue | null>(null);
  const [prometheusOpen, setPrometheusOpen] = useState(false);

  const data = report.data;
  const update = (next: HealthCheckReport) => queryClient.setQueryData(queryKeys.health, next);

  const runNow = async () => {
    setRunning(true);
    try {
      update(await endpoints.healthcheck.run());
      toast.success(t('health.ranNow'));
    } catch {
      toast.error(t('health.runFailed'));
    } finally {
      setRunning(false);
    }
  };
  const ignore = async (issueId: string, reason: string) => {
    update(await endpoints.healthcheck.ignore(issueId, reason));
    toast.success(t('health.ignored'));
  };
  const restore = async (issueId: string) => {
    try {
      update(await endpoints.healthcheck.unignore(issueId));
      toast.success(t('health.restored'));
    } catch {
      toast.error(t('health.restoreFailed'));
    }
  };

  const visible = useMemo(
    () =>
      (data?.activeIssues ?? []).filter(
        (issue) => severities.includes(issue.severity) && (targetType === '' || issue.targetType === targetType),
      ),
    [data, severities, targetType],
  );

  if (report.isPending) {
    return <CenteredSpinner />;
  }
  if (report.isError || !data) {
    return (
      <div className="p-6">
        <ErrorMessage error={report.error} onRetry={() => void report.refetch()} />
      </div>
    );
  }

  const toggleSeverity = (severity: HealthSeverity) =>
    setSeverities((current) =>
      current.includes(severity) ? current.filter((item) => item !== severity) : HEALTH_SEVERITIES.filter((item) => item === severity || current.includes(item)),
    );

  return (
    <div className="flex h-full flex-col">
      <header className="flex flex-wrap items-center gap-3 border-b border-gk-border bg-gk-panel px-6 py-4">
        <span className="flex h-9 w-9 items-center justify-center rounded-lg bg-gk-cyan/10 text-gk-cyan">
          <HeartPulse className="h-5 w-5" aria-hidden />
        </span>
        <div className="min-w-0">
          <h1 className="text-lg font-semibold text-gk-text">{t('health.title')}</h1>
          <p className="text-xs text-gk-muted">{t('health.subtitle')}</p>
        </div>
        <div className="ml-auto flex flex-wrap items-center gap-2">
          <button type="button" className="gk-btn-secondary" onClick={() => setPrometheusOpen(true)}>
            <Activity className="h-4 w-4" aria-hidden />
            {t('health.prometheus.button')}
          </button>
          <button type="button" className="gk-btn-primary" onClick={() => void runNow()} disabled={running}>
            <Play className={`h-4 w-4 ${running ? 'animate-pulse' : ''}`} aria-hidden />
            {running ? t('health.running') : t('health.runNow')}
          </button>
        </div>
      </header>

      <section className="grid gap-4 border-b border-gk-border bg-gk-bg px-6 py-5 lg:grid-cols-[auto_minmax(0,1fr)_auto]">
        <ScoreRing score={data.healthScore} status={data.status} available={data.clusterAvailable} />
        <div className="grid grid-cols-2 gap-3 self-center sm:grid-cols-4">
          {HEALTH_SEVERITIES.map((severity) => {
            const tone = SEVERITY_TONE[severity];
            const Icon = tone.icon;
            const count = severity === 'CRITICAL' ? data.counts.critical : severity === 'WARNING' ? data.counts.warning : data.counts.info;
            return (
              <div key={severity} className="gk-card px-4 py-3">
                <span className={`flex items-center gap-1.5 text-xs font-medium ${tone.text}`}>
                  <Icon className="h-3.5 w-3.5" aria-hidden />
                  {t(`health.counters.${severity}`)}
                </span>
                <span className="gk-mono mt-1 block text-2xl font-semibold text-gk-text">{count}</span>
              </div>
            );
          })}
          <div className="gk-card px-4 py-3">
            <span className="flex items-center gap-1.5 text-xs font-medium text-gk-muted">
              <EyeOff className="h-3.5 w-3.5" aria-hidden />
              {t('health.counters.ignored')}
            </span>
            <span className="gk-mono mt-1 block text-2xl font-semibold text-gk-text">{data.counts.ignored}</span>
          </div>
        </div>
        <dl className="grid content-center gap-1.5 text-xs lg:text-right">
          <div>
            <dt className="text-gk-subtle">{t('health.lastRun')}</dt>
            <dd className="gk-mono text-gk-text">{data.lastRunTime ? formatDateTime(Date.parse(data.lastRunTime), i18n.language) : '—'}</dd>
          </div>
          <div>
            <dt className="text-gk-subtle">{t('health.schedule')}</dt>
            <dd className="text-gk-text">
              {data.intervalMillis > 0 ? t('health.every', { interval: formatDuration(data.intervalMillis, t) }) : t('health.manualOnly')}
            </dd>
          </div>
          <div>
            <dt className="text-gk-subtle">{t('health.coverage')}</dt>
            <dd className="text-gk-text">
              {t('health.coverageValue', { maps: data.checkedMaps, sampled: data.sampledMaps, ms: data.durationMillis })}
            </dd>
          </div>
        </dl>
      </section>

      {!data.clusterAvailable && (
        <div className="flex items-start gap-2 border-b border-gk-danger/30 bg-gk-danger/10 px-6 py-3 text-gk-text">
          <ServerOff className="mt-0.5 h-4 w-4 shrink-0 text-gk-danger" aria-hidden />
          <span>
            {t('health.unavailable')}
            {data.error && <span className="gk-mono mt-0.5 block text-xs text-gk-muted">{data.error}</span>}
          </span>
        </div>
      )}

      <Tabs<HealthTab>
        active={tab}
        onChange={(next) => setSearchParams(next === 'active' ? {} : { tab: next }, { replace: true })}
        tabs={[
          { id: 'active', label: t('health.tabs.active', { count: data.activeIssues.length }), icon: ListChecks },
          { id: 'ignored', label: t('health.tabs.ignored', { count: data.ignores.length }), icon: EyeOff },
        ]}
      />

      <div className="min-h-0 flex-1 overflow-y-auto">
        {tab === 'active' ? (
          <div className="space-y-4 p-6">
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-xs text-gk-muted">{t('health.filterSeverity')}</span>
              {HEALTH_SEVERITIES.map((severity) => {
                const on = severities.includes(severity);
                const tone = SEVERITY_TONE[severity];
                return (
                  <button
                    key={severity}
                    type="button"
                    aria-pressed={on}
                    onClick={() => toggleSeverity(severity)}
                    className={`gk-badge cursor-pointer gap-1 py-1 transition-opacity ${tone.badge} ${on ? '' : 'opacity-40'}`}
                  >
                    <tone.icon className="h-3 w-3" aria-hidden />
                    {t(`health.severity.${severity}`)}
                  </button>
                );
              })}
              <label className="ml-auto flex items-center gap-2 text-xs text-gk-muted">
                {t('health.filterTarget')}
                <select
                  className="gk-input w-auto py-1 text-xs"
                  value={targetType}
                  onChange={(event) => setTargetType(event.target.value as HealthTargetType | '')}
                >
                  <option value="">{t('health.allTargets')}</option>
                  {HEALTH_TARGET_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {t(`health.targetTypes.${type}`)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
            {data.activeIssues.length === 0 && data.clusterAvailable ? (
              <div className="gk-panel flex flex-col items-center gap-2 py-14 text-center">
                <CheckCircle2 className="h-9 w-9 text-gk-green" aria-hidden />
                <p className="font-medium text-gk-text">{t('health.noIssues')}</p>
                <p className="max-w-md text-xs text-gk-muted">{t('health.noIssuesHint')}</p>
              </div>
            ) : visible.length === 0 ? (
              <p className="text-gk-muted">{t('health.noMatches')}</p>
            ) : (
              <div className="grid gap-4 xl:grid-cols-2">
                {visible.map((issue) => (
                  <IssueCard key={issue.issueId} issue={issue} onIgnore={() => setToIgnore(issue)} />
                ))}
              </div>
            )}
          </div>
        ) : (
          <IgnoredTab report={data} onRestore={(issueId) => void restore(issueId)} />
        )}
      </div>

      <IgnoreIssueModal issue={toIgnore} onClose={() => setToIgnore(null)} onConfirm={ignore} />
      <PrometheusModal open={prometheusOpen} onClose={() => setPrometheusOpen(false)} />
    </div>
  );
}

/** Ignore list entries with what they hide, and the hidden issues themselves. */
function IgnoredTab({ report, onRestore }: { report: HealthCheckReport; onRestore: (issueId: string) => void }) {
  const { t, i18n } = useTranslation();
  const hiddenBy = (entry: HealthCheckIgnore) =>
    report.ignoredIssues.filter((issue) =>
      isWholeRule(entry.issueId) ? issue.checkType === ruleOf(entry.issueId) : issue.issueId === entry.issueId,
    );
  if (report.ignores.length === 0) {
    return (
      <div className="p-6">
        <div className="gk-panel flex flex-col items-center gap-2 py-14 text-center">
          <EyeOff className="h-8 w-8 text-gk-subtle" aria-hidden />
          <p className="font-medium text-gk-text">{t('health.ignoredEmpty')}</p>
          <p className="max-w-md text-xs text-gk-muted">{t('health.ignoredEmptyHint')}</p>
        </div>
      </div>
    );
  }
  return (
    <div className="space-y-6 p-6">
      <div className="gk-panel overflow-x-auto">
        <table className="w-full min-w-[720px] text-left text-xs">
          <thead className="bg-gk-bg text-gk-muted">
            <tr>
              <th className="px-4 py-2 font-medium">{t('health.ignoredColumns.rule')}</th>
              <th className="px-4 py-2 font-medium">{t('health.ignoredColumns.scope')}</th>
              <th className="px-4 py-2 font-medium">{t('health.ignoredColumns.reason')}</th>
              <th className="px-4 py-2 font-medium">{t('health.ignoredColumns.added')}</th>
              <th className="px-4 py-2 text-right font-medium">
                <span className="sr-only">{t('common.actions')}</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {report.ignores.map((entry) => {
              const rule = ruleOf(entry.issueId);
              const known = i18n.exists(`health.checks.${rule}.title`);
              const hidden = hiddenBy(entry);
              return (
                <tr key={entry.issueId} className="border-t border-gk-border align-top">
                  <td className="px-4 py-2.5">
                    <span className="block font-medium text-gk-text">{known ? t(`health.checks.${rule}.title`) : rule}</span>
                    <span className="gk-mono block break-all text-gk-subtle">{entry.issueId}</span>
                  </td>
                  <td className="px-4 py-2.5">
                    <span className="gk-badge bg-gk-card text-gk-muted">
                      {isWholeRule(entry.issueId) ? t('health.ignoreDialog.scopeRule') : t('health.ignoreDialog.scopeTarget')}
                    </span>
                    <span className="mt-1 block text-gk-subtle">{t('health.hides', { count: hidden.length })}</span>
                  </td>
                  <td className="max-w-xs px-4 py-2.5 text-gk-text">{entry.reason || <span className="text-gk-subtle">—</span>}</td>
                  <td className="px-4 py-2.5 text-gk-muted">
                    {entry.createdAt ? formatDateTime(Date.parse(entry.createdAt), i18n.language) : '—'}
                    {entry.createdBy && <span className="block text-gk-subtle">{entry.createdBy}</span>}
                  </td>
                  <td className="px-4 py-2.5 text-right">
                    <button type="button" className="gk-btn-secondary px-2.5 py-1 text-xs" onClick={() => onRestore(entry.issueId)}>
                      <RotateCcw className="h-3.5 w-3.5" aria-hidden />
                      {t('health.restore')}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {report.ignoredIssues.length > 0 && (
        <div className="space-y-3">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-gk-muted">{t('health.hiddenIssues')}</h2>
          <div className="grid gap-4 opacity-80 xl:grid-cols-2">
            {report.ignoredIssues.map((issue) => {
              const entry = report.ignores.find((item) =>
                isWholeRule(item.issueId) ? issue.checkType === ruleOf(item.issueId) : item.issueId === issue.issueId,
              );
              return (
                <IssueCard
                  key={issue.issueId}
                  issue={issue}
                  ignoredBy={entry?.issueId}
                  onRestore={entry ? () => onRestore(entry.issueId) : undefined}
                />
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
