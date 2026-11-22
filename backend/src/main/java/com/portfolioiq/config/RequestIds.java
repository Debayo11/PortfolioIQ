package com.portfolioiq.config;

import java.util.UUID;
import java.util.regex.Pattern;

/** Decides which request id to use. A caller's id is kept only if it is short and harmless (no log injection). */
public final class RequestIds {
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    private RequestIds() {}

    public static String resolve(String inbound) {
        return inbound != null && SAFE.matcher(inbound).matches() ? inbound : UUID.randomUUID().toString();
    }
}
