package com.portfolioiq.portfolio;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "portfolios")
public class Portfolio {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Column(length = 50)
    private String category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Portfolio() {}

    public Portfolio(Long userId, String name, String description, String baseCurrency, String category) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.baseCurrency = baseCurrency;
        this.category = category;
    }

    public void update(String name, String description, String category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getBaseCurrency() { return baseCurrency; }
    public String getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
}
