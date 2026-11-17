package com.portfolioiq.alert;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.portfolioiq.alert.AlertCondition.*;
import static org.junit.jupiter.api.Assertions.*;

class AlertRulesTest {
    private static BigDecimal d(String v) { return new BigDecimal(v); }

    @Test void aboveAndBelowAreInclusive() {
        assertTrue(AlertRules.isTriggered(ABOVE, d("100"), null, d("100"), null));
        assertFalse(AlertRules.isTriggered(ABOVE, d("99.99"), null, d("100"), null));
        assertTrue(AlertRules.isTriggered(BELOW, d("90"), null, d("90"), null));
        assertFalse(AlertRules.isTriggered(BELOW, d("90.01"), null, d("90"), null));
    }

    @Test void percentMoveWorksBothDirections() {
        assertTrue(AlertRules.isTriggered(PERCENT_MOVE, d("105"), d("100"), null, d("5")));
        assertTrue(AlertRules.isTriggered(PERCENT_MOVE, d("95"), d("100"), null, d("5")));
        assertFalse(AlertRules.isTriggered(PERCENT_MOVE, d("104"), d("100"), null, d("5")));
    }

    @Test void percentMoveWithoutPreviousCloseNeverFires() {
        assertFalse(AlertRules.isTriggered(PERCENT_MOVE, d("500"), null, null, d("1")));
        assertFalse(AlertRules.isTriggered(PERCENT_MOVE, d("500"), BigDecimal.ZERO, null, d("1")));
    }

    @Test void definitionsAreValidated() {
        assertThrows(IllegalArgumentException.class, () -> AlertRules.validate(ABOVE, null, null));
        assertThrows(IllegalArgumentException.class, () -> AlertRules.validate(ABOVE, d("0"), null));
        assertThrows(IllegalArgumentException.class, () -> AlertRules.validate(ABOVE, d("10"), d("5")));
        assertThrows(IllegalArgumentException.class, () -> AlertRules.validate(PERCENT_MOVE, null, null));
        assertThrows(IllegalArgumentException.class, () -> AlertRules.validate(PERCENT_MOVE, d("10"), d("5")));
        assertThrows(IllegalArgumentException.class, () -> AlertRules.validate(PERCENT_MOVE, null, d("1001")));
        assertDoesNotThrow(() -> AlertRules.validate(BELOW, d("12.5"), null));
        assertDoesNotThrow(() -> AlertRules.validate(PERCENT_MOVE, null, d("7.5")));
    }
}
