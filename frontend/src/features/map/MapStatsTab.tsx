import type { ReactNode } from 'react';
import { Activity, Clock, Database, Gauge, HardDrive, Layers, MousePointerClick, PieChart, Server, Zap } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useMapStats } from '../../api/queries';
import type { MapStatsDTO } from '../../api/types';
import { SplitDonut } from '../../components/charts/SplitDonut';
import { ErrorMessage } from '../../components/ui/ErrorMessage';
import { Panel } from '../../components/ui/Panel';
import { CenteredSpinner } from '../../components/ui/Spinner';
import { StatCard } from '../../components/ui/StatCard';
import { StatsUnavailable } from '../../components/ui/StatsUnavailable';
import { formatBytes, formatDateTime, formatNumber, formatTime } from '../../lib/format';
import { OperationsChart } from './OperationsChart';

/** "Memory & Performance Stats" tab: LocalMapStats aggregated over the members. */
export function MapStatsTab({ mapName }: { mapName: string }) {
  const { t, i18n } = useTranslation();
  const stats = useMapStats(mapName);

  if (stats.isPending) {
    return <CenteredSpinner />;
  }
  if (stats.isError) {
    return (
      <div className="p-6">
        <ErrorMessage error={stats.error} onRetry={() => void stats.refetch()} />
      </div>
    );
  }

  const s = stats.data;
  const n = (value: number | undefined) => formatNumber(value, i18n.language);

  return (
    <div className="space-y-4 p-6">
      {!s.stats.available && <StatsUnavailable errors={s.stats.errors} />}
      {s.stats.collectedAt && (
        <p className="text-xs text-gk-muted">{t('dashboard.statsCollectedAt', { time: formatTime(s.stats.collectedAt, i18n.language) })}</p>
      )}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-3 2xl:grid-cols-6">
        <StatCard icon={Database} label={t('stats.ownedEntries')} value={n(s.ownedEntryCount)} />
        <StatCard icon={Layers} label={t('stats.backupEntries')} value={n(s.backupEntryCount)} sub={`${t('stats.backupCount')}: ${s.backupCount}`} accent="purple" />
        <StatCard icon={HardDrive} label={t('stats.ownedMemory')} value={s.ownedEntryMemoryCostFormatted} sub={`${n(s.ownedEntryMemoryCost)} ${t('common.bytes')}`} />
        <StatCard icon={HardDrive} label={t('stats.backupMemory')} value={s.backupEntryMemoryCostFormatted} sub={`${n(s.backupEntryMemoryCost)} ${t('common.bytes')}`} accent="purple" />
        <StatCard icon={Gauge} label={t('stats.heapCost')} value={s.heapCostFormatted} sub={`${t('stats.merkleTreesCost')}: ${formatBytes(s.merkleTreesCost)}`} accent="warning" />
        <StatCard icon={MousePointerClick} label={t('stats.hits')} value={n(s.hits)} accent="green" />
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Panel title={t('stats.readsVsWrites')} icon={Activity} className="xl:col-span-2">
          <div className="h-64">
            <OperationsChart stats={s} />
          </div>
        </Panel>
        <Panel title={t('dashboard.ownedVsBackup')} icon={PieChart} bodyClassName="flex items-center justify-center p-4">
          <SplitDonut
            first={{ label: t('dashboard.owned'), value: s.ownedEntryMemoryCost }}
            second={{ label: t('dashboard.backup'), value: s.backupEntryMemoryCost }}
            formatValue={formatBytes}
            centerLabel={formatBytes(s.ownedEntryMemoryCost + s.backupEntryMemoryCost)}
            centerCaption={t('stats.memory')}
          />
        </Panel>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Panel title={t('stats.operations')} icon={Zap} className="xl:col-span-2" bodyClassName="overflow-x-auto">
          <LatencyTable stats={s} />
        </Panel>
        <Panel title={t('stats.timestamps')} icon={Clock}>
          <dl className="space-y-2">
            <Row label={t('stats.creationTime')} value={formatDateTime(s.creationTime, i18n.language)} />
            <Row label={t('stats.lastAccessTime')} value={formatDateTime(s.lastAccessTime, i18n.language)} />
            <Row label={t('stats.lastUpdateTime')} value={formatDateTime(s.lastUpdateTime, i18n.language)} />
            <Row label={t('stats.lockedEntries')} value={n(s.lockedEntryCount)} />
            <Row label={t('stats.dirtyEntries')} value={n(s.dirtyEntryCount)} />
            <Row label={t('stats.events')} value={n(s.numberOfEvents)} />
            <Row label={t('stats.evictions')} value={n(s.evictionCount)} />
            <Row label={t('stats.expirations')} value={n(s.expirationCount)} />
            <Row label={t('stats.queries')} value={n(s.queryCount)} />
            <Row label={t('stats.indexedQueries')} value={n(s.indexedQueryCount)} />
          </dl>
        </Panel>
      </div>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
        <Panel title={t('stats.nearCache')} icon={Zap}>
          {s.nearCache ? (
            <div className="space-y-4">
              <div className="flex justify-center">
                <SplitDonut
                  first={{ label: t('stats.nearCacheHits'), value: s.nearCache.hits }}
                  second={{ label: t('stats.nearCacheMisses'), value: s.nearCache.misses }}
                  formatValue={(value) => formatNumber(value, i18n.language)}
                  centerLabel={`${(s.nearCache.ratio * 100).toFixed(1)}%`}
                  centerCaption={t('stats.nearCacheRatio')}
                />
              </div>
              <dl className="space-y-2">
                <Row label={t('stats.nearCacheEntries')} value={n(s.nearCache.ownedEntryCount)} />
                <Row label={t('stats.nearCacheMemory')} value={formatBytes(s.nearCache.ownedEntryMemoryCost)} />
                <Row label={t('stats.nearCacheEvictions')} value={n(s.nearCache.evictions)} />
                <Row label={t('stats.nearCacheExpirations')} value={n(s.nearCache.expirations)} />
                <Row label={t('stats.nearCacheInvalidations')} value={n(s.nearCache.invalidations)} />
              </dl>
            </div>
          ) : (
            <p className="text-gk-muted">{t('stats.nearCacheDisabled')}</p>
          )}
        </Panel>
        <Panel title={t('stats.perMember')} icon={Server} className="xl:col-span-2" bodyClassName="overflow-x-auto">
          <MemberTable stats={s} />
        </Panel>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <dt className="text-gk-muted">{label}</dt>
      <dd className="gk-mono text-right text-gk-text">{value}</dd>
    </div>
  );
}

