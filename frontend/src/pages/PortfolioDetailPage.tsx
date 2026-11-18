import { useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ArrowLeftRight, ChevronLeft, ChevronRight, Pencil, Plus, Trash2, Undo2 } from 'lucide-react';
import { api } from '../api/endpoints';
import type { Holding, Portfolio, PortfolioSummary, Transaction, TransactionType } from '../api/types';
import { Async } from '../components/Async';
import { ConfirmModal } from '../components/ConfirmModal';
import { PortfolioModal, TransactionModal, TransferModal } from '../components/portfolio/Modals';
import { Badge, Button, Card, EmptyState, Money, Notice, PageHeader, Pct, SectionTitle, SelectField, SimulatedBadge, Skeleton, Stat } from '../components/ui';
import { fmtDateTime, fmtNumber, humanize, tone } from '../lib/format';

const CASH_IN: TransactionType[] = ['SELL', 'DIVIDEND', 'DEPOSIT'];
const CASH_OUT: TransactionType[] = ['BUY', 'WITHDRAWAL', 'FEE'];
const TYPE_FILTERS: TransactionType[] = ['BUY', 'SELL', 'DIVIDEND', 'DEPOSIT', 'WITHDRAWAL', 'FEE', 'TRANSFER_IN', 'TRANSFER_OUT'];

function typeTone(t: TransactionType): 'neutral' | 'gain' | 'loss' | 'warn' | 'info' {
  if (t === 'BUY') return 'info';
  if (t === 'SELL') return 'warn';
  if (t === 'DIVIDEND' || t === 'DEPOSIT') return 'gain';
  if (t === 'WITHDRAWAL' || t === 'FEE') return 'loss';
  return 'neutral';
}

function SummaryCards({ s, currency }: { s: PortfolioSummary; currency: string }) {
  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Market value" value={<Money value={s.marketValue} currency={currency} />} sub={s.dayChange !== 0 ? <span><Money value={s.dayChange} currency={currency} signed colored /> today</span> : undefined} />
        <Stat label="Invested" value={<Money value={s.investedCost} currency={currency} />} sub="Cost of open positions" />
        <Stat label="Total return" tone={tone(s.totalReturn)} value={<Money value={s.totalReturn} currency={currency} signed />} sub="Incl. dividends, net of fees" />
        <Stat label="Open positions" value={fmtNumber(s.openPositions, 0)} sub={`${s.transactionCount} transactions`} />
      </div>
      <div className="grid grid-cols-3 gap-3">
        <Stat label="Unrealized" tone={tone(s.unrealizedPnl)} value={<Money value={s.unrealizedPnl} currency={currency} signed />} />
        <Stat label="Realized" tone={tone(s.realizedPnl)} value={<Money value={s.realizedPnl} currency={currency} signed />} />
        <Stat label="Dividends" tone={tone(s.dividendIncome)} value={<Money value={s.dividendIncome} currency={currency} signed />} />
      </div>
      {(s.pricesSimulated || s.unpricedPositions > 0) && (
        <Notice tone="warn">{s.pricesSimulated ? 'Market values use simulated development prices. ' : ''}{s.unpricedPositions > 0 ? `${s.unpricedPositions} position(s) have no price yet.` : ''}</Notice>
      )}
    </div>
  );
}

