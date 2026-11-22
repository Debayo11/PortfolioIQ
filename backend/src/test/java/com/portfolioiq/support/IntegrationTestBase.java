package com.portfolioiq.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portfolioiq.auth.EmailService;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Starts one PostgreSQL 16 and one Redis 7 container for the whole test run (shared by every subclass) and boots the
 * real application against them, so migrations, queries, security and caching are exercised for real.
 * Tests are skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability   // keeps request metrics on so the API-usage endpoint can be tested
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(properties = {
        "app.jwt.secret=integration-test-secret-integration-test-secret-1234",
        "app.market.refresh-enabled=false",
        "app.rate-limit.enabled=false",
        "app.bootstrap-admin-email=" + IntegrationTestBase.ADMIN_EMAIL,
        "app.auth.require-verified-email=false",
})
public abstract class IntegrationTestBase {
    public static final String ADMIN_EMAIL = "boss@example.com";
    protected static final String PASSWORD = "Integration-pass-123";

    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> redis;
    private static volatile String cachedAdminToken;

    private static synchronized void startContainers() {
        if (postgres == null) {
            postgres = new PostgreSQLContainer<>("postgres:16-alpine");
            postgres.start();
        }
        if (redis == null) {
            redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
            redis.start();
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        startContainers();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @MockBean protected EmailService emailService;

    // ---- HTTP helpers ---------------------------------------------------------------------------------

    protected ResultActions send(HttpMethod method, String path, String token, Object body) throws Exception {
        MockHttpServletRequestBuilder b = request(method, path).accept(MediaType.APPLICATION_JSON);
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(b);
    }

    protected JsonNode json(ResultActions actions, int expectedStatus) throws Exception {
        MvcResult r = actions.andExpect(status().is(expectedStatus)).andReturn();
        String text = r.getResponse().getContentAsString();
        return text.isEmpty() ? mapper.nullNode() : mapper.readTree(text);
    }

    protected JsonNode get(String path, String token, int status) throws Exception { return json(send(HttpMethod.GET, path, token, null), status); }
    protected JsonNode post(String path, String token, Object body, int status) throws Exception { return json(send(HttpMethod.POST, path, token, body), status); }
    protected JsonNode put(String path, String token, Object body, int status) throws Exception { return json(send(HttpMethod.PUT, path, token, body), status); }
    protected JsonNode delete(String path, String token, int status) throws Exception { return json(send(HttpMethod.DELETE, path, token, null), status); }

    // ---- fixtures --------------------------------------------------------------------------------------

    protected String uniqueEmail() { return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com"; }

    protected Map<String, Object> registration(String email) {
        return Map.of("email", email, "password", PASSWORD, "firstName", "Test", "lastName", "User");
    }

    /** Registers a user and returns its token. */
    protected String register(String email) throws Exception {
        return post("/api/auth/register", null, registration(email), 201).get("token").asText();
    }

    protected String registerNew() throws Exception { return register(uniqueEmail()); }

    protected long createPortfolio(String token, String name, String currency) throws Exception {
        return post("/api/portfolios", token, Map.of("name", name, "baseCurrency", currency), 201).get("id").asLong();
    }

    protected long createAsset(String adminToken, String symbolPrefix) throws Exception {
        String symbol = (symbolPrefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6)).toUpperCase();
        return post("/api/assets", adminToken,
                Map.of("symbol", symbol, "name", symbol + " Corp", "assetType", "STOCK", "currency", "USD"), 201).get("id").asLong();
    }

    /** The token from the most recent email sent to this address (verification or password reset). */
    protected String emailedToken(String email) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, atLeastOnce()).send(eq(email), anyString(), body.capture());
        Matcher m = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(body.getValue());
        if (!m.find()) throw new AssertionError("No token in email body");
        return m.group(1);
    }

    /** An administrator, created through the real bootstrap flow: register, verify the email, role is granted. */
    protected String adminToken() throws Exception {
        String cached = cachedAdminToken;
        if (cached != null) return cached;
        synchronized (IntegrationTestBase.class) {
            if (cachedAdminToken == null) cachedAdminToken = createAdmin();
            return cachedAdminToken;
        }
    }

    private String createAdmin() throws Exception {
        MvcResult login = send(HttpMethod.POST, "/api/auth/login", null, Map.of("email", ADMIN_EMAIL, "password", PASSWORD)).andReturn();
        if (login.getResponse().getStatus() == 200) return mapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
        String token = register(ADMIN_EMAIL);
        post("/api/auth/verify-email", null, Map.of("token", emailedToken(ADMIN_EMAIL)), 204);
        return token; // roles are read from the database on every request, so this token now has administrator access
    }
}
