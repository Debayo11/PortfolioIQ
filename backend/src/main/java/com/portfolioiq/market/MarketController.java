package com.portfolioiq.market;

import com.portfolioiq.asset.Asset;
import com.portfolioiq.asset.AssetRepository;
import com.portfolioiq.common.ApiException;
import com.portfolioiq.market.MarketDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/market")
public class MarketController {
    private static final int MAX_BATCH = 50;

    private final MarketDataService market;
    private final AssetRepository assets;

    public MarketController(MarketDataService market, AssetRepository assets) {
        this.market = market;
        this.assets = assets;
    }

    /** Lets clients label the data source (real vs simulated) without guessing. */
    @GetMapping("/status")
    public MarketStatus status() {
        var p = market.provider();
        return new MarketStatus(p.name(), p.simulated(), p.simulated()
                ? "SIMULATED market data for development. Set MARKET_DATA_API_KEY to use real quotes."
                : "Live quotes from " + p.name());
    }

    @GetMapping("/quotes/{assetId}")
    public QuoteResponse quote(@PathVariable Long assetId) {
        Asset a = market.requireAsset(assetId);
        QuoteView v = market.latest(a).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No price available for this asset"));
        return QuoteResponse.of(a, v);
    }

    /** Batch lookup; assets with no available price are omitted. */
    @GetMapping("/quotes")
    public List<QuoteResponse> quotes(@RequestParam List<Long> assetIds) {
        if (assetIds.size() > MAX_BATCH) throw new ApiException(HttpStatus.BAD_REQUEST, "At most " + MAX_BATCH + " assets per request");
        List<Asset> found = assets.findAllById(assetIds);
        var views = market.latestMany(found);
        return found.stream().filter(a -> views.containsKey(a.getId()))
                .map(a -> QuoteResponse.of(a, views.get(a.getId()))).toList();
    }

    @GetMapping("/history/{assetId}")
    public HistoryResponse history(@PathVariable Long assetId, @RequestParam(defaultValue = "30D") String range) {
        HistoryRange r = HistoryRange.parse(range);
        Asset a = market.requireAsset(assetId);
        return new HistoryResponse(a.getId(), a.getSymbol(), r.name(), market.provider().name(),
                market.provider().simulated(), market.history(a, r));
    }
}
