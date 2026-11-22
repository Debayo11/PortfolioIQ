package com.portfolioiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolioiq.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Runs against a real Redis with a very low limit. Uses its own application context. */
@TestPropertySource(properties = {"app.rate-limit.enabled=true", "app.rate-limit.auth-per-minute=3"})
class RateLimitIT extends IntegrationTestBase {
    @Test void repeatedSignInAttemptsAreThrottled() throws Exception {
        Map<String, String> badLogin = Map.of("email", "nobody@example.com", "password", "Wrong-password-1");
        int firstThrottled = -1;
        MvcResult throttled = null;
        // a fixed one-minute window may roll over mid-test, so allow a few extra attempts
        for (int attempt = 1; attempt <= 8 && throttled == null; attempt++) {
            MvcResult r = send(HttpMethod.POST, "/api/auth/login", null, badLogin).andReturn();
            if (r.getResponse().getStatus() == 429) { throttled = r; firstThrottled = attempt; }
            else assertEquals(401, r.getResponse().getStatus(), "before the limit the normal error is returned");
        }
        assertNotNull(throttled, "a 429 must appear after the limit is exceeded");
        assertTrue(firstThrottled <= 8);
        assertTrue(Integer.parseInt(throttled.getResponse().getHeader("Retry-After")) >= 1);
        JsonNode body = mapper.readTree(throttled.getResponse().getContentAsString());
        assertEquals(429, body.get("status").asInt());
        assertEquals("Too Many Requests", body.get("error").asText());
    }

    @Test void healthChecksAreNeverThrottled() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        }
    }
}
