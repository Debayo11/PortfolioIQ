package com.portfolioiq.asset;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    boolean existsBySymbolIgnoreCase(String symbol);

    Page<Asset> findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(String symbol, String name, Pageable pageable);
}
