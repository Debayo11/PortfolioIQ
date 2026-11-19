package com.portfolioiq.admin;

import com.portfolioiq.market.RefreshResult;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class AdminMarketDtos {
    private AdminMarketDtos() {}

    /** The API key itself is never returned, only whether one is configured. */
    public record MarketAdminView(String provider, boolean simulated, boolean apiKeyConfigured, boolean refreshEnabled,
                                  int maxRequestsPerRefresh, long refreshIntervalMs, long cacheTtlSeconds,
                                  int trackedAssets, long storedPriceRows, RefreshResult lastRefresh, String note) {}

    public record MarketSettingsRequest(@NotNull Boolean refreshEnabled, @NotNull @Min(1) @Max(500) Integer maxRequestsPerRefresh) {}
}
