import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, X } from 'lucide-react';
import { api } from '../api/endpoints';
import type { Asset } from '../api/types';
import { useDebounced } from '../lib/hooks';
import { cn } from '../lib/cn';

/** Search-as-you-type asset selector. */
export function AssetPicker({ label, value, onChange, error }: {
  label: string; value: Asset | null; onChange: (a: Asset | null) => void; error?: string;
}) {
  const [text, setText] = useState('');
  const debounced = useDebounced(text.trim(), 250);
  const results = useQuery({
    queryKey: ['asset-search', debounced],
    queryFn: () => api.assets.search(debounced, 0, 8),
    enabled: !value,
  });

  return (
    <div>
      <span className="label">{label}</span>
      {value ? (
        <div className="flex items-center justify-between gap-3 rounded-xl border border-ink-600 bg-ink-900 px-3.5 py-2.5">
          <div className="min-w-0"><span className="font-semibold text-white">{value.symbol}</span> <span className="truncate text-sm text-ink-300">{value.name}</span></div>
          <button type="button" onClick={() => { onChange(null); setText(''); }} aria-label="Choose a different asset" className="rounded-lg p-1 text-ink-300 hover:bg-ink-700"><X className="h-4 w-4" /></button>
        </div>
      ) : (
        <div>
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" aria-hidden />
            <input className={cn('field pl-9', error && 'border-red-500/60')} placeholder="Search by symbol or name" value={text}
              onChange={(e) => setText(e.target.value)} aria-label={label} autoComplete="off" />
          </div>
          <ul className="mt-1.5 max-h-48 overflow-y-auto rounded-xl border border-ink-700 bg-ink-900">
            {results.isLoading && <li className="px-3.5 py-2.5 text-sm text-ink-400">Searching…</li>}
            {results.data?.content.map((a) => (
              <li key={a.id}>
                <button type="button" onClick={() => onChange(a)} className="flex w-full items-center justify-between gap-3 px-3.5 py-2.5 text-left hover:bg-ink-800">
                  <span className="font-semibold text-white">{a.symbol}</span>
                  <span className="truncate text-sm text-ink-300">{a.name}</span>
                </button>
              </li>
            ))}
            {results.data && results.data.content.length === 0 && <li className="px-3.5 py-2.5 text-sm text-ink-400">No assets found</li>}
          </ul>
        </div>
      )}
      {error && <p className="mt-1 text-xs text-red-300">{error}</p>}
    </div>
  );
}
