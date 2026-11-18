/**
 * Client-side form validation. It mirrors the server's rules so people get instant feedback; the server
 * remains the authority and its messages are shown if it still rejects a request.
 */
import type {
  AlertCondition, CreateAlertRequest, CreateTransactionRequest, PortfolioRequest, TransactionType, TransferRequest,
} from '../api/types';

export type FieldErrors = Record<string, string>;
export type Validated<T> = { ok: true; value: T } | { ok: false; errors: FieldErrors };

const DECIMAL = /^\d+(\.\d+)?$/;
const FUTURE_TOLERANCE_MS = 5 * 60 * 1000;

interface DecimalRule { label: string; maxFraction: number; maxInteger?: number; required?: boolean; allowZero?: boolean }

function parseDecimal(raw: string, key: string, rule: DecimalRule, errors: FieldErrors): number | undefined {
  const s = raw.trim();
  if (s === '') {
    if (rule.required) errors[key] = `${rule.label} is required`;
    return undefined;
  }
  if (!DECIMAL.test(s)) { errors[key] = `${rule.label} must be a number like 12.50`; return undefined; }
  const [whole, fraction = ''] = s.split('.');
  if (fraction.replace(/0+$/, '').length > rule.maxFraction) {
    errors[key] = `${rule.label} allows at most ${rule.maxFraction} decimal places`;
    return undefined;
  }
  if (whole.replace(/^0+(?=\d)/, '').length > (rule.maxInteger ?? 15)) { errors[key] = `${rule.label} is too large`; return undefined; }
  const n = Number(s);
  if (n === 0 && !rule.allowZero) { errors[key] = `${rule.label} must be greater than 0`; return undefined; }
  return n;
}

function parseDate(raw: string, errors: FieldErrors, now: Date, key = 'executedAt'): string | undefined {
  if (!raw) { errors[key] = 'Date is required'; return undefined; }
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) { errors[key] = 'Enter a valid date and time'; return undefined; }
  if (d.getTime() > now.getTime() + FUTURE_TOLERANCE_MS) { errors[key] = 'Date cannot be in the future'; return undefined; }
  return d.toISOString();
}

export function needsAsset(type: TransactionType): boolean {
  return type === 'BUY' || type === 'SELL' || type === 'DIVIDEND';
}

// ---- transactions -----------------------------------------------------------------------------

export interface TransactionForm {
  type: TransactionType; assetId: string; quantity: string; unitPrice: string; fee: string; amount: string;
  executedAt: string; notes: string;
}

export function validateTransaction(f: TransactionForm, now: Date = new Date()): Validated<CreateTransactionRequest> {
  const errors: FieldErrors = {};
  const executedAt = parseDate(f.executedAt, errors, now);
  if (f.notes.length > 500) errors.notes = 'Notes can be at most 500 characters';
  let value: CreateTransactionRequest | undefined;

  if (needsAsset(f.type)) {
    const assetId = Number(f.assetId);
    if (!f.assetId || !Number.isInteger(assetId) || assetId <= 0) errors.assetId = 'Choose an asset';
    const quantity = parseDecimal(f.quantity, 'quantity', { label: 'Quantity', maxFraction: 8, maxInteger: 16, required: true }, errors);
    const unitPrice = parseDecimal(f.unitPrice, 'unitPrice',
      { label: f.type === 'DIVIDEND' ? 'Dividend per share' : 'Price', maxFraction: 8, maxInteger: 16, required: true }, errors);
    const fee = parseDecimal(f.fee, 'fee', { label: 'Fee', maxFraction: 4, allowZero: true }, errors);
    if (quantity !== undefined && unitPrice !== undefined && fee !== undefined && f.type !== 'BUY'
      && fee > quantity * unitPrice + 1e-9) errors.fee = 'Fee cannot exceed the transaction value';
    if (Object.keys(errors).length === 0 && executedAt) {
      value = { type: f.type, assetId, quantity, unitPrice, fee, executedAt, notes: f.notes.trim() || undefined };
    }
  } else {
    const amount = parseDecimal(f.amount, 'amount', { label: 'Amount', maxFraction: 4, required: true }, errors);
    if (Object.keys(errors).length === 0 && executedAt) {
      value = { type: f.type, amount, executedAt, notes: f.notes.trim() || undefined };
    }
  }
  return value ? { ok: true, value } : { ok: false, errors };
}

