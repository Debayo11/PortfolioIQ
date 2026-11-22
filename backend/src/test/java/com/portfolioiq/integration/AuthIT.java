package com.portfolioiq.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.portfolioiq.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AuthIT extends IntegrationTestBase {
    @Test void registerLoginAndMe() throws Exception {
        String email = uniqueEmail();
        JsonNode reg = post("/api/auth/register", null, registration(email), 201);
        assertEquals("Bearer", reg.get("tokenType").asText());
        assertEquals("INVESTOR", reg.get("roles").get(0).asText());

        JsonNode login = post("/api/auth/login", null, Map.of("email", email.toUpperCase(), "password", PASSWORD), 200);
        JsonNode me = get("/api/auth/me", login.get("token").asText(), 200);
        assertEquals(email, me.get("email").asText());
        assertFalse(me.get("emailVerified").asBoolean());
        assertFalse(me.has("passwordHash"));
    }

    @Test void registeringTheSameAddressTwiceIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email);
        JsonNode err = post("/api/auth/register", null, registration(email.toUpperCase()), 409);
        assertEquals(409, err.get("status").asInt());
    }

    @Test void weakPasswordsAndBadEmailsGetFieldDetails() throws Exception {
        JsonNode err = post("/api/auth/register", null,
                Map.of("email", "not-an-email", "password", "short", "firstName", "A", "lastName", "B"), 400);
        assertEquals("Validation failed", err.get("message").asText());
        String details = err.get("details").toString();
        assertTrue(details.contains("password"));
        assertTrue(details.contains("email"));
    }

    @Test void wrongPasswordGivesAGenericMessage() throws Exception {
        String email = uniqueEmail();
        register(email);
        JsonNode bad = post("/api/auth/login", null, Map.of("email", email, "password", "Wrong-password-1"), 401);
        JsonNode unknown = post("/api/auth/login", null, Map.of("email", uniqueEmail(), "password", "Wrong-password-1"), 401);
        assertEquals("Invalid credentials", bad.get("message").asText());
        assertEquals(bad.get("message").asText(), unknown.get("message").asText()); // no account enumeration
    }

    @Test void protectedEndpointsNeedAValidToken() throws Exception {
        get("/api/portfolios", null, 401);
        get("/api/portfolios", "garbage.token.value", 401);
        String token = registerNew();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");
        get("/api/auth/me", tampered, 401);
    }

    @Test void unknownPathsUseTheCommonErrorShape() throws Exception {
        JsonNode err = get("/api/does-not-exist", registerNew(), 404);
        assertEquals(404, err.get("status").asInt());
        assertTrue(err.has("timestamp"));
    }

    @Test void emailVerificationTokenWorksOnce() throws Exception {
        String email = uniqueEmail();
        String token = register(email);
        String emailed = emailedToken(email);
        assertFalse(get("/api/auth/me", token, 200).get("emailVerified").asBoolean());

        post("/api/auth/verify-email", null, Map.of("token", emailed), 204);
        assertTrue(get("/api/auth/me", token, 200).get("emailVerified").asBoolean());
        post("/api/auth/verify-email", null, Map.of("token", emailed), 400);                 // single use
        post("/api/auth/verify-email", null, Map.of("token", "not-a-real-token"), 400);
    }

    @Test void passwordResetFlow() throws Exception {
        String email = uniqueEmail();
        register(email);
        post("/api/auth/forgot-password", null, Map.of("email", email), 202);
        String emailed = emailedToken(email);

        String newPassword = "A-brand-new-password-9";
        post("/api/auth/reset-password", null, Map.of("token", emailed, "newPassword", newPassword), 204);
        post("/api/auth/login", null, Map.of("email", email, "password", PASSWORD), 401);
        post("/api/auth/login", null, Map.of("email", email, "password", newPassword), 200);
        post("/api/auth/reset-password", null, Map.of("token", emailed, "newPassword", "Another-password-9"), 400); // single use
    }

    @Test void forgotPasswordNeverRevealsWhetherAnAccountExists() throws Exception {
        String unknown = uniqueEmail();
        post("/api/auth/forgot-password", null, Map.of("email", unknown), 202);
        verify(emailService, never()).send(eq(unknown), any(), any());
    }
}
