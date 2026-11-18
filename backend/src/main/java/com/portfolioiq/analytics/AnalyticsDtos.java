package com.portfolioiq.analytics;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record ExcludedPortfolio(Long id, String name, String currency) {}

    public record SeriesPoint(LocalDate date, BigDecimal marketValue, BigDecimal costBasis, BigDecimal performanceIndex) {}

    /** Whole-history figures. simpleRoiPercent is lifetime total return divided by everything paid into buys. */
    public record Lifetime(BigDecimal marketValue, BigDecimal investedCost, BigDecimal unrealizedPnl,
                           BigDecimal realizedPnl, BigDecimal dividendIncome, BigDecimal totalReturn,
                           BigDecimal simpleRoiPercent) {}

    /** Figures for the selected period only. */
    public record Window(BigDecimal startValue, BigDecimal endValue, BigDecimal netContributions,
                         BigDecimal dividends, BigDecimal timeWeightedReturnPercent) {}

    public record IndexPoint(LocalDate date, BigDecimal index) {}

    public record Benchmark(Long assetId, String symbol, String name, BigDecimal returnPercent,
                            BigDecimal excessReturnPercent, boolean simulated, List<IndexPoint> series) {}

    public record DataQuality(boolean pricesSimulated, int approximatedDays, int unpricedPositions, List<String> warnings) {}

    public record MetricNote(String metric, String explanation) {}

    public record PerformanceReport(String scope, String currency, List<Long> portfolioIds,
                                    List<ExcludedPortfolio> excludedPortfolios, String range, LocalDate from, LocalDate to,
                                    Lifetime lifetime, Window window, List<SeriesPoint> series, RiskMetrics.Risk risk,
                                    Benchmark benchmark, DataQuality dataQuality, List<MetricNote> notes, String disclaimer) {}

    public record AllocationSlice(String label, BigDecimal value, BigDecimal weightPercent, boolean valuedAtCost) {}

    public record AllocationReport(String currency, List<Long> portfolioIds, BigDecimal totalValue,
                                   List<AllocationSlice> byAsset, List<AllocationSlice> byAssetType,
                                   Diversification.Result diversification, List<String> warnings) {}

    /** unrealizedPnl and marketValue are null for an open position with no price. Closed positions have both realized and total return. */
    public record AssetPerformance(Long assetId, String symbol, String name, String assetType, BigDecimal quantity,
                                   BigDecimal investedCost, BigDecimal marketValue, BigDecimal unrealizedPnl,
                                   BigDecimal realizedPnl, BigDecimal dividendIncome, BigDecimal totalReturn,
                                   BigDecimal totalBought, BigDecimal returnPercent, boolean open, boolean priced) {}

    public record ComparedPortfolio(Long portfolioId, String name, String currency, BigDecimal timeWeightedReturnPercent,
                                    BigDecimal annualizedVolatilityPercent, BigDecimal maxDrawdownPercent,
                                    List<IndexPoint> series) {}

    public record ComparisonReport(String range, LocalDate from, LocalDate to, List<ComparedPortfolio> portfolios,
                                   List<String> warnings, String disclaimer) {}

    public record RecentTransaction(Long id, Long portfolioId, String type, String symbol, BigDecimal quantity,
                                    BigDecimal totalAmount, Instant executedAt) {}

    public record Mover(Long assetId, String symbol, BigDecimal price, BigDecimal changePercent, boolean simulated) {}

    public record Totals(BigDecimal marketValue, BigDecimal investedCost, BigDecimal totalReturn,
                         BigDecimal simpleRoiPercent, BigDecimal dayChange, BigDecimal dayChangePercent,
                         int activeInvestments, boolean pricesSimulated) {}

    public record Dashboard(String currency, List<Long> portfolioIds, List<ExcludedPortfolio> excludedPortfolios,
                            Totals totals, AssetPerformance bestPerformer, AssetPerformance worstPerformer,
                            List<RecentTransaction> recentTransactions, List<Mover> movers, AllocationReport allocation,
                            PerformanceReport performance) {}
}
