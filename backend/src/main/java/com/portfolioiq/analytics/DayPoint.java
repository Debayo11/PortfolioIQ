package com.portfolioiq.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * End-of-day state of a portfolio's holdings.
 * netFlow = money put into holdings that day (buys incl. fees, transfers in at market value) minus money taken out.
 * dividends = income received that day. approximated = a price had to be estimated from the last trade price.
 */
public record DayPoint(LocalDate date, BigDecimal marketValue, BigDecimal costBasis, BigDecimal netFlow,
                       BigDecimal dividends, boolean approximated) {}
