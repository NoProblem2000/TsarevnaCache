import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

interface StatCardProps {
  icon: LucideIcon;
  label: string;
  value: ReactNode;
  sub?: ReactNode;
  hint?: string;
  accent?: 'cyan' | 'purple' | 'green' | 'warning';
}

const ACCENTS = {
  cyan: 'text-gk-cyan bg-gk-cyan/10',
  purple: 'text-gk-purple bg-gk-purple/15',
  green: 'text-gk-green bg-gk-green/10',
  warning: 'text-gk-warning bg-gk-warning/10',
};

/** Headline metric tile: label, large monospace value and a secondary line. */
export function StatCard({ icon: Icon, label, value, sub, hint, accent = 'cyan' }: StatCardProps) {
  return (
    <div className="gk-card flex flex-col gap-2 p-4" title={hint}>
      <div className="flex items-center gap-2">
        <span className={`rounded-md p-1.5 ${ACCENTS[accent]}`}>
          <Icon className="h-4 w-4" aria-hidden />
        </span>
        <span className="text-xs font-medium uppercase tracking-wide text-gk-muted">{label}</span>
      </div>
      <div className="font-mono text-2xl font-semibold text-gk-text">{value}</div>
      {sub && <div className="text-xs text-gk-muted">{sub}</div>}
    </div>
  );
}
