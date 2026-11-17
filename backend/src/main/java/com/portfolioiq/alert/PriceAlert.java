package com.portfolioiq.alert;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_alerts")
public class PriceAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private Long assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_type", nullable = false, length = 20)
    private AlertCondition condition;

    @Column(name = "target_price", precision = 24, scale = 8)
    private BigDecimal targetPrice;

    @Column(name = "threshold_percent", precision = 8, scale = 4)
    private BigDecimal thresholdPercent;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "triggered_at")
    private Instant triggeredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PriceAlert() {}

    public PriceAlert(Long userId, Long assetId, AlertCondition condition, BigDecimal targetPrice, BigDecimal thresholdPercent) {
        this.userId = userId;
        this.assetId = assetId;
        this.condition = condition;
        this.targetPrice = targetPrice;
        this.thresholdPercent = thresholdPercent;
    }

    /** Alerts fire once; the user creates a new one to keep watching. */
    public void trigger(Instant when) {
        this.active = false;
        this.triggeredAt = when;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getAssetId() { return assetId; }
    public AlertCondition getCondition() { return condition; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public BigDecimal getThresholdPercent() { return thresholdPercent; }
    public boolean isActive() { return active; }
    public Instant getTriggeredAt() { return triggeredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
