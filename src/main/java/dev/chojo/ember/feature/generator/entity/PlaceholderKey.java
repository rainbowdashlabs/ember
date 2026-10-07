/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

/**
 * A key as a template writes it: the key of a value of the catalogue, and for a date the format it prints
 * in after a bar, such as {@code member.birthDate|long}.
 *
 * @param base   the key of the value in the catalogue
 * @param format what the key names after the bar, or null where it names no format
 */
public record PlaceholderKey(String base, @Nullable String format) {
    /** What stands between the key of a value and its format. */
    public static final String SEPARATOR = "|";

    /**
     * @param written a key as a template writes it
     * @return the key read into its parts
     */
    public static PlaceholderKey parse(String written) {
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
