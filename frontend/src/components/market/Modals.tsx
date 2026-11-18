import { useState } from 'react';
import type { FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Check } from 'lucide-react';
import { api } from '../../api/endpoints';
import type { AlertCondition, Asset, AssetType } from '../../api/types';
import { humanize } from '../../lib/format';
import { validateAlert } from '../../lib/validation';
import type { FieldErrors } from '../../lib/validation';
import { AssetPicker } from '../AssetPicker';
import { Button, ErrorNote, Modal, Notice, SelectField, Spinner, TextField } from '../ui';

// ---- add an asset to the shared catalogue (administrators and analysts) -----------------------------

const ASSET_TYPES: AssetType[] = ['STOCK', 'ETF', 'BOND', 'FUND', 'CRYPTO'];

function AssetFormBody({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient();
  const [f, setF] = useState({ symbol: '', name: '', assetType: 'STOCK' as AssetType, exchange: '', currency: 'USD' });
  const [errors, setErrors] = useState<FieldErrors>({});
  const save = useMutation({
    mutationFn: () => api.assets.create({ symbol: f.symbol.trim(), name: f.name.trim(), assetType: f.assetType, exchange: f.exchange.trim() || undefined, currency: f.currency.trim() || undefined }),
    onSuccess: async () => { await qc.invalidateQueries(); onClose(); },
  });
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const next: FieldErrors = {};
    if (!/^[A-Za-z0-9.-]{1,20}$/.test(f.symbol.trim())) next.symbol = 'Use 1-20 letters, digits, "." or "-"';
    if (!f.name.trim()) next.name = 'Name is required';
    if (f.currency.trim() && !/^[A-Za-z]{3}$/.test(f.currency.trim())) next.currency = 'Use a 3-letter code';
    setErrors(next);
    if (Object.keys(next).length === 0) save.mutate();
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <ErrorNote error={save.error} />
      <div className="grid grid-cols-2 gap-3">
        <TextField label="Symbol" value={f.symbol} onChange={set('symbol')} error={errors.symbol} autoCapitalize="characters" maxLength={20} />
        <SelectField label="Type" value={f.assetType} onChange={set('assetType')}>{ASSET_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}</SelectField>
      </div>
      <TextField label="Name" value={f.name} onChange={set('name')} error={errors.name} maxLength={200} />
      <div className="grid grid-cols-2 gap-3">
        <TextField label="Exchange" value={f.exchange} onChange={set('exchange')} placeholder="Optional" maxLength={30} />
        <TextField label="Currency" value={f.currency} onChange={set('currency')} error={errors.currency} maxLength={3} autoCapitalize="characters" />
      </div>
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button type="submit" loading={save.isPending}>Add asset</Button>
      </div>
    </form>
  );
}

export function AssetModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  return <Modal open={open} onClose={onClose} title="Add asset"><AssetFormBody onClose={onClose} /></Modal>;
}

// ---- price alert ----------------------------------------------------------------------------------------

function AlertFormBody({ fixed, onClose }: { fixed?: Asset | null; onClose: () => void }) {
  const qc = useQueryClient();
  const [asset, setAsset] = useState<Asset | null>(fixed ?? null);
  const [f, setF] = useState({ condition: 'ABOVE' as AlertCondition, targetPrice: '', thresholdPercent: '' });
  const [errors, setErrors] = useState<FieldErrors>({});
  const save = useMutation({
    mutationFn: (body: Parameters<typeof api.alerts.create>[0]) => api.alerts.create(body),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['alerts'] }); onClose(); },
  });

  function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const r = validateAlert({ assetId: asset ? String(asset.id) : '', ...f });
    if (!r.ok) { setErrors(r.errors); return; }
    setErrors({});
    save.mutate(r.value);
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <ErrorNote error={save.error} />
      {fixed ? <div className="text-sm text-ink-200">Alert for <span className="font-semibold text-white">{fixed.symbol}</span> {fixed.name}</div>
        : <AssetPicker label="Asset" value={asset} onChange={setAsset} error={errors.assetId} />}
      <SelectField label="Alert me when" value={f.condition} onChange={(e) => setF({ ...f, condition: e.target.value as AlertCondition })}>
        <option value="ABOVE">Price rises to or above</option>
        <option value="BELOW">Price falls to or below</option>
        <option value="PERCENT_MOVE">Price moves by a percentage in a day</option>
      </SelectField>
      {f.condition === 'PERCENT_MOVE'
        ? <TextField label="Percent move" inputMode="decimal" value={f.thresholdPercent} onChange={(e) => setF({ ...f, thresholdPercent: e.target.value })} error={errors.thresholdPercent} placeholder="5" hint="Up or down, compared with the previous close" />
        : <TextField label="Target price" inputMode="decimal" value={f.targetPrice} onChange={(e) => setF({ ...f, targetPrice: e.target.value })} error={errors.targetPrice} placeholder="0.00" />}
      <p className="text-xs text-ink-400">Each alert fires once, then switches off. You will get an in-app notification.</p>
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="ghost" onClick={onClose}>Cancel</Button>
        <Button type="submit" loading={save.isPending}>Create alert</Button>
      </div>
    </form>
  );
}

