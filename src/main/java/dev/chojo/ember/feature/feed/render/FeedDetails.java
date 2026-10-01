/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.render;

import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.service.NotificationText;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The detail rows of one feed entry, filled in by the {@link FeedDetailsContributor} of the feature
 * the notification came from.
 *
 * <p>Rows appear in the order they are added, each a label and a value. Besides collecting them, this
 * is where a contributor finds the reader's language: labels from the feed bundle, dates and times in
 * the reader's locale, and moments in the clock of the station they belong to.
 */
public final class FeedDetails {
    private final Map<String, String> rows = new LinkedHashMap<>();
    private final NotificationText notificationText;
    private final Function<Integer, ZoneId> zones;
    private final Notification notification;
    private final String locale;

    FeedDetails(
            NotificationText notificationText,
            Function<Integer, ZoneId> zones,
            Notification notification,
            String locale) {
        this.notificationText = notificationText;
        this.zones = zones;
        this.notification = notification;
        this.locale = locale;
    }

    Map<String, String> rows() {
        return rows;
    }

    private static boolean notBlank(@Nullable String s) {
        return s != null && !s.isBlank();
    }

    /** The reader's locale, such as {@code de}. */
    public String locale() {
        return locale;
    }

    /** The {@code id} of the thing the notification links to, or null where it names none. */
    public @Nullable Integer linkId() {
        return linkParam("id");
    }

    /**
     * A numeric parameter of the notification's link, or null where the link has no such parameter
     * or it is not a number.
     */
    public @Nullable Integer linkParam(String key) {
        var link = notification.data().link();
        if (link == null || link.routeParams() == null) return null;
        Object raw = link.routeParams().get(key);
        if (raw == null) return null;
        try {
            return raw instanceof Number n ? n.intValue() : Integer.parseInt(raw.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A {@code feedLabel} entry as the bundle has it. */
    public String label(String key) {
        return localized("feedLabel", key, null);
    }

    /**
     * A {@code feedLabel} entry, or {@code fallback} where the bundle does not carry the key yet. Lets
     * a detail row be added without every bundle being updated in the same step.
     */
    public String label(String key, String fallback) {
        String value = localized("feedLabel", key, null);
        return key.equals(value) ? fallback : value;
    }

    /** An entry of any bundle, with its placeholders filled from {@code params}. */
    public String localized(String bundle, String key, @Nullable Map<String, String> params) {
        return notificationText.resolveLocalized(locale, bundle, key, params);
    }

    /** A status name as the reader sees it, marked with its symbol. */
    public String statusWithSymbol(String status) {
        return notificationText.resolveStatusWithSymbol(locale, status);
    }

    /**
     * The clock one station's times are written in, UTC where the station is unknown.
     *
     * <p>A personal feed carries entries from every station somebody belongs to, so the zone is a
     * question per entry rather than per feed.
     */
    public ZoneId zoneOf(Integer stationId) {
        return zones.apply(stationId);
    }

    /** A day in the reader's medium date format. */
    public String date(LocalDate date) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.forLanguageTag(locale))
                .format(date);
    }

    /** Two days as one range, or the single day where both are the same. */
    public String dateRange(LocalDate from, LocalDate to) {
        if (from.equals(to)) return date(from);
        return date(from) + " – " + date(to);
    }

    /**
     * One moment, written in the given clock.
     *
     * <p>Never the machine's: a feed is rendered on a server that is almost always in UTC, so an
     * appointment from 09:00 to 16:00 in Berlin would be read by the people who go to it as 07:00 to
     * 14:00. An appointment happens at the station, so the station's clock is the one to use.
     */
    public String moment(Instant instant, ZoneId zone) {
        var fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                .withLocale(Locale.forLanguageTag(locale))
                .withZone(zone);
        return fmt.format(instant);
    }

    /** Whether two moments fall on the same day in the given clock. */
    public boolean sameDay(Instant a, Instant b, ZoneId zone) {
        return a.atZone(zone).toLocalDate().equals(b.atZone(zone).toLocalDate());
    }

    /** A start and end on the same day as one fact: the day, then both times. */
    public String sameDayRange(Instant start, Instant end, ZoneId zone) {
        var loc = Locale.forLanguageTag(locale);
        var dateFmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(loc)
                .withZone(zone);
        var timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(loc)
                .withZone(zone);
        return dateFmt.format(start) + ", " + timeFmt.format(start) + " – " + timeFmt.format(end);
    }

    /** Adds a row. */
    public void put(String label, String value) {
        rows.put(label, value);
    }

    /** Adds a row where the value says something. */
    public void putIfPresent(String label, @Nullable String value) {
        if (notBlank(value)) rows.put(label, value);
    }

    /**
     * Adds a long-form snippet (preview, description, change text) where there is one, cut to the
     * length every snippet shares so a long article does not blow up a feed entry.
     */
    public void putSnippetIfPresent(String label, @Nullable String value) {
        if (value == null || value.isBlank()) return;
        rows.put(label, NotificationText.truncateSnippet(value, NotificationText.BODY_SNIPPET_MAX));
    }
}
