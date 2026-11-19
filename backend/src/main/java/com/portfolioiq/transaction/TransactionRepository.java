package com.portfolioiq.transaction;

import com.portfolioiq.finance.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findByPortfolioIdAndAssetIdAndTypeIn(Long portfolioId, Long assetId, Collection<TransactionType> types);

    List<Transaction> findByPortfolioIdAndTypeIn(Long portfolioId, Collection<TransactionType> types);

    Optional<Transaction> findByIdAndPortfolioId(Long id, Long portfolioId);

    List<Transaction> findByTransferGroup(UUID transferGroup);

    List<Transaction> findByPortfolioIdInAndTypeIn(Collection<Long> portfolioIds, Collection<TransactionType> types);

    List<Transaction> findTop5ByPortfolioIdInOrderByExecutedAtDescIdDesc(Collection<Long> portfolioIds);

    /** Rows of [portfolio currency, type, count, sum of amounts]. Sums stay per currency. */
    @Query("select p.baseCurrency, t.type, count(t), sum(t.totalAmount) from Transaction t, Portfolio p "
            + "where t.portfolioId = p.id group by p.baseCurrency, t.type")
    List<Object[]> volumeByCurrencyAndType();

    /** Rows of [day, count] of transactions recorded, in UTC. */
    @Query(value = "select cast((created_at at time zone 'UTC') as date) as day, count(*) from transactions "
            + "where created_at >= :from group by 1 order by 1", nativeQuery = true)
    List<Object[]> createdPerDay(@Param("from") java.time.Instant from);

    @Query("select distinct t.assetId from Transaction t where t.assetId is not null")
    List<Long> tradedAssetIds();

    /** Rows of [type, sum(totalAmount), count]. */
    @Query("select t.type, sum(t.totalAmount), count(t) from Transaction t where t.portfolioId = :portfolioId group by t.type")
    List<Object[]> totalsByType(@Param("portfolioId") Long portfolioId);
}
