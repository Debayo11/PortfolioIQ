package com.portfolioiq.admin;

import com.portfolioiq.admin.AdminStatsDtos.DayCount;
import com.portfolioiq.admin.UsageAggregator.Sample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminHelpersTest {
    @Test void daySeriesFillsQuietDaysWithZero() {
        List<Object[]> rows = List.of(new Object[]{java.sql.Date.valueOf("2026-06-02"), 3L}, new Object[]{LocalDate.of(2026, 6, 4), 5});
        List<DayCount> out = DaySeries.fill(rows, LocalDate.of(2026, 6, 1), 5);
        assertEquals(5, out.size());
        assertEquals(0, out.get(0).count());
        assertEquals(3, out.get(1).count());
        assertEquals(0, out.get(2).count());
        assertEquals(5, out.get(3).count());
        assertEquals(LocalDate.of(2026, 6, 5), out.get(4).date());
    }

    @Test void daySeriesAcceptsTextDates() {
        assertEquals(LocalDate.of(2026, 1, 9), DaySeries.toLocalDate("2026-01-09"));
        assertEquals(LocalDate.of(2026, 1, 9), DaySeries.toLocalDate("2026-01-09 00:00:00"));
    }

    @Test void redactorMasksTokensKeysAndJwts() {
        String out = LogRedactor.redact("Reset link: http://x/reset?token=abc123XYZ&b=1 and password=hunter2 Bearer abc.def.ghi");
        assertFalse(out.contains("abc123XYZ"));
        assertFalse(out.contains("hunter2"));
        assertFalse(out.contains("Bearer abc"));
        assertTrue(out.contains("token=[redacted]"));
        String jwt = LogRedactor.redact("bad token eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhQGIuYyJ9.c2lnbmF0dXJl here");
        assertFalse(jwt.contains("eyJhbGci"));
        assertTrue(jwt.contains("[jwt redacted]"));
    }

    @Test void redactorCollapsesWhitespaceAndTruncates() {
        assertEquals("a b c", LogRedactor.redact("a\n  b\t c"));
        assertEquals(501, LogRedactor.redact("x".repeat(900)).length());
        assertEquals("", LogRedactor.redact(null));
    }

    @Test void logBufferKeepsNewestAndFilters() {
        LogBuffer buf = new LogBuffer(3);
        for (int i = 1; i <= 5; i++) {
            buf.add(new LogEntry(Instant.EPOCH.plusSeconds(i), i % 2 == 0 ? "ERROR" : "WARN", "svc", "event " + i, null));
        }
        assertEquals(3, buf.size());
        List<LogEntry> all = buf.snapshot("WARN", null, 10);
        assertEquals("event 5", all.get(0).message());
        assertEquals("event 3", all.get(2).message());
        assertEquals(1, buf.snapshot("ERROR", null, 10).size());
        assertEquals(1, buf.snapshot("WARN", "EVENT 5", 10).size());
        assertEquals(2, buf.snapshot("WARN", null, 2).size());
    }

    @Test void usageGroupsByEndpointAndCountsErrors() {
        var usage = UsageAggregator.aggregate(List.of(
                new Sample("GET", "/api/portfolios", 200, 8, 80, 20),
                new Sample("GET", "/api/portfolios", 500, 2, 40, 30),
                new Sample("POST", "/api/auth/login", 401, 5, 50, 15),
                new Sample("GET", "/actuator/health", 200, 100, 100, 5)), 10);
        assertEquals(15, usage.totalRequests());
        assertEquals(5, usage.clientErrors());
        assertEquals(2, usage.serverErrors());
        assertEquals(2, usage.endpoints().size());
        var top = usage.endpoints().get(0);
        assertEquals("/api/portfolios", top.uri());
        assertEquals(10, top.requests());
        assertEquals(0, new java.math.BigDecimal("12.0").compareTo(top.avgMillis()));
        assertEquals(0, new java.math.BigDecimal("30.0").compareTo(top.maxMillis()));
    }

    @Test void usageRespectsTheLimit() {
        var usage = UsageAggregator.aggregate(List.of(
                new Sample("GET", "/a", 200, 3, 3, 1), new Sample("GET", "/b", 200, 2, 2, 1), new Sample("GET", "/c", 200, 1, 1, 1)), 2);
        assertEquals(2, usage.endpoints().size());
        assertEquals("/a", usage.endpoints().get(0).uri());
    }
}
