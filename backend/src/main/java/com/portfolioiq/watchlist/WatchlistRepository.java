package com.portfolioiq.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface WatchlistRepository extends JpaRepository<Watchlist, Long> {
    List<Watchlist> findByUserIdOrderByNameAsc(Long userId);

    Optional<Watchlist> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    long countByUserId(Long userId);

    @Query("select distinct i from Watchlist w join w.assetIds i")
    List<Long> allWatchedAssetIds();
}
