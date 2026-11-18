import { useEffect, useState } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bell, BellRing, BarChart3, Briefcase, Download, LayoutDashboard, LineChart, LogOut, Settings, Share, Star, WifiOff, X } from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { api } from '../../api/endpoints';
import { useAuth } from '../../auth/AuthContext';
import { useInstall } from '../../lib/install';
import { cn } from '../../lib/cn';
import { Button, SimulatedBadge } from '../ui';

interface NavItem { to: string; label: string; icon: LucideIcon; end?: boolean; bottom?: boolean }

const NAV: NavItem[] = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true, bottom: true },
  { to: '/portfolios', label: 'Portfolios', icon: Briefcase, bottom: true },
  { to: '/market', label: 'Market', icon: LineChart, bottom: true },
  { to: '/analytics', label: 'Analytics', icon: BarChart3, bottom: true },
  { to: '/watchlists', label: 'Watchlists', icon: Star },
  { to: '/alerts', label: 'Alerts', icon: BellRing },
  { to: '/notifications', label: 'Notifications', icon: Bell },
  { to: '/settings', label: 'Settings', icon: Settings, bottom: true },
];

function Logo({ className }: { className?: string }) {
  return (
    <div className={cn('flex items-center gap-2.5', className)}>
      <img src="/favicon.svg" alt="" className="h-8 w-8 rounded-lg" />
      <span className="text-lg font-semibold tracking-tight text-white">Portfolio<span className="text-emerald-400">IQ</span></span>
    </div>
  );
}

function useOnline(): boolean {
  const [online, setOnline] = useState(typeof navigator === 'undefined' ? true : navigator.onLine);
  useEffect(() => {
    const on = () => setOnline(true);
    const off = () => setOnline(false);
    window.addEventListener('online', on);
    window.addEventListener('offline', off);
    return () => { window.removeEventListener('online', on); window.removeEventListener('offline', off); };
  }, []);
  return online;
}

function InstallBanner() {
  const { canInstall, showIosSteps, install } = useInstall();
  const [dismissed, setDismissed] = useState(() => {
    try { return localStorage.getItem('piq.installDismissed') === '1'; } catch { return false; }
  });
  if (dismissed || (!canInstall && !showIosSteps)) return null;
  const close = () => {
    setDismissed(true);
    try { localStorage.setItem('piq.installDismissed', '1'); } catch { /* ignore */ }
  };
  return (
    <div className="mb-4 flex items-start gap-3 rounded-2xl border border-emerald-400/25 bg-emerald-400/10 p-3.5 text-sm text-emerald-50 animate-rise">
      <Download className="mt-0.5 h-5 w-5 shrink-0 text-emerald-300" aria-hidden />
      <div className="min-w-0 flex-1">
        <div className="font-medium">Install PortfolioIQ as an app</div>
        {canInstall ? (
          <p className="mt-0.5 text-emerald-100/80">Opens in its own window with an icon on your home screen or desktop.</p>
        ) : (
          <p className="mt-0.5 text-emerald-100/80">
            Tap <Share className="mx-0.5 inline h-4 w-4" aria-label="Share" /> then “Add to Home Screen”.
          </p>
        )}
        {canInstall && <Button size="sm" className="mt-2" onClick={() => { void install(); }}>Install</Button>}
      </div>
      <button type="button" onClick={close} aria-label="Dismiss" className="rounded-lg p-1 text-emerald-100/70 hover:bg-emerald-400/10"><X className="h-4 w-4" /></button>
    </div>
  );
}

