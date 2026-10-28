import { Server, WifiOff } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useClusterOverview } from '../api/queries';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { CenteredSpinner } from '../components/ui/Spinner';
import { formatBytes, formatDuration } from '../lib/format';

/** Cluster overview: connection state and members. */
export function DashboardPage() {
  const { t } = useTranslation();
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
  return (
    <div className="space-y-6 p-6">
      <header>
        <h1 className="text-xl font-semibold">{t('dashboard.title')}</h1>
        <p className="mt-1 text-gk-muted">{t('dashboard.subtitle', { cluster: data.clusterName })}</p>
        <div className="mt-2 flex flex-wrap gap-2 text-xs">
          <span className="gk-badge bg-gk-card text-gk-muted">{t('dashboard.clientVersion', { version: data.clientVersion })}</span>
          {data.clusterVersion && (
            <span className="gk-badge bg-gk-card text-gk-muted">{t('dashboard.clusterVersion', { version: data.clusterVersion })}</span>
          )}
        </div>
      </header>

      {!data.connected && (
        <div className="flex items-start gap-3 rounded-lg border border-gk-danger/40 bg-gk-danger/10 p-4">
          <WifiOff className="mt-0.5 h-5 w-5 shrink-0 text-gk-danger" aria-hidden />
          <div>
            <p className="font-medium text-gk-text">{t('dashboard.offlineTitle')}</p>
            <p className="mt-1 text-gk-muted">{t('dashboard.offlineText')}</p>
          </div>
        </div>
      )}

      {data.connected && (
        <section className="gk-panel">
          <div className="flex items-center gap-2 border-b border-gk-border px-4 py-3">
            <Server className="h-4 w-4 text-gk-cyan" aria-hidden />
            <h2 className="font-medium">{t('dashboard.members')}</h2>
            <span className="gk-badge bg-gk-purple/20 text-gk-purple">{data.members.length}</span>
          </div>
          <div className="overflow-x-auto">
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
                    <td className="gk-mono px-4 py-2">
                      {member.usedHeap !== undefined ? `${formatBytes(member.usedHeap)} / ${formatBytes(member.maxHeap)}` : '—'}
                    </td>
                    <td className="gk-mono px-4 py-2">{formatDuration(member.uptimeMillis, t)}</td>
                    <td className="gk-mono px-4 py-2 text-xs text-gk-muted">{member.uuid}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
