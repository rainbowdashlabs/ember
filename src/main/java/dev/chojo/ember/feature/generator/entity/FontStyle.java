/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;

/** Which style of a font family one file is. Every family has at most one file per style. */
public enum FontStyle {
    REGULAR,
    BOLD,
    ITALIC,
    BOLD_ITALIC;

    /** @return whether the style is drawn in a bold weight */
    public boolean bold() {
        return this == BOLD || this == BOLD_ITALIC;
    }

    /** @return whether the style is drawn slanted */
    public boolean italic() {
        return this == ITALIC || this == BOLD_ITALIC;
    }

    /**
     * Reads a style as a form sends it.
     *
     * @param name the name of a style, or null or blank for the regular one
     * @return the style, or empty where the name is none
     */
    public static Optional<FontStyle> parse(@Nullable String name) {
        if (name == null || name.isBlank()) return Optional.of(REGULAR);
        return Arrays.stream(values())
                .filter(style -> style.name().equals(name.strip()))
                .findFirst();
    }
}
