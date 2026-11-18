import { useState } from 'react';
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { BellRing, Check, Plus, Trash2 } from 'lucide-react';
import { api } from '../api/endpoints';
import type { PriceAlert } from '../api/types';
import { Async } from '../components/Async';
import { MarketTabs } from '../components/layout/AppShell';
import { AlertModal } from '../components/market/Modals';
import { Badge, Button, Card, EmptyState, ErrorNote, PageHeader, SectionTitle, Skeleton } from '../components/ui';
import { fmtAgo, fmtDateTime, fmtNumber, humanize } from '../lib/format';
import { cn } from '../lib/cn';

function describe(a: PriceAlert): string {
  if (a.condition === 'PERCENT_MOVE') return `${a.assetSymbol} moves ${fmtNumber(a.thresholdPercent, 4)}% or more in a day`;
  return `${a.assetSymbol} ${a.condition === 'ABOVE' ? 'rises to or above' : 'falls to or below'} ${fmtNumber(a.targetPrice, 8)}`;
}

export function AlertsPage() {
  const qc = useQueryClient();
  const alerts = useQuery({ queryKey: ['alerts'], queryFn: api.alerts.list });
  const [creating, setCreating] = useState(false);
  const remove = useMutation({ mutationFn: (id: number) => api.alerts.remove(id), onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['alerts'] }); } });

  return (
    <>
      <MarketTabs />
      <PageHeader title="Price alerts" subtitle="Get notified when a price reaches your target"
        actions={<Button icon={<Plus className="h-4 w-4" />} onClick={() => setCreating(true)}>New alert</Button>} />
      <ErrorNote error={remove.error} />
      <Async query={alerts} skeleton={<Skeleton className="h-48" />}>
        {(list) => {
          const active = list.filter((a) => a.active);
          const done = list.filter((a) => !a.active);
          if (list.length === 0) {
            return <Card><EmptyState title="No alerts yet" text="Create an alert to be told when an asset crosses a price or moves sharply." action={<Button onClick={() => setCreating(true)}>Create an alert</Button>} /></Card>;
          }
          const row = (a: PriceAlert) => (
            <li key={a.id} className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2"><Badge tone={a.active ? 'info' : 'gain'}>{a.active ? 'Active' : 'Triggered'}</Badge><span className="text-xs text-ink-400">{humanize(a.condition)}</span></div>
                <div className="mt-1 text-sm text-white">{describe(a)}</div>
                <div className="text-xs text-ink-400">{a.triggeredAt ? `Triggered ${fmtDateTime(a.triggeredAt)}` : `Created ${fmtAgo(a.createdAt)}`}</div>
              </div>
              <button type="button" aria-label="Delete alert" onClick={() => remove.mutate(a.id)} className="rounded-lg p-2 text-ink-400 hover:bg-red-500/10 hover:text-red-300"><Trash2 className="h-4 w-4" /></button>
            </li>
          );
          return (
            <div className="space-y-4">
              <Card><SectionTitle>Active ({active.length})</SectionTitle>{active.length ? <ul className="divide-y divide-ink-700/60">{active.map(row)}</ul> : <p className="py-3 text-sm text-ink-400">No active alerts.</p>}</Card>
              {done.length > 0 && <Card><SectionTitle>Triggered ({done.length})</SectionTitle><ul className="divide-y divide-ink-700/60">{done.map(row)}</ul></Card>}
            </div>
          );
        }}
      </Async>
      <AlertModal open={creating} onClose={() => setCreating(false)} />
    </>
  );
}

export function NotificationsPage() {
  const qc = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const list = useQuery({ queryKey: ['notifications', unreadOnly, page], queryFn: () => api.notifications.list(unreadOnly, page, 20), placeholderData: keepPreviousData, refetchInterval: 60_000 });
  const refresh = async () => { await qc.invalidateQueries({ queryKey: ['notifications'] }); await qc.invalidateQueries({ queryKey: ['unread'] }); };
  const markRead = useMutation({ mutationFn: (id: number) => api.notifications.markRead(id), onSuccess: refresh });
  const markAll = useMutation({ mutationFn: () => api.notifications.markAllRead(), onSuccess: refresh });

  return (
    <>
      <PageHeader title="Notifications" subtitle="Alerts, transactions, dividends and transfers"
        actions={<>
          <Button variant="secondary" size="sm" onClick={() => { setUnreadOnly(!unreadOnly); setPage(0); }}>{unreadOnly ? 'Show all' : 'Unread only'}</Button>
          <Button variant="secondary" size="sm" icon={<Check className="h-4 w-4" />} loading={markAll.isPending} onClick={() => markAll.mutate()}>Mark all read</Button>
        </>} />
      <ErrorNote error={markRead.error ?? markAll.error} />
      <Async query={list} skeleton={<Skeleton className="h-64" />}>
        {(p) => p.content.length === 0 ? (
          <Card><EmptyState title={unreadOnly ? 'You are all caught up' : 'No notifications yet'} text="Price alerts and account activity will show up here." /></Card>
        ) : (
          <Card className="p-0 sm:p-0">
            <ul className="divide-y divide-ink-700/60">
              {p.content.map((n) => (
                <li key={n.id}>
                  <button type="button" onClick={() => { if (!n.read) markRead.mutate(n.id); }}
                    className={cn('flex w-full items-start gap-3 px-4 py-3.5 text-left sm:px-5', !n.read && 'bg-emerald-400/5')}>
                    <span className={cn('mt-1 grid h-8 w-8 shrink-0 place-items-center rounded-full', n.read ? 'bg-ink-700 text-ink-300' : 'bg-emerald-400/15 text-emerald-300')}><BellRing className="h-4 w-4" aria-hidden /></span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center justify-between gap-2"><span className={cn('truncate text-sm', n.read ? 'text-ink-200' : 'font-semibold text-white')}>{n.title}</span><span className="shrink-0 text-xs text-ink-400">{fmtAgo(n.createdAt)}</span></span>
                      <span className="mt-0.5 block text-sm text-ink-300">{n.message}</span>
                    </span>
                  </button>
                </li>
              ))}
            </ul>
            {p.totalPages > 1 && (
              <div className="flex items-center justify-between border-t border-ink-700/60 px-4 py-3 text-sm text-ink-300">
                <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Previous</Button>
                <span>Page {p.page + 1} of {p.totalPages}</span>
                <Button variant="ghost" size="sm" disabled={page + 1 >= p.totalPages} onClick={() => setPage(page + 1)}>Next</Button>
              </div>
            )}
          </Card>
        )}
      </Async>
    </>
  );
}
