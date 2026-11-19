package com.portfolioiq.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AdminStatsDtos {
    private AdminStatsDtos() {}

    public record DayCount(LocalDate date, long count) {}
    public record RoleCount(String role, long count) {}
    public record TypeCount(String type, long count) {}

    /** Amounts are in each portfolio's own base currency, so they are grouped by currency and never added across currencies. */
    public record VolumeRow(String currency, String type, long count, BigDecimal totalAmount) {}

    public record UserStats(long total, long enabled, long emailVerified, List<RoleCount> byRole) {}
    public record TransactionStats(long total, List<TypeCount> byType, List<VolumeRow> volume) {}

    public record Statistics(UserStats users, long portfolios, long assets, long watchlists, long activeAlerts,
                             TransactionStats transactions, List<DayCount> registrationsPerDay,
                             List<DayCount> transactionsPerDay, Instant generatedAt) {}
}
