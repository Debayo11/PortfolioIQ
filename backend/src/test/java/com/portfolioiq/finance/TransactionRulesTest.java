package com.portfolioiq.finance;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.portfolioiq.finance.TransactionType.*;
import static org.junit.jupiter.api.Assertions.*;

class TransactionRulesTest {
    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test void buyTotalIncludesFee() {
        var c = TransactionRules.compute(BUY, d("10"), d("100"), d("1"), null);
        assertEquals(0, d("1001").compareTo(c.total()));
    }

    @Test void sellTotalDeductsFee() {
        var c = TransactionRules.compute(SELL, d("5"), d("130"), d("1"), null);
        assertEquals(0, d("649").compareTo(c.total()));
    }

    @Test void dividendTotal() {
        var c = TransactionRules.compute(DIVIDEND, d("15"), d("0.5"), null, null);
        assertEquals(0, d("7.5").compareTo(c.total()));
    }

    @Test void depositUsesAmount() {
        var c = TransactionRules.compute(DEPOSIT, null, null, null, d("500"));
        assertEquals(0, d("500").compareTo(c.total()));
        assertEquals(0, c.quantity().signum());
    }

    @Test void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.compute(BUY, d("0"), d("10"), null, null));
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.compute(BUY, d("1"), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.compute(BUY, d("1"), d("10"), d("-1"), null));
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.compute(BUY, d("0.123456789"), d("10"), null, null));
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.compute(SELL, d("1"), d("1"), d("5"), null));
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.compute(DEPOSIT, d("1"), null, null, d("10")));
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.compute(WITHDRAWAL, null, null, null, d("0")));
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.compute(BUY, d("1"), d("10"), null, d("10")));
        assertThrows(IllegalArgumentException.class, () -> TransactionRules.compute(DEPOSIT, null, null, null, d("1e20")));
    }
}
