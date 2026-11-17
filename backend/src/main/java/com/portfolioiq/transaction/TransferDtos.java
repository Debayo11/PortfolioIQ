package com.portfolioiq.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TransferDtos {
    private TransferDtos() {}

    public record TransferRequest(@NotNull Long fromPortfolioId, @NotNull Long toPortfolioId, @NotNull Long assetId,
                                  @NotNull BigDecimal quantity, @NotNull Instant executedAt, @Size(max = 500) String notes) {}

    /** unitCost is the carried-over cost basis per unit; no profit or loss is realized by a transfer. */
    public record TransferResponse(UUID transferId, Long fromPortfolioId, Long toPortfolioId, Long assetId,
                                   BigDecimal quantity, BigDecimal unitCost, BigDecimal totalCost, Instant executedAt) {}
}
