package com.portfolioiq.admin;

import java.time.Instant;

/** A warning or error from the application log. Messages are redacted before they are stored. */
public record LogEntry(Instant time, String level, String logger, String message, String exception) {}
