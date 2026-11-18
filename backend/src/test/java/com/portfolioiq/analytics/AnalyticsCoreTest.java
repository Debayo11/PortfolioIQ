package com.portfolioiq.analytics;

import com.portfolioiq.finance.TradeEvent;
import com.portfolioiq.finance.TradeEvent.Type;
import com.portfolioiq.market.PricePoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsCoreTest {
    private static final LocalDate D0 = LocalDate.of(2026, 1, 1);

    private static BigDecimal d(String v) { return new BigDecimal(v); }
    private static LocalDate day(int n) { return D0.plusDays(n); }
    private static Instant at(int n) { return day(n).atStartOfDay().toInstant(ZoneOffset.UTC); }
    private static AssetEvent ev(long asset, Type t, String q, String p, String fee, int n) {
        return new AssetEvent(asset, new TradeEvent(t, d(q), d(p), d(fee), at(n)));
    }
    private static PriceSeries prices(String... p) {
        List<PricePoint> pts = new ArrayList<>();
        for (int i = 0; i < p.length; i++) pts.add(new PricePoint(at(i), d(p[i])));
        return PriceSeries.of(pts);
    }
    private static void assertNum(String expected, BigDecimal actual) {
        assertEquals(0, d(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test void additionalMoneyDoesNotCountAsPerformance() {
        var series = SeriesBuilder.build(
                List.of(ev(1, Type.BUY, "10", "100", "0", 0), ev(1, Type.BUY, "10", "110", "0", 2)),
                Map.of(1L, prices("100", "110", "110", "121")), day(0), day(3));
        assertNum("2420", series.get(3).marketValue());
        assertNum("2100", series.get(3).costBasis());
        var r = Returns.timeWeighted(series);
        assertNum("21", r.twrPercent());   // 1.10 x 1.00 x 1.10
    }

    @Test void saleAndFeesAreTreatedAsOutflows() {
        var series = SeriesBuilder.build(
                List.of(ev(1, Type.BUY, "10", "100", "0", 0), ev(1, Type.SELL, "5", "120", "1", 2)),
                Map.of(1L, prices("100", "110", "120")), day(0), day(2));
        assertNum("-599", series.get(2).netFlow());
        assertNum("9", Returns.timeWeighted(series).dailyReturns().get(2).multiply(d("100")));
    }

    @Test void missingPricesFallBackToTradePriceAndAreFlagged() {
        var series = SeriesBuilder.build(List.of(ev(1, Type.BUY, "10", "100", "0", 0)), Map.of(), day(0), day(2));
        assertNum("1000", series.get(2).marketValue());
        assertTrue(series.get(2).approximated());
    }

    @Test void transfersBetweenOwnPortfoliosNetToZero() {
        var prices = Map.of(1L, prices("100", "110", "120"));
        var a = SeriesBuilder.build(List.of(ev(1, Type.BUY, "10", "100", "0", 0), ev(1, Type.TRANSFER_OUT, "4", "100", "0", 2)),
                prices, day(0), day(2));
        var b = SeriesBuilder.build(List.of(ev(1, Type.TRANSFER_IN, "4", "100", "0", 2)), prices, day(0), day(2));
        var both = SeriesBuilder.sum(List.of(a, b));
        assertNum("0", both.get(2).netFlow());
        assertNum("1200", both.get(2).marketValue());
        assertNum("0", both.get(2).dividends());
    }

    @Test void dividendsCountAsReturn() {
        var series = SeriesBuilder.build(
                List.of(ev(1, Type.BUY, "10", "100", "0", 0), ev(1, Type.DIVIDEND, "10", "1", "0", 1)),
                Map.of(1L, prices("100", "100")), day(0), day(1));
        assertNum("1", Returns.timeWeighted(series).twrPercent());   // 10 / 1000
    }

    @Test void maxDrawdownAndBestWorstDay() {
        var pts = new ArrayList<DayPoint>();
        String[] values = {"100", "120", "90", "110"};
        for (int i = 0; i < values.length; i++) {
            pts.add(new DayPoint(day(i), d(values[i]), d(values[i]), BigDecimal.ZERO, BigDecimal.ZERO, false));
        }
        var r = Returns.timeWeighted(pts);
        var risk = RiskMetrics.compute(pts, r);
        assertNum("-25", risk.maxDrawdownPercent());
        assertEquals(day(1), risk.drawdownPeakDate());
        assertEquals(day(2), risk.drawdownTroughDate());
        assertEquals(day(3), risk.bestDay());   // 90 -> 110 is +22.2%, better than 100 -> 120 (+20%)
        assertEquals(day(2), risk.worstDay());
        assertEquals(3, risk.returnObservations());
    }

    @Test void volatilityIsAnnualisedSampleStandardDeviation() {
        var pts = new ArrayList<DayPoint>();
        String[] values = {"100", "101", "100", "101", "100"};
        for (int i = 0; i < values.length; i++) {
            pts.add(new DayPoint(day(i), d(values[i]), d(values[i]), BigDecimal.ZERO, BigDecimal.ZERO, false));
        }
        var risk = RiskMetrics.compute(pts, Returns.timeWeighted(pts));
        assertNotNull(risk.annualizedVolatilityPercent());
        assertTrue(risk.annualizedVolatilityPercent().doubleValue() > 15 && risk.annualizedVolatilityPercent().doubleValue() < 18);
    }

    @Test void noHoldingsMeansNoRiskFigures() {
        var pts = List.of(new DayPoint(day(0), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false),
                new DayPoint(day(1), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
        var risk = RiskMetrics.compute(pts, Returns.timeWeighted(pts));
        assertNull(risk.annualizedVolatilityPercent());
        assertNull(risk.maxDrawdownPercent());
        assertEquals(0, risk.returnObservations());
    }

    @Test void priceIndexRebasesToOneHundred() {
        var idx = Returns.priceIndex(prices("50", "55", "44"), List.of(day(0), day(1), day(2)));
        assertNum("100", idx.get(0));
        assertNum("110", idx.get(1));
        assertNum("88", idx.get(2));
        assertNull(Returns.priceIndex(new PriceSeries(), List.of(day(0))));
    }

    @Test void diversificationLevels() {
        assertEquals("CONCENTRATED", Diversification.analyse(List.of(new Diversification.Weight("A", d("1")))).level());
        assertEquals("NONE", Diversification.analyse(List.of()).level());
        var equal10 = new ArrayList<Diversification.Weight>();
        for (int i = 0; i < 10; i++) equal10.add(new Diversification.Weight("X" + i, d("10")));
        var r = Diversification.analyse(equal10);
        assertEquals("DIVERSIFIED", r.level());
        assertNum("0.1", r.hhi());
        assertNum("10", r.effectiveHoldings());
        var four = new ArrayList<Diversification.Weight>();
        for (int i = 0; i < 4; i++) four.add(new Diversification.Weight("Y" + i, d("25")));
        assertEquals("MODERATE", Diversification.analyse(four).level());
    }

    @Test void totalBoughtIncludesFeesAndOptionallyTransfers() {
        var events = List.of(new TradeEvent(Type.BUY, d("10"), d("100"), d("1"), at(0)),
                new TradeEvent(Type.TRANSFER_IN, d("4"), d("100"), BigDecimal.ZERO, at(1)));
        assertNum("1001", Breakdown.totalBought(events, false));
        assertNum("1401", Breakdown.totalBought(events, true));
    }
}
