/** Shapes returned by the PortfolioIQ API. Decimal values arrive as JSON numbers; dates as ISO strings. */

export type Role = 'ADMINISTRATOR' | 'INVESTOR' | 'ANALYST';
export type TransactionType = 'BUY' | 'SELL' | 'DIVIDEND' | 'DEPOSIT' | 'WITHDRAWAL' | 'FEE' | 'TRANSFER_IN' | 'TRANSFER_OUT';
export type AlertCondition = 'ABOVE' | 'BELOW' | 'PERCENT_MOVE';
export type AssetType = 'STOCK' | 'ETF' | 'BOND' | 'FUND' | 'CRYPTO';
export type RangeCode = '24H' | '7D' | '30D' | '3M' | '6M' | '1Y' | 'ALL';

export interface ApiErrorBody { timestamp: string; status: number; error: string; message: string; details: string[] }

export interface PageResponse<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export interface AuthResponse { token: string; tokenType: string; expiresInSeconds: number; email: string; roles: Role[] }
export interface Me { id: number; email: string; firstName: string; lastName: string; emailVerified: boolean; roles: Role[] }

export interface Portfolio { id: number; name: string; description: string | null; baseCurrency: string; category: string | null; createdAt: string }
export interface PortfolioRequest { name: string; description?: string; baseCurrency?: string; category?: string }

export interface Holding {
  assetId: number; symbol: string; name: string; quantity: number; averageCost: number; totalCost: number;
  realizedPnl: number; dividendIncome: number; price: number | null; marketValue: number | null;
  unrealizedPnl: number | null; dayChange: number | null; priceAsOf: string | null; priceSource: string | null;
  priceSimulated: boolean; priceStale: boolean;
}

export interface PortfolioSummary {
  portfolioId: number; baseCurrency: string; investedCost: number; marketValue: number; unrealizedPnl: number;
  realizedPnl: number; dividendIncome: number; totalFees: number; totalReturn: number; dayChange: number;
  netDeposits: number; openPositions: number; unpricedPositions: number; pricesSimulated: boolean;
  transactionCount: number; note: string;
}

export interface Transaction {
  id: number; portfolioId: number; assetId: number | null; assetSymbol: string | null; type: TransactionType;
  quantity: number; unitPrice: number; fee: number; totalAmount: number; executedAt: string; notes: string | null;
  transferId: string | null;
}
export interface CreateTransactionRequest {
  type: TransactionType; assetId?: number; quantity?: number; unitPrice?: number; fee?: number; amount?: number;
  executedAt: string; notes?: string;
}

export interface Asset { id: number; symbol: string; name: string; assetType: AssetType; exchange: string | null; currency: string }
export interface CreateAssetRequest { symbol: string; name: string; assetType: AssetType; exchange?: string; currency?: string }

export interface Quote {
  assetId: number; symbol: string; name: string; price: number; previousClose: number | null; change: number | null;
  changePercent: number | null; volume: number | null; marketCap: number | null; asOf: string; source: string;
  simulated: boolean; stale: boolean;
}
export interface PricePoint { time: string; price: number }
export interface MarketHistory { assetId: number; symbol: string; range: string; source: string; simulated: boolean; points: PricePoint[] }
export interface MarketStatus { provider: string; simulated: boolean; message: string }

export interface WatchlistItem { assetId: number; symbol: string; name: string; quote: Quote | null }
export interface Watchlist { id: number; name: string; items: WatchlistItem[] }

export interface PriceAlert {
  id: number; assetId: number; assetSymbol: string; condition: AlertCondition; targetPrice: number | null;
  thresholdPercent: number | null; active: boolean; triggeredAt: string | null; createdAt: string;
}
export interface CreateAlertRequest { assetId: number; condition: AlertCondition; targetPrice?: number; thresholdPercent?: number }

export interface AppNotification { id: number; type: string; title: string; message: string; read: boolean; createdAt: string }

export interface TransferRequest { fromPortfolioId: number; toPortfolioId: number; assetId: number; quantity: number; executedAt: string; notes?: string }
export interface TransferResult { transferId: string; fromPortfolioId: number; toPortfolioId: number; assetId: number; quantity: number; unitCost: number; totalCost: number; executedAt: string }

