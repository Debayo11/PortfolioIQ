import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight, Plus } from 'lucide-react';
import { api } from '../api/endpoints';
import type { Portfolio } from '../api/types';
import { Async } from '../components/Async';
import { PortfolioModal } from '../components/portfolio/Modals';
import { Badge, Button, Card, EmptyState, Money, PageHeader, Pct, Skeleton } from '../components/ui';
import { fmtDate } from '../lib/format';

function PortfolioCard({ p }: { p: Portfolio }) {
  const summary = useQuery({ queryKey: ['summary', p.id], queryFn: () => api.portfolios.summary(p.id) });
  const s = summary.data;
  const roi = s && s.investedCost > 0 ? (s.totalReturn / s.investedCost) * 100 : null;
  return (
    <Link to={`/portfolios/${p.id}`} className="card block p-4 transition hover:border-emerald-400/40 sm:p-5">
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="truncate text-base font-semibold text-white">{p.name}</h3>
          <div className="mt-1 flex flex-wrap items-center gap-2">
            <Badge>{p.baseCurrency}</Badge>
            {p.category && <Badge tone="info">{p.category}</Badge>}
            <span className="text-xs text-ink-400">Created {fmtDate(p.createdAt)}</span>
          </div>
        </div>
        <ChevronRight className="mt-1 h-5 w-5 shrink-0 text-ink-400" aria-hidden />
      </div>
      {p.description && <p className="mt-2 line-clamp-2 text-sm text-ink-300">{p.description}</p>}
      <div className="mt-4 grid grid-cols-2 gap-3">
        <div>
          <div className="text-xs uppercase tracking-wide text-ink-400">Value</div>
          {s ? <div className="text-lg font-semibold text-white"><Money value={s.marketValue} currency={p.baseCurrency} /></div> : <Skeleton className="mt-1 h-6 w-24" />}
        </div>
        <div>
          <div className="text-xs uppercase tracking-wide text-ink-400">Total return</div>
          {s ? <div className="text-lg font-semibold"><Money value={s.totalReturn} currency={p.baseCurrency} signed colored /> {roi !== null && <span className="text-sm"><Pct value={roi} /></span>}</div> : <Skeleton className="mt-1 h-6 w-24" />}
        </div>
      </div>
    </Link>
  );
}

export function PortfoliosPage() {
  const navigate = useNavigate();
  const list = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios.list });
  const [creating, setCreating] = useState(false);

  return (
    <>
      <PageHeader title="Portfolios" subtitle="Organise your investments into separate portfolios"
        actions={<Button icon={<Plus className="h-4 w-4" />} onClick={() => setCreating(true)}>New portfolio</Button>} />
      <Async query={list} skeleton={<div className="grid gap-4 md:grid-cols-2"><Skeleton className="h-40" /><Skeleton className="h-40" /></div>}>
        {(portfolios) => portfolios.length === 0 ? (
          <Card><EmptyState title="No portfolios yet" text="Create a portfolio, then record your first transaction."
            action={<Button onClick={() => setCreating(true)}>Create a portfolio</Button>} /></Card>
        ) : (
          <div className="grid gap-4 md:grid-cols-2">{portfolios.map((p) => <PortfolioCard key={p.id} p={p} />)}</div>
        )}
      </Async>
      <PortfolioModal open={creating} onClose={() => setCreating(false)} onSaved={(p) => { setCreating(false); navigate(`/portfolios/${p.id}`); }} />
    </>
  );
}
