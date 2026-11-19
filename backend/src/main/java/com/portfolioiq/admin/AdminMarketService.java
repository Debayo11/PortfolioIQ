package com.portfolioiq.admin;

import com.portfolioiq.admin.AdminMarketDtos.MarketAdminView;
import com.portfolioiq.audit.AuditService;
import com.portfolioiq.market.*;
import com.portfolioiq.settings.SettingsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime control of market data refreshing. The data provider and its API key are deployment settings (environment
 * variables) and are deliberately not editable here, so secrets never pass through the database or the browser.
 */
@Service
public class AdminMarketService {
    private final MarketDataProvider provider;
    private final SettingsService settings;
    private final MarketRefreshJob job;
    private final MarketRefreshStats stats;
    private final MarketRefreshService refresh;
    private final MarketPriceRepository prices;
    private final AuditService audit;
    private final boolean defaultEnabled;
    private final int defaultMax;
    private final long intervalMs;
    private final long cacheTtl;
    private final boolean keyConfigured;

    public AdminMarketService(MarketDataProvider provider, SettingsService settings, MarketRefreshJob job,
                              MarketRefreshStats stats, MarketRefreshService refresh, MarketPriceRepository prices,
                              AuditService audit,
                              @Value("${app.market.refresh-enabled:true}") boolean defaultEnabled,
                              @Value("${app.market.max-requests-per-refresh:50}") int defaultMax,
                              @Value("${app.market.refresh-interval-ms:300000}") long intervalMs,
                              @Value("${app.market.cache-ttl-seconds:60}") long cacheTtl,
                              @Value("${app.market.api-key:}") String apiKey) {
        this.provider = provider;
        this.settings = settings;
        this.job = job;
        this.stats = stats;
        this.refresh = refresh;
        this.prices = prices;
        this.audit = audit;
        this.defaultEnabled = defaultEnabled;
        this.defaultMax = defaultMax;
        this.intervalMs = intervalMs;
        this.cacheTtl = cacheTtl;
        this.keyConfigured = apiKey != null && !apiKey.isBlank();
    }

    @Transactional(readOnly = true)
    public MarketAdminView view() {
        return new MarketAdminView(provider.name(), provider.simulated(), keyConfigured,
                settings.getBoolean(MarketSettings.KEY_ENABLED, defaultEnabled),
                settings.getInt(MarketSettings.KEY_MAX_PER_CYCLE, defaultMax), intervalMs, cacheTtl,
                refresh.trackedAssetIds().size(), prices.count(), stats.last(),
                "The data provider and API key are set with the MARKET_DATA_API_KEY environment variable and need a restart to change. "
                        + "The refresh interval is set with MARKET_REFRESH_INTERVAL_MS. Settings here apply to this deployment's database.");
    }

    @Transactional
    public MarketAdminView update(Long adminId, boolean enabled, int maxPerCycle) {
        settings.set(MarketSettings.KEY_ENABLED, String.valueOf(enabled), adminId);
        settings.set(MarketSettings.KEY_MAX_PER_CYCLE, String.valueOf(maxPerCycle), adminId);
        audit.log(adminId, "ADMIN_MARKET_SETTINGS_CHANGED", "SETTINGS", "market",
                "refreshEnabled=" + enabled + ", maxRequestsPerRefresh=" + maxPerCycle);
        return view();
    }

    public RefreshResult refreshNow(Long adminId) {
        audit.log(adminId, "ADMIN_MARKET_REFRESH_TRIGGERED", "SETTINGS", "market", null);
        return job.runNow();
    }
}
