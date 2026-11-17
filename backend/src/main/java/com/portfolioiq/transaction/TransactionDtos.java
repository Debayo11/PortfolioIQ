package com.portfolioiq.transaction;

import com.portfolioiq.finance.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TransactionDtos {
    private TransactionDtos() {}

    /**
     * BUY/SELL/DIVIDEND: assetId, quantity and unitPrice (dividend per share) are required.
     * DEPOSIT/WITHDRAWAL/FEE: only amount is used. Numeric rules are enforced by TransactionRules.
     */
    public record CreateTransactionRequest(
            @NotNull TransactionType type,
            Long assetId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal fee,
            BigDecimal amount,
            @NotNull Instant executedAt,
            @Size(max = 500) String notes) {}

    public record TransactionResponse(Long id, Long portfolioId, Long assetId, String assetSymbol, TransactionType type,
                                      BigDecimal quantity, BigDecimal unitPrice, BigDecimal fee, BigDecimal totalAmount,
                                      Instant executedAt, String notes, UUID transferId) {}
}
