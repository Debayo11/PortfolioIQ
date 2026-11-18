import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import { setUnauthorizedHandler } from '../api/client';
import type { Me, Role } from '../api/types';
import { sessionStore } from '../lib/session';

type Status = 'loading' | 'authenticated' | 'anonymous';

interface AuthState {
  user: Me | null;
  status: Status;
  login: (email: string, password: string) => Promise<void>;
  register: (body: { email: string; password: string; firstName: string; lastName: string }) => Promise<void>;
  logout: () => void;
  refreshUser: () => Promise<void>;
  hasRole: (...roles: Role[]) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<Me | null>(null);
  const [status, setStatus] = useState<Status>('loading');

  const signOut = useCallback(() => {
    sessionStore.clear();
    queryClient.clear();
    setUser(null);
    setStatus('anonymous');
  }, [queryClient]);

  // Any signed-in request rejected with 401 ends the session.
  useEffect(() => {
    setUnauthorizedHandler(signOut);
    return () => setUnauthorizedHandler(null);
  }, [signOut]);

  // Restore a saved session on start.
  useEffect(() => {
    if (!sessionStore.load()) { setStatus('anonymous'); return; }
    api.auth.me().then((me) => { setUser(me); setStatus('authenticated'); }).catch(() => signOut());
  }, [signOut]);

  // Sign out automatically when the token expires.
  useEffect(() => {
    if (status !== 'authenticated') return undefined;
    const session = sessionStore.load();
    if (!session) return undefined;
    const timer = setTimeout(signOut, Math.max(0, session.expiresAt - Date.now()));
    return () => clearTimeout(timer);
  }, [status, user, signOut]);

  const finishSignIn = useCallback(async (token: string, expiresInSeconds: number) => {
    sessionStore.save(token, expiresInSeconds);
    const me = await api.auth.me();
    queryClient.clear();
    setUser(me);
    setStatus('authenticated');
  }, [queryClient]);

  const login = useCallback(async (email: string, password: string) => {
    const r = await api.auth.login(email.trim(), password);
    await finishSignIn(r.token, r.expiresInSeconds);
  }, [finishSignIn]);

  const register = useCallback(async (body: { email: string; password: string; firstName: string; lastName: string }) => {
    const r = await api.auth.register({ ...body, email: body.email.trim() });
    await finishSignIn(r.token, r.expiresInSeconds);
  }, [finishSignIn]);

  const refreshUser = useCallback(async () => { setUser(await api.auth.me()); }, []);

  const value = useMemo<AuthState>(() => ({
    user, status, login, register, logout: signOut, refreshUser,
    hasRole: (...roles: Role[]) => !!user && roles.some((r) => user.roles.includes(r)),
  }), [user, status, login, register, signOut, refreshUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
