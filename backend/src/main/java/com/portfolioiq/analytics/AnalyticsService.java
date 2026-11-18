package com.portfolioiq.analytics;

import com.portfolioiq.analytics.AnalyticsDtos.*;
import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.finance.Position;
import com.portfolioiq.finance.PositionCalculator;
import com.portfolioiq.finance.TradeEvent;
import com.portfolioiq.market.HistoryRange;
import com.portfolioiq.market.MarketDataService;
import com.portfolioiq.market.Quote;
import com.portfolioiq.market.QuoteView;
import com.portfolioiq.portfolio.Portfolio;
import com.portfolioiq.portfolio.PortfolioRepository;
import com.portfolioiq.transaction.TradeEvents;
import com.portfolioiq.transaction.Transaction;
import com.portfolioiq.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Historical analytics derived from the transaction ledger and price history. Nothing here is a projection.
 * Several portfolios are only ever combined when they share a base currency; others are listed as excluded.
 */
@Service
public class AnalyticsService {
    static final String DISCLAIMER = "All figures describe past performance, calculated from your recorded transactions "
            + "and historical prices. They are not projections, forecasts or investment advice.";

    static final List<MetricNote> NOTES = List.of(
            new MetricNote("timeWeightedReturnPercent", "How the holdings grew over the period with the effect of adding or "
                    + "withdrawing money removed. It measures how the investments performed, not how much you contributed."),
            new MetricNote("simpleRoiPercent", "Lifetime total return (realized gains + unrealized gains + dividends) divided by "
                    + "everything you paid for buys, including fees. It ignores timing."),
            new MetricNote("annualizedVolatilityPercent", "How much the daily return typically swings, scaled to a year "
                    + "(252 trading days). Higher means a bumpier ride, not necessarily a worse one."),
            new MetricNote("maxDrawdownPercent", "The largest fall from a peak to a later low within the period."),
            new MetricNote("diversification", "Effective holdings is how many equally sized positions would give the same "
                    + "concentration. A single holding scores 1."),
            new MetricNote("benchmark", "Compares the time-weighted return with the plain price change of the benchmark "
                    + "asset you choose. Benchmark dividends are not included."),
            new MetricNote("realizedVsUnrealized", "Realized gains come from positions you sold. Unrealized gains are on paper "
                    + "for positions you still hold."));

    private static final int MAX_HISTORY_DAYS = 1825;
    private static final RiskMetrics.Risk EMPTY_RISK =
            new RiskMetrics.Risk(null, null, null, null, null, null, null, null, null, 0);

    private final PortfolioRepository portfolios;
    private final TransactionRepository txs;
    private final AssetRepository assets;
    private final MarketDataService market;
    private final Clock clock;

    public AnalyticsService(PortfolioRepository portfolios, TransactionRepository txs, AssetRepository assets,
                            MarketDataService market, Clock clock) {
        this.portfolios = portfolios;
        this.txs = txs;
        this.assets = assets;
        this.market = market;
        this.clock = clock;
    }

    // ---- internal state ---------------------------------------------------------------------

    private record Scope(List<Portfolio> portfolios, String currency, List<ExcludedPortfolio> excluded) {
        List<Long> ids() { return portfolios.stream().map(Portfolio::getId).toList(); }
        boolean single() { return portfolios.size() == 1; }
    }

    private record Book(List<AssetPerformance> rows, Map<Long, QuoteView> quotes, Map<Long, Asset> assets) {}

    private record Context(Scope scope, List<Transaction> transactions, Book book) {}

    private record Computed(List<DayPoint> points, Returns.Result returns, RiskMetrics.Risk risk,
                            Map<Long, PriceSeries> prices, int daysBack) {}

    private static final class Agg {
        BigDecimal qty = BigDecimal.ZERO, cost = BigDecimal.ZERO, realized = BigDecimal.ZERO, dividends = BigDecimal.ZERO;
    }

