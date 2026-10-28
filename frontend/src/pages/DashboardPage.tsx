import { BarChart3, Boxes, Database, HardDrive, KeyRound, PieChart, Server, Shapes, WifiOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router-dom';
import { useClusterOverview } from '../api/queries';
import { STRUCTURE_TYPES, type ClusterOverviewDTO } from '../api/types';
import { SplitDonut } from '../components/charts/SplitDonut';
import { TopMapsChart } from '../components/charts/TopMapsChart';
import { STRUCTURE_ICONS } from '../components/layout/structureIcons';
import { StatsUnavailable } from '../components/ui/StatsUnavailable';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { Panel } from '../components/ui/Panel';
import { CenteredSpinner } from '../components/ui/Spinner';
import { StatCard } from '../components/ui/StatCard';
import { formatBytes, formatDuration, formatNumber, formatTime } from '../lib/format';

/** Cluster overview: headline metrics, memory charts, structures by type and members. */
export function DashboardPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const overview = useClusterOverview();

  if (overview.isPending) {
    return <CenteredSpinner />;
  }
  if (overview.isError) {
    return (
      <div className="p-6">
        <ErrorMessage error={overview.error} onRetry={() => void overview.refetch()} />
      </div>
    );
  }

  const data = overview.data;
  const totals = data.totals;
  const master = data.members.find((member) => member.master);

  return (
    <div className="space-y-6 p-6">
      <header className="flex flex-wrap items-end gap-3">
        <div className="min-w-0 flex-1">
          <h1 className="text-xl font-semibold">{t('dashboard.title')}</h1>
          <p className="mt-1 text-gk-muted">{t('dashboard.subtitle', { cluster: data.clusterName })}</p>
        </div>
        <div className="flex flex-wrap gap-2 text-xs">
          <span className="gk-badge bg-gk-card py-1 text-gk-muted">{t('dashboard.clientVersion', { version: data.clientVersion })}</span>
          {data.clusterVersion && (
            <span className="gk-badge bg-gk-card py-1 text-gk-muted">{t('dashboard.clusterVersion', { version: data.clusterVersion })}</span>
          )}
          {data.stats.collectedAt && (
            <span className="gk-badge bg-gk-card py-1 text-gk-muted">
              {t('dashboard.statsCollectedAt', { time: formatTime(data.stats.collectedAt, i18n.language) })}
            </span>
          )}
        </div>
      </header>

      {!data.connected && <OfflineBanner />}
      {data.connected && !data.stats.available && <StatsUnavailable errors={data.stats.errors} />}

      {data.connected && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <StatCard
              icon={Server}
              label={t('dashboard.members')}
              value={data.members.length}
              sub={master ? `${t('dashboard.master')}: ${master.address}` : undefined}
              hint={t('dashboard.membersHint')}
            />
            <StatCard
              icon={HardDrive}
              label={t('dashboard.mapMemory')}
              value={formatBytes(totals.ownedEntryMemoryCost + totals.backupEntryMemoryCost)}
              sub={`${t('dashboard.owned')} ${totals.ownedEntryMemoryCostFormatted} · ${t('dashboard.backup')} ${totals.backupEntryMemoryCostFormatted}`}
              hint={t('dashboard.mapMemoryHint')}
              accent="purple"
            />
            <StatCard
              icon={KeyRound}
              label={t('dashboard.totalKeys')}
              value={formatNumber(totals.mapEntries, i18n.language)}
              sub={t('dashboard.mapsCount', { count: totals.mapCount })}
              hint={t('dashboard.totalKeysHint')}
              accent="green"
            />
            <StatCard
              icon={Boxes}
              label={t('dashboard.totalObjects')}
              value={formatNumber(totals.distributedObjects, i18n.language)}
              hint={t('dashboard.totalObjectsHint')}
              accent="warning"
            />
          </div>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
            <Panel title={t('dashboard.topMaps')} icon={BarChart3} hint={t('dashboard.topMapsHint')} className="xl:col-span-2">
              {data.topMaps.length === 0 ? (
                <p className="py-10 text-center text-gk-muted">{t('dashboard.topMapsEmpty')}</p>
              ) : (
                <div className="h-72">
                  <TopMapsChart maps={data.topMaps} onSelect={(name) => navigate(`/maps/${encodeURIComponent(name)}`)} />
                </div>
              )}
              {data.topMaps.length > 0 && <TopMapsTable data={data} />}
            </Panel>
            <Panel title={t('dashboard.ownedVsBackup')} icon={PieChart} bodyClassName="flex items-center justify-center p-4">
              <SplitDonut
                first={{ label: t('dashboard.owned'), value: totals.ownedEntryMemoryCost }}
                second={{ label: t('dashboard.backup'), value: totals.backupEntryMemoryCost }}
                formatValue={formatBytes}
                centerLabel={formatBytes(totals.ownedEntryMemoryCost + totals.backupEntryMemoryCost)}
                centerCaption={t('dashboard.mapMemory')}
              />
            </Panel>
          </div>

          <div className="grid grid-cols-1 gap-4 xl:grid-cols-3">
            <Panel title={t('dashboard.structuresByType')} icon={Shapes}>
              <ul className="space-y-1.5">
                {STRUCTURE_TYPES.filter((type) => (data.structureCounts[type] ?? 0) > 0).map((type) => {
                  const Icon = STRUCTURE_ICONS[type];
                  return (
                    <li key={type} className="flex items-center gap-2">
                      <Icon className="h-4 w-4 text-gk-subtle" aria-hidden />
                      <span className="flex-1 text-gk-text">{t(`types.plural.${type}`)}</span>
                      <span className="gk-mono text-gk-muted">{data.structureCounts[type]}</span>
                    </li>
                  );
                })}
              </ul>
            </Panel>
            <Panel title={t('dashboard.members')} icon={Server} className="xl:col-span-2" bodyClassName="overflow-x-auto">
              <MembersTable data={data} />
            </Panel>
          </div>
        </>
      )}
    </div>
  );
}