// ---- portfolios -------------------------------------------------------------------------------

export function validatePortfolio(
  f: { name: string; description: string; baseCurrency: string; category: string }, requireCurrency = true,
): Validated<PortfolioRequest> {
  const errors: FieldErrors = {};
  const name = f.name.trim();
  if (!name) errors.name = 'Name is required';
  else if (name.length > 120) errors.name = 'Name can be at most 120 characters';
  if (f.description.length > 500) errors.description = 'Description can be at most 500 characters';
  if (f.category.length > 50) errors.category = 'Category can be at most 50 characters';
  const currency = f.baseCurrency.trim();
  if (requireCurrency && !/^[A-Za-z]{3}$/.test(currency)) errors.baseCurrency = 'Use a 3-letter currency code such as USD';
  if (Object.keys(errors).length > 0) return { ok: false, errors };
  return {
    ok: true,
    value: {
      name,
      description: f.description.trim() || undefined,
      baseCurrency: requireCurrency ? currency.toUpperCase() : undefined,
      category: f.category.trim() || undefined,
    },
  };
}

// ---- alerts -----------------------------------------------------------------------------------

export function validateAlert(
  f: { assetId: string; condition: AlertCondition; targetPrice: string; thresholdPercent: string },
): Validated<CreateAlertRequest> {
  const errors: FieldErrors = {};
  const assetId = Number(f.assetId);
  if (!f.assetId || !Number.isInteger(assetId) || assetId <= 0) errors.assetId = 'Choose an asset';
  let targetPrice: number | undefined;
  let thresholdPercent: number | undefined;
  if (f.condition === 'PERCENT_MOVE') {
    thresholdPercent = parseDecimal(f.thresholdPercent, 'thresholdPercent', { label: 'Percent move', maxFraction: 4, required: true }, errors);
    if (thresholdPercent !== undefined && thresholdPercent > 1000) errors.thresholdPercent = 'Percent move can be at most 1000';
  } else {
    targetPrice = parseDecimal(f.targetPrice, 'targetPrice', { label: 'Target price', maxFraction: 8, maxInteger: 16, required: true }, errors);
  }
  if (Object.keys(errors).length > 0) return { ok: false, errors };
  return { ok: true, value: { assetId, condition: f.condition, targetPrice, thresholdPercent } };
}

// ---- transfers --------------------------------------------------------------------------------

export function validateTransfer(
  f: { fromPortfolioId: number; toPortfolioId: string; assetId: string; quantity: string; executedAt: string; notes: string },
  now: Date = new Date(),
): Validated<TransferRequest> {
  const errors: FieldErrors = {};
  const to = Number(f.toPortfolioId);
  if (!f.toPortfolioId || !Number.isInteger(to)) errors.toPortfolioId = 'Choose a destination portfolio';
  else if (to === f.fromPortfolioId) errors.toPortfolioId = 'Choose a different portfolio';
  const assetId = Number(f.assetId);
  if (!f.assetId || !Number.isInteger(assetId) || assetId <= 0) errors.assetId = 'Choose an asset';
  const quantity = parseDecimal(f.quantity, 'quantity', { label: 'Quantity', maxFraction: 8, maxInteger: 16, required: true }, errors);
  const executedAt = parseDate(f.executedAt, errors, now);
  if (Object.keys(errors).length > 0 || quantity === undefined || !executedAt) return { ok: false, errors };
  return {
    ok: true,
    value: { fromPortfolioId: f.fromPortfolioId, toPortfolioId: to, assetId, quantity, executedAt, notes: f.notes.trim() || undefined },
  };
}

// ---- account ----------------------------------------------------------------------------------

export function validatePassword(password: string): string | null {
  if (password.length < 10) return 'Use at least 10 characters';
  if (password.length > 72) return 'Use at most 72 characters';
  return null;
}

export function isEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim());
}

/** Value for <input type="datetime-local"> in the user's own time zone. */
export function toLocalInputValue(d: Date = new Date()): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
