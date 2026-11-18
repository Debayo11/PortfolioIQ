import { useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ArrowLeft, BellPlus, Plus, Search, Star } from 'lucide-react';
import { api } from '../api/endpoints';
import type { Asset, Quote, RangeCode } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { Async } from '../components/Async';
import { PriceChart } from '../components/charts/charts';
import { MarketTabs } from '../components/layout/AppShell';
import { AlertModal, AssetModal, NoAssetsNotice, WatchlistPickModal } from '../components/market/Modals';
import { Badge, Button, Card, EmptyState, Money, Notice, PageHeader, Pct, Segmented, SectionTitle, SimulatedBadge, Skeleton, Stat } from '../components/ui';
import { fmtAgo, fmtCompact, fmtDateTime, humanize } from '../lib/format';
import { RANGE_OPTIONS, useDebounced } from '../lib/hooks';

export function MarketPage() {
  const { hasRole } = useAuth();
  const [text, setText] = useState('');
  const q = useDebounced(text.trim(), 300);
  const [adding, setAdding] = useState(false);
  const status = useQuery({ queryKey: ['market-status'], queryFn: api.market.status, staleTime: 5 * 60_000 });
  const assets = useQuery({ queryKey: ['assets', q], queryFn: () => api.assets.search(q, 0, 30), placeholderData: keepPreviousData });
  const ids = assets.data?.content.map((a) => a.id) ?? [];
  const quotes = useQuery({
    queryKey: ['quotes', ids.join(',')], queryFn: () => api.market.quotes(ids), enabled: ids.length > 0, refetchInterval: 60_000,
  });
  const byId = new Map<number, Quote>((quotes.data ?? []).map((x) => [x.assetId, x]));
  const canAdd = hasRole('ADMINISTRATOR', 'ANALYST');

  return (
    <>
      <MarketTabs />
      <PageHeader title="Market" subtitle="Search assets and follow their prices"
        actions={canAdd ? <Button icon={<Plus className="h-4 w-4" />} onClick={() => setAdding(true)}>Add asset</Button> : undefined} />
      {status.data && <div className="mb-4"><Notice tone={status.data.simulated ? 'warn' : 'info'}>{status.data.message}</Notice></div>}
      <div className="relative mb-4">
        <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" aria-hidden />
        <input className="field pl-10" placeholder="Search by symbol or name" aria-label="Search assets" value={text} onChange={(e) => setText(e.target.value)} />
      </div>
      <Async query={assets} skeleton={<Skeleton className="h-64" />}>
        {(page) => page.content.length === 0 ? (
          <Card><EmptyState title={q ? 'No matches' : 'No assets yet'} text={q ? 'Try a different symbol or name.' : undefined} action={!q && !canAdd ? <NoAssetsNotice /> : undefined} /></Card>
        ) : (
          <Card className="p-0 sm:p-0">
            <ul className="divide-y divide-ink-700/60">
              {page.content.map((a) => {
                const quote = byId.get(a.id);
                return (
                  <li key={a.id}>
                    <Link to={`/market/${a.id}`} className="flex items-center justify-between gap-3 px-4 py-3.5 hover:bg-ink-800/60 sm:px-5">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2"><span className="font-semibold text-white">{a.symbol}</span><Badge>{humanize(a.assetType)}</Badge></div>
                        <div className="truncate text-sm text-ink-400">{a.name}</div>
                      </div>
                      <div className="shrink-0 text-right">
                        {quote ? (<><div className="tabular font-medium text-white"><Money value={quote.price} currency={a.currency} /></div><div className="text-sm"><Pct value={quote.changePercent} /></div></>) : <span className="text-sm text-ink-500">No price</span>}
                      </div>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </Card>
        )}
      </Async>
      <AssetModal open={adding} onClose={() => setAdding(false)} />
    </>
  );
}

function AssetBody({ asset }: { asset: Asset }) {
  const [range, setRange] = useState<RangeCode>('30D');
  const [watch, setWatch] = useState(false);
  const [alert, setAlert] = useState(false);
  const quote = useQuery({ queryKey: ['quote', asset.id], queryFn: () => api.market.quote(asset.id), refetchInterval: 60_000 });
  const history = useQuery({ queryKey: ['history', asset.id, range], queryFn: () => api.market.history(asset.id, range), placeholderData: keepPreviousData });

  return (
    <>
      <Link to="/market" className="mb-3 inline-flex items-center gap-1.5 text-sm text-ink-300 hover:text-white"><ArrowLeft className="h-4 w-4" aria-hidden /> Market</Link>
      <PageHeader title={asset.symbol} subtitle={<span className="flex flex-wrap items-center gap-2">{asset.name}<Badge>{humanize(asset.assetType)}</Badge>{asset.exchange && <Badge>{asset.exchange}</Badge>}</span>}
        actions={<>
          <Button variant="secondary" icon={<Star className="h-4 w-4" />} onClick={() => setWatch(true)}>Watch</Button>
          <Button variant="secondary" icon={<BellPlus className="h-4 w-4" />} onClick={() => setAlert(true)}>Alert</Button>
        </>} />
      <div className="space-y-4">
        <Async query={quote} skeleton={<Skeleton className="h-36" />}>
          {(qt) => (
            <Card>
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-medium uppercase tracking-wide text-ink-400">Last price</span>
                <span className="flex gap-2">{qt.simulated && <SimulatedBadge />}{qt.stale && <Badge tone="warn">Stale</Badge>}</span>
              </div>
              <div className="tabular mt-1 text-4xl font-semibold text-white"><Money value={qt.price} currency={asset.currency} /></div>
              <div className="mt-1 text-sm"><Money value={qt.change} currency={asset.currency} signed colored /> <Pct value={qt.changePercent} /> <span className="text-ink-400">vs previous close</span></div>
              <div className="mt-4 grid grid-cols-2 gap-3 lg:grid-cols-4">
                <Stat label="Previous close" value={<Money value={qt.previousClose} currency={asset.currency} />} />
                <Stat label="Volume" value={qt.volume === null ? '—' : fmtCompact(qt.volume)} />
                <Stat label="Market cap" value={qt.marketCap === null ? '—' : fmtCompact(qt.marketCap)} />
                <Stat label="Updated" value={fmtAgo(qt.asOf)} sub={`${qt.source} · ${fmtDateTime(qt.asOf)}`} />
              </div>
            </Card>
          )}
        </Async>
        <Card>
          <SectionTitle action={<Segmented ariaLabel="Time range" value={range} options={RANGE_OPTIONS} onChange={setRange} />}>Price history</SectionTitle>
          <Async query={history} skeleton={<Skeleton className="h-64" />}>
            {(h) => h.points.length < 2 ? (
              <EmptyState title="Not enough history yet" text={h.simulated ? 'Try a longer range.' : 'Real price history builds up from when this app first saw the asset. Check back later.'} />
            ) : (
              <>
                <PriceChart points={h.points} currency={asset.currency} intraday={range === '24H'} />
                <p className="mt-2 text-xs text-ink-400">Source: {h.source}{h.simulated ? ' (simulated development data)' : ''}. Past prices do not predict future results.</p>
              </>
            )}
          </Async>
        </Card>
        <p className="text-center text-xs text-ink-500">Volume and market cap are shown only when the data source provides them.</p>
      </div>
      <WatchlistPickModal open={watch} asset={asset} onClose={() => setWatch(false)} />
      <AlertModal open={alert} asset={asset} onClose={() => setAlert(false)} />
    </>
  );
}

export function AssetDetailPage() {
  const { assetId } = useParams();
  const id = Number(assetId);
  const asset = useQuery({ queryKey: ['asset', id], queryFn: () => api.assets.get(id), enabled: Number.isInteger(id) });
  if (!Number.isInteger(id)) return <Navigate to="/market" replace />;
  return <Async query={asset} skeleton={<Skeleton className="h-64" />}>{(a) => <AssetBody asset={a} />}</Async>;
}
