package com.portfolioiq.market;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {
    Optional<MarketPrice> findFirstByAssetIdOrderByPricedAtDesc(Long assetId);

    boolean existsByAssetIdAndPricedAt(Long assetId, Instant pricedAt);

    List<MarketPrice> findByAssetIdAndPricedAtGreaterThanEqualOrderByPricedAtAsc(Long assetId, Instant from);
}
