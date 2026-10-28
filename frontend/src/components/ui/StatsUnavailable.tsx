import { Activity } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/** Notice shown when member metrics (the source of all statistics) are not available. */
export function StatsUnavailable({ errors }: { errors?: string[] }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-start gap-3 rounded-lg border border-gk-warning/40 bg-gk-warning/10 p-4">
      <Activity className="mt-0.5 h-5 w-5 shrink-0 text-gk-warning" aria-hidden />
      <div className="min-w-0">
        <p className="font-medium text-gk-text">{t('dashboard.statsUnavailable')}</p>
        <p className="mt-1 text-gk-muted">{t('dashboard.statsUnavailableText')}</p>
        {errors && errors.length > 0 && (
          <ul className="gk-mono mt-2 space-y-0.5 text-xs text-gk-muted">
            {errors.map((error) => (
              <li key={error} className="break-words">
                {error}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
