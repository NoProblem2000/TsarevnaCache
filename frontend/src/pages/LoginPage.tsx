import { useState, type FormEvent } from 'react';
import { Info, KeyRound, LogIn } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { LanguageSwitch } from '../components/layout/LanguageSwitch';
import { ErrorMessage } from '../components/ui/ErrorMessage';
import { Logo } from '../components/ui/Logo';

/** Sign-in screen for the built-in administrator account. */
export function LoginPage() {
  const { t } = useTranslation();
  const { login, sessionExpired } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(username.trim(), password);
    } catch (failure) {
      setError(failure);
      setPassword('');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="relative flex min-h-full items-center justify-center bg-gk-bg p-6">
      <div
        className="pointer-events-none absolute inset-0 opacity-60"
        style={{
          background:
            'radial-gradient(600px circle at 20% 15%, rgba(0,229,255,0.08), transparent 60%), radial-gradient(500px circle at 85% 80%, rgba(155,81,224,0.10), transparent 60%)',
        }}
        aria-hidden
      />
      <div className="absolute right-6 top-6">
        <LanguageSwitch />
      </div>

      <form onSubmit={onSubmit} className="gk-panel relative w-full max-w-sm space-y-5 p-8 shadow-panel">
        <div className="flex flex-col items-center gap-3 text-center">
          <Logo className="h-14 w-14" />
          <div>
            <h1 className="text-xl font-semibold text-gk-text">{t('app.name')}</h1>
            <p className="mt-1 text-xs text-gk-muted">{t('auth.subtitle')}</p>
          </div>
        </div>

        {sessionExpired && (
          <div className="flex items-start gap-2 rounded-md border border-gk-warning/40 bg-gk-warning/10 p-3 text-xs text-gk-warning">
            <Info className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
            {t('auth.sessionExpired')}
          </div>
        )}
        {error !== null && <ErrorMessage error={error} showDetails={false} />}

        <div>
          <label htmlFor="username" className="gk-label">
            {t('auth.username')}
          </label>
          <input
            id="username"
            className="gk-input"
            autoComplete="username"
            autoFocus
            required
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </div>
        <div>
          <label htmlFor="password" className="gk-label">
            {t('auth.password')}
          </label>
          <div className="relative">
            <KeyRound className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gk-subtle" aria-hidden />
            <input
              id="password"
              type="password"
              className="gk-input pl-9"
              autoComplete="current-password"
              required
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </div>
        </div>

        <button type="submit" className="gk-btn-primary w-full py-2" disabled={submitting || !username || !password}>
          <LogIn className="h-4 w-4" aria-hidden />
          {submitting ? t('auth.signingIn') : t('auth.signIn')}
        </button>
      </form>
    </div>
  );
}
