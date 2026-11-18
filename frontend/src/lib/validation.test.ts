import { describe, expect, it } from 'vitest';
import {
  isEmail, toLocalInputValue, validateAlert, validatePassword, validatePortfolio, validateTransaction, validateTransfer,
  type TransactionForm,
} from './validation';

const NOW = new Date('2026-06-01T12:00:00Z');
const base: TransactionForm = {
  type: 'BUY', assetId: '5', quantity: '10', unitPrice: '100', fee: '1', amount: '', executedAt: '2026-05-01T10:00', notes: '',
};

describe('validateTransaction', () => {
  it('accepts a valid buy', () => {
    const r = validateTransaction(base, NOW);
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.value.quantity).toBe(10);
      expect(r.value.unitPrice).toBe(100);
      expect(r.value.fee).toBe(1);
      expect(r.value.assetId).toBe(5);
    }
  });

  it('accepts a deposit that only needs an amount', () => {
    const r = validateTransaction({ ...base, type: 'DEPOSIT', assetId: '', quantity: '', unitPrice: '', fee: '', amount: '250.50' }, NOW);
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.value.amount).toBe(250.5);
  });

  it('rejects missing and non-positive numbers', () => {
    const r = validateTransaction({ ...base, quantity: '0', unitPrice: '' }, NOW);
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.errors.quantity).toContain('greater than 0');
      expect(r.errors.unitPrice).toContain('required');
    }
  });

  it('rejects too many decimal places and non-numeric input', () => {
    const a = validateTransaction({ ...base, quantity: '0.123456789' }, NOW);
    const b = validateTransaction({ ...base, unitPrice: '1,000' }, NOW);
    expect(a.ok).toBe(false);
    expect(b.ok).toBe(false);
    if (!a.ok) expect(a.errors.quantity).toContain('8 decimal places');
    if (!b.ok) expect(b.errors.unitPrice).toContain('number');
  });

  it('allows trailing zeros beyond the decimal limit', () => {
    expect(validateTransaction({ ...base, quantity: '1.500000000' }, NOW).ok).toBe(true);
  });

  it('rejects future dates but allows a few minutes of clock skew', () => {
    expect(validateTransaction({ ...base, executedAt: '2026-07-01T10:00' }, NOW).ok).toBe(false);
    expect(validateTransaction({ ...base, executedAt: '' }, NOW).ok).toBe(false);
  });

  it('requires an asset for trades and a fee that fits the proceeds on a sale', () => {
    expect(validateTransaction({ ...base, assetId: '' }, NOW).ok).toBe(false);
    expect(validateTransaction({ ...base, type: 'SELL', quantity: '1', unitPrice: '1', fee: '5' }, NOW).ok).toBe(false);
  });

  it('rejects a zero deposit', () => {
    expect(validateTransaction({ ...base, type: 'DEPOSIT', amount: '0' }, NOW).ok).toBe(false);
  });
});

describe('validatePortfolio', () => {
  it('normalises the currency and trims text', () => {
    const r = validatePortfolio({ name: '  Long term ', description: '', baseCurrency: 'usd', category: '' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.value.name).toBe('Long term');
      expect(r.value.baseCurrency).toBe('USD');
      expect(r.value.description).toBeUndefined();
    }
  });

  it('rejects an empty name and a bad currency', () => {
    const r = validatePortfolio({ name: ' ', description: '', baseCurrency: 'US', category: '' });
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.errors.name).toBeTruthy();
      expect(r.errors.baseCurrency).toBeTruthy();
    }
  });

  it('does not require a currency when editing', () => {
    expect(validatePortfolio({ name: 'A', description: '', baseCurrency: '', category: '' }, false).ok).toBe(true);
  });
});

describe('validateAlert', () => {
  it('needs a target price for above/below and a percent for moves', () => {
    expect(validateAlert({ assetId: '1', condition: 'ABOVE', targetPrice: '100', thresholdPercent: '' }).ok).toBe(true);
    expect(validateAlert({ assetId: '1', condition: 'ABOVE', targetPrice: '', thresholdPercent: '' }).ok).toBe(false);
    expect(validateAlert({ assetId: '1', condition: 'PERCENT_MOVE', targetPrice: '', thresholdPercent: '5' }).ok).toBe(true);
    expect(validateAlert({ assetId: '1', condition: 'PERCENT_MOVE', targetPrice: '', thresholdPercent: '1001' }).ok).toBe(false);
    expect(validateAlert({ assetId: '', condition: 'BELOW', targetPrice: '5', thresholdPercent: '' }).ok).toBe(false);
  });
});

describe('validateTransfer', () => {
  const f = { fromPortfolioId: 1, toPortfolioId: '2', assetId: '5', quantity: '4', executedAt: '2026-05-01T10:00', notes: '' };
  it('accepts a valid transfer', () => { expect(validateTransfer(f, NOW).ok).toBe(true); });
  it('rejects the same portfolio and a missing destination', () => {
    expect(validateTransfer({ ...f, toPortfolioId: '1' }, NOW).ok).toBe(false);
    expect(validateTransfer({ ...f, toPortfolioId: '' }, NOW).ok).toBe(false);
  });
});

describe('account helpers', () => {
  it('checks passwords and emails', () => {
    expect(validatePassword('short')).toBeTruthy();
    expect(validatePassword('long-enough-password')).toBeNull();
    expect(isEmail('a@b.co')).toBe(true);
    expect(isEmail('nope')).toBe(false);
  });

  it('builds a datetime-local value', () => {
    expect(toLocalInputValue(new Date(2026, 0, 5, 9, 7))).toBe('2026-01-05T09:07');
  });
});
