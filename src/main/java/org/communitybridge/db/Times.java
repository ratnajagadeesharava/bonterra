package org.communitybridge.db;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Timestamps are stored and returned as UTC ISO-8601 text with whole seconds, e.g. 2026-11-24T09:30:00Z.
 * Whole seconds keep the text the same length, so text order equals time order.
 */
public final class Times {

    private Times() {
    }

    public static Instant truncate(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS);
    }

    public static String format(Instant instant) {
        return instant == null ? null : DateTimeFormatter.ISO_INSTANT.format(truncate(instant));
    }

    public static Instant parse(String text) {
        return text == null ? null : Instant.parse(text);
    }
}
