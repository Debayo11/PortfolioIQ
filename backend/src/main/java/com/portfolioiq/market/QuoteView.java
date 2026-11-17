package com.portfolioiq.market;

/** A quote plus whether it came from a stored fallback because the live source was unavailable. */
public record QuoteView(Long assetId, String symbol, Quote quote, boolean stale) {}
