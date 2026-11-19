package com.portfolioiq.market;

import java.time.Instant;

/** Outcome of one price refresh cycle. */
public record RefreshResult(Instant startedAt, long durationMs, int tracked, int attempted, int succeeded, int failed,
                            boolean skipped, String note) {
    public static RefreshResult skipped(Instant at, String note) {
        return new RefreshResult(at, 0, 0, 0, 0, 0, true, note);
    }
}
