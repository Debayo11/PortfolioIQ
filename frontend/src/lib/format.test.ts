import { describe, expect, it } from 'vitest';
import { EMPTY, fmtAgo, fmtDate, fmtMoney, fmtNumber, fmtPercent, humanize, tone, toneClass } from './format';

describe('format', () => {
  it('formats money and falls back for missing values', () => {
    expect(fmtMoney(1234.5, 'USD', 'en-US')).toBe('$1,234.50');
    expect(fmtMoney(null, 'USD', 'en-US')).toBe(EMPTY);
    expect(fmtMoney(undefined)).toBe(EMPTY);
  });

  it('shows a sign only when asked', () => {
    expect(fmtMoney(12, 'USD', 'en-US', true)).toBe('+$12.00');
    expect(fmtMoney(-12, 'USD', 'en-US', true)).toBe('-$12.00');
    expect(fmtMoney(0, 'USD', 'en-US', true)).toBe('$0.00');
  });

  it('survives an invalid currency code', () => {
    expect(fmtMoney(5, 'not-a-code', 'en-US')).toBe('5 not-a-code');
  });

  it('formats percentages and numbers', () => {
    expect(fmtPercent(12.5, false, 'en-US')).toBe('12.50%');
    expect(fmtPercent(12.5, true, 'en-US')).toBe('+12.50%');
    expect(fmtPercent(-3, true, 'en-US')).toBe('-3.00%');
    expect(fmtNumber(1.23456789, 4, 'en-US')).toBe('1.2346');
  });

  it('formats dates, including date-only values without time zone drift', () => {
    expect(fmtDate('2026-01-05', 'en-US')).toBe('Jan 5, 2026');
    expect(fmtDate(null)).toBe(EMPTY);
    expect(fmtDate('garbage')).toBe(EMPTY);
  });

  it('formats relative times', () => {
    const now = Date.parse('2026-06-01T12:00:00Z');
    expect(fmtAgo('2026-06-01T11:59:50Z', now)).toBe('just now');
    expect(fmtAgo('2026-06-01T11:30:00Z', now)).toBe('30m ago');
    expect(fmtAgo('2026-06-01T07:00:00Z', now)).toBe('5h ago');
    expect(fmtAgo('2026-05-29T12:00:00Z', now)).toBe('3d ago');
  });

  it('classifies gains and losses', () => {
    expect(tone(1)).toBe('gain');
    expect(tone(-1)).toBe('loss');
    expect(tone(0)).toBe('flat');
    expect(tone(null)).toBe('flat');
    expect(toneClass(5)).toBe('text-gain');
    expect(toneClass(-5)).toBe('text-loss');
  });

  it('humanizes enum values', () => {
    expect(humanize('PERCENT_MOVE')).toBe('Percent move');
  });
});
