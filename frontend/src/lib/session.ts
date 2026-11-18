/** Stores the sign-in token. Tokens are short-lived (the server decides), so an expired one is discarded on load. */

export interface Session { token: string; expiresAt: number }

export interface StorageLike {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
  removeItem(key: string): void;
}

const KEY = 'piq.session';

/** Reads the "exp" claim (seconds) from a JWT without verifying it. Verification is the server's job. */
export function decodeExpiry(token: string): number | null {
  try {
    const part = token.split('.')[1];
    if (!part) return null;
    const json = atob(part.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(part.length / 4) * 4, '='));
    const exp = (JSON.parse(json) as { exp?: unknown }).exp;
    return typeof exp === 'number' ? exp * 1000 : null;
  } catch {
    return null;
  }
}

export function createSessionStore(storage: StorageLike | null) {
  return {
    save(token: string, expiresInSeconds: number, now: number = Date.now()): Session {
      const session = { token, expiresAt: decodeExpiry(token) ?? now + expiresInSeconds * 1000 };
      try { storage?.setItem(KEY, JSON.stringify(session)); } catch { /* storage may be blocked (private mode) */ }
      return session;
    },
    load(now: number = Date.now()): Session | null {
      try {
        const raw = storage?.getItem(KEY);
        if (!raw) return null;
        const s = JSON.parse(raw) as Partial<Session>;
        if (typeof s.token !== 'string' || typeof s.expiresAt !== 'number' || s.expiresAt <= now + 5000) {
          storage?.removeItem(KEY);
          return null;
        }
        return { token: s.token, expiresAt: s.expiresAt };
      } catch {
        return null;
      }
    },
    clear(): void {
      try { storage?.removeItem(KEY); } catch { /* ignore */ }
    },
  };
}

function browserStorage(): StorageLike | null {
  try { return typeof window !== 'undefined' ? window.localStorage : null; } catch { return null; }
}

export const sessionStore = createSessionStore(browserStorage());
