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
 * The stretch of time a document covers, written the way a reader would say it.
 *
 * <p>A month is its name, a week is the number people schedule by, a quarter is the quarter, and a
 * year is the year. The month names come from the language itself rather than from a list kept here,
 * because Java already knows them in every language and a list would be one more thing to translate.
 *
 * <p>The window is read in the station's own zone: an export covering January was asked for in
 * January where the station is, and an hour's difference must not make its name say December.
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

    /**
     * A single day, for a document that is a snapshot rather than a period.
     *
     * <p>Written the way it sorts, so a folder of the same export taken week after week falls into
     * order on its own. Spelled-out dates do not, and a reader with a year of them would have to read
     * every name to find the newest.
     *
     * @param moment the moment the document was made
     * @param zone   the station's zone, so a late evening export is not already tomorrow
     */
    public static String day(Instant moment, ZoneId zone) {
        return DAY.format(moment.atZone(zone));
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
}
