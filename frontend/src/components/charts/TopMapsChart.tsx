import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useTranslation } from 'react-i18next';
import type { MapMemory } from '../../api/types';
import { AXIS_TICK, CHART_COLORS, MONO_AXIS_TICK } from '../../lib/chartTheme';
import { formatBytes } from '../../lib/format';
import { ChartTooltip } from './ChartTooltip';
import { Legend } from './Legend';

interface TopMapsChartProps {
  maps: MapMemory[];
  onSelect: (mapName: string) => void;
}

/**
 * Horizontal bars of the heaviest maps: owned memory stacked with backup memory, rounded data
 * end, 2px surface gap between the segments. Clicking a bar opens the map.
 */
export function TopMapsChart({ maps, onSelect }: TopMapsChartProps) {
  const { t } = useTranslation();
  const owned = t('dashboard.owned');
  const backup = t('dashboard.backup');
  const data = maps.map((map) => ({ name: map.name, owned: map.ownedEntryMemoryCost, backup: map.backupEntryMemoryCost }));

  return (
    <div className="flex h-full flex-col gap-3">
      <Legend
        items={[
          { label: owned, color: CHART_COLORS.primary },
          { label: backup, color: CHART_COLORS.secondary },
        ]}
      />
      <div className="min-h-[220px] flex-1">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout="vertical" margin={{ top: 4, right: 16, bottom: 4, left: 8 }} barCategoryGap="28%">
            <CartesianGrid horizontal={false} stroke={CHART_COLORS.grid} strokeDasharray="3 3" />
            <XAxis type="number" tick={AXIS_TICK} tickFormatter={(value: number) => formatBytes(value)} axisLine={false} tickLine={false} />
            <YAxis
              type="category"
              dataKey="name"
              width={120}
              tick={MONO_AXIS_TICK}
              tickFormatter={(value: string) => (value.length > 16 ? `${value.slice(0, 15)}…` : value)}
              axisLine={false}
              tickLine={false}
            />
            <Tooltip cursor={{ fill: CHART_COLORS.cursor }} content={<ChartTooltip formatValue={formatBytes} />} />
            <Bar
              dataKey="owned"
              name={owned}
              stackId="memory"
              fill={CHART_COLORS.primary}
              stroke={CHART_COLORS.surface}
              strokeWidth={2}
              cursor="pointer"
              isAnimationActive={false}
              onClick={(entry: { name?: string }) => entry.name && onSelect(entry.name)}
            />
            <Bar
              dataKey="backup"
              name={backup}
              stackId="memory"
              fill={CHART_COLORS.secondary}
              stroke={CHART_COLORS.surface}
              strokeWidth={2}
              radius={[0, 4, 4, 0]}
              cursor="pointer"
              isAnimationActive={false}
              onClick={(entry: { name?: string }) => entry.name && onSelect(entry.name)}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
