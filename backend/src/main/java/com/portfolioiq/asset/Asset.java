package com.portfolioiq.asset;

import jakarta.persistence.*;

@Entity
@Table(name = "assets")
public class Asset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String symbol;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 30)
    private AssetType assetType;

    @Column(length = 30)
    private String exchange;

    @Column(nullable = false, length = 3)
    private String currency;

    protected Asset() {}

    public Asset(String symbol, String name, AssetType assetType, String exchange, String currency) {
        this.symbol = symbol;
        this.name = name;
        this.assetType = assetType;
        this.exchange = exchange;
        this.currency = currency;
    }

    public Long getId() { return id; }
    public String getSymbol() { return symbol; }
    public String getName() { return name; }
    public AssetType getAssetType() { return assetType; }
    public String getExchange() { return exchange; }
    public String getCurrency() { return currency; }
}
