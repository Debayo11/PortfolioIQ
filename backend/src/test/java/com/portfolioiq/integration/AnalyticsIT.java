package com.portfolioiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolioiq.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnalyticsIT extends IntegrationTestBase {
    private static String daysAgo(int d) { return Instant.now().minus(Duration.ofDays(d)).toString(); }

    private long portfolioWithHolding(String owner, long asset, String name, String currency) throws Exception {
        long pf = createPortfolio(owner, name, currency);
        post("/api/portfolios/" + pf + "/transactions", owner,
                Map.of("type", "BUY", "assetId", asset, "quantity", 10, "unitPrice", 100, "fee", 0, "executedAt", daysAgo(10)), 201);
        return pf;
    }

    @Test void performanceReportIsConsistentAndLabelled() throws Exception {
        long asset = createAsset(adminToken(), "PERF");
        String owner = registerNew();
        long pf = portfolioWithHolding(owner, asset, "Main", "USD");

        JsonNode r = get("/api/analytics/performance?portfolioId=" + pf + "&range=30D", owner, 200);
        assertEquals("PORTFOLIO", r.get("scope").asText());
        assertEquals(31, r.get("series").size());                      // 30 days plus the baseline day
        assertEquals(100.0, r.get("series").get(0).get("performanceIndex").asDouble(), 0.0001);
        assertTrue(r.get("dataQuality").get("pricesSimulated").asBoolean());
        assertTrue(r.get("disclaimer").asText().contains("not projections"));
        assertTrue(r.get("notes").size() >= 5);
        assertTrue(r.get("risk").get("returnObservations").asInt() >= 1);
        assertEquals(0.0, r.get("lifetime").get("realizedPnl").asDouble(), 0.0001);
        assertEquals(1000.0, r.get("lifetime").get("investedCost").asDouble(), 0.0001);

        // end value agrees with the live valuation (simulated prices move at most ~1.2% within a day)
        double price = get("/api/market/quotes/" + asset, owner, 200).get("price").asDouble();
        assertEquals(10 * price, r.get("lifetime").get("marketValue").asDouble(), 10 * price * 0.05);
        assertEquals(r.get("lifetime").get("unrealizedPnl").asDouble(), r.get("lifetime").get("totalReturn").asDouble(), 0.0001);
    }

    @Test void allocationAndPerAssetBreakdown() throws Exception {
        long asset = createAsset(adminToken(), "ALLOC");
        String owner = registerNew();
        long pf = portfolioWithHolding(owner, asset, "Main", "USD");

        JsonNode alloc = get("/api/analytics/allocation?portfolioId=" + pf, owner, 200);
        assertEquals(1, alloc.get("byAsset").size());
        assertEquals(100.0, alloc.get("byAsset").get(0).get("weightPercent").asDouble(), 0.01);
        assertEquals("CONCENTRATED", alloc.get("diversification").get("level").asText());

        JsonNode assets = get("/api/analytics/assets?portfolioId=" + pf, owner, 200);
        assertEquals(1, assets.size());
        assertTrue(assets.get(0).get("open").asBoolean());
        assertEquals(1000.0, assets.get(0).get("totalBought").asDouble(), 0.0001);
    }

    @Test void dashboardCombinesPortfoliosInOneCurrencyAndListsTheRest() throws Exception {
        long asset = createAsset(adminToken(), "DASH");
        String owner = registerNew();
        long usd = portfolioWithHolding(owner, asset, "Dollars", "USD");
        long eur = createPortfolio(owner, "Euros", "EUR");

        JsonNode d = get("/api/analytics/dashboard?currency=USD&range=30D", owner, 200);
        assertEquals("USD", d.get("currency").asText());
        assertEquals(1, d.get("totals").get("activeInvestments").asInt());
        assertEquals(usd, d.get("portfolioIds").get(0).asLong());
        assertEquals(eur, d.get("excludedPortfolios").get(0).get("id").asLong());   // never silently dropped, never mixed
        assertEquals(31, d.get("performance").get("series").size());
        assertEquals(asset, d.get("bestPerformer").get("assetId").asLong());
        assertTrue(d.get("recentTransactions").size() >= 1);
        assertFalse(d.get("movers").isNull());
    }

    @Test void benchmarkAndPortfolioComparison() throws Exception {
        String admin = adminToken();
        long asset = createAsset(admin, "BMK1");
        long benchmark = createAsset(admin, "BMK2");
        String owner = registerNew();
        long a = portfolioWithHolding(owner, asset, "A", "USD");
        long b = portfolioWithHolding(owner, asset, "B", "USD");

        JsonNode withBenchmark = get("/api/analytics/performance?portfolioId=" + a + "&range=30D&benchmarkAssetId=" + benchmark, owner, 200);
        assertFalse(withBenchmark.get("benchmark").isNull());
        assertEquals(31, withBenchmark.get("benchmark").get("series").size());
        assertEquals(withBenchmark.get("window").get("timeWeightedReturnPercent").asDouble() - withBenchmark.get("benchmark").get("returnPercent").asDouble(),
                withBenchmark.get("benchmark").get("excessReturnPercent").asDouble(), 0.001);

        JsonNode cmp = get("/api/analytics/compare?portfolioIds=" + a + "," + b + "&range=30D", owner, 200);
        assertEquals(2, cmp.get("portfolios").size());
        assertEquals(31, cmp.get("portfolios").get(0).get("series").size());
        assertEquals(cmp.get("portfolios").get(0).get("timeWeightedReturnPercent").asDouble(),
                cmp.get("portfolios").get(1).get("timeWeightedReturnPercent").asDouble(), 0.0001); // identical holdings
        get("/api/analytics/compare?portfolioIds=" + a, owner, 400);
    }

    @Test void invalidInputAndOtherPeoplesPortfoliosAreRejected() throws Exception {
        long asset = createAsset(adminToken(), "SEC");
        String owner = registerNew(), other = registerNew();
        long pf = portfolioWithHolding(owner, asset, "Mine", "USD");

        get("/api/analytics/performance?portfolioId=" + pf + "&range=2W", owner, 400);
        get("/api/analytics/performance?portfolioId=" + pf + "&benchmarkAssetId=99999999", owner, 404);
        get("/api/analytics/performance?portfolioId=" + pf, other, 404);
        get("/api/analytics/allocation?portfolioId=" + pf, other, 404);
        get("/api/analytics/compare?portfolioIds=" + pf + "," + pf, owner, 400);
        get("/api/analytics/dashboard?currency=dollars", owner, 400);
        get("/api/analytics/performance", null, 401);
    }

    @Test void aNewUserGetsEmptyButValidReports() throws Exception {
        String user = registerNew();
        JsonNode r = get("/api/analytics/performance?range=30D", user, 200);
        assertTrue(r.get("series").isEmpty());
        assertTrue(r.get("dataQuality").get("warnings").toString().contains("no transactions"));
        assertEquals(0, get("/api/analytics/assets", user, 200).size());
        assertEquals(0, get("/api/analytics/allocation", user, 200).get("byAsset").size());
    }
}
