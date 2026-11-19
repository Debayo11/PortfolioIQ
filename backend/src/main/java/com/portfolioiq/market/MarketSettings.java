package com.portfolioiq.market;

/** Keys of the runtime settings an administrator can change. Defaults come from application.yml. */
public final class MarketSettings {
    public static final String KEY_ENABLED = "market.refresh.enabled";
    public static final String KEY_MAX_PER_CYCLE = "market.refresh.max_per_cycle";

    private MarketSettings() {}
}
