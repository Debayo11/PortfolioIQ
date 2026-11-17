package com.portfolioiq.market;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "market_prices")
public class MarketPrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false)
    private Long assetId;

    @Column(name = "priced_at", nullable = false)
    private Instant pricedAt;

    @Column(nullable = false, precision = 24, scale = 8)
    private BigDecimal price;

    @Column(name = "previous_close", precision = 24, scale = 8)
    private BigDecimal previousClose;

    private Long volume;

    @Column(name = "market_cap", precision = 24, scale = 2)
    private BigDecimal marketCap;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false)
    private boolean simulated;

    protected MarketPrice() {}

    public MarketPrice(Long assetId, Quote q) {
        this.assetId = assetId;
        this.pricedAt = q.asOf();
        this.price = q.price();
        this.previousClose = q.previousClose();
        this.volume = q.volume();
        this.marketCap = q.marketCap();
        this.source = q.source();
        this.simulated = q.simulated();
    }

    public Long getAssetId() { return assetId; }
    public Instant getPricedAt() { return pricedAt; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getPreviousClose() { return previousClose; }
    public Long getVolume() { return volume; }
    public BigDecimal getMarketCap() { return marketCap; }
    public String getSource() { return source; }
    public boolean isSimulated() { return simulated; }
}
