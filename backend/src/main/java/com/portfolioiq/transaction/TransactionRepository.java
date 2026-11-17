package com.portfolioiq.transaction;

import com.portfolioiq.finance.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findByPortfolioIdAndAssetIdAndTypeIn(Long portfolioId, Long assetId, Collection<TransactionType> types);

    List<Transaction> findByPortfolioIdAndTypeIn(Long portfolioId, Collection<TransactionType> types);

    Optional<Transaction> findByIdAndPortfolioId(Long id, Long portfolioId);

    /** Rows of [type, sum(totalAmount), count]. */
    @Query("select t.type, sum(t.totalAmount), count(t) from Transaction t where t.portfolioId = :portfolioId group by t.type")
    List<Object[]> totalsByType(@Param("portfolioId") Long portfolioId);
}
