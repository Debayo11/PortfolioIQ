package com.portfolioiq.admin;

import java.util.regex.Pattern;

/**
 * Removes secrets from log text before an administrator can see it. Development-mode email logging prints one-time
 * links, and a stray exception message could carry a credential, so tokens and keys are masked defensively.
 */
public final class LogRedactor {
    private static final int MAX_LENGTH = 500;
    private static final Pattern KEY_VALUE = Pattern.compile("(?i)\\b(token|password|passwd|secret|api[-_]?key|authorization)=([^\\s&\"',;]+)");
    private static final Pattern BEARER = Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private LogRedactor() {}

    public static String redact(String message) {
        if (message == null) return "";
        String s = KEY_VALUE.matcher(message).replaceAll("$1=[redacted]");
        s = BEARER.matcher(s).replaceAll("Bearer [redacted]");
        s = JWT.matcher(s).replaceAll("[jwt redacted]");
        s = WHITESPACE.matcher(s).replaceAll(" ").trim();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH) + "…" : s;
    }
}
