import type { LucideIcon } from 'lucide-react';

export interface TabItem<T extends string> {
  id: T;
  label: string;
  icon?: LucideIcon;
}

interface TabsProps<T extends string> {
  tabs: TabItem<T>[];
  active: T;
  onChange: (id: T) => void;
}

/** Underlined tab bar in the GitKraken style. */
export function Tabs<T extends string>({ tabs, active, onChange }: TabsProps<T>) {
  return (
    <div role="tablist" className="flex gap-1 border-b border-gk-border bg-gk-panel px-4">
      {tabs.map((tab) => {
        const selected = tab.id === active;
        const Icon = tab.icon;
        return (
          <button
            key={tab.id}
            type="button"
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(tab.id)}
            className={`-mb-px flex items-center gap-2 border-b-2 px-3 py-2.5 text-sm font-medium transition-colors ${
              selected ? 'border-gk-cyan text-gk-cyan' : 'border-transparent text-gk-muted hover:text-gk-text'
            }`}
          >
            {Icon && <Icon className="h-4 w-4" aria-hidden />}
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}