function HoldingsList({ holdings, currency }: { holdings: Holding[]; currency: string }) {
  if (holdings.length === 0) return <EmptyState title="No open positions" text="Record a buy to start building this portfolio." />;
  return (
    <>
      <div className="hidden overflow-x-auto md:block">
        <table className="w-full text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wide text-ink-400">
              <th className="pb-2 font-medium">Asset</th><th className="pb-2 text-right font-medium">Quantity</th><th className="pb-2 text-right font-medium">Avg cost</th>
              <th className="pb-2 text-right font-medium">Price</th><th className="pb-2 text-right font-medium">Value</th><th className="pb-2 text-right font-medium">Unrealized</th><th className="pb-2 text-right font-medium">Today</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-ink-700/60">
            {holdings.map((h) => (
              <tr key={h.assetId} className="tabular">
                <td className="py-3"><Link to={`/market/${h.assetId}`} className="font-semibold text-white hover:underline">{h.symbol}</Link> <span className="text-ink-400">{h.name}</span>{h.priceStale && <span className="ml-2"><Badge tone="warn">Stale</Badge></span>}</td>
                <td className="py-3 text-right text-ink-200">{fmtNumber(h.quantity, 8)}</td>
                <td className="py-3 text-right text-ink-200"><Money value={h.averageCost} currency={currency} /></td>
                <td className="py-3 text-right text-ink-200"><Money value={h.price} currency={currency} /></td>
                <td className="py-3 text-right text-white"><Money value={h.marketValue} currency={currency} /></td>
                <td className="py-3 text-right"><Money value={h.unrealizedPnl} currency={currency} signed colored /></td>
                <td className="py-3 text-right"><Money value={h.dayChange} currency={currency} signed colored /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="divide-y divide-ink-700/60 md:hidden">
        {holdings.map((h) => (
          <li key={h.assetId}>
            <Link to={`/market/${h.assetId}`} className="block py-3">
              <div className="flex items-center justify-between gap-3">
                <div className="min-w-0"><span className="font-semibold text-white">{h.symbol}</span> <span className="truncate text-sm text-ink-400">{h.name}</span></div>
                <div className="tabular text-right font-medium text-white"><Money value={h.marketValue} currency={currency} /></div>
              </div>
              <div className="mt-1 flex items-center justify-between gap-3 text-xs text-ink-300">
                <span>{fmtNumber(h.quantity, 8)} × <Money value={h.price} currency={currency} />{h.priceSimulated && ' · simulated'}</span>
                <Money value={h.unrealizedPnl} currency={currency} signed colored />
              </div>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}

function TransactionRow({ t, currency, onDelete, onUndo }: { t: Transaction; currency: string; onDelete: () => void; onUndo: () => void }) {
  const sign = CASH_IN.includes(t.type) ? 1 : CASH_OUT.includes(t.type) ? -1 : 0;
  return (
    <li className="flex items-center justify-between gap-3 py-3">
      <div className="min-w-0">
        <div className="flex items-center gap-2"><Badge tone={typeTone(t.type)}>{humanize(t.type)}</Badge><span className="truncate font-medium text-white">{t.assetSymbol ?? 'Cash'}</span></div>
        <div className="mt-1 text-xs text-ink-400">
          {t.assetId !== null && <>{fmtNumber(t.quantity, 8)} @ <Money value={t.unitPrice} currency={currency} />{t.fee > 0 && <> · fee <Money value={t.fee} currency={currency} /></>} · </>}
          {fmtDateTime(t.executedAt)}
        </div>
        {t.notes && <div className="mt-0.5 truncate text-xs text-ink-500">{t.notes}</div>}
      </div>
      <div className="flex shrink-0 items-center gap-2">
        <div className={`tabular text-right font-medium ${sign > 0 ? 'text-gain' : sign < 0 ? 'text-loss' : 'text-ink-200'}`}>
          <Money value={sign * t.totalAmount} currency={currency} signed={sign !== 0} />
        </div>
        {t.transferId ? (
          <button type="button" onClick={onUndo} aria-label="Undo transfer" title="Undo transfer" className="rounded-lg p-2 text-ink-300 hover:bg-ink-700 hover:text-white"><Undo2 className="h-4 w-4" /></button>
        ) : (
          <button type="button" onClick={onDelete} aria-label="Delete transaction" className="rounded-lg p-2 text-ink-400 hover:bg-red-500/10 hover:text-red-300"><Trash2 className="h-4 w-4" /></button>
        )}
      </div>
    </li>
  );
}

function PortfolioBody({ portfolio, allPortfolios }: { portfolio: Portfolio; allPortfolios: Portfolio[] }) {
  const pid = portfolio.id;
  const qc = useQueryClient();
  const navigate = useNavigate();
  const [typeFilter, setTypeFilter] = useState('');
  const [page, setPage] = useState(0);
  const [modal, setModal] = useState<null | 'tx' | 'transfer' | 'edit' | 'delete'>(null);
  const [toRemove, setToRemove] = useState<Transaction | null>(null);

  const summary = useQuery({ queryKey: ['summary', pid], queryFn: () => api.portfolios.summary(pid), refetchInterval: 60_000 });
  const holdings = useQuery({ queryKey: ['holdings', pid], queryFn: () => api.portfolios.holdings(pid), refetchInterval: 60_000 });
  const txs = useQuery({
    queryKey: ['transactions', pid, typeFilter, page],
    queryFn: () => api.portfolios.transactions(pid, { type: typeFilter || undefined, page, size: 15 }),
    placeholderData: keepPreviousData,
  });

  const removeTx = useMutation({
    mutationFn: (t: Transaction) => (t.transferId ? api.transfers.undo(t.transferId) : api.portfolios.deleteTransaction(pid, t.id)),
    onSuccess: async () => { setToRemove(null); await qc.invalidateQueries(); },
  });
  const removePortfolio = useMutation({
    mutationFn: () => api.portfolios.remove(pid),
    onSuccess: async () => { await qc.invalidateQueries(); navigate('/portfolios', { replace: true }); },
  });

  return (
    <>
      <Link to="/portfolios" className="mb-3 inline-flex items-center gap-1.5 text-sm text-ink-300 hover:text-white"><ArrowLeft className="h-4 w-4" aria-hidden /> Portfolios</Link>
      <PageHeader title={portfolio.name}
        subtitle={<span className="flex flex-wrap items-center gap-2"><Badge>{portfolio.baseCurrency}</Badge>{portfolio.category && <Badge tone="info">{portfolio.category}</Badge>}{summary.data?.pricesSimulated && <SimulatedBadge />}</span>}
        actions={<>
          <Button icon={<Plus className="h-4 w-4" />} onClick={() => setModal('tx')}>Add transaction</Button>
          <Button variant="secondary" icon={<ArrowLeftRight className="h-4 w-4" />} onClick={() => setModal('transfer')}>Transfer</Button>
          <Button variant="ghost" aria-label="Edit portfolio" icon={<Pencil className="h-4 w-4" />} onClick={() => setModal('edit')} />
          <Button variant="ghost" aria-label="Delete portfolio" icon={<Trash2 className="h-4 w-4" />} onClick={() => setModal('delete')} />
        </>} />
      {portfolio.description && <p className="-mt-2 mb-4 text-sm text-ink-300">{portfolio.description}</p>}

      <div className="space-y-4">
        <Async query={summary} skeleton={<Skeleton className="h-40" />}>{(s) => <SummaryCards s={s} currency={portfolio.baseCurrency} />}</Async>

        <Card>
          <SectionTitle>Holdings</SectionTitle>
          <Async query={holdings} skeleton={<Skeleton className="h-32" />}>{(h) => <HoldingsList holdings={h} currency={portfolio.baseCurrency} />}</Async>
        </Card>

        <Card>
          <SectionTitle action={
            <SelectField label="Filter" aria-label="Filter by type" value={typeFilter} onChange={(e) => { setTypeFilter(e.target.value); setPage(0); }} className="w-40">
              <option value="">All types</option>
              {TYPE_FILTERS.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
            </SelectField>}>Transactions</SectionTitle>
          <Async query={txs} skeleton={<Skeleton className="h-40" />}>
            {(p) => p.content.length === 0 ? <EmptyState title="No transactions" text={typeFilter ? 'Nothing matches this filter.' : 'Add a deposit or a buy to get started.'} /> : (
              <>
                <ul className="divide-y divide-ink-700/60">
                  {p.content.map((t) => <TransactionRow key={t.id} t={t} currency={portfolio.baseCurrency} onDelete={() => setToRemove(t)} onUndo={() => setToRemove(t)} />)}
                </ul>
                {p.totalPages > 1 && (
                  <div className="mt-3 flex items-center justify-between text-sm text-ink-300">
                    <Button variant="ghost" size="sm" disabled={page === 0} icon={<ChevronLeft className="h-4 w-4" />} onClick={() => setPage(page - 1)}>Previous</Button>
                    <span>Page {p.page + 1} of {p.totalPages}</span>
                    <Button variant="ghost" size="sm" disabled={page + 1 >= p.totalPages} onClick={() => setPage(page + 1)}>Next <ChevronRight className="h-4 w-4" /></Button>
                  </div>
                )}
              </>
            )}
          </Async>
        </Card>
      </div>

      <TransactionModal open={modal === 'tx'} portfolio={portfolio} onClose={() => setModal(null)} />
      <TransferModal open={modal === 'transfer'} portfolio={portfolio} holdings={holdings.data ?? []} portfolios={allPortfolios} onClose={() => setModal(null)} />
      <PortfolioModal open={modal === 'edit'} initial={portfolio} onClose={() => setModal(null)} onSaved={() => setModal(null)} />
      <ConfirmModal open={modal === 'delete'} danger title="Delete this portfolio?" confirmLabel="Delete portfolio" busy={removePortfolio.isPending} error={removePortfolio.error}
        message="This permanently deletes the portfolio and all of its transactions. This cannot be undone." onConfirm={() => removePortfolio.mutate()} onClose={() => setModal(null)} />
      <ConfirmModal open={toRemove !== null} danger={!toRemove?.transferId} title={toRemove?.transferId ? 'Undo this transfer?' : 'Delete this transaction?'}
        confirmLabel={toRemove?.transferId ? 'Undo transfer' : 'Delete'} busy={removeTx.isPending} error={removeTx.error}
        message={toRemove?.transferId ? 'Both sides of the transfer are removed. This is refused if later sales depend on the shares.' : 'This is refused if later sales depend on this transaction.'}
        onConfirm={() => { if (toRemove) removeTx.mutate(toRemove); }} onClose={() => { setToRemove(null); removeTx.reset(); }} />
    </>
  );
}

export function PortfolioDetailPage() {
  const { id } = useParams();
  const pid = Number(id);
  const portfolio = useQuery({ queryKey: ['portfolio', pid], queryFn: () => api.portfolios.get(pid), enabled: Number.isInteger(pid) });
  const all = useQuery({ queryKey: ['portfolios'], queryFn: api.portfolios.list });
  if (!Number.isInteger(pid)) return <Navigate to="/portfolios" replace />;
  return (
    <Async query={portfolio} skeleton={<Skeleton className="h-64" />}>
      {(p) => <PortfolioBody portfolio={p} allPortfolios={all.data ?? []} />}
    </Async>
  );
}
