import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useTranslation } from 'react-i18next';
import type { MapStatsDTO } from '../../api/types';
import { ChartTooltip } from '../../components/charts/ChartTooltip';
import { Legend } from '../../components/charts/Legend';
import { AXIS_TICK, CHART_COLORS } from '../../lib/chartTheme';
import { formatNumber } from '../../lib/format';

/**
 * Operation counts per type; reads (get) and writes (put/set/remove) carry the two series colors.
 */
export function OperationsChart({ stats }: { stats: MapStatsDTO }) {
  const { t, i18n } = useTranslation();
  const format = (value: number) => formatNumber(value, i18n.language);
  const reads = t('stats.reads');
  const writes = t('stats.writes');
  const data = [
    { name: t('stats.get'), value: stats.getOperationCount, group: reads, fill: CHART_COLORS.primary },
    { name: t('stats.put'), value: stats.putOperationCount, group: writes, fill: CHART_COLORS.secondary },
    { name: t('stats.set'), value: stats.setOperationCount, group: writes, fill: CHART_COLORS.secondary },
    { name: t('stats.remove'), value: stats.removeOperationCount, group: writes, fill: CHART_COLORS.secondary },
  ];
  const totalWrites = stats.putOperationCount + stats.setOperationCount + stats.removeOperationCount;

  return (
    <div className="flex h-full flex-col gap-3">
      <Legend
        items={[
          { label: reads, color: CHART_COLORS.primary, value: format(stats.getOperationCount) },
          { label: writes, color: CHART_COLORS.secondary, value: format(totalWrites) },
        ]}
      />
      <div className="min-h-[200px] flex-1">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }} barCategoryGap="35%">
            <CartesianGrid vertical={false} stroke={CHART_COLORS.grid} strokeDasharray="3 3" />
            <XAxis dataKey="name" tick={AXIS_TICK} axisLine={false} tickLine={false} />
            <YAxis tick={AXIS_TICK} tickFormatter={format} axisLine={false} tickLine={false} width={56} allowDecimals={false} />
            <Tooltip cursor={{ fill: CHART_COLORS.cursor }} content={<ChartTooltip formatValue={format} />} />
            <Bar dataKey="value" name={t('stats.operations')} radius={[4, 4, 0, 0]} isAnimationActive={false}>
              {data.map((entry) => (
                <Cell key={entry.name} fill={entry.fill} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
