import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ArrowDownRight, ArrowUpRight } from 'lucide-react';
import { api } from '../api/endpoints';
import type { AssetPerformance, Dashboard, RangeCode } from '../api/types';
import { Async } from '../components/Async';
import { AllocationDonut, ValueChart } from '../components/charts/charts';
import { Badge, Button, Card, EmptyState, Money, Notice, PageHeader, Pct, Segmented, SectionTitle, SelectField, SimulatedBadge, Skeleton, Stat, Warnings } from '../components/ui';
import { fmtDate, fmtNumber, fmtPercent, humanize, tone, toneClass } from '../lib/format';
import { RANGE_OPTIONS } from '../lib/hooks';
import { cn } from '../lib/cn';

function Performer({ title, asset, currency }: { title: string; asset: AssetPerformance | null; currency: string }) {
  return (
    <Stat label={title} tone={tone(asset?.returnPercent)}
      value={asset ? asset.symbol : '—'}
      sub={asset ? <span><Pct value={asset.returnPercent} /> · <Money value={asset.totalReturn} currency={currency} signed colored /></span> : 'No history yet'} />
  );
}

function Movers({ data }: { data: Dashboard }) {
  const max = Math.max(1, ...data.movers.map((m) => Math.abs(m.changePercent)));
  if (data.movers.length === 0) return <EmptyState title="No price moves yet" text="Today's movers appear here once your holdings have prices." />;
  return (
    <ul className="space-y-3">
      {data.movers.map((m) => (
        <li key={m.assetId}>
          <Link to={`/market/${m.assetId}`} className="block">
            <div className="flex items-center justify-between text-sm">
              <span className="font-medium text-white">{m.symbol}</span>
              <span className="tabular flex items-center gap-1.5">
                <span className="text-ink-300"><Money value={m.price} currency={data.currency} /></span>
                <span className={toneClass(m.changePercent)}>{fmtPercent(m.changePercent, true)}</span>
              </span>
            </div>
            <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-ink-700" aria-hidden>
              <div className={cn('h-full rounded-full', m.changePercent >= 0 ? 'bg-gain' : 'bg-loss')} style={{ width: `${(Math.abs(m.changePercent) / max) * 100}%` }} />
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function DashboardPage() {
  const [range, setRange] = useState<RangeCode>('30D');
  const [currency, setCurrency] = useState<string>('');
  const portfolios = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios.list });
  const currencies = useMemo(() => Array.from(new Set((portfolios.data ?? []).map((p) => p.baseCurrency))).sort(), [portfolios.data]);
  const hasPortfolios = (portfolios.data?.length ?? 0) > 0;

  const dash = useQuery({
    queryKey: ['dashboard', currency || 'auto', range],
    queryFn: () => api.analytics.dashboard({ currency: currency || undefined, range }),
    enabled: hasPortfolios,
    placeholderData: keepPreviousData,
    refetchInterval: 120_000,
  });

  if (portfolios.isSuccess && !hasPortfolios) {
    return (
      <>
        <PageHeader title="Dashboard" />
        <Card><EmptyState title="Create your first portfolio" text="Add a portfolio, record your investments and this dashboard fills with value, returns and risk."
          action={<Link to="/portfolios"><Button>Create a portfolio</Button></Link>} /></Card>
      </>
    );
  }

  return (
    <>
      <PageHeader title="Dashboard" subtitle="Your investments at a glance"
        actions={currencies.length > 1 ? (
          <SelectField label="Currency" value={currency || dash.data?.currency || ''} onChange={(e) => setCurrency(e.target.value)} className="w-32">
            {currencies.map((c) => <option key={c} value={c}>{c}</option>)}
          </SelectField>
        ) : undefined} />
      <Async query={dash} skeleton={<div className="space-y-4"><Skeleton className="h-40" /><Skeleton className="h-24" /><Skeleton className="h-72" /></div>}>
        {(d) => (
          <div className="space-y-4">
            {d.excludedPortfolios.length > 0 && (
              <Notice>Showing {d.currency} portfolios only. Not included: {d.excludedPortfolios.map((p) => `${p.name} (${p.currency})`).join(', ')}.</Notice>
            )}
            <Card className="relative overflow-hidden">
              <div className="pointer-events-none absolute -right-10 -top-10 h-44 w-44 rounded-full bg-emerald-400/10 blur-3xl" aria-hidden />
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="text-xs font-medium uppercase tracking-wide text-ink-400">Total portfolio value</div>
                {d.totals.pricesSimulated && <SimulatedBadge />}
              </div>
              <div className="tabular mt-1 text-4xl font-semibold tracking-tight text-white sm:text-5xl"><Money value={d.totals.marketValue} currency={d.currency} /></div>
              <div className={cn('mt-2 flex items-center gap-1.5 text-sm font-medium', toneClass(d.totals.dayChange))}>
                {d.totals.dayChange >= 0 ? <ArrowUpRight className="h-4 w-4" aria-hidden /> : <ArrowDownRight className="h-4 w-4" aria-hidden />}
                <Money value={d.totals.dayChange} currency={d.currency} signed /> <span>({fmtPercent(d.totals.dayChangePercent, true)}) today</span>
              </div>
            </Card>

            <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
              <Stat label="Invested capital" value={<Money value={d.totals.investedCost} currency={d.currency} />} sub="Cost of open positions" />
              <Stat label="Total profit / loss" tone={tone(d.totals.totalReturn)} value={<Money value={d.totals.totalReturn} currency={d.currency} signed />} sub="Realized + unrealized + dividends" />
              <Stat label="Overall return" tone={tone(d.totals.simpleRoiPercent)} value={fmtPercent(d.totals.simpleRoiPercent, true)} sub="Of everything bought" />
              <Stat label="Active investments" value={fmtNumber(d.totals.activeInvestments, 0)} sub="Open positions" />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <Performer title="Best performer" asset={d.bestPerformer} currency={d.currency} />
              <Performer title="Worst performer" asset={d.worstPerformer} currency={d.currency} />
            </div>

            <Card>
              <SectionTitle action={<Segmented ariaLabel="Time range" value={range} options={RANGE_OPTIONS} onChange={setRange} />}>Performance</SectionTitle>
              {d.performance.series.length > 1 ? (
                <>
                  <ValueChart series={d.performance.series} currency={d.currency} />
                  <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-1 text-sm text-ink-300">
                    <span>Time-weighted return: <Pct value={d.performance.window.timeWeightedReturnPercent} /></span>
                    <span>{fmtDate(d.performance.from)} to {fmtDate(d.performance.to)}</span>
                    <Link to="/analytics" className="text-emerald-300 hover:underline">Risk and benchmark analysis</Link>
                  </div>
                </>
              ) : <EmptyState title="Not enough history yet" text="Record transactions to see how your value changes over time." />}
              <div className="mt-3"><Warnings items={d.performance.dataQuality.warnings} /></div>
            </Card>

            <div className="grid gap-4 lg:grid-cols-2">
              <Card>
                <SectionTitle>Allocation</SectionTitle>
                {d.allocation.byAsset.length > 0
                  ? <><AllocationDonut slices={d.allocation.byAsset} currency={d.currency} />
                    <p className="mt-3 text-xs text-ink-400">{d.allocation.diversification.explanation}</p></>
                  : <EmptyState title="No open positions" />}
              </Card>
              <Card>
                <SectionTitle>Daily market movements</SectionTitle>
                <Movers data={d} />
              </Card>
            </div>

            <Card>
              <SectionTitle>Recent transactions</SectionTitle>
              {d.recentTransactions.length === 0 ? <EmptyState title="No transactions yet" /> : (
                <ul className="divide-y divide-ink-700/60">
                  {d.recentTransactions.map((t) => (
                    <li key={t.id}>
                      <Link to={`/portfolios/${t.portfolioId}`} className="flex items-center justify-between gap-3 py-3">
                        <div className="flex min-w-0 items-center gap-3">
                          <Badge tone="neutral">{humanize(t.type)}</Badge>
                          <span className="truncate text-sm font-medium text-white">{t.symbol ?? 'Cash'}</span>
                        </div>
                        <div className="text-right">
                          <div className="tabular text-sm text-white"><Money value={t.totalAmount} currency={d.currency} /></div>
                          <div className="text-xs text-ink-400">{fmtDate(t.executedAt)}</div>
                        </div>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
            <p className="text-center text-xs text-ink-500">{d.performance.disclaimer}</p>
          </div>
        )}
      </Async>
    </>
  );
}