function Header() {
  const { user } = useAuth();
  const status = useQuery({ queryKey: ['market-status'], queryFn: api.market.status, staleTime: 5 * 60_000 });
  const unread = useQuery({ queryKey: ['unread'], queryFn: api.notifications.unreadCount, refetchInterval: 60_000 });
  const count = unread.data?.unread ?? 0;
  const initials = user ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase() : '?';
  return (
    <header className="pt-safe sticky top-0 z-20 border-b border-ink-700/60 bg-ink-950/80 backdrop-blur-xl">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
        <Logo className="lg:invisible" />
        <div className="flex items-center gap-2">
          {status.data?.simulated && <SimulatedBadge />}
          <NavLink to="/notifications" aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'}
            className="relative grid h-10 w-10 place-items-center rounded-xl text-ink-200 hover:bg-ink-800">
            <Bell className="h-5 w-5" />
            {count > 0 && (
              <span className="absolute right-1 top-1 grid min-w-4 place-items-center rounded-full bg-emerald-400 px-1 text-[10px] font-bold leading-4 text-ink-950">
                {count > 99 ? '99+' : count}
              </span>
            )}
          </NavLink>
          <NavLink to="/settings" aria-label="Account settings"
            className="grid h-9 w-9 place-items-center rounded-full bg-gradient-to-br from-emerald-400 to-sky-400 text-xs font-bold text-ink-950">{initials}</NavLink>
        </div>
      </div>
    </header>
  );
}

export function AppShell() {
  const { user, logout } = useAuth();
  const online = useOnline();
  return (
    <div className="min-h-full">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-ink-700/60 bg-ink-900/80 px-3 py-5 backdrop-blur-xl lg:flex">
        <Logo className="px-3" />
        <nav className="mt-8 flex flex-1 flex-col gap-1" aria-label="Main">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end}
              className={({ isActive }) => cn('flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition',
                isActive ? 'bg-emerald-400/10 text-emerald-300' : 'text-ink-300 hover:bg-ink-800 hover:text-white')}>
              <Icon className="h-5 w-5" aria-hidden /> {label}
            </NavLink>
          ))}
        </nav>
        <div className="rounded-xl border border-ink-700/60 bg-ink-800/60 p-3">
          <div className="truncate text-sm font-medium text-white">{user?.firstName} {user?.lastName}</div>
          <div className="truncate text-xs text-ink-400">{user?.email}</div>
          <button type="button" onClick={logout} className="mt-2 inline-flex items-center gap-2 text-xs text-ink-300 hover:text-white">
            <LogOut className="h-3.5 w-3.5" aria-hidden /> Sign out
          </button>
        </div>
      </aside>

      <div className="lg:pl-64">
        <Header />
        {!online && (
          <div role="status" className="flex items-center justify-center gap-2 bg-amber-500/15 px-4 py-2 text-sm text-amber-200">
            <WifiOff className="h-4 w-4" aria-hidden /> You are offline. Data will refresh when you reconnect.
          </div>
        )}
        <main className="mx-auto w-full max-w-6xl px-4 pb-28 pt-5 sm:px-6 lg:pb-12">
          <InstallBanner />
          <Outlet />
        </main>
      </div>

      <nav aria-label="Primary" className="pb-safe pl-safe pr-safe fixed inset-x-0 bottom-0 z-30 border-t border-ink-700/70 bg-ink-900/90 backdrop-blur-xl lg:hidden">
        <ul className="mx-auto grid max-w-lg grid-cols-5">
          {NAV.filter((n) => n.bottom).map(({ to, label, icon: Icon, end }) => (
            <li key={to}>
              <NavLink to={to} end={end}
                className={({ isActive }) => cn('flex min-h-14 flex-col items-center justify-center gap-0.5 text-[11px] font-medium transition',
                  isActive ? 'text-emerald-300' : 'text-ink-400 hover:text-ink-100')}>
                <Icon className="h-5 w-5" aria-hidden /> {label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  );
}

/** Tabs shared by the three market screens so Watchlists and Alerts stay one tap away on a phone. */
export function MarketTabs() {
  const tabs = [{ to: '/market', label: 'Explore', end: true }, { to: '/watchlists', label: 'Watchlists' }, { to: '/alerts', label: 'Alerts' }];
  return (
    <div className="mb-4 inline-flex rounded-xl border border-ink-700 bg-ink-900/70 p-1 lg:hidden">
      {tabs.map((t) => (
        <NavLink key={t.to} to={t.to} end={t.end}
          className={({ isActive }) => cn('min-h-8 rounded-lg px-4 text-sm font-medium', isActive ? 'bg-ink-600 text-white' : 'text-ink-300')}>{t.label}</NavLink>
      ))}
    </div>
  );
}
