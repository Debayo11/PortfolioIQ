package com.portfolioiq.market;

import com.portfolioiq.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

/** The time filters from the spec: 24 hours, 7 days, 30 days, 3 months, 6 months, 1 year, all time. */
public enum HistoryRange {
    H24("24H", 1), D7("7D", 7), D30("30D", 30), M3("3M", 90), M6("6M", 180), Y1("1Y", 365), ALL("ALL", 1825);

    private final String code;
    private final int days;

    HistoryRange(String code, int days) { this.code = code; this.days = days; }

    public int days() { return days; }

    public static HistoryRange parse(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        for (HistoryRange r : values()) if (r.code.equals(v)) return r;
        throw new ApiException(HttpStatus.BAD_REQUEST, "range must be one of 24H, 7D, 30D, 3M, 6M, 1Y, ALL");
    }
}
