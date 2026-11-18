import { sessionStore } from '../lib/session';
import type { ApiErrorBody } from './types';

const BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');

export class ApiError extends Error {
  status: number;
  details: string[];
  constructor(status: number, message: string, details: string[] = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.details = details;
  }
}

type QueryValue = string | number | boolean | null | undefined | Array<string | number>;
export interface RequestOptions { body?: unknown; query?: Record<string, QueryValue>; auth?: boolean }

let onUnauthorized: (() => void) | null = null;
/** Called when a signed-in request is rejected with 401 (expired or invalid token). */
export function setUnauthorizedHandler(fn: (() => void) | null): void { onUnauthorized = fn; }

export function buildQuery(query: Record<string, QueryValue> | undefined): string {
  if (!query) return '';
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === '') continue;
    params.set(key, Array.isArray(value) ? value.join(',') : String(value));
  }
  const s = params.toString();
  return s ? `?${s}` : '';
}

export async function request<T>(method: string, path: string, opts: RequestOptions = {}): Promise<T> {
  const auth = opts.auth !== false;
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const session = sessionStore.load();
    if (session) headers.Authorization = `Bearer ${session.token}`;
  }

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}${buildQuery(opts.query)}`, {
      method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
  }

  if (res.status === 204) return undefined as T;

  let data: unknown = null;
  const text = await res.text();
  if (text) {
    try { data = JSON.parse(text); } catch { data = null; }
  }

  if (!res.ok) {
    if (res.status === 401 && auth) {
      sessionStore.clear();
      onUnauthorized?.();
      throw new ApiError(401, 'Your session has expired. Please sign in again.');
    }
    const body = data as Partial<ApiErrorBody> | null;
    let message = body?.message ?? 'Something went wrong. Please try again.';
    if (res.status === 429) {
      const wait = res.headers.get('Retry-After');
      message = wait ? `Too many requests. Please wait ${wait} seconds and try again.` : 'Too many requests. Please wait a moment.';
    }
    throw new ApiError(res.status, message, body?.details ?? []);
  }
  return data as T;
}

export const http = {
  get: <T>(path: string, query?: Record<string, QueryValue>) => request<T>('GET', path, { query }),
  post: <T>(path: string, body?: unknown, auth = true) => request<T>('POST', path, { body: body ?? {}, auth }),
  put: <T>(path: string, body: unknown) => request<T>('PUT', path, { body }),
  del: <T = void>(path: string) => request<T>('DELETE', path),
};
