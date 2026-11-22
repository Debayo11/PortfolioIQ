package com.portfolioiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolioiq.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class PortfolioFlowIT extends IntegrationTestBase {
    private static final Instant T0 = Instant.now().minus(Duration.ofDays(10));

    private static String at(int daysAfterT0) { return T0.plus(Duration.ofDays(daysAfterT0)).toString(); }

    private Map<String, Object> buy(long asset, int qty, int price, int fee, int day) {
        return Map.of("type", "BUY", "assetId", asset, "quantity", qty, "unitPrice", price, "fee", fee, "executedAt", at(day));
    }

    private Map<String, Object> sell(long asset, int qty, int price, int fee, int day) {
        return Map.of("type", "SELL", "assetId", asset, "quantity", qty, "unitPrice", price, "fee", fee, "executedAt", at(day));
    }

    @Test void buySellAndValuationMatchHandCalculation() throws Exception {
        long asset = createAsset(adminToken(), "ACME");
        String owner = registerNew();
        long pf = createPortfolio(owner, "Main", "USD");
        String tx = "/api/portfolios/" + pf + "/transactions";

        post(tx, owner, Map.of("type", "DEPOSIT", "amount", 10000, "executedAt", at(0)), 201);
        JsonNode bought = post(tx, owner, buy(asset, 10, 100, 1, 1), 201);
        assertEquals(1001.0, bought.get("totalAmount").asDouble(), 0.0001);
        JsonNode sold = post(tx, owner, sell(asset, 5, 120, 1, 2), 201);
        assertEquals(599.0, sold.get("totalAmount").asDouble(), 0.0001);

        JsonNode summary = get("/api/portfolios/" + pf + "/summary", owner, 200);
        assertEquals(98.5, summary.get("realizedPnl").asDouble(), 0.0001);        // 599 - 500.5
        assertEquals(500.5, summary.get("investedCost").asDouble(), 0.0001);
        assertEquals(1, summary.get("openPositions").asInt());
        assertEquals(10000.0, summary.get("netDeposits").asDouble(), 0.0001);
        assertTrue(summary.get("pricesSimulated").asBoolean());

        JsonNode holdings = get("/api/portfolios/" + pf + "/holdings", owner, 200);
        assertEquals(1, holdings.size());
        assertEquals(5.0, holdings.get(0).get("quantity").asDouble(), 0.0001);
        assertEquals(100.1, holdings.get(0).get("averageCost").asDouble(), 0.0001);
        assertTrue(holdings.get(0).get("priceSimulated").asBoolean());
    }

    @Test void sellingMoreThanHeldIsRejected() throws Exception {
        long asset = createAsset(adminToken(), "OVR");
        String owner = registerNew();
        long pf = createPortfolio(owner, "Main", "USD");
        String tx = "/api/portfolios/" + pf + "/transactions";
        post(tx, owner, buy(asset, 5, 10, 0, 1), 201);
        JsonNode err = post(tx, owner, sell(asset, 6, 10, 0, 2), 422);
        assertTrue(err.get("message").asText().contains("only 5 held"));
        // back-dated sale before the purchase is rejected too
        post(tx, owner, sell(asset, 1, 10, 0, 0), 422);
    }

    @Test void validationRejectsBadInput() throws Exception {
        long asset = createAsset(adminToken(), "VAL");
        String owner = registerNew();
        long pf = createPortfolio(owner, "Main", "USD");
        String tx = "/api/portfolios/" + pf + "/transactions";
        post(tx, owner, Map.of("type", "BUY", "assetId", asset, "quantity", 0, "unitPrice", 10, "executedAt", at(1)), 400);
        post(tx, owner, Map.of("type", "BUY", "assetId", asset, "quantity", "0.123456789", "unitPrice", 10, "executedAt", at(1)), 400);
        post(tx, owner, Map.of("type", "DEPOSIT", "assetId", asset, "amount", 5, "executedAt", at(1)), 400);
        post(tx, owner, Map.of("type", "DEPOSIT", "amount", 5, "executedAt", Instant.now().plus(Duration.ofDays(2)).toString()), 400);
        post(tx, owner, Map.of("type", "TRANSFER_IN", "assetId", asset, "quantity", 1, "unitPrice", 1, "executedAt", at(1)), 400);
        post(tx, owner, Map.of("type", "BUY", "assetId", 999999999L, "quantity", 1, "unitPrice", 1, "executedAt", at(1)), 400);
    }

    @Test void otherUsersCannotSeeOrTouchAPortfolio() throws Exception {
        String owner = registerNew();
        String intruder = registerNew();
        long pf = createPortfolio(owner, "Private", "USD");
        get("/api/portfolios/" + pf, intruder, 404);
        get("/api/portfolios/" + pf + "/summary", intruder, 404);
        get("/api/portfolios/" + pf + "/transactions", intruder, 404);
        post("/api/portfolios/" + pf + "/transactions", intruder, Map.of("type", "DEPOSIT", "amount", 1, "executedAt", at(1)), 404);
        delete("/api/portfolios/" + pf, intruder, 404);
        assertEquals(0, get("/api/portfolios", intruder, 200).size());
        assertEquals(1, get("/api/portfolios", owner, 200).size());
    }

    @Test void duplicatePortfolioNamesAreRejectedPerUser() throws Exception {
        String owner = registerNew();
        createPortfolio(owner, "Same", "USD");
        post("/api/portfolios", owner, Map.of("name", "same", "baseCurrency", "USD"), 409);
        createPortfolio(registerNew(), "Same", "USD"); // a different user may reuse the name
    }

    @Test void aPurchaseCannotBeDeletedWhileALaterSaleDependsOnIt() throws Exception {
        long asset = createAsset(adminToken(), "DEL");
        String owner = registerNew();
        long pf = createPortfolio(owner, "Main", "USD");
        String tx = "/api/portfolios/" + pf + "/transactions";
        long buyId = post(tx, owner, buy(asset, 5, 10, 0, 1), 201).get("id").asLong();
        long sellId = post(tx, owner, sell(asset, 5, 12, 0, 2), 201).get("id").asLong();
        assertTrue(delete(tx + "/" + buyId, owner, 409).get("message").asText().contains("later sales"));
        delete(tx + "/" + sellId, owner, 204);
        delete(tx + "/" + buyId, owner, 204);
    }

    @Test void filteringPagingAndSortingTransactions() throws Exception {
        long asset = createAsset(adminToken(), "PAG");
        String owner = registerNew();
        long pf = createPortfolio(owner, "Main", "USD");
        String tx = "/api/portfolios/" + pf + "/transactions";
        post(tx, owner, Map.of("type", "DEPOSIT", "amount", 100, "executedAt", at(0)), 201);
        post(tx, owner, buy(asset, 1, 10, 0, 1), 201);
        post(tx, owner, buy(asset, 1, 11, 0, 2), 201);

        JsonNode page = get(tx + "?size=2&page=0&sort=executedAt,asc", owner, 200);
        assertEquals(2, page.get("content").size());
        assertEquals(3, page.get("totalElements").asInt());
        assertEquals(2, page.get("totalPages").asInt());
        assertEquals("DEPOSIT", page.get("content").get(0).get("type").asText());
        assertEquals(2, get(tx + "?type=BUY", owner, 200).get("totalElements").asInt());
        assertEquals(1, get(tx + "?assetId=" + asset + "&from=" + at(2), owner, 200).get("totalElements").asInt());
        get(tx + "?sort=password,asc", owner, 400); // only whitelisted sort fields
    }

    @Test void transfersKeepCostBasisAndCanBeUndone() throws Exception {
        long asset = createAsset(adminToken(), "TRF");
        String owner = registerNew();
        long from = createPortfolio(owner, "From", "USD");
        long to = createPortfolio(owner, "To", "USD");
        long euro = createPortfolio(owner, "Euro", "EUR");
        post("/api/portfolios/" + from + "/transactions", owner, buy(asset, 5, 100, 0, 1), 201);

        JsonNode transfer = post("/api/transfers", owner, Map.of("fromPortfolioId", from, "toPortfolioId", to,
                "assetId", asset, "quantity", 2, "executedAt", at(2)), 201);
        assertEquals(100.0, transfer.get("unitCost").asDouble(), 0.0001);
        String transferId = transfer.get("transferId").asText();

        assertEquals(3.0, get("/api/portfolios/" + from + "/holdings", owner, 200).get(0).get("quantity").asDouble(), 0.0001);
        JsonNode dest = get("/api/portfolios/" + to + "/holdings", owner, 200).get(0);
        assertEquals(2.0, dest.get("quantity").asDouble(), 0.0001);
        assertEquals(200.0, dest.get("totalCost").asDouble(), 0.0001);
        assertEquals(0.0, get("/api/portfolios/" + from + "/summary", owner, 200).get("realizedPnl").asDouble(), 0.0001);

        long leg = get("/api/portfolios/" + from + "/transactions?type=TRANSFER_OUT", owner, 200).get("content").get(0).get("id").asLong();
        assertTrue(delete("/api/portfolios/" + from + "/transactions/" + leg, owner, 409).get("message").asText().contains("transfer"));

        post("/api/transfers", owner, Map.of("fromPortfolioId", from, "toPortfolioId", euro, "assetId", asset, "quantity", 1, "executedAt", at(3)), 400);
        post("/api/transfers", owner, Map.of("fromPortfolioId", from, "toPortfolioId", to, "assetId", asset, "quantity", 99, "executedAt", at(3)), 422);

        delete("/api/transfers/" + transferId, owner, 204);
        assertEquals(5.0, get("/api/portfolios/" + from + "/holdings", owner, 200).get(0).get("quantity").asDouble(), 0.0001);
        assertEquals(0, get("/api/portfolios/" + to + "/holdings", owner, 200).size());
    }

    @Test void simultaneousSalesOfTheSameSharesCannotBothSucceed() throws Exception {
        long asset = createAsset(adminToken(), "RACE");
        String owner = registerNew();
        long pf = createPortfolio(owner, "Race", "USD");
        String tx = "/api/portfolios/" + pf + "/transactions";
        post(tx, owner, buy(asset, 10, 100, 0, 1), 201);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return send(org.springframework.http.HttpMethod.POST, tx, owner, sell(asset, 10, 120, 0, 2)).andReturn().getResponse().getStatus();
            }));
        }
        go.countDown();
        int created = 0, rejected = 0;
        for (Future<Integer> f : results) {
            int status = f.get();
            if (status == 201) created++; else if (status == 422) rejected++;
        }
        pool.shutdown();
        assertEquals(1, created, "exactly one sale may succeed");
        assertEquals(threads - 1, rejected, "the others must be refused for lack of shares");
        assertEquals(0, get("/api/portfolios/" + pf + "/holdings", owner, 200).size());
    }

    @Test void deletingAPortfolioRemovesItsTransactions() throws Exception {
        String owner = registerNew();
        long pf = createPortfolio(owner, "Temp", "USD");
        post("/api/portfolios/" + pf + "/transactions", owner, Map.of("type", "DEPOSIT", "amount", 10, "executedAt", at(1)), 201);
        delete("/api/portfolios/" + pf, owner, 204);
        get("/api/portfolios/" + pf, owner, 404);
        Integer left = jdbc.queryForObject("select count(*) from transactions where portfolio_id = ?", Integer.class, pf);
        assertEquals(0, left);
    }
}
