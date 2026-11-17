package com.portfolioiq.market;

import com.portfolioiq.alert.AlertService;
import com.portfolioiq.alert.PriceAlertRepository;
import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.transaction.TransactionRepository;
import com.portfolioiq.watchlist.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class MarketRefreshService {
    private final MarketDataProvider provider;
    private final QuoteCache cache;
    private final MarketPriceRepository prices;
    private final AssetRepository assets;
    private final AlertService alerts;
    private final PriceAlertRepository alertRepo;
    private final WatchlistRepository watchlists;
    private final TransactionRepository txs;

    public MarketRefreshService(MarketDataProvider provider, QuoteCache cache, MarketPriceRepository prices,
                                AssetRepository assets, AlertService alerts, PriceAlertRepository alertRepo,
                                WatchlistRepository watchlists, TransactionRepository txs) {
        this.provider = provider;
        this.cache = cache;
        this.prices = prices;
        this.assets = assets;
        this.alerts = alerts;
        this.alertRepo = alertRepo;
        this.watchlists = watchlists;
        this.txs = txs;
    }

    /** Assets worth refreshing, most important first: active alerts, then watchlists, then held assets. */
    @Transactional(readOnly = true)
    public List<Long> trackedAssetIds() {
        Set<Long> ids = new LinkedHashSet<>(alertRepo.activeAssetIds());
        ids.addAll(watchlists.allWatchedAssetIds());
        ids.addAll(txs.tradedAssetIds());
        return List.copyOf(ids);
    }

    /** Fetches a fresh quote, stores it (once per timestamp), refreshes the cache and evaluates alerts. */
    @Transactional
    public void refresh(Long assetId) {
        Asset asset = assets.findById(assetId).orElse(null);
        if (asset == null) return;
        Optional<Quote> fetched = provider.latest(asset.getSymbol());
        if (fetched.isEmpty()) return;
        Quote quote = fetched.get();
        cache.put(assetId, quote);
        if (!prices.existsByAssetIdAndPricedAt(assetId, quote.asOf())) prices.save(new MarketPrice(assetId, quote));
        alerts.evaluate(asset, quote);
    }
}
