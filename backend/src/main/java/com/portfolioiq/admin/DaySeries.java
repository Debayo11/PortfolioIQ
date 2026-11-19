package com.portfolioiq.admin;

import com.portfolioiq.admin.AdminStatsDtos.DayCount;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Turns sparse "day, count" database rows into a continuous series with zeros for quiet days. */
public final class DaySeries {
    private DaySeries() {}

    /** Drivers return DATE columns as java.sql.Date, LocalDate or text depending on configuration. */
    public static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate d) return d;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        return LocalDate.parse(String.valueOf(value).substring(0, 10));
    }

    public static List<DayCount> fill(List<Object[]> rows, LocalDate from, int days) {
        Map<LocalDate, Long> counts = new HashMap<>();
        for (Object[] row : rows) counts.merge(toLocalDate(row[0]), ((Number) row[1]).longValue(), Long::sum);
        List<DayCount> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate d = from.plusDays(i);
            out.add(new DayCount(d, counts.getOrDefault(d, 0L)));
        }
        return out;
    }
}
