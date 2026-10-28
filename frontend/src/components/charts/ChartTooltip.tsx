import type { TooltipProps } from 'recharts';

type ValueFormatter = (value: number) => string;

interface ChartTooltipProps extends TooltipProps<number, string> {
  formatValue: ValueFormatter;
  /** Optional title override (defaults to the hovered category label). */
  title?: string;
}

/**
 * Dark tooltip: category title, then one row per series with a color swatch (identity) and the
 * value in text color.
 */
export function ChartTooltip({ active, payload, label, formatValue, title }: ChartTooltipProps) {
  if (!active || !payload || payload.length === 0) {
    return null;
  }
  return (
    <div className="rounded-md border border-gk-border bg-gk-bg/95 px-3 py-2 text-xs shadow-panel">
      <div className="gk-mono mb-1 max-w-[260px] truncate font-semibold text-gk-text">{title ?? label ?? payload[0].name}</div>
      {payload.map((item) => (
        <div key={String(item.dataKey ?? item.name)} className="flex items-center gap-2 py-0.5">
          <span className="h-2.5 w-2.5 rounded-sm" style={{ background: item.color ?? item.payload?.fill }} aria-hidden />
          <span className="text-gk-muted">{item.name}</span>
          <span className="gk-mono ml-auto pl-3 text-gk-text">{formatValue(Number(item.value ?? 0))}</span>
        </div>
      ))}
    </div>
  );
}
