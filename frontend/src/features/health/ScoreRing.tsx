import { useTranslation } from 'react-i18next';
import type { HealthStatus } from '../../api/types';
import { STATUS_TONE } from './healthModel';

const SIZE = 132;
const STROKE = 10;
const RADIUS = (SIZE - STROKE) / 2;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

/** Health score as a ring around the number; the status is also given as icon and text. */
export function ScoreRing({ score, status, available }: { score: number; status: HealthStatus; available: boolean }) {
  const { t } = useTranslation();
  const tone = STATUS_TONE[status];
  const Icon = tone.icon;
  const value = Math.max(0, Math.min(100, score));
  return (
    <div className="flex items-center gap-4">
      <div className="relative shrink-0" style={{ width: SIZE, height: SIZE }}>
        <svg width={SIZE} height={SIZE} viewBox={`0 0 ${SIZE} ${SIZE}`} role="img" aria-label={t('health.scoreAria', { score: value })}>
          <circle cx={SIZE / 2} cy={SIZE / 2} r={RADIUS} fill="none" stroke="#2D333B" strokeWidth={STROKE} />
          {available && value > 0 && (
            <circle
              cx={SIZE / 2}
              cy={SIZE / 2}
              r={RADIUS}
              fill="none"
              stroke={tone.hex}
              strokeWidth={STROKE}
              strokeLinecap="round"
              strokeDasharray={`${(CIRCUMFERENCE * value) / 100} ${CIRCUMFERENCE}`}
              transform={`rotate(-90 ${SIZE / 2} ${SIZE / 2})`}
              style={{ transition: 'stroke-dasharray 400ms ease-out' }}
            />
          )}
        </svg>
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          <span className="gk-mono text-3xl font-semibold text-gk-text">{available ? value : '—'}</span>
          <span className="text-[11px] uppercase tracking-wide text-gk-muted">{t('health.score')}</span>
        </div>
      </div>
      <div className="min-w-0">
        <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-sm font-medium ${tone.badge}`}>
          <Icon className="h-4 w-4" aria-hidden />
          {available ? t(`health.status.${status}`) : t('health.status.UNAVAILABLE')}
        </span>
        <p className="mt-2 max-w-xs text-xs text-gk-muted">{available ? t(`health.statusHint.${status}`) : t('health.statusHint.UNAVAILABLE')}</p>
      </div>
    </div>
  );
}
