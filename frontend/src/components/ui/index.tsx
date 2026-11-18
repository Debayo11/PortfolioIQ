import { useEffect, useId, useRef } from 'react';
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from 'react';
import { AlertCircle, Info, Loader2, TriangleAlert, X } from 'lucide-react';
import { ApiError } from '../../api/client';
import { cn } from '../../lib/cn';
import { fmtMoney, fmtPercent, toneClass, type Tone } from '../../lib/format';

// ---- buttons ------------------------------------------------------------------------------------

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant; size?: 'sm' | 'md'; loading?: boolean; icon?: ReactNode;
}

const variants: Record<Variant, string> = {
  primary: 'bg-gradient-to-b from-emerald-400 to-emerald-500 text-ink-950 font-semibold shadow-lg shadow-emerald-500/20 hover:from-emerald-300 hover:to-emerald-400',
  secondary: 'border border-ink-600 bg-ink-800 text-ink-100 hover:bg-ink-700',
  ghost: 'text-ink-200 hover:bg-ink-800',
  danger: 'border border-red-500/40 bg-red-500/10 text-red-300 hover:bg-red-500/20',
};

export function Button({ variant = 'primary', size = 'md', loading, icon, className, children, disabled, type = 'button', ...rest }: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={cn(
        'inline-flex items-center justify-center gap-2 rounded-xl font-medium transition active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-50',
        size === 'sm' ? 'min-h-9 px-3 text-sm' : 'min-h-11 px-4 text-sm',
        variants[variant], className,
      )}
      {...rest}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : icon}
      {children}
    </button>
  );
}

// ---- layout bits --------------------------------------------------------------------------------

export function Card({ className, children }: { className?: string; children: ReactNode }) {
  return <section className={cn('card p-4 sm:p-5', className)}>{children}</section>;
}

export function SectionTitle({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="mb-3 flex items-center justify-between gap-3">
      <h2 className="text-sm font-semibold uppercase tracking-wide text-ink-300">{children}</h2>
      {action}
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3 animate-rise">
      <div className="min-w-0">
        <h1 className="truncate text-2xl font-semibold tracking-tight text-white sm:text-3xl">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-ink-300">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Stat({ label, value, sub, tone }: { label: string; value: ReactNode; sub?: ReactNode; tone?: Tone }) {
  return (
    <div className="min-w-0 rounded-xl border border-ink-700/60 bg-ink-900/50 p-3.5">
      <div className="text-xs font-medium uppercase tracking-wide text-ink-400">{label}</div>
      <div className={cn('tabular mt-1 truncate text-lg font-semibold sm:text-xl', tone === 'gain' && 'text-gain', tone === 'loss' && 'text-loss', !tone && 'text-white')}>{value}</div>
      {sub && <div className="mt-0.5 text-xs text-ink-400">{sub}</div>}
    </div>
  );
}

export function Badge({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'gain' | 'loss' | 'warn' | 'info' }) {
  const tones = {
    neutral: 'bg-ink-700 text-ink-200', gain: 'bg-emerald-500/15 text-emerald-300', loss: 'bg-red-500/15 text-red-300',
    warn: 'bg-amber-500/15 text-amber-300', info: 'bg-sky-500/15 text-sky-300',
  };
  return <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', tones[tone])}>{children}</span>;
}

export function SimulatedBadge() {
  return <Badge tone="warn">Simulated data</Badge>;
}

export function Spinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-ink-300" role="status">
      <Loader2 className="h-5 w-5 animate-spin" aria-hidden /> <span className="text-sm">{label}…</span>
    </div>
  );
}

export function Skeleton({ className }: { className?: string }) {
  return <div className={cn('animate-pulse rounded-xl bg-ink-800', className)} aria-hidden />;
}

export function EmptyState({ title, text, action }: { title: string; text?: string; action?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 px-4 py-10 text-center">
      <div className="text-base font-semibold text-white">{title}</div>
      {text && <p className="max-w-sm text-sm text-ink-300">{text}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}

// ---- messages -----------------------------------------------------------------------------------

export function messageOf(error: unknown): string {
  if (error instanceof ApiError) return error.message;
  if (error instanceof Error) return error.message;
  return typeof error === 'string' ? error : 'Something went wrong. Please try again.';
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null;
  const details = error instanceof ApiError ? error.details : [];
  return (
    <div role="alert" className="flex gap-2.5 rounded-xl border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">
      <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div>
        <div>{messageOf(error)}</div>
        {details.length > 0 && <ul className="mt-1 list-disc pl-4 text-red-300/90">{details.map((d) => <li key={d}>{d}</li>)}</ul>}
      </div>
    </div>
  );
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'warn'; children: ReactNode }) {
  const Icon = tone === 'warn' ? TriangleAlert : Info;
  return (
    <div className={cn('flex gap-2.5 rounded-xl border p-3 text-sm', tone === 'warn'
      ? 'border-amber-500/30 bg-amber-500/10 text-amber-100' : 'border-sky-500/25 bg-sky-500/10 text-sky-100')}>
      <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div className="min-w-0">{children}</div>
    </div>
  );
}

export function Warnings({ items }: { items: string[] }) {
  if (items.length === 0) return null;
  return (
    <Notice tone="warn">
      <ul className="space-y-1">{items.map((w) => <li key={w}>{w}</li>)}</ul>
    </Notice>
  );
}

// ---- form fields --------------------------------------------------------------------------------

interface FieldShell { label: string; error?: string; hint?: string }

export function TextField({ label, error, hint, className, ...rest }: FieldShell & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="label">{label}</label>
      <input id={id} className={cn('field', error && 'border-red-500/60')} aria-invalid={!!error} aria-describedby={error ? `${id}-e` : undefined} {...rest} />
      {hint && !error && <p className="mt-1 text-xs text-ink-400">{hint}</p>}
      {error && <p id={`${id}-e`} className="mt-1 text-xs text-red-300">{error}</p>}
    </div>
  );
}

