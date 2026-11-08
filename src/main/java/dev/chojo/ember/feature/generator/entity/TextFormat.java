/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import java.util.regex.Pattern;

/**
 * A format a text question can ask its answer to have.
 */
public enum TextFormat {
    /** Digits only. */
    DIGITS("\\d+"),
    /** Letters, with spaces, hyphens and apostrophes between them, as a name is written. */
    LETTERS("\\p{L}+(?:[ '\\-]+\\p{L}+)*"),
    /** A postal code of five digits. */
    POSTAL_CODE("\\d{5}"),
    /** A mail address. */
    EMAIL("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"),
    /** A phone number: digits, spaces, slashes and hyphens, perhaps led by a plus. */
    PHONE("\\+?\\d[\\d /\\-]*");

    private final Pattern pattern;

    TextFormat(String pattern) {
        this.pattern = Pattern.compile(pattern);
    }

    /**
     * @param text an answer
     * @return whether it has this format
     */
    public boolean matches(String text) {
        return pattern.matcher(text).matches();
    }
}
