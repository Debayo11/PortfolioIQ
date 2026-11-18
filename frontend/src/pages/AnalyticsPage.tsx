import { useMemo, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { api } from '../api/endpoints';
import type { Asset, AssetPerformance, PerformanceReport, RangeCode } from '../api/types';
import { Async } from '../components/Async';
import { AssetPicker } from '../components/AssetPicker';
import { AllocationDonut, IndexChart, PALETTE, ValueChart } from '../components/charts/charts';
import { Badge, Button, Card, EmptyState, Money, Notice, PageHeader, Pct, Segmented, SectionTitle, SelectField, SimulatedBadge, Skeleton, Stat, Warnings } from '../components/ui';
import { fmtDate, fmtNumber, fmtPercent, humanize, tone, toneClass } from '../lib/format';
import { DAILY_RANGE_OPTIONS } from '../lib/hooks';
import { cn } from '../lib/cn';

function noteFor(r: PerformanceReport, metric: string): string | undefined {
  return r.notes.find((n) => n.metric === metric)?.explanation;
}

function Hint({ text }: { text?: string }) {
  return text ? <p className="mt-2 text-xs leading-relaxed text-ink-400">{text}</p> : null;
}

function PerformanceSection({ r, benchmarkSelected }: { r: PerformanceReport; benchmarkSelected: boolean }) {
  const c = r.currency;
  const lines = [{ name: 'Portfolio', color: PALETTE[0], data: r.series.map((p) => ({ date: p.date, index: p.performanceIndex })) }];
  if (r.benchmark) lines.push({ name: r.benchmark.symbol, color: PALETTE[1], data: r.benchmark.series });
  return (
    <div className="space-y-4">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Time-weighted return" tone={tone(r.window.timeWeightedReturnPercent)} value={fmtPercent(r.window.timeWeightedReturnPercent, true)} sub={`${fmtDate(r.from)} to ${fmtDate(r.to)}`} />
        <Stat label="Value at start" value={<Money value={r.window.startValue} currency={c} />} />
        <Stat label="Value at end" value={<Money value={r.window.endValue} currency={c} />} />
        <Stat label="Money added" value={<Money value={r.window.netContributions} currency={c} signed />} sub="Net buys less sales" />
      </div>
      <Hint text={noteFor(r, 'timeWeightedReturnPercent')} />
      {r.series.length > 1 ? (
        <Card>
          <SectionTitle>Value and cost basis</SectionTitle>
          <ValueChart series={r.series} currency={c} />
        </Card>
      ) : <Card><EmptyState title="Not enough history yet" text="Record transactions to build a performance history." /></Card>}
      {r.benchmark && (
        <Card>
          <SectionTitle>Versus {r.benchmark.symbol}</SectionTitle>
          <IndexChart lines={lines} />
          <div className="mt-3 grid grid-cols-3 gap-3">
            <Stat label="Portfolio" tone={tone(r.window.timeWeightedReturnPercent)} value={fmtPercent(r.window.timeWeightedReturnPercent, true)} />
            <Stat label={r.benchmark.symbol} tone={tone(r.benchmark.returnPercent)} value={fmtPercent(r.benchmark.returnPercent, true)} />
            <Stat label="Difference" tone={tone(r.benchmark.excessReturnPercent)} value={fmtPercent(r.benchmark.excessReturnPercent, true)} />
          </div>
          <Hint text={noteFor(r, 'benchmark')} />
        </Card>
      )}
      {benchmarkSelected && !r.benchmark && <Notice tone="warn">No benchmark comparison could be shown. See the notes below.</Notice>}
    </div>
  );
}

function RiskSection({ r }: { r: PerformanceReport }) {
  const k = r.risk;
  if (k.returnObservations === 0) return <Card><EmptyState title="No risk figures yet" text="Risk is measured once your holdings have a few days of price history." /></Card>;
  return (
    <Card>
      <SectionTitle>Risk</SectionTitle>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Volatility (annual)" value={fmtPercent(k.annualizedVolatilityPercent)} sub={k.annualizedVolatilityPercent === null ? 'Needs 2+ days' : undefined} />
        <Stat label="Max drawdown" tone={tone(k.maxDrawdownPercent)} value={fmtPercent(k.maxDrawdownPercent)} sub={k.drawdownPeakDate ? `${fmtDate(k.drawdownPeakDate)} to ${fmtDate(k.drawdownTroughDate)}` : 'No fall in this period'} />
        <Stat label="Best day" tone="gain" value={fmtPercent(k.bestDayPercent, true)} sub={fmtDate(k.bestDay)} />
        <Stat label="Worst day" tone="loss" value={fmtPercent(k.worstDayPercent, true)} sub={fmtDate(k.worstDay)} />
      </div>
      <div className="mt-3 grid gap-2 sm:grid-cols-2">
        <Hint text={noteFor(r, 'annualizedVolatilityPercent')} />
        <Hint text={noteFor(r, 'maxDrawdownPercent')} />
      </div>
      <p className="mt-2 text-xs text-ink-400">Based on {k.returnObservations} daily returns; {fmtPercent(k.positiveDaysPercent)} of days were positive.</p>
    </Card>
  );
}

function LifetimeSection({ r }: { r: PerformanceReport }) {
  const l = r.lifetime;
  const c = r.currency;
  return (
    <Card>
      <SectionTitle>Realized versus unrealized</SectionTitle>
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Realized gains" tone={tone(l.realizedPnl)} value={<Money value={l.realizedPnl} currency={c} signed />} sub="From positions sold" />
        <Stat label="Unrealized gains" tone={tone(l.unrealizedPnl)} value={<Money value={l.unrealizedPnl} currency={c} signed />} sub="On open positions" />
        <Stat label="Dividend income" tone={tone(l.dividendIncome)} value={<Money value={l.dividendIncome} currency={c} signed />} />
        <Stat label="Simple ROI" tone={tone(l.simpleRoiPercent)} value={fmtPercent(l.simpleRoiPercent, true)} sub={<Money value={l.totalReturn} currency={c} signed />} />
      </div>
      <Hint text={noteFor(r, 'realizedVsUnrealized')} />
      <Hint text={noteFor(r, 'simpleRoiPercent')} />
    </Card>
  );
}

function AllocationSection({ scope, currency }: { scope: string; currency: string }) {
  const q = useQuery({
    queryKey: ['allocation', scope, currency],
    queryFn: () => api.analytics.allocation({ portfolioId: scope ? Number(scope) : undefined, currency: scope ? undefined : currency || undefined }),
    placeholderData: keepPreviousData,
  });
  return (
    <Async query={q} skeleton={<Skeleton className="h-64" />}>
      {(a) => {
        const d = a.diversification;
        const levelTone = d.level === 'DIVERSIFIED' ? 'gain' : d.level === 'MODERATE' ? 'info' : d.level === 'CONCENTRATED' ? 'warn' : 'neutral';
        return (
          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <SectionTitle>Allocation</SectionTitle>
              {a.byAsset.length === 0 ? <EmptyState title="No open positions" /> : <AllocationDonut slices={a.byAsset} currency={a.currency} />}
              {a.byAssetType.length > 1 && <div className="mt-4 border-t border-ink-700/60 pt-4"><div className="label">By asset type</div><AllocationDonut slices={a.byAssetType} currency={a.currency} /></div>}
              <div className="mt-3"><Warnings items={a.warnings} /></div>
            </Card>
            <Card>
              <SectionTitle action={<Badge tone={levelTone}>{humanize(d.level)}</Badge>}>Diversification</SectionTitle>
              <div className="grid grid-cols-2 gap-3">
                <Stat label="Holdings" value={fmtNumber(d.holdings, 0)} />
                <Stat label="Effective holdings" value={d.effectiveHoldings === null ? '—' : fmtNumber(d.effectiveHoldings, 2)} />
                <Stat label="Largest holding" value={fmtPercent(d.topWeightPercent)} />
                <Stat label="Top 3 holdings" value={fmtPercent(d.top3WeightPercent)} />
              </div>
              <p className="mt-3 text-sm text-ink-200">{d.explanation}</p>
              <p className="mt-2 text-xs text-ink-400">Effective holdings is how many equally sized positions would give the same concentration. A single holding scores 1.</p>
            </Card>
          </div>
        );
      }}
    </Async>
  );
}

function AssetsSection({ scope, currency, ccy }: { scope: string; currency: string; ccy: string }) {
  const q = useQuery({
    queryKey: ['asset-performance', scope, currency],
    queryFn: () => api.analytics.assets({ portfolioId: scope ? Number(scope) : undefined, currency: scope ? undefined : currency || undefined }),
  });
  return (
    <Async query={q} skeleton={<Skeleton className="h-40" />}>
      {(rows: AssetPerformance[]) => {
        const max = Math.max(1, ...rows.map((r) => Math.abs(r.returnPercent ?? 0)));
        return (
          <Card>
            <SectionTitle>Investment performance comparison</SectionTitle>
            {rows.length === 0 ? <EmptyState title="No investments yet" /> : (
              <ul className="space-y-4">
                {rows.map((r) => (
                  <li key={r.assetId}>
                    <div className="flex items-center justify-between gap-3 text-sm">
                      <span className="min-w-0"><span className="font-semibold text-white">{r.symbol}</span> {!r.open && <Badge>Closed</Badge>}</span>
                      <span className="tabular flex items-center gap-3"><Money value={r.totalReturn} currency={ccy} signed colored /><span className={cn('w-20 text-right', toneClass(r.returnPercent))}>{fmtPercent(r.returnPercent, true)}</span></span>
                    </div>
                    <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-ink-700" aria-hidden>
                      <div className={cn('h-full rounded-full', (r.returnPercent ?? 0) >= 0 ? 'bg-gain' : 'bg-loss')} style={{ width: `${(Math.abs(r.returnPercent ?? 0) / max) * 100}%` }} />
                    </div>
                    <div className="mt-1 text-xs text-ink-400">Realized <Money value={r.realizedPnl} currency={ccy} signed /> · Unrealized <Money value={r.unrealizedPnl} currency={ccy} signed /> · Dividends <Money value={r.dividendIncome} currency={ccy} signed /></div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        );
      }}
    </Async>
  );
}

function CompareSection({ range }: { range: RangeCode }) {
  const portfolios = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios.list });
  const [picked, setPicked] = useState<number[]>([]);
  const toggle = (id: number) => setPicked(picked.includes(id) ? picked.filter((x) => x !== id) : picked.length < 5 ? [...picked, id] : picked);
  const compare = useQuery({
    queryKey: ['compare', picked.join(','), range], queryFn: () => api.analytics.compare(picked, range), enabled: picked.length >= 2, placeholderData: keepPreviousData,
  });
  const list = portfolios.data ?? [];
  if (list.length < 2) return null;
  return (
    <Card>
      <SectionTitle>Compare portfolios</SectionTitle>
      <div className="flex flex-wrap gap-2">
        {list.map((p) => (
          <button key={p.id} type="button" aria-pressed={picked.includes(p.id)} onClick={() => toggle(p.id)}
            className={cn('rounded-full border px-3 py-1.5 text-sm transition', picked.includes(p.id) ? 'border-emerald-400/60 bg-emerald-400/10 text-emerald-200' : 'border-ink-600 text-ink-300 hover:text-white')}>{p.name}</button>
        ))}
      </div>
      {picked.length < 2 ? <p className="mt-3 text-sm text-ink-400">Pick two to five portfolios to compare their returns on the same scale.</p> : (
        <div className="mt-4">
          <Async query={compare} skeleton={<Skeleton className="h-64" />}>
            {(c) => (
              <>
                <IndexChart lines={c.portfolios.map((p, i) => ({ name: p.name, color: PALETTE[i % PALETTE.length], data: p.series }))} />
                <div className="mt-3 overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead><tr className="text-left text-xs uppercase tracking-wide text-ink-400"><th className="pb-2 font-medium">Portfolio</th><th className="pb-2 text-right font-medium">Return</th><th className="pb-2 text-right font-medium">Volatility</th><th className="pb-2 text-right font-medium">Max drawdown</th></tr></thead>
                    <tbody className="divide-y divide-ink-700/60">
                      {c.portfolios.map((p) => (
                        <tr key={p.portfolioId} className="tabular"><td className="py-2.5 text-white">{p.name}</td><td className="py-2.5 text-right"><Pct value={p.timeWeightedReturnPercent} /></td><td className="py-2.5 text-right text-ink-200">{fmtPercent(p.annualizedVolatilityPercent)}</td><td className="py-2.5 text-right text-ink-200">{fmtPercent(p.maxDrawdownPercent)}</td></tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                <div className="mt-3"><Warnings items={c.warnings} /></div>
              </>
            )}
          </Async>
        </div>
      )}
    </Card>
  );
}

export function AnalyticsPage() {
  const portfolios = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios.list });
  const [scope, setScope] = useState('');
  const [currency, setCurrency] = useState('');
  const [range, setRange] = useState<RangeCode>('3M');
  const [benchmark, setBenchmark] = useState<Asset | null>(null);
  const list = portfolios.data ?? [];
  const currencies = useMemo(() => Array.from(new Set(list.map((p) => p.baseCurrency))).sort(), [list]);

  const perf = useQuery({
    queryKey: ['performance', scope, currency, range, benchmark?.id ?? 0],
    queryFn: () => api.analytics.performance({ portfolioId: scope ? Number(scope) : undefined, currency: scope ? undefined : currency || undefined, range, benchmarkAssetId: benchmark?.id }),
    enabled: list.length > 0,
    placeholderData: keepPreviousData,
  });

  if (portfolios.isSuccess && list.length === 0) {
    return <><PageHeader title="Analytics" /><Card><EmptyState title="Nothing to analyse yet" text="Create a portfolio and record transactions to see returns, risk and allocation." /></Card></>;
  }

  return (
    <>
      <PageHeader title="Analytics" subtitle="Historical performance, risk and diversification" />
      <div className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <SelectField label="Scope" value={scope} onChange={(e) => setScope(e.target.value)}>
          <option value="">All portfolios combined</option>
          {list.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </SelectField>
        {!scope && currencies.length > 1 && (
          <SelectField label="Currency" value={currency || perf.data?.currency || ''} onChange={(e) => setCurrency(e.target.value)}>
            {currencies.map((c) => <option key={c} value={c}>{c}</option>)}
          </SelectField>
        )}
        <div className="sm:col-span-2">
          <AssetPicker label="Benchmark (optional)" value={benchmark} onChange={setBenchmark} />
        </div>
      </div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <Segmented ariaLabel="Time range" value={range} options={DAILY_RANGE_OPTIONS} onChange={setRange} />
        {perf.data?.dataQuality.pricesSimulated && <SimulatedBadge />}
      </div>

      <Async query={perf} skeleton={<div className="space-y-4"><Skeleton className="h-28" /><Skeleton className="h-72" /></div>}>
        {(r) => (
          <div className="space-y-4">
            {r.excludedPortfolios.length > 0 && <Notice>Showing {r.currency} portfolios only. Not included: {r.excludedPortfolios.map((p) => `${p.name} (${p.currency})`).join(', ')}.</Notice>}
            <PerformanceSection r={r} benchmarkSelected={benchmark !== null} />
            <RiskSection r={r} />
            <LifetimeSection r={r} />
            <AllocationSection scope={scope} currency={currency} />
            <AssetsSection scope={scope} currency={currency} ccy={r.currency} />
            <CompareSection range={range} />
            <Warnings items={r.dataQuality.warnings} />
            <Card>
              <SectionTitle>How to read these numbers</SectionTitle>
              <dl className="space-y-3 text-sm">
                {r.notes.map((n) => (<div key={n.metric}><dt className="font-medium text-white">{humanize(n.metric.replace(/([A-Z])/g, ' $1').replace(/ percent$/i, ''))}</dt><dd className="text-ink-300">{n.explanation}</dd></div>))}
              </dl>
            </Card>
            <p className="text-center text-xs text-ink-500">{r.disclaimer}</p>
          </div>
        )}
      </Async>
    </>
  );
}
