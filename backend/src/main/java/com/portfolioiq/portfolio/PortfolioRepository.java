package com.portfolioiq.portfolio;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** Every lookup is scoped by user id, so another user's portfolio is indistinguishable from a missing one. */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {
    List<Portfolio> findByUserIdOrderByNameAsc(Long userId);

    Optional<Portfolio> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndNameIgnoreCase(Long userId, String name);

    /** Row lock that serialises concurrent writes to the same portfolio (prevents double-selling the same shares). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Portfolio p where p.id = :id and p.userId = :userId")
    Optional<Portfolio> lockByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
