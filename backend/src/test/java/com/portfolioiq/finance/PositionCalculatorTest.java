package com.portfolioiq.finance;

import com.portfolioiq.finance.TradeEvent.Type;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PositionCalculatorTest {
    private static BigDecimal d(String v) { return new BigDecimal(v); }
    private static Instant day(int n) { return Instant.parse("2026-01-01T00:00:00Z").plusSeconds(86_400L * n); }
    private static TradeEvent ev(Type t, String q, String p, String f, int day) {
        return new TradeEvent(t, d(q), d(p), d(f), day(day));
    }

    @Test
    void averageCostIncludesFees() {
        var pos = PositionCalculator.calculate(List.of(
                ev(Type.BUY, "10", "100", "1", 0),
                ev(Type.BUY, "10", "120", "1", 1)));
        assertEquals(0, d("20").compareTo(pos.quantity()));
        assertEquals(0, d("2202.0000").compareTo(pos.totalCost()));
        assertEquals(0, d("110.1000").compareTo(pos.averageCost()));
    }

    @Test
    void partialSellRealizesProfitAtAverageCost() {
        var pos = PositionCalculator.calculate(List.of(
                ev(Type.BUY, "10", "100", "1", 0),
                ev(Type.BUY, "10", "120", "1", 1),
                ev(Type.SELL, "5", "130", "1", 2)));
        assertEquals(0, d("98.5").compareTo(pos.realizedPnl()));     // 649 - 550.5
        assertEquals(0, d("15").compareTo(pos.quantity()));
        assertEquals(0, d("1651.5").compareTo(pos.totalCost()));
    }

    @Test
    void unrealizedPnlAndDividends() {
        var pos = PositionCalculator.calculate(List.of(
                ev(Type.BUY, "10", "100", "1", 0),
                ev(Type.BUY, "10", "120", "1", 1),
                ev(Type.SELL, "5", "130", "1", 2),
                ev(Type.DIVIDEND, "15", "0.5", "0", 3)));
        assertEquals(0, d("223.5").compareTo(pos.unrealizedPnl(d("125"))));
        assertEquals(0, d("7.5").compareTo(pos.dividendIncome()));
    }

    @Test
    void fullSaleClearsPositionExactly() {
        var pos = PositionCalculator.calculate(List.of(
                ev(Type.BUY, "3", "10.3333", "0", 0),
                ev(Type.SELL, "3", "12", "0", 1)));
        assertEquals(0, pos.totalCost().signum());
        assertEquals(0, pos.averageCost().signum());
        assertEquals(0, d("5.0001").compareTo(pos.realizedPnl()));
    }

    @Test
    void overSellIsRejected() {
        assertThrows(InsufficientHoldingsException.class, () -> PositionCalculator.calculate(List.of(
                ev(Type.BUY, "5", "10", "0", 0),
                ev(Type.SELL, "6", "10", "0", 1))));
    }

    @Test
    void sellBeforeAnyBuyIsRejected() {
        assertThrows(InsufficientHoldingsException.class, () -> PositionCalculator.calculate(List.of(
                ev(Type.SELL, "1", "10", "0", 0))));
    }

    @Test
    void invalidInputsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ev(Type.BUY, "0", "10", "0", 0));
        assertThrows(IllegalArgumentException.class, () -> ev(Type.BUY, "1", "-10", "0", 0));
        assertThrows(IllegalArgumentException.class, () -> ev(Type.BUY, "1", "10", "-1", 0));
    }

    @Test
    void fractionalSharesDoNotDrift() {
        var pos = PositionCalculator.calculate(List.of(
                ev(Type.BUY, "0.1", "10", "0", 0),
                ev(Type.BUY, "0.2", "10", "0", 1)));
        assertEquals(0, d("0.3").compareTo(pos.quantity())); // would fail with double (0.30000000000000004)
    }
}
