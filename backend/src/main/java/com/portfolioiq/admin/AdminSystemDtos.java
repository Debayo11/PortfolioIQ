package com.portfolioiq.admin;

import java.time.Instant;
import java.util.List;

public final class AdminSystemDtos {
    private AdminSystemDtos() {}

    /** Counts are for this server instance since it started; they reset on restart. */
    public record ApiUsage(Instant since, long uptimeSeconds, long totalRequests, long clientErrors, long serverErrors,
                           long rateLimitRejections, List<UsageAggregator.EndpointUsage> endpoints, String note) {}
}
