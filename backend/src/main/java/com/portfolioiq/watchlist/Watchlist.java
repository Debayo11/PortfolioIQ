package com.portfolioiq.watchlist;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "watchlists")
public class Watchlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    /** Rows of watchlist_items; its added_at column is filled by the database default. */
    @ElementCollection
    @CollectionTable(name = "watchlist_items", joinColumns = @JoinColumn(name = "watchlist_id"))
    @Column(name = "asset_id", nullable = false)
    private Set<Long> assetIds = new LinkedHashSet<>();

    protected Watchlist() {}

    public Watchlist(Long userId, String name) {
        this.userId = userId;
        this.name = name;
    }

    public void rename(String name) { this.name = name; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getName() { return name; }
    public Set<Long> getAssetIds() { return assetIds; }
}
