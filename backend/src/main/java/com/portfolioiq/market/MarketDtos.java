package com.portfolioiq.market;

import com.portfolioiq.asset.Asset;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

public final class MarketDtos {
    private MarketDtos() {}

    /** simulated=true means development data; clients must label it. stale=true means the live source was unavailable. */
    public record QuoteResponse(Long assetId, String symbol, String name, BigDecimal price, BigDecimal previousClose,
                                BigDecimal change, BigDecimal changePercent, Long volume, BigDecimal marketCap,
                                Instant asOf, String source, boolean simulated, boolean stale) {
        public static QuoteResponse of(Asset a, QuoteView v) {
            Quote q = v.quote();
            BigDecimal change = null, pct = null;
            if (q.previousClose() != null && q.previousClose().signum() > 0) {
                change = q.price().subtract(q.previousClose());
                pct = change.multiply(BigDecimal.valueOf(100)).divide(q.previousClose(), 4, RoundingMode.HALF_UP);
            }
            return new QuoteResponse(a.getId(), a.getSymbol(), a.getName(), q.price(), q.previousClose(), change, pct,
                    q.volume(), q.marketCap(), q.asOf(), q.source(), q.simulated(), v.stale());
        }
    }

    public record HistoryResponse(Long assetId, String symbol, String range, String source, boolean simulated,
                                  List<PricePoint> points) {}

    public record MarketStatus(String provider, boolean simulated, String message) {}
}
