package com.portfolioiq.portfolio;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.finance.*;
import com.portfolioiq.portfolio.PortfolioDtos.*;
import com.portfolioiq.transaction.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Holdings and cost-based summary derived from the transaction ledger.
 * Market value and unrealized P&L need prices and arrive with the market-data stage.
 */
@Service
public class PortfolioReportService {
    private static final Set<TransactionType> ASSET_TYPES =
            EnumSet.of(TransactionType.BUY, TransactionType.SELL, TransactionType.DIVIDEND);

    private final PortfolioRepository portfolios;
    private final TransactionRepository txs;
    private final AssetRepository assets;

    public PortfolioReportService(PortfolioRepository portfolios, TransactionRepository txs, AssetRepository assets) {
        this.portfolios = portfolios;
        this.txs = txs;
        this.assets = assets;
    }

    @Transactional(readOnly = true)
    public List<HoldingResponse> holdings(Long userId, Long portfolioId) {
        owned(userId, portfolioId);
        Map<Long, Position> positions = positionsByAsset(portfolioId);
        Map<Long, Asset> assetMap = assets.findAllById(positions.keySet()).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));
        return positions.entrySet().stream()
                .filter(e -> e.getValue().quantity().signum() > 0)
                .map(e -> {
                    Position p = e.getValue();
                    Asset a = assetMap.get(e.getKey());
                    return new HoldingResponse(e.getKey(), a.getSymbol(), a.getName(), p.quantity(), p.averageCost(),
                            p.totalCost(), p.realizedPnl(), p.dividendIncome());
                })
                .sorted(Comparator.comparing(HoldingResponse::symbol))
                .toList();
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResponse summary(Long userId, Long portfolioId) {
        Portfolio portfolio = owned(userId, portfolioId);

        BigDecimal invested = BigDecimal.ZERO, realized = BigDecimal.ZERO, dividends = BigDecimal.ZERO,
                fees = BigDecimal.ZERO;
        int open = 0;
        for (Position p : positionsByAsset(portfolioId).values()) {
            invested = invested.add(p.totalCost());
            realized = realized.add(p.realizedPnl());
            dividends = dividends.add(p.dividendIncome());
            fees = fees.add(p.totalFees());
            if (p.quantity().signum() > 0) open++;
        }

        BigDecimal deposits = BigDecimal.ZERO, withdrawals = BigDecimal.ZERO;
        long count = 0;
        for (Object[] row : txs.totalsByType(portfolioId)) {
            TransactionType type = (TransactionType) row[0];
            BigDecimal sum = (BigDecimal) row[1];
            count += (Long) row[2];
            switch (type) {
                case DEPOSIT -> deposits = sum;
                case WITHDRAWAL -> withdrawals = sum;
                case FEE -> fees = fees.add(sum);
                default -> { }
            }
        }
        return new PortfolioSummaryResponse(portfolioId, portfolio.getBaseCurrency(), invested, realized, dividends,
                fees, deposits.subtract(withdrawals), open, count,
                "Cost-based figures only. Market value and unrealized P&L require market prices.");
    }

    private Map<Long, Position> positionsByAsset(Long portfolioId) {
        Map<Long, List<TradeEvent>> byAsset = new HashMap<>();
        txs.findByPortfolioIdAndTypeIn(portfolioId, ASSET_TYPES).forEach(t ->
                byAsset.computeIfAbsent(t.getAssetId(), k -> new ArrayList<>()).add(new TradeEvent(
                        TradeEvent.Type.valueOf(t.getType().name()), t.getQuantity(), t.getUnitPrice(),
                        t.getFee(), t.getExecutedAt())));
        Map<Long, Position> result = new HashMap<>();
        byAsset.forEach((id, events) -> result.put(id, PositionCalculator.calculate(events)));
        return result;
    }

    private Portfolio owned(Long userId, Long id) {
        return portfolios.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Portfolio not found"));
    }
}
