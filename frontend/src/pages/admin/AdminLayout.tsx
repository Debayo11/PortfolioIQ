import { NavLink, Outlet } from 'react-router-dom';
import { PageHeader } from '../../components/ui';
import { cn } from '../../lib/cn';

const TABS = [
  { to: '/admin', label: 'Overview', end: true },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/activity', label: 'Activity' },
  { to: '/admin/system', label: 'System' },
  { to: '/admin/market', label: 'Market data' },
];

export function AdminLayout() {
  return (
    <>
      <PageHeader title="Administration" subtitle="Users, activity and system health" />
      <nav aria-label="Administration" className="mb-5 flex gap-1 overflow-x-auto rounded-xl border border-ink-700 bg-ink-900/70 p-1">
        {TABS.map((t) => (
          <NavLink key={t.to} to={t.to} end={t.end}
            className={({ isActive }) => cn('min-h-9 shrink-0 whitespace-nowrap rounded-lg px-4 text-sm font-medium transition', isActive ? 'bg-ink-600 text-white shadow' : 'text-ink-300 hover:text-white')}>
            {t.label}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  );
}
