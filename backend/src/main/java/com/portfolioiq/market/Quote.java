package com.portfolioiq.market;

import java.math.BigDecimal;
import java.time.Instant;

/** A price observation. simulated=true marks development data that must never be presented as real. */
public record Quote(String symbol, BigDecimal price, BigDecimal previousClose, Long volume, BigDecimal marketCap,
                    Instant asOf, String source, boolean simulated) {}
