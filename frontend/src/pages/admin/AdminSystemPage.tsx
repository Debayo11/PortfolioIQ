import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RefreshCw } from 'lucide-react';
import { api } from '../../api/endpoints';
import { Async } from '../../components/Async';
import { Badge, Button, Card, EmptyState, Notice, SectionTitle, SelectField, Skeleton, Stat, TextField } from '../../components/ui';
import { fmtDateTime, fmtDuration, fmtNumber } from '../../lib/format';
import { useDebounced } from '../../lib/hooks';

function Logs() {
  const [level, setLevel] = useState('WARN');
  const [text, setText] = useState('');
  const q = useDebounced(text.trim(), 300);
  const logs = useQuery({ queryKey: ['admin-logs', level, q], queryFn: () => api.admin.logs({ level, q: q || undefined, limit: 100 }), refetchInterval: 30_000 });
  return (
    <Card>
      <SectionTitle action={<Button variant="ghost" size="sm" icon={<RefreshCw className="h-4 w-4" />} onClick={() => { void logs.refetch(); }}>Refresh</Button>}>System log</SectionTitle>
      <div className="mb-3 grid gap-3 sm:grid-cols-3">
        <SelectField label="Level" value={level} onChange={(e) => setLevel(e.target.value)}><option value="WARN">Warnings and errors</option><option value="ERROR">Errors only</option></SelectField>
        <TextField className="sm:col-span-2" label="Search" value={text} onChange={(e) => setText(e.target.value)} placeholder="Filter by message or component" />
      </div>
      <Async query={logs} skeleton={<Skeleton className="h-48" />}>
        {(entries) => entries.length === 0 ? <EmptyState title="No warnings or errors" text="The most recent 500 warnings and errors since the server started would appear here." /> : (
          <ul className="divide-y divide-ink-700/60">
            {entries.map((e, i) => (
              <li key={`${e.time}-${i}`} className="py-3">
                <div className="flex flex-wrap items-center gap-2">
                  <Badge tone={e.level === 'ERROR' ? 'loss' : 'warn'}>{e.level}</Badge>
                  <span className="text-xs text-ink-400">{fmtDateTime(e.time)} · {e.logger}</span>
                  {e.exception && <span className="text-xs text-ink-500">{e.exception.split('.').pop()}</span>}
                </div>
                <div className="mt-1 break-words text-sm text-ink-100">{e.message}</div>
              </li>
            ))}
          </ul>
        )}
      </Async>
      <p className="mt-3 text-xs text-ink-500">Kept in memory on this server and cleared on restart. Tokens, passwords and keys are masked, and stack traces are never shown.</p>
    </Card>
  );
}

function Usage() {
  const [filter, setFilter] = useState('');
  const usage = useQuery({ queryKey: ['admin-usage'], queryFn: api.admin.apiUsage, refetchInterval: 30_000 });
  return (
    <Card>
      <SectionTitle>API usage</SectionTitle>
      <Async query={usage} skeleton={<Skeleton className="h-48" />}>
        {(u) => {
          const rows = u.endpoints.filter((e) => `${e.method} ${e.uri}`.toLowerCase().includes(filter.toLowerCase()));
          return (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                <Stat label="Requests" value={fmtNumber(u.totalRequests, 0)} sub={`Up ${fmtDuration(u.uptimeSeconds)}`} />
                <Stat label="Client errors (4xx)" value={fmtNumber(u.clientErrors, 0)} />
                <Stat label="Server errors (5xx)" value={fmtNumber(u.serverErrors, 0)} tone={u.serverErrors > 0 ? 'loss' : undefined} />
                <Stat label="Rate-limited" value={fmtNumber(u.rateLimitRejections, 0)} tone={u.rateLimitRejections > 0 ? 'loss' : undefined} />
              </div>
              <TextField label="Filter endpoints" value={filter} onChange={(e) => setFilter(e.target.value)} placeholder="For example /api/portfolios" />
              {rows.length === 0 ? <p className="text-sm text-ink-400">No endpoints match.</p> : (
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead><tr className="text-left text-xs uppercase tracking-wide text-ink-400"><th className="pb-2 font-medium">Endpoint</th><th className="pb-2 text-right font-medium">Requests</th><th className="pb-2 text-right font-medium">4xx</th><th className="pb-2 text-right font-medium">5xx</th><th className="pb-2 text-right font-medium">Avg ms</th><th className="pb-2 text-right font-medium">Max ms</th></tr></thead>
                    <tbody className="divide-y divide-ink-700/60">
                      {rows.map((e) => (
                        <tr key={`${e.method} ${e.uri}`} className="tabular">
                          <td className="py-2.5 pr-3"><Badge>{e.method}</Badge> <span className="break-all text-ink-100">{e.uri}</span></td>
                          <td className="py-2.5 text-right text-white">{fmtNumber(e.requests, 0)}</td>
                          <td className="py-2.5 text-right text-ink-200">{e.clientErrors}</td>
                          <td className={`py-2.5 text-right ${e.serverErrors > 0 ? 'text-loss' : 'text-ink-200'}`}>{e.serverErrors}</td>
                          <td className="py-2.5 text-right text-ink-200">{fmtNumber(e.avgMillis, 1)}</td>
                          <td className="py-2.5 text-right text-ink-200">{fmtNumber(e.maxMillis, 1)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              <Notice>{u.note}</Notice>
            </div>
          );
        }}
      </Async>
    </Card>
  );
}

export function AdminSystemPage() {
  return <div className="space-y-4"><Usage /><Logs /></div>;
}
