import { useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/endpoints';
import type { Asset, Holding, Portfolio, TransactionType } from '../../api/types';
import { fmtMoney, fmtNumber } from '../../lib/format';
import { needsAsset, toLocalInputValue, validatePortfolio, validateTransaction, validateTransfer } from '../../lib/validation';
import type { FieldErrors, TransactionForm } from '../../lib/validation';
import { AssetPicker } from '../AssetPicker';
import { Button, ErrorNote, Modal, Notice, SelectField, TextAreaField, TextField } from '../ui';

// ---- create / edit portfolio ---------------------------------------------------------------------

function PortfolioForm({ initial, onSaved, onClose }: { initial?: Portfolio; onSaved: (p: Portfolio) => void; onClose: () => void }) {
  const qc = useQueryClient();
  const [f, setF] = useState({
    name: initial?.name ?? '', description: initial?.description ?? '', baseCurrency: initial?.baseCurrency ?? 'USD', category: initial?.category ?? '',
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const save = useMutation({
    mutationFn: (body: Parameters<typeof api.portfolios.create>[0]) => (initial ? api.portfolios.update(initial.id, body) : api.portfolios.create(body)),
    onSuccess: async (p) => { await qc.invalidateQueries(); onSaved(p); },
  });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const r = validatePortfolio(f, !initial);
    if (!r.ok) { setErrors(r.errors); return; }
    setErrors({});
    save.mutate(r.value);
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <ErrorNote error={save.error} />
      <TextField label="Name" value={f.name} onChange={set('name')} error={errors.name} maxLength={120} autoFocus />
      <div className="grid grid-cols-2 gap-3">
        <TextField label="Base currency" value={f.baseCurrency} onChange={set('baseCurrency')} error={errors.baseCurrency} maxLength={3}
          disabled={!!initial} hint={initial ? 'Fixed once created' : 'For example USD, EUR, GBP'} autoCapitalize="characters" />
        <TextField label="Category" value={f.category} onChange={set('category')} error={errors.category} placeholder="Optional" maxLength={50} />
      </div>
      <TextAreaField label="Description" value={f.description} onChange={set('description')} error={errors.description} maxLength={500} placeholder="Optional" />
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button type="submit" loading={save.isPending}>{initial ? 'Save changes' : 'Create portfolio'}</Button>
      </div>
    </form>
  );
}

export function PortfolioModal({ open, initial, onClose, onSaved }: { open: boolean; initial?: Portfolio; onClose: () => void; onSaved: (p: Portfolio) => void }) {
  return (
    <Modal open={open} onClose={onClose} title={initial ? 'Edit portfolio' : 'New portfolio'}>
      <PortfolioForm initial={initial} onSaved={onSaved} onClose={onClose} />
    </Modal>
  );
}

// ---- add transaction ------------------------------------------------------------------------------

const TYPE_OPTIONS: Array<{ value: TransactionType; label: string }> = [
  { value: 'BUY', label: 'Buy' }, { value: 'SELL', label: 'Sell' }, { value: 'DIVIDEND', label: 'Dividend' },
  { value: 'DEPOSIT', label: 'Deposit' }, { value: 'WITHDRAWAL', label: 'Withdrawal' }, { value: 'FEE', label: 'Fee' },
];

function TransactionFormBody({ portfolio, onClose }: { portfolio: Portfolio; onClose: () => void }) {
  const qc = useQueryClient();
  const [f, setF] = useState<TransactionForm>({
    type: 'BUY', assetId: '', quantity: '', unitPrice: '', fee: '', amount: '', executedAt: toLocalInputValue(), notes: '',
  });
  const [asset, setAsset] = useState<Asset | null>(null);
  const [errors, setErrors] = useState<FieldErrors>({});
  const save = useMutation({
    mutationFn: (body: Parameters<typeof api.portfolios.addTransaction>[1]) => api.portfolios.addTransaction(portfolio.id, body),
    onSuccess: async () => { await qc.invalidateQueries(); onClose(); },
  });
  const set = (k: keyof TransactionForm) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const trade = needsAsset(f.type);
  const qty = Number(f.quantity);
  const price = Number(f.unitPrice);
  const estimate = trade && qty > 0 && price > 0 ? qty * price + (f.type === 'BUY' ? 1 : -1) * (Number(f.fee) || 0) : null;

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const r = validateTransaction(f);
    if (!r.ok) { setErrors(r.errors); return; }
    setErrors({});
    save.mutate(r.value);
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <ErrorNote error={save.error} />
      <SelectField label="Type" value={f.type} onChange={(e) => setF({ ...f, type: e.target.value as TransactionType })}>
        {TYPE_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
      </SelectField>
      {trade ? (
        <>
          <AssetPicker label="Asset" value={asset} error={errors.assetId} onChange={(a) => { setAsset(a); setF({ ...f, assetId: a ? String(a.id) : '' }); }} />
          <div className="grid grid-cols-2 gap-3">
            <TextField label={f.type === 'DIVIDEND' ? 'Shares held' : 'Quantity'} inputMode="decimal" value={f.quantity} onChange={set('quantity')} error={errors.quantity} placeholder="0" />
            <TextField label={f.type === 'DIVIDEND' ? 'Dividend per share' : 'Price per unit'} inputMode="decimal" value={f.unitPrice} onChange={set('unitPrice')} error={errors.unitPrice} placeholder="0.00" />
          </div>
          <TextField label="Fee" inputMode="decimal" value={f.fee} onChange={set('fee')} error={errors.fee} placeholder="0.00" hint="Optional" />
          {estimate !== null && (
            <p className="text-sm text-ink-300">{f.type === 'BUY' ? 'Total cost' : 'Total received'}: <span className="tabular font-medium text-white">{fmtMoney(estimate, portfolio.baseCurrency)}</span></p>
          )}
        </>
      ) : (
        <TextField label="Amount" inputMode="decimal" value={f.amount} onChange={set('amount')} error={errors.amount} placeholder="0.00" hint={`In ${portfolio.baseCurrency}`} />
      )}
      <TextField label="Date and time" type="datetime-local" value={f.executedAt} onChange={set('executedAt')} error={errors.executedAt} />
      <TextAreaField label="Notes" value={f.notes} onChange={set('notes')} error={errors.notes} maxLength={500} placeholder="Optional" />
      <p className="text-xs text-ink-400">Record prices in {portfolio.baseCurrency}. Currency conversion is not supported.</p>
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button type="submit" loading={save.isPending}>Record transaction</Button>
      </div>
    </form>
  );
}

export function TransactionModal({ open, portfolio, onClose }: { open: boolean; portfolio: Portfolio; onClose: () => void }) {
  return (
    <Modal open={open} onClose={onClose} title="Add transaction">
      <TransactionFormBody portfolio={portfolio} onClose={onClose} />
    </Modal>
  );
}

// ---- transfer between portfolios ---------------------------------------------------------------------

function TransferFormBody({ portfolio, holdings, portfolios, onClose }: {
  portfolio: Portfolio; holdings: Holding[]; portfolios: Portfolio[]; onClose: () => void;
}) {
  const qc = useQueryClient();
  const destinations = portfolios.filter((p) => p.id !== portfolio.id && p.baseCurrency === portfolio.baseCurrency);
  const [f, setF] = useState({ toPortfolioId: '', assetId: holdings[0] ? String(holdings[0].assetId) : '', quantity: '', executedAt: toLocalInputValue(), notes: '' });
  const [errors, setErrors] = useState<FieldErrors>({});
  const save = useMutation({
    mutationFn: (body: Parameters<typeof api.transfers.create>[0]) => api.transfers.create(body),
    onSuccess: async () => { await qc.invalidateQueries(); onClose(); },
  });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });
  const held = holdings.find((h) => String(h.assetId) === f.assetId);

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const r = validateTransfer({ fromPortfolioId: portfolio.id, ...f });
    if (!r.ok) { setErrors(r.errors); return; }
    setErrors({});
    save.mutate(r.value);
  }

  if (holdings.length === 0) return <Notice>This portfolio has no open positions to transfer.</Notice>;
  if (destinations.length === 0) {
    return <Notice>You need another portfolio with the same base currency ({portfolio.baseCurrency}) to transfer shares to.</Notice>;
  }
  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <ErrorNote error={save.error} />
      <SelectField label="Asset" value={f.assetId} onChange={set('assetId')} error={errors.assetId}>
        {holdings.map((h) => <option key={h.assetId} value={h.assetId}>{h.symbol} ({fmtNumber(h.quantity, 8)} held)</option>)}
      </SelectField>
      <SelectField label="Move to" value={f.toPortfolioId} onChange={set('toPortfolioId')} error={errors.toPortfolioId}>
        <option value="">Choose a portfolio</option>
        {destinations.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
      </SelectField>
      <TextField label="Quantity" inputMode="decimal" value={f.quantity} onChange={set('quantity')} error={errors.quantity}
        hint={held ? `Up to ${fmtNumber(held.quantity, 8)}` : undefined} />
      <TextField label="Date and time" type="datetime-local" value={f.executedAt} onChange={set('executedAt')} error={errors.executedAt} />
      <TextAreaField label="Notes" value={f.notes} onChange={set('notes')} maxLength={500} placeholder="Optional" />
      <p className="text-xs text-ink-400">The shares keep their original cost, so a transfer creates no profit or loss.</p>
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button type="submit" loading={save.isPending}>Transfer</Button>
      </div>
    </form>
  );
}

export function TransferModal(props: { open: boolean; portfolio: Portfolio; holdings: Holding[]; portfolios: Portfolio[]; onClose: () => void }) {
  return (
    <Modal open={props.open} onClose={props.onClose} title="Transfer shares">
      <TransferFormBody portfolio={props.portfolio} holdings={props.holdings} portfolios={props.portfolios} onClose={props.onClose} />
    </Modal>
  );
}
