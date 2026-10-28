import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { UNAUTHORIZED_EVENT } from '../api/client';
import { endpoints } from '../api/endpoints';
import type { AuthUser } from '../api/types';

type AuthStatus = 'checking' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: AuthStatus;
  user: AuthUser | null;
  /** Set when the session ended unexpectedly (401 on a regular call). */
  sessionExpired: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * Holds the signed-in administrator. The session itself lives in an HTTP-only cookie; on start
 * the provider probes {@code /api/auth/me} to restore it after a page reload.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<AuthStatus>('checking');
  const [user, setUser] = useState<AuthUser | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);

  useEffect(() => {
    let cancelled = false;
    endpoints.auth
      .me()
      .then((me) => {
        if (!cancelled) {
          setUser(me);
          setStatus('authenticated');
        }
      })
      .catch(() => {
        if (!cancelled) {
          setStatus('anonymous');
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const statusRef = useRef<AuthStatus>(status);
  statusRef.current = status;

  useEffect(() => {
    const onUnauthorized = () => {
      if (statusRef.current === 'authenticated') {
        setSessionExpired(true);
      }
      setUser(null);
      setStatus('anonymous');
      queryClient.clear();
    };
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [queryClient]);

  const login = useCallback(async (username: string, password: string) => {
    const me = await endpoints.auth.login(username, password);
    setUser(me);
    setSessionExpired(false);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    try {
      await endpoints.auth.logout();
    } finally {
      setUser(null);
      setSessionExpired(false);
      setStatus('anonymous');
      queryClient.clear();
    }
  }, [queryClient]);

  const value = useMemo(
    () => ({ status, user, sessionExpired, login, logout }),
    [status, user, sessionExpired, login, logout],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside <AuthProvider>');
  }
  return context;
}
