package com.portfolioiq.market;

import java.math.BigDecimal;
import java.time.Instant;

public record PricePoint(Instant time, BigDecimal price) {}
