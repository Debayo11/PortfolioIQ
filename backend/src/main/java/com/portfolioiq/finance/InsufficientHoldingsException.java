package com.portfolioiq.finance;

public class InsufficientHoldingsException extends RuntimeException {
    public InsufficientHoldingsException(String message) { super(message); }
}