function LatencyTable({ stats }: { stats: MapStatsDTO }) {
  const { t, i18n } = useTranslation();
  const average = (total: number, count: number) => (count > 0 ? (total / count).toFixed(2) : '0');
  const rows = [
    { label: t('stats.get'), count: stats.getOperationCount, total: stats.totalGetLatency, max: stats.maxGetLatency },
    { label: t('stats.put'), count: stats.putOperationCount, total: stats.totalPutLatency, max: stats.maxPutLatency },
    { label: t('stats.set'), count: stats.setOperationCount, total: stats.totalSetLatency, max: stats.maxSetLatency },
    { label: t('stats.remove'), count: stats.removeOperationCount, total: stats.totalRemoveLatency, max: stats.maxRemoveLatency },
  ];
  return (
    <table className="w-full text-left">
      <thead className="text-xs uppercase tracking-wide text-gk-muted">
        <tr>
          <th className="px-4 py-2 font-medium">{t('stats.operations')}</th>
          <th className="px-4 py-2 text-right font-medium">#</th>
          <th className="px-4 py-2 text-right font-medium">{t('stats.latency')}</th>
          <th className="px-4 py-2 text-right font-medium">{t('stats.maxLatency')}</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.label} className="border-t border-gk-border">
            <td className="px-4 py-2">{row.label}</td>
            <td className="gk-mono px-4 py-2 text-right">{formatNumber(row.count, i18n.language)}</td>
            <td className="gk-mono px-4 py-2 text-right">{t('stats.milliseconds', { value: average(row.total, row.count) })}</td>
            <td className="gk-mono px-4 py-2 text-right">{t('stats.milliseconds', { value: row.max })}</td>
          </tr>
        ))}
        <tr className="border-t border-gk-border">
          <td className="px-4 py-2">{t('stats.other')}</td>
          <td className="gk-mono px-4 py-2 text-right">{formatNumber(stats.numberOfOtherOperations, i18n.language)}</td>
          <td className="px-4 py-2" />
          <td className="px-4 py-2" />
        </tr>
      </tbody>
    </table>
  );
}

function MemberTable({ stats }: { stats: MapStatsDTO }) {
  const { t, i18n } = useTranslation();
  const n = (value: number) => formatNumber(value, i18n.language);
  return (
    <table className="w-full text-left">
      <thead className="text-xs uppercase tracking-wide text-gk-muted">
        <tr>
          <th className="px-4 py-2 font-medium">{t('stats.member')}</th>
          <th className="px-4 py-2 text-right font-medium">{t('stats.ownedEntries')}</th>
          <th className="px-4 py-2 text-right font-medium">{t('stats.backupEntries')}</th>
          <th className="px-4 py-2 text-right font-medium">{t('stats.ownedMemory')}</th>
          <th className="px-4 py-2 text-right font-medium">{t('stats.backupMemory')}</th>
          <th className="px-4 py-2 text-right font-medium">{t('stats.hits')}</th>
          <th className="px-4 py-2 text-right font-medium">{t('stats.get')}</th>
          <th className="px-4 py-2 text-right font-medium">{t('stats.put')}</th>
        </tr>
      </thead>
      <tbody>
        {stats.members.map((member) => (
          <tr key={member.uuid} className="border-t border-gk-border hover:bg-gk-card">
            <td className="gk-mono px-4 py-2 text-gk-cyan" title={member.uuid}>
              {member.address}
            </td>
            <td className="gk-mono px-4 py-2 text-right">{n(member.ownedEntryCount)}</td>
            <td className="gk-mono px-4 py-2 text-right">{n(member.backupEntryCount)}</td>
            <td className="gk-mono px-4 py-2 text-right">{formatBytes(member.ownedEntryMemoryCost)}</td>
            <td className="gk-mono px-4 py-2 text-right">{formatBytes(member.backupEntryMemoryCost)}</td>
            <td className="gk-mono px-4 py-2 text-right">{n(member.hits)}</td>
            <td className="gk-mono px-4 py-2 text-right">{n(member.getOperationCount)}</td>
            <td className="gk-mono px-4 py-2 text-right">{n(member.putOperationCount + member.setOperationCount)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