export function SelectField({ label, error, hint, className, children, ...rest }: FieldShell & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="label">{label}</label>
      <select id={id} className={cn('field', error && 'border-red-500/60')} aria-invalid={!!error} {...rest}>{children}</select>
      {hint && !error && <p className="mt-1 text-xs text-ink-400">{hint}</p>}
      {error && <p className="mt-1 text-xs text-red-300">{error}</p>}
    </div>
  );
}

export function TextAreaField({ label, error, hint, className, ...rest }: FieldShell & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <div className={className}>
      <label htmlFor={id} className="label">{label}</label>
      <textarea id={id} rows={3} className={cn('field resize-none', error && 'border-red-500/60')} {...rest} />
      {hint && !error && <p className="mt-1 text-xs text-ink-400">{hint}</p>}
      {error && <p className="mt-1 text-xs text-red-300">{error}</p>}
    </div>
  );
}

// ---- controls -----------------------------------------------------------------------------------

export function Segmented<T extends string>({ value, options, onChange, ariaLabel }: {
  value: T; options: ReadonlyArray<{ value: T; label: string }>; onChange: (v: T) => void; ariaLabel: string;
}) {
  return (
    <div role="tablist" aria-label={ariaLabel} className="inline-flex max-w-full overflow-x-auto rounded-xl border border-ink-700 bg-ink-900/70 p-1">
      {options.map((o) => (
        <button
          key={o.value} role="tab" aria-selected={o.value === value} type="button" onClick={() => onChange(o.value)}
          className={cn('min-h-8 whitespace-nowrap rounded-lg px-3 text-xs font-medium transition sm:text-sm',
            o.value === value ? 'bg-ink-600 text-white shadow' : 'text-ink-300 hover:text-white')}
        >{o.label}</button>
      ))}
    </div>
  );
}

// ---- modal / bottom sheet -----------------------------------------------------------------------

export function Modal({ open, onClose, title, children, footer }: {
  open: boolean; onClose: () => void; title: string; children: ReactNode; footer?: ReactNode;
}) {
  const panel = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    document.addEventListener('keydown', onKey);
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    panel.current?.focus();
    return () => { document.removeEventListener('keydown', onKey); document.body.style.overflow = previous; };
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center sm:items-center" role="dialog" aria-modal="true" aria-label={title}>
      <button type="button" aria-label="Close dialog" className="absolute inset-0 bg-black/65 backdrop-blur-sm" onClick={onClose} />
      <div ref={panel} tabIndex={-1} className="animate-sheet relative z-10 flex max-h-[92dvh] w-full flex-col rounded-t-3xl border border-ink-600 bg-ink-800 shadow-2xl outline-none sm:max-w-lg sm:rounded-3xl">
        <div className="flex items-center justify-between gap-3 border-b border-ink-700 px-5 py-4">
          <h2 className="text-base font-semibold text-white">{title}</h2>
          <button type="button" onClick={onClose} aria-label="Close" className="rounded-lg p-1.5 text-ink-300 hover:bg-ink-700 hover:text-white"><X className="h-5 w-5" /></button>
        </div>
        <div className="overflow-y-auto px-5 py-4">{children}</div>
        {footer && <div className="pb-safe border-t border-ink-700 px-5 py-4">{footer}</div>}
      </div>
    </div>
  );
}

// ---- money display ------------------------------------------------------------------------------

export function Money({ value, currency, signed, colored }: { value: number | null | undefined; currency: string; signed?: boolean; colored?: boolean }) {
  return <span className={cn('tabular', colored && toneClass(value))}>{fmtMoney(value, currency, undefined, signed)}</span>;
}

export function Pct({ value, signed = true, colored = true }: { value: number | null | undefined; signed?: boolean; colored?: boolean }) {
  return <span className={cn('tabular', colored && toneClass(value))}>{fmtPercent(value, signed)}</span>;
}
