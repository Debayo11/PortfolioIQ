package com.portfolioiq.analytics;

import com.portfolioiq.finance.TradeEvent;

import java.time.LocalDate;
import java.time.ZoneOffset;

public record AssetEvent(long assetId, TradeEvent event) {
    public LocalDate date() { return event.executedAt().atZone(ZoneOffset.UTC).toLocalDate(); }
}
