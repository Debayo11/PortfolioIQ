package com.portfolioiq.watchlist;

import com.portfolioiq.market.MarketDtos.QuoteResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class WatchlistDtos {
    private WatchlistDtos() {}

    public record WatchlistRequest(@NotBlank @Size(max = 120) String name) {}

    public record AddItemRequest(@NotNull Long assetId) {}

    /** quote is null when no price is available for the asset. */
    public record WatchlistItemResponse(Long assetId, String symbol, String name, QuoteResponse quote) {}

    public record WatchlistResponse(Long id, String name, List<WatchlistItemResponse> items) {}
}
