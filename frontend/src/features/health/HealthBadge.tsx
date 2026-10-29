import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useHealthReport } from '../../api/queries';
import { STATUS_TONE, badgeSummary } from './healthModel';

/** Top bar indicator: "92% Healthy", "2 Warnings" or "1 Critical"; opens the Healthcheck screen. */
export function HealthBadge() {
  const { t } = useTranslation();
  const report = useHealthReport();
  if (!report.data) {
    return null;
  }
  const summary = badgeSummary(report.data);
  const tone = STATUS_TONE[summary.status];
  const Icon = tone.icon;
  return (
    <Link
      to="/healthcheck"
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors hover:brightness-125 ${tone.border} ${tone.badge}`}
      title={t('health.badge.title', { score: summary.score })}
    >
      <Icon className="h-3.5 w-3.5" aria-hidden />
      <span className="gk-mono">{summary.score}%</span>
      <span className="hidden sm:inline">· {t(summary.key, { count: summary.count, score: summary.score })}</span>
    </Link>
  );
}
