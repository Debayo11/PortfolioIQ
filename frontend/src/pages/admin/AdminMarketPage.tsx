import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { CheckCircle2, RefreshCw, XCircle } from 'lucide-react';
import { api } from '../../api/endpoints';
import type { MarketAdminView, RefreshResult } from '../../api/types';
import { Async } from '../../components/Async';
import { Badge, Button, Card, ErrorNote, Notice, SectionTitle, SimulatedBadge, Skeleton, Stat, TextField } from '../../components/ui';
import { fmtDateTime, fmtDuration, fmtNumber } from '../../lib/format';

function Result({ r }: { r: RefreshResult }) {
  return (
    <div className="rounded-xl border border-ink-700 bg-ink-900/60 p-3 text-sm">
      <div className="flex items-center gap-2">
        {r.skipped ? <Badge tone="warn">Skipped</Badge> : r.failed > 0 ? <Badge tone="warn">Finished with errors</Badge> : <Badge tone="gain">Completed</Badge>}
        <span className="text-xs text-ink-400">{fmtDateTime(r.startedAt)}</span>
      </div>
      {!r.skipped && <p className="mt-2 text-ink-200">{r.succeeded} of {r.attempted} assets refreshed in {fmtNumber(r.durationMs, 0)} ms ({r.tracked} tracked){r.failed > 0 ? `, ${r.failed} failed` : ''}.</p>}
      {r.note && <p className="mt-1 text-xs text-ink-400">{r.note}</p>}
    </div>
  );
}

function Controls({ view }: { view: MarketAdminView }) {
  const qc = useQueryClient();
  const [enabled, setEnabled] = useState(view.refreshEnabled);
  const [max, setMax] = useState(String(view.maxRequestsPerRefresh));
  const [maxError, setMaxError] = useState('');
  const [manual, setManual] = useState<RefreshResult | null>(null);
  const save = useMutation({
    mutationFn: (body: { refreshEnabled: boolean; maxRequestsPerRefresh: number }) => api.admin.updateMarket(body),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['admin-market'] }); },
  });
  const refresh = useMutation({
    mutationFn: () => api.admin.refreshMarket(),
    onSuccess: async (r) => { setManual(r); await qc.invalidateQueries({ queryKey: ['admin-market'] }); },
  });
  const n = Number(max);
  const valid = Number.isInteger(n) && n >= 1 && n <= 500;
  const dirty = enabled !== view.refreshEnabled || n !== view.maxRequestsPerRefresh;

  return (
    <Card>
      <SectionTitle>Refresh controls</SectionTitle>
      <div className="space-y-4">
        <label className="flex cursor-pointer items-start gap-3 rounded-xl border border-ink-700 bg-ink-900/60 p-3">
          <input type="checkbox" className="mt-1 h-4 w-4 accent-emerald-400" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />
          <span><span className="block text-sm font-medium text-white">Refresh prices automatically</span><span className="block text-xs text-ink-400">When off, prices and alerts only update when you run a refresh below.</span></span>
        </label>
        <TextField label="Assets per refresh (1 to 500)" inputMode="numeric" value={max} error={maxError} hint="Free data plans allow roughly 60 requests a minute"
          onChange={(e) => { setMax(e.target.value); setMaxError(''); }} />
        <ErrorNote error={save.error} />
        <div className="flex flex-wrap gap-2">
          <Button loading={save.isPending} disabled={!dirty} onClick={() => {
            if (!valid) { setMaxError('Enter a whole number from 1 to 500'); return; }
            save.mutate({ refreshEnabled: enabled, maxRequestsPerRefresh: n });
          }}>Save settings</Button>
          <Button variant="secondary" icon={<RefreshCw className="h-4 w-4" />} loading={refresh.isPending} onClick={() => refresh.mutate()}>Refresh now</Button>
        </div>
        <ErrorNote error={refresh.error} />
        {manual && <Result r={manual} />}
      </div>
    </Card>
  );
}

export function AdminMarketPage() {
  const view = useQuery({ queryKey: ['admin-market'], queryFn: api.admin.market, refetchInterval: 30_000 });
  return (
    <Async query={view} skeleton={<Skeleton className="h-72" />}>
      {(v) => (
        <div className="space-y-4">
          <Card>
            <SectionTitle action={v.simulated ? <SimulatedBadge /> : undefined}>Data source</SectionTitle>
            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <Stat label="Provider" value={v.provider} />
              <Stat label="API key" value={v.apiKeyConfigured ? <span className="flex items-center gap-1.5"><CheckCircle2 className="h-4 w-4 text-gain" aria-hidden />Configured</span> : <span className="flex items-center gap-1.5 text-ink-300"><XCircle className="h-4 w-4" aria-hidden />Not set</span>} sub="The key is never shown" />
              <Stat label="Refresh interval" value={fmtDuration(v.refreshIntervalMs / 1000)} sub="Set by environment" />
              <Stat label="Quote cache" value={`${v.cacheTtlSeconds}s`} sub="Redis lifetime" />
              <Stat label="Tracked assets" value={fmtNumber(v.trackedAssets, 0)} sub="Held, watched or alerted" />
              <Stat label="Stored prices" value={fmtNumber(v.storedPriceRows, 0)} sub="History rows" />
            </div>
            <div className="mt-4"><Notice>{v.note}</Notice></div>
          </Card>
          <Controls key={`${v.refreshEnabled}-${v.maxRequestsPerRefresh}`} view={v} />
          <Card>
            <SectionTitle>Last scheduled or manual refresh on this server</SectionTitle>
            {v.lastRefresh ? <Result r={v.lastRefresh} /> : <p className="text-sm text-ink-400">No refresh has run since this server started.</p>}
          </Card>
        </div>
      )}
    </Async>
  );
}
