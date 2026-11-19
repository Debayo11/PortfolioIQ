package com.portfolioiq.admin;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Thread-safe ring buffer of the most recent warnings and errors (per server instance, lost on restart). */
public final class LogBuffer {
    private final int capacity;
    private final ArrayDeque<LogEntry> entries = new ArrayDeque<>();

    public LogBuffer(int capacity) { this.capacity = capacity; }

    public static int rank(String level) {
        return "ERROR".equalsIgnoreCase(level) ? 2 : "WARN".equalsIgnoreCase(level) ? 1 : 0;
    }

    public synchronized void add(LogEntry entry) {
        if (entries.size() >= capacity) entries.removeFirst();
        entries.addLast(entry);
    }

    /** Newest first. minLevel is WARN or ERROR; contains is a case-insensitive text filter (may be null). */
    public synchronized List<LogEntry> snapshot(String minLevel, String contains, int limit) {
        int min = Math.max(rank(minLevel), 1);
        String needle = contains == null || contains.isBlank() ? null : contains.toLowerCase(Locale.ROOT);
        List<LogEntry> out = new ArrayList<>();
        var it = entries.descendingIterator();
        while (it.hasNext() && out.size() < limit) {
            LogEntry e = it.next();
            if (rank(e.level()) < min) continue;
            if (needle != null && !(e.message().toLowerCase(Locale.ROOT).contains(needle)
                    || e.logger().toLowerCase(Locale.ROOT).contains(needle))) continue;
            out.add(e);
        }
        return out;
    }

    public synchronized int size() { return entries.size(); }
}
