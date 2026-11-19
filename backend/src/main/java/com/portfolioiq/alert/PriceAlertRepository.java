package com.portfolioiq.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PriceAlertRepository extends JpaRepository<PriceAlert, Long> {
    List<PriceAlert> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<PriceAlert> findByIdAndUserId(Long id, Long userId);

    List<PriceAlert> findByAssetIdAndActiveTrue(Long assetId);

    long countByUserIdAndActiveTrue(Long userId);

    long countByActiveTrue();

    @Query("select distinct a.assetId from PriceAlert a where a.active = true")
    List<Long> activeAssetIds();
}
