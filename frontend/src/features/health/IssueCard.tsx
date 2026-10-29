import { ArrowRight, EyeOff, Lightbulb, RotateCcw } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import type { HealthCheckIssue } from '../../api/types';
import { formatDateTime } from '../../lib/format';
import { SEVERITY_TONE, issueAction, targetRoute } from './healthModel';

interface IssueCardProps {
  issue: HealthCheckIssue;
  /** Hides the issue (opens the ignore dialog). */
  onIgnore?: () => void;
  /** Shown for ignored issues: the ignore list entry that hides it. */
  ignoredBy?: string;
  onRestore?: () => void;
}

/** One finding: severity, target link, description, current value, recommendation, actions. */
export function IssueCard({ issue, onIgnore, ignoredBy, onRestore }: IssueCardProps) {
  const { t, i18n } = useTranslation();
  const tone = SEVERITY_TONE[issue.severity];
  const Icon = tone.icon;
  const action = issueAction(issue);
  const check = `health.checks.${issue.checkType}`;
  return (
    <article className={`gk-card border-l-4 ${tone.border}`} style={{ borderLeftColor: tone.hex }}>
      <header className="flex flex-wrap items-center gap-2 border-b border-gk-border px-4 py-2.5">
        <span className={`gk-badge gap-1 ${tone.badge}`}>
          <Icon className="h-3 w-3" aria-hidden />
          {t(`health.severity.${issue.severity}`)}
        </span>
        <h3 className="font-medium text-gk-text">{t(`${check}.title`)}</h3>
        <span className="text-gk-subtle">·</span>
        <span className="text-xs text-gk-muted">{t(`health.targetTypes.${issue.targetType}`)}</span>
        <Link to={targetRoute(issue)} className="gk-mono min-w-0 truncate text-gk-cyan hover:underline" title={issue.targetName}>
          {issue.targetName}
        </Link>
        <span className="ml-auto text-[11px] text-gk-subtle" title={issue.issueId}>
          {t('health.detectedAt', { time: formatDateTime(Date.parse(issue.detectedAt), i18n.language) })}
        </span>
      </header>
      <div className="space-y-3 px-4 py-3">
        <p className="text-gk-muted">{t(`${check}.description`)}</p>
        <div>
          <span className="text-[11px] uppercase tracking-wide text-gk-subtle">{t('health.currentValue')}</span>
          <p className="gk-mono mt-0.5 break-words rounded-md border border-gk-border bg-gk-bg px-3 py-2 text-xs text-gk-text">
            {issue.currentValue}
          </p>
        </div>
        <div className="rounded-md border border-gk-purple/30 bg-gk-purple/5 px-3 py-2.5">
          <p className="mb-1 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-gk-purple">
            <Lightbulb className="h-3.5 w-3.5" aria-hidden />
            {t('health.recommendation')}
          </p>
          <p className="whitespace-pre-line text-gk-text">{t(`health.recommendations.${issue.recommendationCode}`)}</p>
        </div>
        {ignoredBy && (
          <p className="text-xs text-gk-muted">
            {t('health.ignoredBy')} <span className="gk-mono text-gk-text">{ignoredBy}</span>
          </p>
        )}
      </div>
      <footer className="flex flex-wrap gap-2 border-t border-gk-border px-4 py-2">
        {onIgnore && (
          <button type="button" className="gk-btn-ghost px-2 py-1 text-xs" onClick={onIgnore}>
            <EyeOff className="h-3.5 w-3.5" aria-hidden />
            {t('health.ignoreCheck')}
          </button>
        )}
        {onRestore && (
          <button type="button" className="gk-btn-ghost px-2 py-1 text-xs" onClick={onRestore}>
            <RotateCcw className="h-3.5 w-3.5" aria-hidden />
            {t('health.restore')}
          </button>
        )}
        <Link to={action.route} className="gk-btn-secondary ml-auto px-2.5 py-1 text-xs">
          {t(action.labelKey)}
          <ArrowRight className="h-3.5 w-3.5" aria-hidden />
        </Link>
      </footer>
    </article>
  );
}
