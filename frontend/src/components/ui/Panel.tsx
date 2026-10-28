import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';

interface PanelProps {
  title: string;
  icon?: LucideIcon;
  hint?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
}

/** Titled panel used to group dashboard and statistics content. */
export function Panel({ title, icon: Icon, hint, actions, children, className = '', bodyClassName = 'p-4' }: PanelProps) {
  return (
    <section className={`gk-panel flex flex-col ${className}`}>
      <div className="flex items-center gap-2 border-b border-gk-border px-4 py-3">
        {Icon && <Icon className="h-4 w-4 text-gk-cyan" aria-hidden />}
        <h2 className="font-medium text-gk-text">{title}</h2>
        {hint && <span className="hidden truncate text-xs text-gk-muted xl:inline">· {hint}</span>}
        {actions && <div className="ml-auto flex items-center gap-2">{actions}</div>}
      </div>
      <div className={`min-h-0 flex-1 ${bodyClassName}`}>{children}</div>
    </section>
  );
}