export function AlertModal({ open, asset, onClose }: { open: boolean; asset?: Asset | null; onClose: () => void }) {
  return <Modal open={open} onClose={onClose} title="New price alert"><AlertFormBody fixed={asset} onClose={onClose} /></Modal>;
}

// ---- add an asset to a watchlist -------------------------------------------------------------------------

function WatchlistPickBody({ asset, onClose }: { asset: Asset; onClose: () => void }) {
  const qc = useQueryClient();
  const lists = useQuery({ queryKey: ['watchlists'], queryFn: api.watchlists.list });
  const [name, setName] = useState('');
  const add = useMutation({
    mutationFn: (id: number) => api.watchlists.addItem(id, asset.id),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['watchlists'] }); },
  });
  const createAndAdd = useMutation({
    mutationFn: async () => { const w = await api.watchlists.create(name.trim()); return api.watchlists.addItem(w.id, asset.id); },
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['watchlists'] }); setName(''); },
  });

  if (lists.isLoading) return <Spinner />;
  return (
    <div className="space-y-4">
      <ErrorNote error={lists.error ?? add.error ?? createAndAdd.error} />
      {lists.data && lists.data.length > 0 && (
        <ul className="space-y-2">
          {lists.data.map((w) => {
            const has = w.items.some((i) => i.assetId === asset.id);
            return (
              <li key={w.id} className="flex items-center justify-between gap-3 rounded-xl border border-ink-700 bg-ink-900/60 px-3.5 py-2.5">
                <span className="truncate text-sm text-white">{w.name} <span className="text-ink-400">({w.items.length})</span></span>
                {has ? <span className="flex items-center gap-1 text-sm text-gain"><Check className="h-4 w-4" aria-hidden /> Added</span>
                  : <Button size="sm" variant="secondary" loading={add.isPending && add.variables === w.id} onClick={() => add.mutate(w.id)}>Add</Button>}
              </li>
            );
          })}
        </ul>
      )}
      <form className="flex items-end gap-2" onSubmit={(e) => { e.preventDefault(); if (name.trim()) createAndAdd.mutate(); }}>
        <TextField className="flex-1" label="New watchlist" value={name} onChange={(e) => setName(e.target.value)} placeholder="For example, Tech" maxLength={120} />
        <Button type="submit" loading={createAndAdd.isPending} disabled={!name.trim()}>Create</Button>
      </form>
      <div className="flex justify-end"><Button variant="ghost" onClick={onClose}>Done</Button></div>
    </div>
  );
}

export function WatchlistPickModal({ open, asset, onClose }: { open: boolean; asset: Asset; onClose: () => void }) {
  return <Modal open={open} onClose={onClose} title={`Add ${asset.symbol} to a watchlist`}><WatchlistPickBody asset={asset} onClose={onClose} /></Modal>;
}

export function NameModal({ open, title, initial, onClose, onSubmit, busy, error }: {
  open: boolean; title: string; initial?: string; onClose: () => void; onSubmit: (name: string) => void; busy?: boolean; error?: unknown;
}) {
  return (
    <Modal open={open} onClose={onClose} title={title}>
      <NameFormBody initial={initial ?? ''} onClose={onClose} onSubmit={onSubmit} busy={busy} error={error} />
    </Modal>
  );
}

function NameFormBody({ initial, onClose, onSubmit, busy, error }: { initial: string; onClose: () => void; onSubmit: (n: string) => void; busy?: boolean; error?: unknown }) {
  const [name, setName] = useState(initial);
  return (
    <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); if (name.trim()) onSubmit(name.trim()); }}>
      <ErrorNote error={error} />
      <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} maxLength={120} autoFocus />
      <div className="flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button type="submit" loading={busy} disabled={!name.trim()}>Save</Button></div>
    </form>
  );
}

export function NoAssetsNotice() {
  return <Notice>No assets are available yet. An administrator or analyst needs to add them to the catalogue.</Notice>;
}
