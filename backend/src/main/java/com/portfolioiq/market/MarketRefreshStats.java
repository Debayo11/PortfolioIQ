package com.portfolioiq.market;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/** The last refresh cycle run by this instance, for the administrator panel. */
@Component
public class MarketRefreshStats {
    private final AtomicReference<RefreshResult> last = new AtomicReference<>();

    public void record(RefreshResult r) { last.set(r); }

    public RefreshResult last() { return last.get(); }
}
