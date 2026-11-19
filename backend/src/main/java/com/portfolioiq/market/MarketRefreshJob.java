package com.portfolioiq.market;

import com.portfolioiq.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Periodically refreshes prices for tracked assets and evaluates alerts. An administrator can pause it, change its
 * per-cycle cap, or run a cycle immediately. A short-lived Redis lock stops several instances refreshing at once;
 * if Redis is unreachable the job runs anyway (fail open).
 */
@Component
public class MarketRefreshJob {
    private static final Logger log = LoggerFactory.getLogger(MarketRefreshJob.class);

    private final MarketRefreshService refresh;
    private final StringRedisTemplate redis;
    private final SettingsService settings;
    private final MarketRefreshStats stats;
    private final boolean defaultEnabled;
    private final int defaultMax;
    private final long lockSeconds;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MarketRefreshJob(MarketRefreshService refresh, StringRedisTemplate redis, SettingsService settings,
                            MarketRefreshStats stats,
                            @Value("${app.market.refresh-enabled:true}") boolean defaultEnabled,
                            @Value("${app.market.max-requests-per-refresh:50}") int defaultMax,
                            @Value("${app.market.refresh-interval-ms:300000}") long intervalMs) {
        this.refresh = refresh;
        this.redis = redis;
        this.settings = settings;
        this.stats = stats;
        this.defaultEnabled = defaultEnabled;
        this.defaultMax = defaultMax;
        this.lockSeconds = Math.max(10, intervalMs / 1000 * 8 / 10);
    }

    @Scheduled(fixedDelayString = "${app.market.refresh-interval-ms:300000}",
               initialDelayString = "${app.market.initial-delay-ms:30000}")
    public void run() {
        if (!settings.getBoolean(MarketSettings.KEY_ENABLED, defaultEnabled) || !acquireLock()) return;
        cycle();
    }

    /** Runs one cycle now, even if scheduled refreshing is paused. */
    public RefreshResult runNow() { return cycle(); }

    private RefreshResult cycle() {
        if (!running.compareAndSet(false, true)) {
            return RefreshResult.skipped(Instant.now(), "A refresh is already running on this server");
        }
        Instant started = Instant.now();
        try {
            List<Long> ids = refresh.trackedAssetIds();
            int max = settings.getInt(MarketSettings.KEY_MAX_PER_CYCLE, defaultMax);
            int attempted = 0, ok = 0, failed = 0;
            for (Long id : ids) {
                if (attempted >= max) break;
                attempted++;
                try {
                    refresh.refresh(id);
                    ok++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Price refresh failed for asset {}: {}", id, e.getClass().getSimpleName());
                }
            }
            String note = ids.size() > attempted ? "Capped at " + max + " of " + ids.size() + " assets this cycle" : null;
            RefreshResult result = new RefreshResult(started, Duration.between(started, Instant.now()).toMillis(),
                    ids.size(), attempted, ok, failed, false, note);
            stats.record(result);
            return result;
        } finally {
            running.set(false);
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
