package com.portfolioiq.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Periodically refreshes prices for tracked assets and evaluates alerts. A short-lived Redis lock stops several
 * app instances from refreshing at once. If Redis is unreachable the job runs anyway (fail open).
 */
@Component
public class MarketRefreshJob {
    private static final Logger log = LoggerFactory.getLogger(MarketRefreshJob.class);

    private final MarketRefreshService refresh;
    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int maxPerCycle;
    private final long lockSeconds;

    public MarketRefreshJob(MarketRefreshService refresh, StringRedisTemplate redis,
                            @Value("${app.market.refresh-enabled:true}") boolean enabled,
                            @Value("${app.market.max-requests-per-refresh:50}") int maxPerCycle,
                            @Value("${app.market.refresh-interval-ms:300000}") long intervalMs) {
        this.refresh = refresh;
        this.redis = redis;
        this.enabled = enabled;
        this.maxPerCycle = maxPerCycle;
        this.lockSeconds = Math.max(10, intervalMs / 1000 * 8 / 10);
    }

    @Scheduled(fixedDelayString = "${app.market.refresh-interval-ms:300000}",
               initialDelayString = "${app.market.initial-delay-ms:30000}")
    public void run() {
        if (!enabled || !acquireLock()) return;
        List<Long> ids = refresh.trackedAssetIds();
        int done = 0;
        for (Long id : ids) {
            if (done++ >= maxPerCycle) {
                log.info("Refresh capped at {} assets this cycle ({} tracked)", maxPerCycle, ids.size());
                break;
            }
            try {
                refresh.refresh(id);
            } catch (Exception e) {
                log.warn("Price refresh failed for asset {}: {}", id, e.getClass().getSimpleName());
            }
        }
    }

    private boolean acquireLock() {
        try {
            return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("market:refresh:lock", "1", Duration.ofSeconds(lockSeconds)));
        } catch (Exception e) {
            log.debug("Refresh lock unavailable, continuing without it");
            return true;
        }
    }
}
