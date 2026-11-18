/** Display helpers. Numbers arrive from the API as JSON numbers; all rounding here is for display only. */

const DEFAULT_LOCALE = typeof navigator !== 'undefined' && navigator.language ? navigator.language : 'en-US';
export const EMPTY = '—';

type Num = number | null | undefined;

export function fmtMoney(value: Num, currency = 'USD', locale: string = DEFAULT_LOCALE, signed = false): string {
  if (value == null || Number.isNaN(value)) return EMPTY;
  try {
    return new Intl.NumberFormat(locale, {
      style: 'currency',
      currency,
      signDisplay: signed ? 'exceptZero' : 'auto',
    }).format(value);
  } catch {
    // unknown currency code: fall back to a plain number with the code
    return `${signed && value > 0 ? '+' : ''}${fmtNumber(value, 2, locale)} ${currency}`;
  }
}

export function fmtNumber(value: Num, maxFractionDigits = 4, locale: string = DEFAULT_LOCALE): string {
  if (value == null || Number.isNaN(value)) return EMPTY;
  return new Intl.NumberFormat(locale, { maximumFractionDigits: maxFractionDigits }).format(value);
}

/** value is already a percentage (12.5 means 12.5%). */
export function fmtPercent(value: Num, signed = false, locale: string = DEFAULT_LOCALE): string {
  if (value == null || Number.isNaN(value)) return EMPTY;
  return `${new Intl.NumberFormat(locale, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    signDisplay: signed ? 'exceptZero' : 'auto',
  }).format(value)}%`;
}

export function fmtCompact(value: Num, locale: string = DEFAULT_LOCALE): string {
  if (value == null || Number.isNaN(value)) return EMPTY;
  return new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 2 }).format(value);
}

export function fmtDate(iso: string | null | undefined, locale: string = DEFAULT_LOCALE): string {
  if (!iso) return EMPTY;
  const dateOnly = iso.length === 10;
  const d = new Date(dateOnly ? `${iso}T00:00:00Z` : iso);
  if (Number.isNaN(d.getTime())) return EMPTY;
  return new Intl.DateTimeFormat(locale, {
    day: 'numeric', month: 'short', year: 'numeric', timeZone: dateOnly ? 'UTC' : undefined,
  }).format(d);
}

export function fmtDateTime(iso: string | null | undefined, locale: string = DEFAULT_LOCALE): string {
  if (!iso) return EMPTY;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return EMPTY;
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(d);
}

/** Short relative time such as "5m ago". */
export function fmtAgo(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return EMPTY;
  const diff = Math.max(0, now - new Date(iso).getTime());
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

export type Tone = 'gain' | 'loss' | 'flat';

export function tone(value: Num): Tone {
  if (value == null || value === 0 || Number.isNaN(value)) return 'flat';
  return value > 0 ? 'gain' : 'loss';
}

export function toneClass(value: Num): string {
  const t = tone(value);
  return t === 'gain' ? 'text-gain' : t === 'loss' ? 'text-loss' : 'text-ink-300';
}

/** Title-case an enum value such as PERCENT_MOVE -> Percent move. */
export function humanize(value: string): string {
  const s = value.toLowerCase().replace(/_/g, ' ');
  return s.charAt(0).toUpperCase() + s.slice(1);
}
