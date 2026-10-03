/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * A key as a template writes it: the key of a value of the catalogue, and for a date the format it prints
 * in after a bar, such as {@code member.birthDate|long}.
 *
 * <p>Two keys of the first letter templates named a date in one format each,
 * {@code member.joinDate.monthYear} and {@code today.long}. Templates saved with them keep working: they
 * read as the join date in {@link DatePreset#MONTH_YEAR} and today in {@link DatePreset#LONG}.
 *
 * @param base   the key of the value in the catalogue
 * @param format what the key names after the bar, or null where it names no format
 */
public record PlaceholderKey(String base, @Nullable String format) {
    /** What stands between the key of a value and its format. */
    public static final String SEPARATOR = "|";

    private static final Map<String, PlaceholderKey> EARLIER_KEYS = Map.of(
            "member.joinDate.monthYear",
            new PlaceholderKey(BuiltInPlaceholder.MEMBER_JOIN_DATE.key(), DatePreset.MONTH_YEAR.written()),
            "today.long",
            new PlaceholderKey(BuiltInPlaceholder.TODAY.key(), DatePreset.LONG.written()));

    /**
     * @param written a key as a template writes it
     * @return the key read into its parts
     */
    public static PlaceholderKey parse(String written) {
        var earlier = EARLIER_KEYS.get(written);
        if (earlier != null) return earlier;
        int bar = written.indexOf(SEPARATOR);
        if (bar < 0) return new PlaceholderKey(written, null);
        return new PlaceholderKey(written.substring(0, bar), written.substring(bar + 1));
    }

    /**
     * @param base   the key of a value
     * @param format its format, or null for none
     * @return the key as a template writes it
     */
    public static String written(String base, @Nullable String format) {
        return format == null ? base : base + SEPARATOR + format;
    }
}