function MembersTable({ data }: { data: ClusterOverviewDTO }) {
  const { t } = useTranslation();
  return (
    <table className="w-full text-left">
      <thead className="text-xs uppercase tracking-wide text-gk-muted">
        <tr>
          <th className="px-4 py-2 font-medium">{t('dashboard.memberTable.address')}</th>
          <th className="px-4 py-2 font-medium">{t('dashboard.memberTable.role')}</th>
          <th className="px-4 py-2 font-medium">{t('dashboard.memberTable.version')}</th>
          <th className="px-4 py-2 font-medium">{t('dashboard.memberTable.heap')}</th>
          <th className="px-4 py-2 font-medium">{t('dashboard.memberTable.uptime')}</th>
          <th className="px-4 py-2 font-medium">{t('dashboard.memberTable.uuid')}</th>
        </tr>
      </thead>
      <tbody>
        {data.members.map((member) => (
          <tr key={member.uuid} className="border-t border-gk-border hover:bg-gk-card">
            <td className="gk-mono px-4 py-2 text-gk-cyan">{member.address}</td>
            <td className="px-4 py-2">
              <div className="flex gap-1.5">
                {member.master && <span className="gk-badge bg-gk-cyan/15 text-gk-cyan">{t('dashboard.master')}</span>}
                <span className={`gk-badge ${member.liteMember ? 'bg-gk-warning/15 text-gk-warning' : 'bg-gk-green/15 text-gk-green'}`}>
                  {member.liteMember ? t('dashboard.liteMember') : t('dashboard.dataMember')}
                </span>
              </div>
            </td>
            <td className="gk-mono px-4 py-2">{member.version ?? '—'}</td>
            <td className="px-4 py-2">
              <HeapBar used={member.usedHeap} max={member.maxHeap} />
            </td>
            <td className="gk-mono px-4 py-2">{formatDuration(member.uptimeMillis, t)}</td>
            <td className="gk-mono px-4 py-2 text-xs text-gk-muted">{member.uuid}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** Table view of the top maps chart (exact values, links to the maps). */
function TopMapsTable({ data }: { data: ClusterOverviewDTO }) {
  const { t, i18n } = useTranslation();
  return (
    <table className="mt-3 w-full text-left text-xs">
      <thead className="text-gk-muted">
        <tr>
          <th className="py-1.5 font-medium">{t('types.singular.MAP')}</th>
          <th className="py-1.5 text-right font-medium">{t('stats.ownedEntries')}</th>
          <th className="py-1.5 text-right font-medium">{t('dashboard.owned')}</th>
          <th className="py-1.5 text-right font-medium">{t('dashboard.backup')}</th>
        </tr>
      </thead>
      <tbody>
        {data.topMaps.map((map) => (
          <tr key={map.name} className="border-t border-gk-border">
            <td className="py-1.5">
              <Link to={`/maps/${encodeURIComponent(map.name)}`} className="gk-mono inline-flex items-center gap-1.5 text-gk-cyan hover:underline">
                <Database className="h-3 w-3" aria-hidden />
                {map.name}
              </Link>
            </td>
            <td className="gk-mono py-1.5 text-right">{formatNumber(map.ownedEntryCount, i18n.language)}</td>
            <td className="gk-mono py-1.5 text-right">{formatBytes(map.ownedEntryMemoryCost)}</td>
            <td className="gk-mono py-1.5 text-right">{formatBytes(map.backupEntryMemoryCost)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function OfflineBanner() {
  const { t } = useTranslation();
  return (
    <div className="flex items-start gap-3 rounded-lg border border-gk-danger/40 bg-gk-danger/10 p-4">
      <WifiOff className="mt-0.5 h-5 w-5 shrink-0 text-gk-danger" aria-hidden />
      <div>
        <p className="font-medium text-gk-text">{t('dashboard.offlineTitle')}</p>
        <p className="mt-1 text-gk-muted">{t('dashboard.offlineText')}</p>
      </div>
    </div>
  );
}

/** Used/max heap as text plus a thin meter. */
function HeapBar({ used, max }: { used?: number; max?: number }) {
  if (used === undefined || !max) {
    return <span className="gk-mono">—</span>;
  }
  const ratio = Math.min(1, used / max);
  const color = ratio > 0.85 ? 'bg-gk-danger' : ratio > 0.7 ? 'bg-gk-warning' : 'bg-gk-green';
  return (
    <div className="min-w-[140px]">
      <div className="gk-mono text-xs">
        {formatBytes(used)} / {formatBytes(max)}
      </div>
      <div className="mt-1 h-1 rounded-full bg-gk-border">
        <div className={`h-1 rounded-full ${color}`} style={{ width: `${Math.max(2, ratio * 100)}%` }} />
      </div>
    </div>
  );
}
