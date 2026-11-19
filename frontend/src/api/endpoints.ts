import { http } from './client';
import type * as T from './types';

type Range = T.RangeCode;

export const api = {
  auth: {
    login: (email: string, password: string) => http.post<T.AuthResponse>('/api/auth/login', { email, password }, false),
    register: (body: { email: string; password: string; firstName: string; lastName: string }) =>
      http.post<T.AuthResponse>('/api/auth/register', body, false),
    me: () => http.get<T.Me>('/api/auth/me'),
    verifyEmail: (token: string) => http.post<void>('/api/auth/verify-email', { token }, false),
    resendVerification: () => http.post<void>('/api/auth/resend-verification'),
    forgotPassword: (email: string) => http.post<void>('/api/auth/forgot-password', { email }, false),
    resetPassword: (token: string, newPassword: string) => http.post<void>('/api/auth/reset-password', { token, newPassword }, false),
  },
  portfolios: {
    list: () => http.get<T.Portfolio[]>('/api/portfolios'),
    get: (id: number) => http.get<T.Portfolio>(`/api/portfolios/${id}`),
    create: (body: T.PortfolioRequest) => http.post<T.Portfolio>('/api/portfolios', body),
    update: (id: number, body: T.PortfolioRequest) => http.put<T.Portfolio>(`/api/portfolios/${id}`, body),
    remove: (id: number) => http.del(`/api/portfolios/${id}`),
    summary: (id: number) => http.get<T.PortfolioSummary>(`/api/portfolios/${id}/summary`),
    holdings: (id: number) => http.get<T.Holding[]>(`/api/portfolios/${id}/holdings`),
    transactions: (id: number, q: { type?: string; assetId?: number; from?: string; to?: string; page?: number; size?: number }) =>
      http.get<T.PageResponse<T.Transaction>>(`/api/portfolios/${id}/transactions`, q),
    addTransaction: (id: number, body: T.CreateTransactionRequest) => http.post<T.Transaction>(`/api/portfolios/${id}/transactions`, body),
    deleteTransaction: (id: number, txId: number) => http.del(`/api/portfolios/${id}/transactions/${txId}`),
  },
  assets: {
    search: (q: string, page = 0, size = 20) => http.get<T.PageResponse<T.Asset>>('/api/assets', { q, page, size }),
    get: (id: number) => http.get<T.Asset>(`/api/assets/${id}`),
    create: (body: T.CreateAssetRequest) => http.post<T.Asset>('/api/assets', body),
  },
  market: {
    status: () => http.get<T.MarketStatus>('/api/market/status'),
    quote: (assetId: number) => http.get<T.Quote>(`/api/market/quotes/${assetId}`),
    quotes: (assetIds: number[]) => http.get<T.Quote[]>('/api/market/quotes', { assetIds }),
    history: (assetId: number, range: Range) => http.get<T.MarketHistory>(`/api/market/history/${assetId}`, { range }),
  },
  watchlists: {
    list: () => http.get<T.Watchlist[]>('/api/watchlists'),
    create: (name: string) => http.post<T.Watchlist>('/api/watchlists', { name }),
    rename: (id: number, name: string) => http.put<T.Watchlist>(`/api/watchlists/${id}`, { name }),
    remove: (id: number) => http.del(`/api/watchlists/${id}`),
    addItem: (id: number, assetId: number) => http.post<T.Watchlist>(`/api/watchlists/${id}/items`, { assetId }),
    removeItem: (id: number, assetId: number) => http.del<T.Watchlist>(`/api/watchlists/${id}/items/${assetId}`),
  },
  alerts: {
    list: () => http.get<T.PriceAlert[]>('/api/alerts'),
    create: (body: T.CreateAlertRequest) => http.post<T.PriceAlert>('/api/alerts', body),
    remove: (id: number) => http.del(`/api/alerts/${id}`),
  },
  notifications: {
    list: (unreadOnly = false, page = 0, size = 20) => http.get<T.PageResponse<T.AppNotification>>('/api/notifications', { unreadOnly, page, size }),
    unreadCount: () => http.get<{ unread: number }>('/api/notifications/unread-count'),
    markRead: (id: number) => http.post<void>(`/api/notifications/${id}/read`),
    markAllRead: () => http.post<{ marked: number }>('/api/notifications/read-all'),
  },
  transfers: {
    create: (body: T.TransferRequest) => http.post<T.TransferResult>('/api/transfers', body),
    undo: (transferId: string) => http.del(`/api/transfers/${transferId}`),
  },
  analytics: {
    dashboard: (q: { portfolioId?: number; currency?: string; range: Range }) => http.get<T.Dashboard>('/api/analytics/dashboard', q),
    performance: (q: { portfolioId?: number; currency?: string; range: Range; benchmarkAssetId?: number }) =>
      http.get<T.PerformanceReport>('/api/analytics/performance', q),
    allocation: (q: { portfolioId?: number; currency?: string }) => http.get<T.AllocationReport>('/api/analytics/allocation', q),
    assets: (q: { portfolioId?: number; currency?: string }) => http.get<T.AssetPerformance[]>('/api/analytics/assets', q),
    compare: (portfolioIds: number[], range: Range) => http.get<T.ComparisonReport>('/api/analytics/compare', { portfolioIds, range }),
  },
  admin: {
    users: (q: { q?: string; role?: string; enabled?: boolean; page?: number; size?: number }) =>
      http.get<T.PageResponse<T.AdminUser>>('/api/admin/users', q),
    setRoles: (id: number, roles: T.Role[]) => http.put<T.AdminUser>(`/api/admin/users/${id}/roles`, { roles }),
    setEnabled: (id: number, enabled: boolean) => http.put<T.AdminUser>(`/api/admin/users/${id}/enabled`, { enabled }),
    statistics: () => http.get<T.AdminStatistics>('/api/admin/statistics'),
    auditLogs: (q: { userId?: number; action?: string; from?: string; to?: string; page?: number; size?: number }) =>
      http.get<T.PageResponse<T.AuditEntry>>('/api/admin/audit-logs', q),
    auditActions: () => http.get<string[]>('/api/admin/audit-logs/actions'),
    logs: (q: { level?: string; q?: string; limit?: number }) => http.get<T.SystemLogEntry[]>('/api/admin/logs', q),
    apiUsage: () => http.get<T.ApiUsage>('/api/admin/api-usage'),
    market: () => http.get<T.MarketAdminView>('/api/admin/market'),
    updateMarket: (body: { refreshEnabled: boolean; maxRequestsPerRefresh: number }) => http.put<T.MarketAdminView>('/api/admin/market/settings', body),
    refreshMarket: () => http.post<T.RefreshResult>('/api/admin/market/refresh'),
  },
};
