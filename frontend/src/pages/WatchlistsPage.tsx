import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Pencil, Plus, Trash2, X } from 'lucide-react';
import { api } from '../api/endpoints';
import type { Watchlist } from '../api/types';
import { Async } from '../components/Async';
import { AssetPicker } from '../components/AssetPicker';
import { ConfirmModal } from '../components/ConfirmModal';
import { MarketTabs } from '../components/layout/AppShell';
import { NameModal } from '../components/market/Modals';
import { Badge, Button, Card, EmptyState, ErrorNote, Modal, PageHeader, Pct, Skeleton } from '../components/ui';
import { fmtNumber } from '../lib/format';
import type { Asset } from '../api/types';

function AddAssetBody({ watchlist, onClose }: { watchlist: Watchlist; onClose: () => void }) {
  const qc = useQueryClient();
  const [asset, setAsset] = useState<Asset | null>(null);
  const add = useMutation({
    mutationFn: (a: Asset) => api.watchlists.addItem(watchlist.id, a.id),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['watchlists'] }); onClose(); },
  });
  return (
    <div className="space-y-4">
      <ErrorNote error={add.error} />
      <AssetPicker label="Asset" value={asset} onChange={setAsset} />
      <div className="flex justify-end gap-2"><Button variant="ghost" onClick={onClose}>Cancel</Button><Button disabled={!asset} loading={add.isPending} onClick={() => { if (asset) add.mutate(asset); }}>Add to list</Button></div>
    </div>
  );
}

function WatchlistCard({ w, onRename, onDelete, onAdd }: { w: Watchlist; onRename: () => void; onDelete: () => void; onAdd: () => void }) {
  const qc = useQueryClient();
  const remove = useMutation({
    mutationFn: (assetId: number) => api.watchlists.removeItem(w.id, assetId),
    onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['watchlists'] }); },
  });
  return (
    <Card>
      <div className="mb-3 flex items-center justify-between gap-2">
        <h2 className="truncate text-base font-semibold text-white">{w.name} <span className="text-sm font-normal text-ink-400">({w.items.length})</span></h2>
        <div className="flex items-center">
          <Button variant="ghost" size="sm" aria-label="Add asset" icon={<Plus className="h-4 w-4" />} onClick={onAdd} />
          <Button variant="ghost" size="sm" aria-label="Rename watchlist" icon={<Pencil className="h-4 w-4" />} onClick={onRename} />
          <Button variant="ghost" size="sm" aria-label="Delete watchlist" icon={<Trash2 className="h-4 w-4" />} onClick={onDelete} />
        </div>
      </div>
      <ErrorNote error={remove.error} />
      {w.items.length === 0 ? <p className="py-4 text-center text-sm text-ink-400">Nothing here yet. Add an asset to follow its price.</p> : (
        <ul className="divide-y divide-ink-700/60">
          {w.items.map((i) => (
            <li key={i.assetId} className="flex items-center justify-between gap-3 py-3">
              <Link to={`/market/${i.assetId}`} className="min-w-0 flex-1">
                <div className="font-semibold text-white">{i.symbol} {i.quote?.simulated && <Badge tone="warn">Simulated</Badge>}</div>
                <div className="truncate text-sm text-ink-400">{i.name}</div>
              </Link>
              <div className="text-right">
                {i.quote ? (<><div className="tabular text-white">{fmtNumber(i.quote.price, 4)}</div><div className="text-sm"><Pct value={i.quote.changePercent} /></div></>) : <span className="text-sm text-ink-500">No price</span>}
              </div>
              <button type="button" aria-label={`Remove ${i.symbol}`} onClick={() => remove.mutate(i.assetId)} className="rounded-lg p-2 text-ink-400 hover:bg-ink-700 hover:text-white"><X className="h-4 w-4" /></button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

export function WatchlistsPage() {
  const qc = useQueryClient();
  const lists = useQuery({ queryKey: ['watchlists'], queryFn: api.watchlists.list, refetchInterval: 60_000 });
  const [creating, setCreating] = useState(false);
  const [renaming, setRenaming] = useState<Watchlist | null>(null);
  const [deleting, setDeleting] = useState<Watchlist | null>(null);
  const [adding, setAdding] = useState<Watchlist | null>(null);

  const create = useMutation({ mutationFn: (name: string) => api.watchlists.create(name), onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['watchlists'] }); setCreating(false); } });
  const rename = useMutation({ mutationFn: (v: { id: number; name: string }) => api.watchlists.rename(v.id, v.name), onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['watchlists'] }); setRenaming(null); } });
  const remove = useMutation({ mutationFn: (id: number) => api.watchlists.remove(id), onSuccess: async () => { await qc.invalidateQueries({ queryKey: ['watchlists'] }); setDeleting(null); } });

  return (
    <>
      <MarketTabs />
      <PageHeader title="Watchlists" subtitle="Follow assets without owning them" actions={<Button icon={<Plus className="h-4 w-4" />} onClick={() => setCreating(true)}>New watchlist</Button>} />
      <Async query={lists} skeleton={<Skeleton className="h-48" />}>
        {(items) => items.length === 0 ? (
          <Card><EmptyState title="No watchlists yet" text="Create one, then add assets from here or from any asset page." action={<Button onClick={() => setCreating(true)}>Create a watchlist</Button>} /></Card>
        ) : (
          <div className="grid gap-4 lg:grid-cols-2">
            {items.map((w) => <WatchlistCard key={w.id} w={w} onRename={() => setRenaming(w)} onDelete={() => setDeleting(w)} onAdd={() => setAdding(w)} />)}
          </div>
        )}
      </Async>
      <NameModal open={creating} title="New watchlist" onClose={() => { setCreating(false); create.reset(); }} onSubmit={(n) => create.mutate(n)} busy={create.isPending} error={create.error} />
      <NameModal open={renaming !== null} title="Rename watchlist" initial={renaming?.name} onClose={() => { setRenaming(null); rename.reset(); }}
        onSubmit={(n) => { if (renaming) rename.mutate({ id: renaming.id, name: n }); }} busy={rename.isPending} error={rename.error} />
      <ConfirmModal open={deleting !== null} danger title="Delete this watchlist?" confirmLabel="Delete" busy={remove.isPending} error={remove.error}
        message={`"${deleting?.name ?? ''}" will be removed. The assets themselves are not affected.`}
        onConfirm={() => { if (deleting) remove.mutate(deleting.id); }} onClose={() => { setDeleting(null); remove.reset(); }} />
      <Modal open={adding !== null} onClose={() => setAdding(null)} title="Add to watchlist">{adding && <AddAssetBody watchlist={adding} onClose={() => setAdding(null)} />}</Modal>
    </>
  );
}
