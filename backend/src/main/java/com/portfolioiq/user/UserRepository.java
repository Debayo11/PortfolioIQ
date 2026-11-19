package com.portfolioiq.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select u.id from User u where lower(u.email) = lower(:email)")
    Optional<Long> findIdByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    long countByEnabledTrue();

    long countByEmailVerifiedTrue();

    /** Rows of [role, number of users]. */
    @Query("select r.name, count(distinct u) from User u join u.roles r group by r.name")
    List<Object[]> countByRole();

    @Query("select count(distinct u) from User u join u.roles r where r.name = :role and u.enabled = true")
    long countEnabledWithRole(@Param("role") RoleName role);

    /** Rows of [day, count] in UTC. */
    @Query(value = "select cast((created_at at time zone 'UTC') as date) as day, count(*) from users "
            + "where created_at >= :from group by 1 order by 1", nativeQuery = true)
    List<Object[]> registrationsPerDay(@Param("from") Instant from);
}
