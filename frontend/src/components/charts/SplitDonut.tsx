import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from 'recharts';
import { CHART_COLORS } from '../../lib/chartTheme';
import { ChartTooltip } from './ChartTooltip';
import { Legend } from './Legend';

interface SplitDonutProps {
  first: { label: string; value: number };
  second: { label: string; value: number };
  formatValue: (value: number) => string;
  /** Text in the middle of the ring (e.g. the total). */
  centerLabel: string;
  centerCaption?: string;
}

/**
 * Two-part composition ring (owned vs backup, hits vs misses) with a 2px surface gap between the
 * slices, a hover tooltip and a legend carrying the values.
 */
export function SplitDonut({ first, second, formatValue, centerLabel, centerCaption }: SplitDonutProps) {
  const total = first.value + second.value;
  const data = [
    { name: first.label, value: first.value, fill: CHART_COLORS.primary },
    { name: second.label, value: second.value, fill: CHART_COLORS.secondary },
  ];
  return (
    <div className="flex flex-col items-center gap-3">
      <div className="relative h-44 w-44">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie
              data={total > 0 ? data : [{ name: '—', value: 1, fill: CHART_COLORS.grid }]}
              dataKey="value"
              nameKey="name"
              innerRadius="68%"
              outerRadius="100%"
              startAngle={90}
              endAngle={-270}
              stroke={CHART_COLORS.surface}
              strokeWidth={2}
              isAnimationActive={false}
            >
              {(total > 0 ? data : [{ fill: CHART_COLORS.grid }]).map((entry, index) => (
                <Cell key={index} fill={entry.fill} />
              ))}
            </Pie>
            {total > 0 && <Tooltip content={<ChartTooltip formatValue={formatValue} />} />}
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="font-mono text-lg font-semibold text-gk-text">{centerLabel}</span>
          {centerCaption && <span className="text-[11px] text-gk-muted">{centerCaption}</span>}
        </div>
      </div>
      <Legend
        items={[
          { label: first.label, color: CHART_COLORS.primary, value: formatValue(first.value) },
          { label: second.label, color: CHART_COLORS.secondary, value: formatValue(second.value) },
        ]}
      />
    </div>
  );
}
