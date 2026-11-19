package com.portfolioiq.admin;

import com.portfolioiq.admin.AdminStatsDtos.*;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.alert.PriceAlertRepository;
import com.portfolioiq.finance.TransactionType;
import com.portfolioiq.portfolio.PortfolioRepository;
import com.portfolioiq.transaction.TransactionRepository;
import com.portfolioiq.user.UserRepository;
import com.portfolioiq.watchlist.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class AdminStatsService {
    private static final int DAYS = 30;

    private final UserRepository users;
    private final PortfolioRepository portfolios;
    private final AssetRepository assets;
    private final WatchlistRepository watchlists;
    private final PriceAlertRepository alerts;
    private final TransactionRepository txs;
    private final Clock clock;

    public AdminStatsService(UserRepository users, PortfolioRepository portfolios, AssetRepository assets,
                             WatchlistRepository watchlists, PriceAlertRepository alerts, TransactionRepository txs, Clock clock) {
        this.users = users;
        this.portfolios = portfolios;
        this.assets = assets;
        this.watchlists = watchlists;
        this.alerts = alerts;
        this.txs = txs;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Statistics statistics() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate from = today.minusDays(DAYS - 1L);
        var since = from.atStartOfDay().toInstant(ZoneOffset.UTC);

        List<RoleCount> byRole = new ArrayList<>();
        for (Object[] row : users.countByRole()) byRole.add(new RoleCount(String.valueOf(row[0]), ((Number) row[1]).longValue()));
        byRole.sort(Comparator.comparing(RoleCount::role));
        UserStats userStats = new UserStats(users.count(), users.countByEnabledTrue(), users.countByEmailVerifiedTrue(), byRole);

        List<VolumeRow> volume = new ArrayList<>();
        Map<String, Long> byType = new TreeMap<>();
        long total = 0;
        for (Object[] row : txs.volumeByCurrencyAndType()) {
            String type = ((TransactionType) row[1]).name();
            long count = ((Number) row[2]).longValue();
            volume.add(new VolumeRow(String.valueOf(row[0]), type, count, row[3] == null ? BigDecimal.ZERO : (BigDecimal) row[3]));
            byType.merge(type, count, Long::sum);
            total += count;
        }
        volume.sort(Comparator.comparing(VolumeRow::currency).thenComparing(VolumeRow::type));
        List<TypeCount> typeCounts = byType.entrySet().stream().map(e -> new TypeCount(e.getKey(), e.getValue())).toList();

        return new Statistics(userStats, portfolios.count(), assets.count(), watchlists.count(), alerts.countByActiveTrue(),
                new TransactionStats(total, typeCounts, volume),
                DaySeries.fill(users.registrationsPerDay(since), from, DAYS),
                DaySeries.fill(txs.createdPerDay(since), from, DAYS), clock.instant());
    }
}
