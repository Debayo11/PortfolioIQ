package com.portfolioiq.transaction;

import com.portfolioiq.finance.TransactionType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portfolio_id", nullable = false, updatable = false)
    private Long portfolioId;

    @Column(name = "asset_id", updatable = false)
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 24, scale = 8)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal fee;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Transaction() {}

    public Transaction(Long portfolioId, Long assetId, TransactionType type, BigDecimal quantity, BigDecimal unitPrice,
                       BigDecimal fee, BigDecimal totalAmount, Instant executedAt, String notes) {
        this.portfolioId = portfolioId;
        this.assetId = assetId;
        this.type = type;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.fee = fee;
        this.totalAmount = totalAmount;
        this.executedAt = executedAt;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public Long getPortfolioId() { return portfolioId; }
    public Long getAssetId() { return assetId; }
    public TransactionType getType() { return type; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getFee() { return fee; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public Instant getExecutedAt() { return executedAt; }
    public String getNotes() { return notes; }
}
