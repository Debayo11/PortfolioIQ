package com.portfolioiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolioiq.market.MarketRefreshService;
import com.portfolioiq.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MarketAlertIT extends IntegrationTestBase {
    @Autowired MarketRefreshService refreshService;

    @Test void quotesAreLabelledSimulatedWhenNoApiKeyIsConfigured() throws Exception {
        String user = registerNew();
        long asset = createAsset(adminToken(), "QTE");
        JsonNode status = get("/api/market/status", user, 200);
        assertTrue(status.get("simulated").asBoolean());

        JsonNode quote = get("/api/market/quotes/" + asset, user, 200);
        assertTrue(quote.get("simulated").asBoolean());
        assertEquals("SIMULATED", quote.get("source").asText());
        assertTrue(quote.get("price").asDouble() > 0);
        assertFalse(quote.get("stale").asBoolean());
        // a second call is served from the Redis cache and must agree
        assertEquals(quote.get("price").asDouble(), get("/api/market/quotes/" + asset, user, 200).get("price").asDouble(), 0.0);
    }

    @Test void historySupportsEveryRangeAndRejectsUnknownOnes() throws Exception {
        String user = registerNew();
        long asset = createAsset(adminToken(), "HST");
        assertEquals(24, get("/api/market/history/" + asset + "?range=24H", user, 200).get("points").size());
        assertEquals(30, get("/api/market/history/" + asset + "?range=30D", user, 200).get("points").size());
        assertEquals(365, get("/api/market/history/" + asset + "?range=1Y", user, 200).get("points").size());
        assertTrue(get("/api/market/history/" + asset + "?range=7D", user, 200).get("simulated").asBoolean());
        get("/api/market/history/" + asset + "?range=2W", user, 400);
        get("/api/market/history/99999999?range=7D", user, 404);
    }

    @Test void batchQuotesAreCapped() throws Exception {
        String user = registerNew();
        String admin = adminToken();
        long a = createAsset(admin, "BA"), b = createAsset(admin, "BB");
        assertEquals(2, get("/api/market/quotes?assetIds=" + a + "," + b, user, 200).size());
        StringBuilder many = new StringBuilder();
        for (int i = 1; i <= 51; i++) many.append(i).append(i < 51 ? "," : "");
        get("/api/market/quotes?assetIds=" + many, user, 400);
    }

    @Test void assetCatalogueIsSearchableAndProtected() throws Exception {
        String user = registerNew();
        long asset = createAsset(adminToken(), "SRCH");
        String symbol = get("/api/assets/" + asset, user, 200).get("symbol").asText();
        assertTrue(get("/api/assets?q=" + symbol.substring(0, 6).toLowerCase(), user, 200).get("totalElements").asInt() >= 1);
        post("/api/assets", user, Map.of("symbol", "NOPE1", "name", "x", "assetType", "STOCK"), 403);
        post("/api/assets", adminToken(), Map.of("symbol", symbol.toLowerCase(), "name", "dup", "assetType", "STOCK"), 409);
        post("/api/assets", adminToken(), Map.of("symbol", "bad symbol!", "name", "x", "assetType", "STOCK"), 400);
    }

    @Test void watchlistsBelongToTheirOwner() throws Exception {
        String owner = registerNew(), other = registerNew();
        long asset = createAsset(adminToken(), "WL");
        long list = post("/api/watchlists", owner, Map.of("name", "Tech"), 201).get("id").asLong();

        JsonNode withItem = post("/api/watchlists/" + list + "/items", owner, Map.of("assetId", asset), 200);
        assertEquals(1, withItem.get("items").size());
        assertFalse(withItem.get("items").get(0).get("quote").isNull());
        assertEquals(1, post("/api/watchlists/" + list + "/items", owner, Map.of("assetId", asset), 200).get("items").size()); // idempotent

        post("/api/watchlists", owner, Map.of("name", "tech"), 409);
        get("/api/watchlists/" + list, other, 404);
        post("/api/watchlists/" + list + "/items", other, Map.of("assetId", asset), 404);
        assertEquals(0, get("/api/watchlists", other, 200).size());

        assertEquals(0, delete("/api/watchlists/" + list + "/items/" + asset, owner, 200).get("items").size());
        delete("/api/watchlists/" + list, owner, 204);
    }

    @Test void aPriceAlertFiresOnceAndNotifiesItsOwner() throws Exception {
        String owner = registerNew(), other = registerNew();
        long asset = createAsset(adminToken(), "ALRT");
        JsonNode alert = post("/api/alerts", owner, Map.of("assetId", asset, "condition", "ABOVE", "targetPrice", 0.01), 201);
        assertTrue(alert.get("active").asBoolean());
        long alertId = alert.get("id").asLong();

        refreshService.refresh(asset);   // what the scheduled job does for each tracked asset

        JsonNode after = get("/api/alerts", owner, 200);
        assertFalse(after.get(0).get("active").asBoolean());
        assertFalse(after.get(0).get("triggeredAt").isNull());
        assertEquals(1, get("/api/notifications/unread-count", owner, 200).get("unread").asInt());
        JsonNode notes = get("/api/notifications", owner, 200);
        assertEquals("PRICE_ALERT", notes.get("content").get(0).get("type").asText());
        assertTrue(notes.get("content").get(0).get("message").asText().contains("simulated"));

        refreshService.refresh(asset);   // fires once only
        assertEquals(1, get("/api/notifications", owner, 200).get("totalElements").asInt());

        assertEquals(0, get("/api/alerts", other, 200).size());
        assertEquals(0, get("/api/notifications/unread-count", other, 200).get("unread").asInt());
        delete("/api/alerts/" + alertId, other, 404);

        post("/api/notifications/read-all", owner, null, 200);
        assertEquals(0, get("/api/notifications/unread-count", owner, 200).get("unread").asInt());
    }

    @Test void alertDefinitionsAreValidated() throws Exception {
        String user = registerNew();
        long asset = createAsset(adminToken(), "AVAL");
        post("/api/alerts", user, Map.of("assetId", asset, "condition", "PERCENT_MOVE"), 400);
        post("/api/alerts", user, Map.of("assetId", asset, "condition", "ABOVE"), 400);
        post("/api/alerts", user, Map.of("assetId", asset, "condition", "ABOVE", "targetPrice", 5, "thresholdPercent", 3), 400);
        post("/api/alerts", user, Map.of("assetId", 99999999L, "condition", "ABOVE", "targetPrice", 5), 400);
        post("/api/alerts", user, Map.of("assetId", asset, "condition", "PERCENT_MOVE", "thresholdPercent", 5), 201);
    }

    @Test void transactionsAndTransfersCreateNotifications() throws Exception {
        String owner = registerNew();
        long pf = createPortfolio(owner, "Notify", "USD");
        post("/api/portfolios/" + pf + "/transactions", owner,
                Map.of("type", "DEPOSIT", "amount", 100, "executedAt", java.time.Instant.now().minusSeconds(60).toString()), 201);
        JsonNode notes = get("/api/notifications", owner, 200);
        assertEquals("TRANSACTION_RECORDED", notes.get("content").get(0).get("type").asText());
    }
}