// ---- analytics ----
export interface ExcludedPortfolio { id: number; name: string; currency: string }
export interface SeriesPoint { date: string; marketValue: number; costBasis: number; performanceIndex: number }
export interface Lifetime { marketValue: number; investedCost: number; unrealizedPnl: number; realizedPnl: number; dividendIncome: number; totalReturn: number; simpleRoiPercent: number | null }
export interface WindowStats { startValue: number; endValue: number; netContributions: number; dividends: number; timeWeightedReturnPercent: number }
export interface IndexPoint { date: string; index: number }
export interface Benchmark { assetId: number; symbol: string; name: string; returnPercent: number; excessReturnPercent: number; simulated: boolean; series: IndexPoint[] }
export interface Risk {
  annualizedVolatilityPercent: number | null; maxDrawdownPercent: number | null; drawdownPeakDate: string | null;
  drawdownTroughDate: string | null; bestDayPercent: number | null; bestDay: string | null; worstDayPercent: number | null;
  worstDay: string | null; positiveDaysPercent: number | null; returnObservations: number;
}
export interface DataQuality { pricesSimulated: boolean; approximatedDays: number; unpricedPositions: number; warnings: string[] }
export interface MetricNote { metric: string; explanation: string }
export interface PerformanceReport {
  scope: string; currency: string; portfolioIds: number[]; excludedPortfolios: ExcludedPortfolio[]; range: string;
  from: string; to: string; lifetime: Lifetime; window: WindowStats; series: SeriesPoint[]; risk: Risk;
  benchmark: Benchmark | null; dataQuality: DataQuality; notes: MetricNote[]; disclaimer: string;
}
export interface AllocationSlice { label: string; value: number; weightPercent: number | null; valuedAtCost: boolean }
export interface Diversification { holdings: number; hhi: number | null; effectiveHoldings: number | null; topWeightPercent: number | null; top3WeightPercent: number | null; level: string; explanation: string }
export interface AllocationReport { currency: string; portfolioIds: number[]; totalValue: number; byAsset: AllocationSlice[]; byAssetType: AllocationSlice[]; diversification: Diversification; warnings: string[] }
export interface AssetPerformance {
  assetId: number; symbol: string; name: string; assetType: string; quantity: number; investedCost: number;
  marketValue: number | null; unrealizedPnl: number | null; realizedPnl: number; dividendIncome: number;
  totalReturn: number; totalBought: number; returnPercent: number | null; open: boolean; priced: boolean;
}
export interface ComparedPortfolio { portfolioId: number; name: string; currency: string; timeWeightedReturnPercent: number; annualizedVolatilityPercent: number | null; maxDrawdownPercent: number | null; series: IndexPoint[] }
export interface ComparisonReport { range: string; from: string; to: string; portfolios: ComparedPortfolio[]; warnings: string[]; disclaimer: string }
export interface RecentTransaction { id: number; portfolioId: number; type: string; symbol: string | null; quantity: number; totalAmount: number; executedAt: string }
export interface Mover { assetId: number; symbol: string; price: number; changePercent: number; simulated: boolean }
export interface Totals { marketValue: number; investedCost: number; totalReturn: number; simpleRoiPercent: number | null; dayChange: number; dayChangePercent: number | null; activeInvestments: number; pricesSimulated: boolean }
export interface Dashboard {
  currency: string; portfolioIds: number[]; excludedPortfolios: ExcludedPortfolio[]; totals: Totals;
  bestPerformer: AssetPerformance | null; worstPerformer: AssetPerformance | null; recentTransactions: RecentTransaction[];
  movers: Mover[]; allocation: AllocationReport; performance: PerformanceReport;
}

// ---- administration ----
export interface AdminUser {
  id: number; email: string; firstName: string; lastName: string; enabled: boolean; emailVerified: boolean;
  roles: Role[]; createdAt: string; portfolioCount: number;
}
export interface DayCount { date: string; count: number }
export interface AdminStatistics {
  users: { total: number; enabled: number; emailVerified: number; byRole: Array<{ role: string; count: number }> };
  portfolios: number; assets: number; watchlists: number; activeAlerts: number;
  transactions: { total: number; byType: Array<{ type: string; count: number }>; volume: Array<{ currency: string; type: string; count: number; totalAmount: number }> };
  registrationsPerDay: DayCount[]; transactionsPerDay: DayCount[]; generatedAt: string;
}
export interface AuditEntry {
  id: number; userId: number | null; userEmail: string | null; action: string; entityType: string | null;
  entityId: string | null; details: string | null; ipAddress: string | null; createdAt: string;
}
export interface SystemLogEntry { time: string; level: string; logger: string; message: string; exception: string | null }
export interface EndpointUsage { method: string; uri: string; requests: number; clientErrors: number; serverErrors: number; avgMillis: number; maxMillis: number }
export interface ApiUsage {
  since: string; uptimeSeconds: number; totalRequests: number; clientErrors: number; serverErrors: number;
  rateLimitRejections: number; endpoints: EndpointUsage[]; note: string;
}
export interface RefreshResult { startedAt: string; durationMs: number; tracked: number; attempted: number; succeeded: number; failed: number; skipped: boolean; note: string | null }
export interface MarketAdminView {
  provider: string; simulated: boolean; apiKeyConfigured: boolean; refreshEnabled: boolean; maxRequestsPerRefresh: number;
  refreshIntervalMs: number; cacheTtlSeconds: number; trackedAssets: number; storedPriceRows: number;
  lastRefresh: RefreshResult | null; note: string;
}
