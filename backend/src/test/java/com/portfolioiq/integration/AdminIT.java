package com.portfolioiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolioiq.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminIT extends IntegrationTestBase {
    private boolean hasRole(JsonNode roles, String role) {
        for (JsonNode r : roles) if (r.asText().equals(role)) return true;
        return false;
    }

    @Test void theBootstrapAddressBecomesTheFirstAdministrator() throws Exception {
        JsonNode me = get("/api/auth/me", adminToken(), 200);
        assertEquals(ADMIN_EMAIL, me.get("email").asText());
        assertTrue(hasRole(me.get("roles"), "ADMINISTRATOR"));
        assertTrue(me.get("emailVerified").asBoolean());
    }

    @Test void ordinaryUsersAndAnonymousCallersAreKeptOut() throws Exception {
        String investor = registerNew();
        for (String path : new String[]{"/api/admin/users", "/api/admin/statistics", "/api/admin/logs", "/api/admin/api-usage",
                "/api/admin/market", "/api/admin/audit-logs", "/api/admin/audit-logs/actions"}) {
            get(path, investor, 403);
            get(path, null, 401);
        }
        put("/api/admin/users/1/roles", investor, Map.of("roles", new String[]{"ADMINISTRATOR"}), 403);
        put("/api/admin/users/1/enabled", investor, Map.of("enabled", false), 403);
    }

    @Test void usersCanBeSearchedFilteredAndPaged() throws Exception {
        String admin = adminToken();
        String key = UUID.randomUUID().toString().substring(0, 8);
        String email = "findme-" + key + "@example.com";
        register(email);

        JsonNode found = get("/api/admin/users?q=" + key, admin, 200);
        assertEquals(1, found.get("totalElements").asInt());
        JsonNode u = found.get("content").get(0);
        assertEquals(email, u.get("email").asText());
        assertEquals("INVESTOR", u.get("roles").get(0).asText());
        assertEquals(0, u.get("portfolioCount").asInt());
        assertFalse(u.has("passwordHash"));

        JsonNode admins = get("/api/admin/users?role=ADMINISTRATOR&size=1", admin, 200);
        assertTrue(admins.get("totalElements").asInt() >= 1);
        assertEquals(ADMIN_EMAIL, get("/api/admin/users?q=boss@", admin, 200).get("content").get(0).get("email").asText());
        get("/api/admin/users?role=ROOT", admin, 400);
    }

    @Test void searchTextIsNeverTreatedAsAWildcard() throws Exception {
        String admin = adminToken();
        register(uniqueEmail());
        // sent as a real parameter so MockMvc does not re-encode it: '%' and '_' must match themselves only
        MvcResult r = mvc.perform(MockMvcRequestBuilders.get("/api/admin/users").param("q", "%_%")
                .header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andReturn();
        assertEquals(0, mapper.readTree(r.getResponse().getContentAsString()).get("totalElements").asInt());
    }

    @Test void rolesCanBeChangedAndTakeEffectImmediately() throws Exception {
        String admin = adminToken();
        String key = UUID.randomUUID().toString().substring(0, 8);
        String email = "role-" + key + "@example.com";
        String userToken = register(email);
        long id = get("/api/admin/users?q=" + key, admin, 200).get("content").get(0).get("id").asLong();

        post("/api/assets", userToken, Map.of("symbol", "RL" + key.substring(0, 4).toUpperCase(), "name", "X", "assetType", "STOCK"), 403);

        JsonNode updated = put("/api/admin/users/" + id + "/roles", admin, Map.of("roles", new String[]{"INVESTOR", "ANALYST"}), 200);
        assertEquals("ANALYST", updated.get("roles").get(0).asText());
        assertEquals("INVESTOR", updated.get("roles").get(1).asText());

        // the same token now carries analyst rights: roles come from the database, not from the token
        post("/api/assets", userToken, Map.of("symbol", "RL" + key.substring(0, 4).toUpperCase(), "name", "X", "assetType", "STOCK"), 201);

        put("/api/admin/users/" + id + "/roles", admin, Map.of("roles", new String[]{}), 400);
        put("/api/admin/users/" + id + "/roles", admin, Map.of("roles", new String[]{"ROOT"}), 400);
        put("/api/admin/users/99999999/roles", admin, Map.of("roles", new String[]{"INVESTOR"}), 404);
    }

    @Test void disabledAccountsAreLockedOutAtOnce() throws Exception {
        String admin = adminToken();
        String key = UUID.randomUUID().toString().substring(0, 8);
        String email = "lock-" + key + "@example.com";
        String userToken = register(email);
        long id = get("/api/admin/users?q=" + key, admin, 200).get("content").get(0).get("id").asLong();

        get("/api/auth/me", userToken, 200);
        assertFalse(put("/api/admin/users/" + id + "/enabled", admin, Map.of("enabled", false), 200).get("enabled").asBoolean());
        get("/api/auth/me", userToken, 401);                                                    // existing token stops working
        post("/api/auth/login", null, Map.of("email", email, "password", PASSWORD), 401);       // and so does signing in

        assertTrue(put("/api/admin/users/" + id + "/enabled", admin, Map.of("enabled", true), 200).get("enabled").asBoolean());
        get("/api/auth/me", userToken, 200);
    }

    @Test void administratorsCannotLockThemselvesOut() throws Exception {
        String admin = adminToken();
        long myId = get("/api/auth/me", admin, 200).get("id").asLong();
        assertTrue(put("/api/admin/users/" + myId + "/enabled", admin, Map.of("enabled", false), 409).get("message").asText().contains("your own"));
        assertTrue(put("/api/admin/users/" + myId + "/roles", admin, Map.of("roles", new String[]{"INVESTOR"}), 409).get("message").asText().contains("your own"));
        get("/api/auth/me", admin, 200); // still an administrator
    }

    @Test void everyAdministratorActionIsAudited() throws Exception {
        String admin = adminToken();
        String key = UUID.randomUUID().toString().substring(0, 8);
        register("audit-" + key + "@example.com");
        long id = get("/api/admin/users?q=" + key, admin, 200).get("content").get(0).get("id").asLong();
        put("/api/admin/users/" + id + "/enabled", admin, Map.of("enabled", false), 200);
        put("/api/admin/users/" + id + "/enabled", admin, Map.of("enabled", true), 200);

        JsonNode logs = get("/api/admin/audit-logs?action=ADMIN_USER_DISABLED", admin, 200);
        assertTrue(logs.get("totalElements").asInt() >= 1);
        assertEquals(ADMIN_EMAIL, logs.get("content").get(0).get("userEmail").asText());
        JsonNode actions = get("/api/admin/audit-logs/actions", admin, 200);
        assertTrue(actions.toString().contains("ADMIN_USER_ENABLED"));
        assertTrue(actions.toString().contains("ADMIN_BOOTSTRAPPED"));
        assertEquals(0, get("/api/admin/audit-logs?from=2999-01-01T00:00:00Z", admin, 200).get("totalElements").asInt());
    }

    @Test void statisticsUseRealQueriesAndCoverThirtyDays() throws Exception {
        String admin = adminToken();
        registerNew();
        JsonNode s = get("/api/admin/statistics", admin, 200);
        assertTrue(s.get("users").get("total").asInt() >= 2);
        assertEquals(30, s.get("registrationsPerDay").size());
        assertEquals(30, s.get("transactionsPerDay").size());
        assertTrue(s.get("users").get("byRole").toString().contains("ADMINISTRATOR"));
        assertTrue(s.get("registrationsPerDay").get(29).get("count").asInt() >= 1); // today
    }

    @Test void systemLogAndApiUsageAreAvailable() throws Exception {
        String admin = adminToken();
        get("/api/admin/logs?level=WARN", admin, 200);
        get("/api/admin/logs?level=INFO", admin, 400);
        get("/api/auth/me", admin, 200);
        JsonNode usage = get("/api/admin/api-usage", admin, 200);
        assertTrue(usage.get("totalRequests").asInt() > 0);
        assertFalse(usage.get("endpoints").toString().contains("/actuator"));
    }

    @Test void marketControlsWorkAndNeverExposeTheKey() throws Exception {
        String admin = adminToken();
        JsonNode view = get("/api/admin/market", admin, 200);
        assertEquals("SIMULATED", view.get("provider").asText());
        assertTrue(view.get("simulated").asBoolean());
        assertFalse(view.get("apiKeyConfigured").asBoolean());
        assertFalse(view.has("apiKey"));

        JsonNode saved = put("/api/admin/market/settings", admin, Map.of("refreshEnabled", false, "maxRequestsPerRefresh", 25), 200);
        assertEquals(25, saved.get("maxRequestsPerRefresh").asInt());
        assertFalse(saved.get("refreshEnabled").asBoolean());
        put("/api/admin/market/settings", admin, Map.of("refreshEnabled", false, "maxRequestsPerRefresh", 0), 400);
        put("/api/admin/market/settings", admin, Map.of("refreshEnabled", false, "maxRequestsPerRefresh", 501), 400);

        JsonNode run = post("/api/admin/market/refresh", admin, null, 200);
        assertFalse(run.get("skipped").asBoolean());
    }
}
