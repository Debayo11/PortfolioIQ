package com.portfolioiq.portfolio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class PortfolioDtos {
    private PortfolioDtos() {}

    /** baseCurrency is fixed at creation and ignored on update. */
    public record PortfolioRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a 3-letter currency code") String baseCurrency,
            @Size(max = 50) String category) {}

    public record PortfolioResponse(Long id, String name, String description, String baseCurrency,
                                    String category, Instant createdAt) {
        static PortfolioResponse of(Portfolio p) {
            return new PortfolioResponse(p.getId(), p.getName(), p.getDescription(), p.getBaseCurrency(),
                    p.getCategory(), p.getCreatedAt());
        }
    }

    public record HoldingResponse(Long assetId, String symbol, String name, BigDecimal quantity, BigDecimal averageCost,
                                  BigDecimal totalCost, BigDecimal realizedPnl, BigDecimal dividendIncome) {}

    public record PortfolioSummaryResponse(Long portfolioId, String baseCurrency, BigDecimal investedCost,
                                           BigDecimal realizedPnl, BigDecimal dividendIncome, BigDecimal totalFees,
                                           BigDecimal netDeposits, int openPositions, long transactionCount, String note) {}
}