    // ---- public API -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PerformanceReport performance(Long userId, Long portfolioId, String currency, String range, Long benchmarkAssetId) {
        return performance(context(userId, portfolioId, currency), HistoryRange.parse(range), benchmarkAssetId);
    }

    @Transactional(readOnly = true)
    public AllocationReport allocation(Long userId, Long portfolioId, String currency) {
        return allocation(context(userId, portfolioId, currency));
    }

    @Transactional(readOnly = true)
    public List<AssetPerformance> assetPerformance(Long userId, Long portfolioId, String currency) {
        return context(userId, portfolioId, currency).book().rows();
    }

    @Transactional(readOnly = true)
    public ComparisonReport compare(Long userId, List<Long> portfolioIds, String range) {
        HistoryRange r = HistoryRange.parse(range);
        List<Long> ids = portfolioIds.stream().distinct().toList();
        if (ids.size() < 2 || ids.size() > 5) throw bad("Provide between 2 and 5 different portfolio ids");

        List<Context> contexts = ids.stream().map(id -> context(userId, id, null)).toList();
        LocalDate end = today();
        LocalDate start = windowStart(r, end, contexts.stream().map(c -> firstEventDate(c.transactions()))
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));

        List<ComparedPortfolio> out = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Context c : contexts) {
            Portfolio p = c.scope().portfolios().get(0);
            if (c.transactions().isEmpty()) {
                warnings.add("Portfolio \"" + p.getName() + "\" has no transactions and is shown flat.");
            }
            Computed comp = compute(c, start, end);
            out.add(new ComparedPortfolio(p.getId(), p.getName(), p.getBaseCurrency(), comp.returns().twrPercent(),
                    comp.risk().annualizedVolatilityPercent(), comp.risk().maxDrawdownPercent(),
                    indexSeries(comp.points(), comp.returns().index())));
        }
        if (out.stream().map(ComparedPortfolio::currency).distinct().count() > 1) {
            warnings.add("Portfolios use different base currencies. Returns are percentages, but currency movements are not adjusted for.");
        }
        return new ComparisonReport(r.name(), start, end, out, warnings, DISCLAIMER);
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(Long userId, Long portfolioId, String currency, String range) {
        Context ctx = context(userId, portfolioId, currency);
        Book book = ctx.book();
        PerformanceReport perf = performance(ctx, HistoryRange.parse(range), null);
        AllocationReport alloc = allocation(ctx);

        BigDecimal marketValue = BigDecimal.ZERO, invested = BigDecimal.ZERO, totalReturn = BigDecimal.ZERO,
                bought = BigDecimal.ZERO, dayChange = BigDecimal.ZERO;
        int active = 0;
        boolean simulated = false;
        for (AssetPerformance r : book.rows()) {
            invested = invested.add(r.investedCost());
            totalReturn = totalReturn.add(r.totalReturn());
            bought = bought.add(r.totalBought());
            if (!r.open()) continue;
            active++;
            if (r.marketValue() != null) marketValue = marketValue.add(r.marketValue());
            QuoteView q = book.quotes().get(r.assetId());
            if (q != null) {
                simulated |= q.quote().simulated();
                if (q.quote().previousClose() != null) {
                    dayChange = dayChange.add(r.quantity().multiply(q.quote().price().subtract(q.quote().previousClose())));
                }
            }
        }
        dayChange = dayChange.setScale(4, RoundingMode.HALF_UP);
        BigDecimal previousValue = marketValue.subtract(dayChange);
        Totals totals = new Totals(marketValue, invested, totalReturn, percent(totalReturn, bought),
                dayChange, percent(dayChange, previousValue), active, simulated);

        List<AssetPerformance> ranked = book.rows().stream().filter(r -> r.returnPercent() != null)
                .sorted(Comparator.comparing(AssetPerformance::returnPercent)).toList();
        AssetPerformance worst = ranked.isEmpty() ? null : ranked.get(0);
        AssetPerformance best = ranked.isEmpty() ? null : ranked.get(ranked.size() - 1);

        return new Dashboard(ctx.scope().currency(), ctx.scope().ids(), ctx.scope().excluded(), totals, best, worst,
                recent(ctx.scope()), movers(book), alloc, perf);
    }

    // ---- context & ledger -------------------------------------------------------------------

    private Context context(Long userId, Long portfolioId, String currency) {
        Scope scope = scope(userId, portfolioId, currency);
        List<Transaction> list = scope.portfolios().isEmpty() ? List.of()
                : txs.findByPortfolioIdInAndTypeIn(scope.ids(), TradeEvents.ASSET_TYPES);
        return new Context(scope, list, book(scope, list));
    }

    private Scope scope(Long userId, Long portfolioId, String currency) {
        List<Portfolio> mine = portfolios.findByUserIdOrderByNameAsc(userId);
        if (portfolioId != null) {
            Portfolio p = mine.stream().filter(x -> Objects.equals(x.getId(), portfolioId)).findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Portfolio not found"));
            return new Scope(List.of(p), p.getBaseCurrency(), List.of());
        }
        String cur;
        if (currency != null && !currency.isBlank()) {
            if (!currency.matches("^[A-Za-z]{3}$")) throw bad("currency must be a 3-letter code");
            cur = currency.toUpperCase(Locale.ROOT);
        } else {
            cur = mostCommonCurrency(mine);
        }
        List<Portfolio> inScope = mine.stream().filter(p -> p.getBaseCurrency().equals(cur)).toList();
        List<ExcludedPortfolio> excluded = mine.stream().filter(p -> !p.getBaseCurrency().equals(cur))
                .map(p -> new ExcludedPortfolio(p.getId(), p.getName(), p.getBaseCurrency())).toList();
        return new Scope(inScope, cur, excluded);
    }

    /** The currency used by most portfolios; ties go to the alphabetically first. */
    private static String mostCommonCurrency(List<Portfolio> mine) {
        Map<String, Long> counts = new TreeMap<>();
        for (Portfolio p : mine) counts.merge(p.getBaseCurrency(), 1L, Long::sum);
        String best = "USD";
        long bestCount = 0;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            if (e.getValue() > bestCount) { best = e.getKey(); bestCount = e.getValue(); }
        }
        return best;
    }

    private Book book(Scope scope, List<Transaction> list) {
        Map<Long, Map<Long, List<TradeEvent>>> byPortfolioAsset = new HashMap<>();
        Map<Long, List<TradeEvent>> byAsset = new HashMap<>();
        for (Transaction t : list) {
            TradeEvent e = TradeEvents.from(t);
            byPortfolioAsset.computeIfAbsent(t.getPortfolioId(), k -> new HashMap<>())
                    .computeIfAbsent(t.getAssetId(), k -> new ArrayList<>()).add(e);
            byAsset.computeIfAbsent(t.getAssetId(), k -> new ArrayList<>()).add(e);
        }
        Map<Long, Agg> agg = new HashMap<>();
        byPortfolioAsset.values().forEach(m -> m.forEach((assetId, events) -> {
            Position p = PositionCalculator.calculate(events);
            Agg a = agg.computeIfAbsent(assetId, k -> new Agg());
            a.qty = a.qty.add(p.quantity());
            a.cost = a.cost.add(p.totalCost());
            a.realized = a.realized.add(p.realizedPnl());
            a.dividends = a.dividends.add(p.dividendIncome());
        }));

        Map<Long, Asset> assetMap = assets.findAllById(agg.keySet()).stream().collect(Collectors.toMap(Asset::getId, a -> a));
        List<Asset> open = agg.entrySet().stream().filter(e -> e.getValue().qty.signum() > 0)
                .map(e -> assetMap.get(e.getKey())).filter(Objects::nonNull).toList();
        Map<Long, QuoteView> quotes = open.isEmpty() ? Map.of() : market.latestMany(open);

        List<AssetPerformance> rows = new ArrayList<>();
        for (Map.Entry<Long, Agg> en : agg.entrySet()) {
            Asset a = assetMap.get(en.getKey());
            if (a == null) continue;
            Agg g = en.getValue();
            boolean isOpen = g.qty.signum() > 0;
            QuoteView q = isOpen ? quotes.get(a.getId()) : null;
            BigDecimal value = null, unrealized = null;
            if (isOpen && q != null) {
                value = g.qty.multiply(q.quote().price()).setScale(4, RoundingMode.HALF_UP);
                unrealized = value.subtract(g.cost).setScale(4, RoundingMode.HALF_UP);
            }
            BigDecimal ret = g.realized.add(unrealized == null ? BigDecimal.ZERO : unrealized).add(g.dividends)
                    .setScale(4, RoundingMode.HALF_UP);
            BigDecimal bought = Breakdown.totalBought(byAsset.get(a.getId()), scope.single()).setScale(4, RoundingMode.HALF_UP);
            rows.add(new AssetPerformance(a.getId(), a.getSymbol(), a.getName(), a.getAssetType().name(),
                    g.qty.setScale(8, RoundingMode.HALF_UP), g.cost.setScale(4, RoundingMode.HALF_UP), value, unrealized,
                    g.realized.setScale(4, RoundingMode.HALF_UP), g.dividends.setScale(4, RoundingMode.HALF_UP), ret, bought,
                    percent(ret, bought), isOpen, !isOpen || q != null));
        }
        rows.sort(Comparator.comparing(AssetPerformance::totalReturn).reversed());
        return new Book(rows, quotes, assetMap);
    }

    // ---- performance ------------------------------------------------------------------------

    private PerformanceReport performance(Context ctx, HistoryRange range, Long benchmarkAssetId) {
        LocalDate end = today();
        LocalDate first = firstEventDate(ctx.transactions());
        LocalDate start = windowStart(range, end, first);
        List<String> warnings = new ArrayList<>();
        Lifetime lifetime = lifetime(ctx.book());
        boolean simulated = market.provider().simulated();
        int unpriced = (int) ctx.book().rows().stream().filter(r -> !r.priced()).count();

        if (ctx.transactions().isEmpty()) {
            warnings.add("There are no transactions in scope yet, so there is no history to analyse.");
            return new PerformanceReport(scopeName(ctx.scope()), ctx.scope().currency(), ctx.scope().ids(),
                    ctx.scope().excluded(), range.name(), start, end, lifetime,
                    new Window(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                    List.of(), EMPTY_RISK, null,
                    new DataQuality(simulated, 0, 0, warnings), NOTES, DISCLAIMER);
        }

        Computed c = compute(ctx, start, end);
        List<DayPoint> pts = c.points();
        BigDecimal contributions = BigDecimal.ZERO, dividends = BigDecimal.ZERO;
        for (int i = 1; i < pts.size(); i++) {
            contributions = contributions.add(pts.get(i).netFlow());
            dividends = dividends.add(pts.get(i).dividends());
        }
        Window window = new Window(pts.get(0).marketValue(), pts.get(pts.size() - 1).marketValue(), contributions,
                dividends, c.returns().twrPercent());

        Benchmark benchmark = null;
        if (benchmarkAssetId != null) {
            Asset b = market.requireAsset(benchmarkAssetId);
            PriceSeries bs = PriceSeries.of(market.history(b, c.daysBack()));
            market.latest(b).ifPresent(q -> bs.put(end, q.quote().price()));
            List<BigDecimal> idx = Returns.priceIndex(bs, pts.stream().map(DayPoint::date).toList());
            if (idx == null) {
                warnings.add("The benchmark " + b.getSymbol() + " has no price history for the start of this period, so no comparison is shown.");
            } else {
                BigDecimal ret = idx.get(idx.size() - 1).subtract(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
                benchmark = new Benchmark(b.getId(), b.getSymbol(), b.getName(), ret,
                        c.returns().twrPercent().subtract(ret), simulated, indexSeries(pts, idx));
            }
        }
        if (!market.provider().supportsHistory()) {
            warnings.add("History from the real market data source only goes back to when this app first stored prices; "
                    + "earlier days are estimated from your trade prices.");
        }
        if (simulated) warnings.add("Prices are SIMULATED development data, not real market prices.");
        if (unpriced > 0) warnings.add(unpriced + " open position(s) have no current price and are valued at their last trade price.");
        int approx = (int) pts.stream().filter(DayPoint::approximated).count();
        if (approx > 0) warnings.add(approx + " day(s) in this period use estimated prices.");

        List<SeriesPoint> series = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            series.add(new SeriesPoint(pts.get(i).date(), pts.get(i).marketValue(), pts.get(i).costBasis(),
                    c.returns().index().get(i).setScale(4, RoundingMode.HALF_UP)));
        }
        return new PerformanceReport(scopeName(ctx.scope()), ctx.scope().currency(), ctx.scope().ids(),
                ctx.scope().excluded(), range.name(), start, end, lifetime, window, series, c.risk(), benchmark,
                new DataQuality(simulated, approx, unpriced, warnings), NOTES, DISCLAIMER);
    }

    private Computed compute(Context ctx, LocalDate start, LocalDate end) {
        Map<Long, List<AssetEvent>> byPortfolio = new HashMap<>();
        Set<Long> assetIds = new LinkedHashSet<>();
        for (Transaction t : ctx.transactions()) {
            byPortfolio.computeIfAbsent(t.getPortfolioId(), k -> new ArrayList<>())
                    .add(new AssetEvent(t.getAssetId(), TradeEvents.from(t)));
            assetIds.add(t.getAssetId());
        }
        LocalDate first = firstEventDate(ctx.transactions());
        LocalDate earliest = first != null && first.isBefore(start) ? first : start;
        int daysBack = (int) Math.max(2, Math.min(MAX_HISTORY_DAYS, ChronoUnit.DAYS.between(earliest, end) + 2));

        Map<Long, PriceSeries> prices = new HashMap<>();
        for (Long id : assetIds) {
            Asset a = ctx.book().assets().get(id);
            if (a == null) continue;
            PriceSeries s = PriceSeries.of(market.history(a, daysBack));
            QuoteView q = ctx.book().quotes().get(id);
            if (q != null) s.put(end, q.quote().price()); // today's value matches the live valuation
            prices.put(id, s);
        }
        List<List<DayPoint>> perPortfolio = new ArrayList<>();
        for (Portfolio p : ctx.scope().portfolios()) {
            perPortfolio.add(SeriesBuilder.build(byPortfolio.getOrDefault(p.getId(), List.of()), prices, start, end));
        }
        List<DayPoint> pts = perPortfolio.isEmpty() ? flat(start, end) : SeriesBuilder.sum(perPortfolio);
        Returns.Result r = Returns.timeWeighted(pts);
        return new Computed(pts, r, RiskMetrics.compute(pts, r), prices, daysBack);
    }

    private static List<DayPoint> flat(LocalDate start, LocalDate end) {
        List<DayPoint> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            out.add(new DayPoint(d, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
        }
        return out;
    }

    private static Lifetime lifetime(Book book) {
        BigDecimal value = BigDecimal.ZERO, invested = BigDecimal.ZERO, unrealized = BigDecimal.ZERO,
                realized = BigDecimal.ZERO, dividends = BigDecimal.ZERO, bought = BigDecimal.ZERO;
        for (AssetPerformance r : book.rows()) {
            invested = invested.add(r.investedCost());
            realized = realized.add(r.realizedPnl());
            dividends = dividends.add(r.dividendIncome());
            bought = bought.add(r.totalBought());
            if (r.marketValue() != null) value = value.add(r.marketValue());
            if (r.unrealizedPnl() != null) unrealized = unrealized.add(r.unrealizedPnl());
        }
        BigDecimal total = realized.add(unrealized).add(dividends);
        return new Lifetime(value, invested, unrealized, realized, dividends, total, percent(total, bought));
    }

    // ---- allocation -------------------------------------------------------------------------

    private AllocationReport allocation(Context ctx) {
        List<String> warnings = new ArrayList<>();
        List<AssetPerformance> open = ctx.book().rows().stream().filter(AssetPerformance::open).toList();
        BigDecimal total = BigDecimal.ZERO;
        for (AssetPerformance r : open) total = total.add(value(r));

        List<AllocationSlice> byAsset = new ArrayList<>();
        Map<String, BigDecimal> byType = new TreeMap<>();
        Map<String, Boolean> typeAtCost = new HashMap<>();
        boolean anyAtCost = false;
        for (AssetPerformance r : open) {
            boolean atCost = r.marketValue() == null;
            anyAtCost |= atCost;
            byAsset.add(new AllocationSlice(r.symbol(), value(r), percent(value(r), total), atCost));
            byType.merge(r.assetType(), value(r), BigDecimal::add);
            typeAtCost.merge(r.assetType(), atCost, Boolean::logicalOr);
        }
        byAsset.sort(Comparator.comparing(AllocationSlice::value).reversed());
        List<AllocationSlice> types = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : byType.entrySet()) {
            types.add(new AllocationSlice(e.getKey(), e.getValue(), percent(e.getValue(), total), typeAtCost.get(e.getKey())));
        }
        types.sort(Comparator.comparing(AllocationSlice::value).reversed());
        if (anyAtCost) warnings.add("Some positions have no current price and are shown at cost.");
        if (market.provider().simulated()) warnings.add("Prices are SIMULATED development data, not real market prices.");

        Diversification.Result div = Diversification.analyse(
                byAsset.stream().map(s -> new Diversification.Weight(s.label(), s.value())).toList());
        return new AllocationReport(ctx.scope().currency(), ctx.scope().ids(), total.setScale(4, RoundingMode.HALF_UP),
                byAsset, types, div, warnings);
    }

    private static BigDecimal value(AssetPerformance r) { return r.marketValue() != null ? r.marketValue() : r.investedCost(); }

    // ---- dashboard extras -------------------------------------------------------------------

    private List<RecentTransaction> recent(Scope scope) {
        if (scope.portfolios().isEmpty()) return List.of();
        List<Transaction> latest = txs.findTop5ByPortfolioIdInOrderByExecutedAtDescIdDesc(scope.ids());
        Set<Long> ids = latest.stream().map(Transaction::getAssetId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> symbols = ids.isEmpty() ? Map.of()
                : assets.findAllById(ids).stream().collect(Collectors.toMap(Asset::getId, Asset::getSymbol));
        return latest.stream().map(t -> new RecentTransaction(t.getId(), t.getPortfolioId(), t.getType().name(),
                t.getAssetId() == null ? null : symbols.get(t.getAssetId()), t.getQuantity(), t.getTotalAmount(),
                t.getExecutedAt())).toList();
    }

    private static List<Mover> movers(Book book) {
        List<Mover> out = new ArrayList<>();
        for (QuoteView v : book.quotes().values()) {
            Quote q = v.quote();
            if (q.previousClose() == null || q.previousClose().signum() <= 0) continue;
            BigDecimal pct = q.price().subtract(q.previousClose()).multiply(BigDecimal.valueOf(100))
                    .divide(q.previousClose(), 2, RoundingMode.HALF_UP);
            out.add(new Mover(v.assetId(), v.symbol(), q.price(), pct, q.simulated()));
        }
        out.sort(Comparator.comparing((Mover m) -> m.changePercent().abs()).reversed());
        return out.size() > 10 ? out.subList(0, 10) : out;
    }

    // ---- helpers ----------------------------------------------------------------------------

    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneOffset.UTC)); }

    private static LocalDate firstEventDate(List<Transaction> list) {
        return list.stream().map(t -> t.getExecutedAt().atZone(ZoneOffset.UTC).toLocalDate())
                .min(Comparator.naturalOrder()).orElse(null);
    }

    private static LocalDate windowStart(HistoryRange range, LocalDate end, LocalDate firstEvent) {
        LocalDate start = range == HistoryRange.ALL ? (firstEvent != null ? firstEvent : end) : end.minusDays(range.days());
        return start.isAfter(end) ? end : start;
    }

    private static List<IndexPoint> indexSeries(List<DayPoint> pts, List<BigDecimal> index) {
        List<IndexPoint> out = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) out.add(new IndexPoint(pts.get(i).date(), index.get(i).setScale(4, RoundingMode.HALF_UP)));
        return out;
    }

    private static String scopeName(Scope s) { return s.single() ? "PORTFOLIO" : "ALL_PORTFOLIOS"; }

    /** Percentage of 'whole', or null when there is nothing to divide by. */
    private static BigDecimal percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() <= 0) return null;
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static ApiException bad(String m) { return new ApiException(HttpStatus.BAD_REQUEST, m); }
}
