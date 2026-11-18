import { describe, expect, it } from 'vitest';
import { createSessionStore, decodeExpiry, type StorageLike } from './session';

function memory(): StorageLike & { data: Record<string, string> } {
  const data: Record<string, string> = {};
  return {
    data,
    getItem: (k) => (k in data ? data[k] : null),
    setItem: (k, v) => { data[k] = v; },
    removeItem: (k) => { delete data[k]; },
  };
}

function jwt(exp: number): string {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_');
  return `${b64({ alg: 'HS256' })}.${b64({ sub: 'a@b.c', exp })}.signature`;
}

describe('session', () => {
  it('reads the expiry from a token', () => {
    expect(decodeExpiry(jwt(2_000_000_000))).toBe(2_000_000_000_000);
    expect(decodeExpiry('not-a-jwt')).toBeNull();
    expect(decodeExpiry('a.b.c')).toBeNull();
  });

  it('saves and loads a valid session', () => {
    const store = createSessionStore(memory());
    const now = Date.parse('2026-06-01T00:00:00Z');
    store.save(jwt(Math.floor(now / 1000) + 3600), 3600, now);
    expect(store.load(now)?.token).toBeTruthy();
  });

  it('discards an expired session and removes it', () => {
    const mem = memory();
    const store = createSessionStore(mem);
    const now = Date.parse('2026-06-01T00:00:00Z');
    store.save(jwt(Math.floor(now / 1000) + 60), 60, now);
    expect(store.load(now + 120_000)).toBeNull();
    expect(Object.keys(mem.data).length).toBe(0);
  });

  it('falls back to the server-provided lifetime when the token cannot be decoded', () => {
    const store = createSessionStore(memory());
    const s = store.save('opaque-token', 600, 1_000);
    expect(s.expiresAt).toBe(601_000);
  });

  it('ignores corrupt storage and works without storage', () => {
    const mem = memory();
    mem.setItem('piq.session', '{broken');
    expect(createSessionStore(mem).load()).toBeNull();
    const none = createSessionStore(null);
    none.save('x', 10);
    expect(none.load()).toBeNull();
  });
});
