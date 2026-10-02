/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.IsoFields;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * The stretch of time a document covers, as a reader says it, read in the station's zone so an hour's
 * difference cannot turn January into December.
 */
public final class DocumentPeriod {

    private DocumentPeriod() {}

    /**
     * Names the period a window falls in.
     *
     * @param period   {@code week}, {@code month}, {@code quarter} or {@code year}
     * @param from     the first moment the document covers
     * @param zone     the station's zone
     * @param language {@code de} or {@code en}
     */
    public static String of(String period, Instant from, ZoneId zone, String language) {
        ZonedDateTime start = from.atZone(zone);
        Locale locale = "en".equals(language) ? Locale.ENGLISH : Locale.GERMAN;
        return switch (period == null ? "" : period) {
            case "week" -> DocumentWord.WEEK.in(language) + " " + weekOf(start) + " " + start.getYear();
            case "quarter" -> "Q" + start.get(IsoFields.QUARTER_OF_YEAR) + " " + start.getYear();
            case "year" -> String.valueOf(start.getYear());
            default -> start.getMonth().getDisplayName(TextStyle.FULL, locale) + " " + start.getYear();
        };
    }

    /** The week people schedule by, which is the ISO one everywhere both languages are spoken. */
    private static int weekOf(ZonedDateTime moment) {
        return moment.get(WeekFields.ISO.weekOfWeekBasedYear());
    }

    /** A snapshot's day as {@code yyyy-MM-dd} in the station's zone, so a folder of exports sorts by name. */
    public static String day(Instant moment, ZoneId zone) {
        return DAY.format(moment.atZone(zone));
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
}
