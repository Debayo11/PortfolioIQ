package com.portfolioiq.analytics;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.asset.AssetType;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.finance.TransactionType;
import com.portfolioiq.market.MarketDataProvider;
import com.portfolioiq.market.MarketDataService;
import com.portfolioiq.market.PricePoint;
import com.portfolioiq.market.Quote;
import com.portfolioiq.market.QuoteView;
import com.portfolioiq.portfolio.Portfolio;
import com.portfolioiq.portfolio.PortfolioRepository;
import com.portfolioiq.transaction.Transaction;
import com.portfolioiq.transaction.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {
    private static final long USER = 7L, PORTFOLIO = 1L, ASSET = 5L, BENCH = 9L;
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    private final PortfolioRepository portfolios = mock(PortfolioRepository.class);
    private final TransactionRepository txs = mock(TransactionRepository.class);
    private final AssetRepository assets = mock(AssetRepository.class);
    private final MarketDataService market = mock(MarketDataService.class);
    private final MarketDataProvider provider = mock(MarketDataProvider.class);
    private final Asset acme = new Asset("ACME", "Acme Corp", AssetType.STOCK, null, "USD");
    private final Asset bench = new Asset("INDEX", "Index fund", AssetType.ETF, null, "USD");
    private AnalyticsService service;

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(acme, "id", ASSET);
        ReflectionTestUtils.setField(bench, "id", BENCH);
        Portfolio p = new Portfolio(USER, "Main", null, "USD", null);
        ReflectionTestUtils.setField(p, "id", PORTFOLIO);
        when(portfolios.findByUserIdOrderByNameAsc(USER)).thenReturn(List.of(p));
        when(assets.findAllById(any())).thenReturn(List.of(acme));
        when(market.provider()).thenReturn(provider);
        when(provider.simulated()).thenReturn(false);
        when(provider.supportsHistory()).thenReturn(true);

        // ACME traded flat at 100 until yesterday; today's live quote is 120
        List<PricePoint> history = new ArrayList<>();
        for (LocalDate day = LocalDate.of(2026, 4, 1); day.isBefore(LocalDate.of(2026, 6, 1)); day = day.plusDays(1)) {
            history.add(new PricePoint(day.atStartOfDay().toInstant(ZoneOffset.UTC), d("100")));
        }
        when(market.history(eq(acme), anyInt())).thenReturn(history);
        when(market.history(eq(bench), anyInt())).thenReturn(List.of());
        when(market.requireAsset(BENCH)).thenReturn(bench);
        Quote quote = new Quote("ACME", d("120"), d("118"), null, null, NOW, "TEST", false);
        when(market.latestMany(any())).thenReturn(Map.of(ASSET, new QuoteView(ASSET, "ACME", quote, false)));

        Transaction buy = new Transaction(PORTFOLIO, ASSET, TransactionType.BUY, d("10"), d("100"), d("0"), d("1000"),
                Instant.parse("2026-05-22T10:00:00Z"), null);
        when(txs.findByPortfolioIdInAndTypeIn(any(), any())).thenReturn(List.of(buy));
        service = new AnalyticsService(portfolios, txs, assets, market, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void lifetimeAndPeriodFiguresMatchHandCalculation() {
        var report = service.performance(USER, PORTFOLIO, null, "30D", null);
        assertEquals(0, d("1200").compareTo(report.lifetime().marketValue()));
        assertEquals(0, d("200").compareTo(report.lifetime().unrealizedPnl()));
        assertEquals(0, d("20").compareTo(report.lifetime().simpleRoiPercent()));
        assertEquals(0, d("20").compareTo(report.window().timeWeightedReturnPercent()));
        assertEquals(31, report.series().size());
        assertEquals("PORTFOLIO", report.scope());
        assertTrue(report.disclaimer().contains("not projections"));
    }

    @Test
    void emptyScopeExplainsInsteadOfFailing() {
        when(txs.findByPortfolioIdInAndTypeIn(any(), any())).thenReturn(List.of());
        var report = service.performance(USER, PORTFOLIO, null, "30D", null);
        assertTrue(report.series().isEmpty());
        assertTrue(report.dataQuality().warnings().stream().anyMatch(w -> w.contains("no transactions")));
    }

    @Test
    void benchmarkWithoutHistoryIsReportedNotInvented() {
        var report = service.performance(USER, PORTFOLIO, null, "30D", BENCH);
        assertNull(report.benchmark());
        assertTrue(report.dataQuality().warnings().stream().anyMatch(w -> w.contains("no price history")));
    }

    @Test
    void someoneElsesPortfolioLooksLikeNotFound() {
        var ex = assertThrows(ApiException.class, () -> service.performance(USER, 99L, null, "30D", null));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void comparisonNeedsAtLeastTwoDifferentPortfolios() {
        assertThrows(ApiException.class, () -> service.compare(USER, List.of(PORTFOLIO, PORTFOLIO), "30D"));
    }

    @Test
    void invalidRangeIsRejected() {
        assertThrows(ApiException.class, () -> service.performance(USER, PORTFOLIO, null, "2W", null));
    }

    @Test
    void allocationOfOneHoldingIsFullyConcentrated() {
        var report = service.allocation(USER, PORTFOLIO, null);
        assertEquals(1, report.byAsset().size());
        assertEquals(0, d("100").compareTo(report.byAsset().get(0).weightPercent()));
        assertEquals("CONCENTRATED", report.diversification().level());
    }
}
