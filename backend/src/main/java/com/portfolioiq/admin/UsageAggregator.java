package com.portfolioiq.admin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Summarises request metrics by endpoint. Pure so it can be tested without a metrics registry. */
public final class UsageAggregator {
    private UsageAggregator() {}

    /** One metric series: a method, a route template, a status code and its totals. */
    public record Sample(String method, String uri, int status, long count, double totalMillis, double maxMillis) {}

    public record EndpointUsage(String method, String uri, long requests, long clientErrors, long serverErrors,
                                BigDecimal avgMillis, BigDecimal maxMillis) {}

    public record Usage(long totalRequests, long clientErrors, long serverErrors, List<EndpointUsage> endpoints) {}

    private static final class Acc {
        long requests, client, server; double totalMs, maxMs;
    }

    public static Usage aggregate(List<Sample> samples, int limit) {
        Map<String, Acc> byEndpoint = new LinkedHashMap<>();
        long total = 0, client = 0, server = 0;
        for (Sample s : samples) {
            if (s.uri() == null || s.uri().startsWith("/actuator") || s.count() <= 0) continue;
            Acc a = byEndpoint.computeIfAbsent(s.method() + " " + s.uri(), k -> new Acc());
            a.requests += s.count();
            a.totalMs += s.totalMillis();
            a.maxMs = Math.max(a.maxMs, s.maxMillis());
            total += s.count();
            if (s.status() >= 500) { a.server += s.count(); server += s.count(); }
            else if (s.status() >= 400) { a.client += s.count(); client += s.count(); }
        }
        List<EndpointUsage> rows = new ArrayList<>();
        for (Map.Entry<String, Acc> e : byEndpoint.entrySet()) {
            String[] parts = e.getKey().split(" ", 2);
            Acc a = e.getValue();
            rows.add(new EndpointUsage(parts[0], parts[1], a.requests, a.client, a.server,
                    BigDecimal.valueOf(a.totalMs / a.requests).setScale(1, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(a.maxMs).setScale(1, RoundingMode.HALF_UP)));
        }
        rows.sort(Comparator.comparingLong(EndpointUsage::requests).reversed());
        return new Usage(total, client, server, rows.size() > limit ? rows.subList(0, limit) : rows);
    }
}
