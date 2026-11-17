package com.portfolioiq.market;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class MarketDataService {
    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private final MarketDataProvider provider;
    private final QuoteCache cache;
    private final MarketPriceRepository prices;
    private final AssetRepository assets;
    private final Clock clock;

    public MarketDataService(MarketDataProvider provider, QuoteCache cache, MarketPriceRepository prices,
                             AssetRepository assets, Clock clock) {
        this.provider = provider;
        this.cache = cache;
        this.prices = prices;
        this.assets = assets;
        this.clock = clock;
    }

    public MarketDataProvider provider() { return provider; }

    /** Cache, then provider, then the last stored price (flagged stale). Empty if nothing is known. */
    @Transactional(readOnly = true)
    public Optional<QuoteView> latest(Asset asset) {
        Long id = asset.getId();
        Optional<Quote> cached = cache.get(id);
        if (cached.isPresent()) return Optional.of(new QuoteView(id, asset.getSymbol(), cached.get(), false));

        try {
            Optional<Quote> fresh = provider.latest(asset.getSymbol());
            if (fresh.isPresent()) {
                cache.put(id, fresh.get());
                return Optional.of(new QuoteView(id, asset.getSymbol(), fresh.get(), false));
            }
        } catch (RuntimeException e) {
            log.warn("Market data lookup failed for {}: {}", asset.getSymbol(), e.getClass().getSimpleName());
        }
        return prices.findFirstByAssetIdOrderByPricedAtDesc(id)
                .map(p -> new QuoteView(id, asset.getSymbol(), new Quote(asset.getSymbol(), p.getPrice(),
                        p.getPreviousClose(), p.getVolume(), p.getMarketCap(), p.getPricedAt(), p.getSource(),
                        p.isSimulated()), true));
    }

    @Transactional(readOnly = true)
    public Map<Long, QuoteView> latestMany(Collection<Asset> assetList) {
        Map<Long, QuoteView> out = new HashMap<>();
        for (Asset a : assetList) latest(a).ifPresent(v -> out.put(a.getId(), v));
        return out;
    }

    @Transactional(readOnly = true)
    public Asset requireAsset(Long id) {
        return assets.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Asset not found"));
    }

    /** Price history. Simulated data is generated; real data is the history this app has stored. */
    @Transactional(readOnly = true)
    public List<PricePoint> history(Asset asset, HistoryRange range) {
        if (provider.supportsHistory()) return provider.history(asset.getSymbol(), range.days());

        Instant from = clock.instant().minus(Duration.ofDays(range.days()));
        List<MarketPrice> rows = prices.findByAssetIdAndPricedAtGreaterThanEqualOrderByPricedAtAsc(asset.getId(), from);
        if (range.days() <= 1) {
            return rows.stream().map(r -> new PricePoint(r.getPricedAt(), r.getPrice())).toList();
        }
        Map<LocalDate, MarketPrice> lastPerDay = new LinkedHashMap<>();
        for (MarketPrice r : rows) lastPerDay.put(r.getPricedAt().atZone(ZoneOffset.UTC).toLocalDate(), r);
        return lastPerDay.values().stream().map(r -> new PricePoint(r.getPricedAt(), r.getPrice())).toList();
    }
}
