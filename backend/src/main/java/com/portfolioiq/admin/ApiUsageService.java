package com.portfolioiq.admin;

import com.portfolioiq.admin.AdminSystemDtos.ApiUsage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Reads the request metrics Spring Boot already records (http.server.requests); nothing extra is stored. */
@Service
public class ApiUsageService {
    private final MeterRegistry registry;
    private final Clock clock;

    public ApiUsageService(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    public ApiUsage usage() {
        List<UsageAggregator.Sample> samples = new ArrayList<>();
        for (Timer t : registry.find("http.server.requests").timers()) {
            String status = t.getId().getTag("status");
            int code;
            try { code = Integer.parseInt(status == null ? "0" : status); } catch (NumberFormatException e) { code = 0; }
            String method = t.getId().getTag("method");
            samples.add(new UsageAggregator.Sample(method == null ? "?" : method, t.getId().getTag("uri"), code, t.count(),
                    t.totalTime(TimeUnit.MILLISECONDS), t.max(TimeUnit.MILLISECONDS)));
        }
        long rejected = (long) registry.find("portfolioiq.ratelimit.rejected").counters().stream()
                .mapToDouble(Counter::count).sum();
        Instant since = Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
        UsageAggregator.Usage u = UsageAggregator.aggregate(samples, 50);
        return new ApiUsage(since, Duration.between(since, clock.instant()).toSeconds(), u.totalRequests(), u.clientErrors(),
                u.serverErrors(), rejected, u.endpoints(),
                "Counts are for this server instance since it started and reset on restart. Health checks are excluded.");
    }
}
