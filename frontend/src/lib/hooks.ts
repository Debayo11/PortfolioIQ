import { useEffect, useState } from 'react';
import type { RangeCode } from '../api/types';

export function useDebounced<T>(value: T, ms = 250): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export const RANGE_OPTIONS: ReadonlyArray<{ value: RangeCode; label: string }> = [
  { value: '24H', label: '24H' }, { value: '7D', label: '7D' }, { value: '30D', label: '30D' },
  { value: '3M', label: '3M' }, { value: '6M', label: '6M' }, { value: '1Y', label: '1Y' }, { value: 'ALL', label: 'All' },
];

/** Analytics are daily, so the 24H option is left out there. */
export const DAILY_RANGE_OPTIONS = RANGE_OPTIONS.filter((r) => r.value !== '24H');

/** Only same-site paths are allowed as post-login destinations (prevents open redirects). */
export function safePath(path: string | undefined | null): string {
  return path && path.startsWith('/') && !path.startsWith('//') ? path : '/';
}
