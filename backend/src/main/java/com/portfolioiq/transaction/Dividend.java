package com.portfolioiq.transaction;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "dividends")
public class Dividend {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true)
    private Long transactionId;

    @Column(name = "portfolio_id", nullable = false)
    private Long portfolioId;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "ex_date")
    private LocalDate exDate;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    protected Dividend() {}

    public Dividend(Long transactionId, Long portfolioId, Long assetId, BigDecimal amount, LocalDate exDate, LocalDate payDate) {
        this.transactionId = transactionId;
        this.portfolioId = portfolioId;
        this.assetId = assetId;
        this.amount = amount;
        this.exDate = exDate;
        this.payDate = payDate;
    }

    public Long getId() { return id; }
    public BigDecimal getAmount() { return amount; }
}
