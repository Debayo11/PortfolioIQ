import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../../api/endpoints';
import { Async } from '../../components/Async';
import { Badge, Button, Card, EmptyState, SelectField, Skeleton, TextField } from '../../components/ui';
import { fmtDateTime, humanize } from '../../lib/format';

function actionTone(action: string): 'neutral' | 'warn' | 'loss' | 'info' {
  if (action.includes('FAILED')) return 'loss';
  if (action.startsWith('ADMIN')) return 'warn';
  if (action.startsWith('LOGIN') || action.startsWith('USER') || action.startsWith('PASSWORD') || action.startsWith('EMAIL')) return 'info';
  return 'neutral';
}

export function AdminActivityPage() {
  const [action, setAction] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const actions = useQuery({ queryKey: ['admin-actions'], queryFn: api.admin.auditActions, staleTime: 60_000 });
  const logs = useQuery({
    queryKey: ['admin-audit', action, from, to, page],
    queryFn: () => api.admin.auditLogs({
      action: action || undefined, from: from ? `${from}T00:00:00Z` : undefined, to: to ? `${to}T23:59:59Z` : undefined, page, size: 30,
    }),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  });
  const reset = () => { setAction(''); setFrom(''); setTo(''); setPage(0); };

  return (
    <>
      <div className="mb-4 grid gap-3 sm:grid-cols-4">
        <SelectField label="Action" value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }}>
          <option value="">All actions</option>
          {(actions.data ?? []).map((a) => <option key={a} value={a}>{humanize(a)}</option>)}
        </SelectField>
        <TextField label="From (UTC)" type="date" value={from} onChange={(e) => { setFrom(e.target.value); setPage(0); }} />
        <TextField label="To (UTC)" type="date" value={to} onChange={(e) => { setTo(e.target.value); setPage(0); }} />
        <div className="flex items-end"><Button variant="secondary" className="w-full" onClick={reset}>Clear filters</Button></div>
      </div>
      <Async query={logs} skeleton={<Skeleton className="h-72" />}>
        {(p) => p.content.length === 0 ? <Card><EmptyState title="No activity found" text="Nothing matches these filters." /></Card> : (
          <Card className="p-0 sm:p-0">
            <ul className="divide-y divide-ink-700/60">
              {p.content.map((e) => (
                <li key={e.id} className="px-4 py-3 sm:px-5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <Badge tone={actionTone(e.action)}>{humanize(e.action)}</Badge>
                    <span className="text-xs text-ink-400">{fmtDateTime(e.createdAt)}</span>
                  </div>
                  <div className="mt-1.5 text-sm text-ink-200">
                    <span className="text-white">{e.userEmail ?? (e.userId ? `User ${e.userId}` : 'Not signed in')}</span>
                    {e.entityType && <span className="text-ink-400"> · {humanize(e.entityType)}{e.entityId ? ` ${e.entityId}` : ''}</span>}
                  </div>
                  {e.details && <div className="mt-0.5 break-words text-xs text-ink-400">{e.details}</div>}
                  {e.ipAddress && <div className="mt-0.5 text-xs text-ink-500">{e.ipAddress}</div>}
                </li>
              ))}
            </ul>
            <div className="flex items-center justify-between border-t border-ink-700/60 px-4 py-3 text-sm text-ink-300">
              <Button variant="ghost" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>Newer</Button>
              <span>Page {p.page + 1} of {Math.max(p.totalPages, 1)} · {p.totalElements} events</span>
              <Button variant="ghost" size="sm" disabled={page + 1 >= p.totalPages} onClick={() => setPage(page + 1)}>Older</Button>
            </div>
          </Card>
        )}
      </Async>
      <p className="mt-3 text-xs text-ink-500">Events show who did what and from which address. Passwords and tokens are never recorded.</p>
    </>
  );
}
