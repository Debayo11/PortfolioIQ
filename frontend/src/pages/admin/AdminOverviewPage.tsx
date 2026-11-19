import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/endpoints';
import { Async } from '../../components/Async';
import { DayBars } from '../../components/charts/charts';
import { Badge, Card, SectionTitle, Skeleton, Stat } from '../../components/ui';
import { fmtDateTime, fmtMoney, fmtNumber, humanize } from '../../lib/format';

export function AdminOverviewPage() {
  const stats = useQuery({ queryKey: ['admin-stats'], queryFn: api.admin.statistics, refetchInterval: 60_000 });
  return (
    <Async query={stats} skeleton={<div className="space-y-4"><Skeleton className="h-28" /><Skeleton className="h-56" /></div>}>
      {(s) => (
        <div className="space-y-4">
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Stat label="Users" value={fmtNumber(s.users.total, 0)} sub={`${s.users.enabled} enabled · ${s.users.emailVerified} verified`} />
            <Stat label="Portfolios" value={fmtNumber(s.portfolios, 0)} />
            <Stat label="Transactions" value={fmtNumber(s.transactions.total, 0)} />
            <Stat label="Assets" value={fmtNumber(s.assets, 0)} />
            <Stat label="Active alerts" value={fmtNumber(s.activeAlerts, 0)} />
            <Stat label="Watchlists" value={fmtNumber(s.watchlists, 0)} />
            <Stat label="Disabled accounts" value={fmtNumber(s.users.total - s.users.enabled, 0)} tone={s.users.total - s.users.enabled > 0 ? 'loss' : undefined} />
            <Stat label="Unverified emails" value={fmtNumber(s.users.total - s.users.emailVerified, 0)} />
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card><SectionTitle>New users, last 30 days</SectionTitle><DayBars data={s.registrationsPerDay} label="New users" /></Card>
            <Card><SectionTitle>Transactions recorded, last 30 days</SectionTitle><DayBars data={s.transactionsPerDay} label="Transactions" color="#38bdf8" /></Card>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <SectionTitle>Users by role</SectionTitle>
              <ul className="space-y-2">
                {s.users.byRole.map((r) => (
                  <li key={r.role} className="flex items-center justify-between text-sm"><Badge tone="info">{humanize(r.role)}</Badge><span className="tabular text-white">{fmtNumber(r.count, 0)}</span></li>
                ))}
              </ul>
            </Card>
            <Card>
              <SectionTitle>Transactions by type</SectionTitle>
              {s.transactions.byType.length === 0 ? <p className="text-sm text-ink-400">No transactions yet.</p> : (
                <ul className="space-y-2">
                  {s.transactions.byType.map((t) => (
                    <li key={t.type} className="flex items-center justify-between text-sm"><span className="text-ink-200">{humanize(t.type)}</span><span className="tabular text-white">{fmtNumber(t.count, 0)}</span></li>
                  ))}
                </ul>
              )}
            </Card>
          </div>

          <Card>
            <SectionTitle>Transaction volume by currency</SectionTitle>
            {s.transactions.volume.length === 0 ? <p className="text-sm text-ink-400">No transactions yet.</p> : (
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead><tr className="text-left text-xs uppercase tracking-wide text-ink-400"><th className="pb-2 font-medium">Currency</th><th className="pb-2 font-medium">Type</th><th className="pb-2 text-right font-medium">Count</th><th className="pb-2 text-right font-medium">Total amount</th></tr></thead>
                  <tbody className="divide-y divide-ink-700/60">
                    {s.transactions.volume.map((v) => (
                      <tr key={`${v.currency}-${v.type}`} className="tabular"><td className="py-2.5 text-white">{v.currency}</td><td className="py-2.5 text-ink-200">{humanize(v.type)}</td><td className="py-2.5 text-right text-ink-200">{fmtNumber(v.count, 0)}</td><td className="py-2.5 text-right text-white">{fmtMoney(v.totalAmount, v.currency)}</td></tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <p className="mt-3 text-xs text-ink-400">Amounts are shown in each portfolio's own currency and are never added across currencies.</p>
          </Card>
          <p className="text-center text-xs text-ink-500">Generated {fmtDateTime(s.generatedAt)}. Day boundaries are UTC.</p>
        </div>
      )}
    </Async>
  );
}
