import { LogOut, User } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { useClusterOverview } from '../../api/queries';
import { useAuth } from '../../auth/AuthContext';
import { HealthBadge } from '../../features/health/HealthBadge';
import { Logo } from '../ui/Logo';
import { LanguageSwitch } from './LanguageSwitch';

/** Top bar: logo, cluster name, connection status, language switch and logout. */
export function TopBar() {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const overview = useClusterOverview();

  const status: 'checking' | 'online' | 'offline' = overview.isPending
    ? 'checking'
    : overview.data?.connected && !overview.isError
      ? 'online'
      : 'offline';
  const statusStyles = {
    checking: 'border-gk-border text-gk-muted',
    online: 'border-gk-green/40 bg-gk-green/10 text-gk-green',
    offline: 'border-gk-danger/40 bg-gk-danger/10 text-gk-danger',
  }[status];
  const dotStyles = {
    checking: 'bg-gk-muted',
    online: 'bg-gk-green shadow-[0_0_8px_#2ECC71]',
    offline: 'bg-gk-danger',
  }[status];

  return (
    <header className="flex h-14 shrink-0 items-center gap-4 border-b border-gk-border bg-gk-panel px-4">
      <Link to="/" className="flex items-center gap-2.5">
        <Logo />
        <div className="leading-tight">
          <div className="font-semibold text-gk-text">{t('app.name')}</div>
          <div className="text-[11px] text-gk-muted">{t('app.tagline')}</div>
        </div>
      </Link>

      <div className="mx-2 h-6 w-px bg-gk-border" aria-hidden />

      <div className="flex min-w-0 items-center gap-3">
        <span className="text-xs uppercase tracking-wide text-gk-muted">{t('topBar.cluster')}</span>
        <span className="gk-mono truncate text-gk-cyan" title={overview.data?.clusterName}>
          {overview.data?.clusterName ?? '—'}
        </span>
        <span
          className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium ${statusStyles}`}
          aria-live="polite"
        >
          <span className={`h-2 w-2 rounded-full ${dotStyles}`} aria-hidden />
          {t(`topBar.${status}`)}
        </span>
        {status === 'online' && <HealthBadge />}
        {status === 'online' && overview.data && (
          <span className="hidden text-xs text-gk-muted lg:inline">
            {t('topBar.members', { count: overview.data.members.length })}
          </span>
        )}
      </div>

      <div className="ml-auto flex items-center gap-3">
        {overview.data?.appVersion && (
          <span className="gk-badge hidden bg-gk-card py-1 text-gk-muted md:inline-flex" title={t('topBar.version', { version: overview.data.appVersion })}>
            v{overview.data.appVersion}
          </span>
        )}
        <LanguageSwitch />
        {user && (
          <span className="hidden items-center gap-1.5 text-xs text-gk-muted md:inline-flex" title={t('auth.signedInAs', { user: user.username })}>
            <User className="h-3.5 w-3.5" aria-hidden />
            {user.username}
          </span>
        )}
        <button type="button" className="gk-btn-ghost" onClick={() => void logout()} title={t('topBar.logout')}>
          <LogOut className="h-4 w-4" aria-hidden />
          <span className="hidden sm:inline">{t('topBar.logout')}</span>
        </button>
      </div>
    </header>
  );
}
