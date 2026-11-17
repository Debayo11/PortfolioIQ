package com.portfolioiq.portfolio;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.finance.*;
import com.portfolioiq.market.MarketDataService;
import com.portfolioiq.market.Quote;
import com.portfolioiq.market.QuoteView;
import com.portfolioiq.portfolio.PortfolioDtos.*;
import com.portfolioiq.transaction.TradeEvents;
import com.portfolioiq.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** Holdings and valuation derived from the transaction ledger and the latest available prices. */
@Service
public class PortfolioReportService {
    private final PortfolioRepository portfolios;
    private final TransactionRepository txs;
    private final AssetRepository assets;
    private final MarketDataService market;

    public PortfolioReportService(PortfolioRepository portfolios, TransactionRepository txs, AssetRepository assets,
                                  MarketDataService market) {
        this.portfolios = portfolios;
        this.txs = txs;
        this.assets = assets;
        this.market = market;
    }

    private record Valuation(Map<Long, Position> positions, Map<Long, Asset> openAssets, Map<Long, QuoteView> quotes) {}

    @Transactional(readOnly = true)
    public List<HoldingResponse> holdings(Long userId, Long portfolioId) {
        owned(userId, portfolioId);
        Valuation v = valuation(portfolioId);
        return v.positions().entrySet().stream()
                .filter(e -> e.getValue().quantity().signum() > 0)
                .map(e -> {
                    Position p = e.getValue();
                    Asset a = v.openAssets().get(e.getKey());
                    QuoteView q = v.quotes().get(e.getKey());
                    if (q == null) {
                        return new HoldingResponse(e.getKey(), a.getSymbol(), a.getName(), p.quantity(), p.averageCost(),
                                p.totalCost(), p.realizedPnl(), p.dividendIncome(), null, null, null, null, null, null, false, false);
                    }
                    Quote quote = q.quote();
                    return new HoldingResponse(e.getKey(), a.getSymbol(), a.getName(), p.quantity(), p.averageCost(),
                            p.totalCost(), p.realizedPnl(), p.dividendIncome(), quote.price(), p.marketValue(quote.price()),
                            p.unrealizedPnl(quote.price()), dayChange(p, quote), quote.asOf(), quote.source(),
                            quote.simulated(), q.stale());
                })
                .sorted(Comparator.comparing(HoldingResponse::symbol))
                .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse summary(Long userId, Long portfolioId) {
        Portfolio portfolio = owned(userId, portfolioId);
        Valuation v = valuation(portfolioId);

        BigDecimal invested = BigDecimal.ZERO, realized = BigDecimal.ZERO, dividends = BigDecimal.ZERO,
                fees = BigDecimal.ZERO, marketValue = BigDecimal.ZERO, unrealized = BigDecimal.ZERO, day = BigDecimal.ZERO;
        int open = 0, unpriced = 0;
        boolean simulated = false;
        for (Map.Entry<Long, Position> e : v.positions().entrySet()) {
            Position p = e.getValue();
            invested = invested.add(p.totalCost());
            realized = realized.add(p.realizedPnl());
            dividends = dividends.add(p.dividendIncome());
            fees = fees.add(p.totalFees());
            if (p.quantity().signum() <= 0) continue;
            open++;
            QuoteView q = v.quotes().get(e.getKey());
            if (q == null) { unpriced++; continue; }
            simulated |= q.quote().simulated();
            marketValue = marketValue.add(p.marketValue(q.quote().price()));
            unrealized = unrealized.add(p.unrealizedPnl(q.quote().price()));
            BigDecimal d = dayChange(p, q.quote());
            if (d != null) day = day.add(d);
        }

        BigDecimal deposits = BigDecimal.ZERO, withdrawals = BigDecimal.ZERO, standaloneFees = BigDecimal.ZERO;
        long count = 0;
        for (Object[] row : txs.totalsByType(portfolioId)) {
            TransactionType type = (TransactionType) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            count += (Long) row[2];
            switch (type) {
                case DEPOSIT -> deposits = sum;
                case WITHDRAWAL -> withdrawals = sum;
                case FEE -> standaloneFees = sum;
                default -> { }
            }
        }
        BigDecimal totalReturn = realized.add(unrealized).add(dividends).subtract(standaloneFees);
        return new PortfolioSummaryResponse(portfolioId, portfolio.getBaseCurrency(), invested, marketValue, unrealized,
                realized, dividends, fees.add(standaloneFees), totalReturn, day, deposits.subtract(withdrawals), open,
                unpriced, simulated, count, note(simulated, unpriced));
    }

    private Valuation valuation(Long portfolioId) {
        Map<Long, List<TradeEvent>> byAsset = new HashMap<>();
        txs.findByPortfolioIdAndTypeIn(portfolioId, TradeEvents.ASSET_TYPES).forEach(t ->
                byAsset.computeIfAbsent(t.getAssetId(), k -> new ArrayList<>()).add(TradeEvents.from(t)));
        Map<Long, Position> positions = new HashMap<>();
        byAsset.forEach((id, events) -> positions.put(id, PositionCalculator.calculate(events)));

        Set<Long> openIds = positions.entrySet().stream().filter(e -> e.getValue().quantity().signum() > 0)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
        Map<Long, Asset> openAssets = assets.findAllById(openIds).stream().collect(Collectors.toMap(Asset::getId, a -> a));
        return new Valuation(positions, openAssets, market.latestMany(openAssets.values()));
    }

    /** quantity x (price - previous close); null if the previous close is unknown. */
    private static BigDecimal dayChange(Position p, Quote q) {
        if (q.previousClose() == null) return null;
        return p.quantity().multiply(q.price().subtract(q.previousClose()))
                .setScale(PositionCalculator.MONEY_SCALE, PositionCalculator.ROUNDING);
    }

    private static String note(boolean simulated, int unpriced) {
        StringBuilder sb = new StringBuilder("Historical figures only; no projections are included.");
        if (simulated) sb.append(" Market values use SIMULATED prices (no market data API key is configured).");
        if (unpriced > 0) sb.append(" ").append(unpriced).append(" open position(s) have no price and are excluded from market value.");
        return sb.toString();
    }

    private Portfolio owned(Long userId, Long id) {
        return portfolios.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Portfolio not found"));
    }
}
