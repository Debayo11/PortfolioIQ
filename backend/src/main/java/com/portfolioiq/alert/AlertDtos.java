package com.portfolioiq.alert;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class AlertDtos {
    private AlertDtos() {}

    /** ABOVE/BELOW use targetPrice. PERCENT_MOVE uses thresholdPercent (move versus previous close, either direction). */
    public record CreateAlertRequest(@NotNull Long assetId, @NotNull AlertCondition condition,
                                     BigDecimal targetPrice, BigDecimal thresholdPercent) {}

    public record AlertResponse(Long id, Long assetId, String assetSymbol, AlertCondition condition,
                                BigDecimal targetPrice, BigDecimal thresholdPercent, boolean active,
                                Instant triggeredAt, Instant createdAt) {}
}
