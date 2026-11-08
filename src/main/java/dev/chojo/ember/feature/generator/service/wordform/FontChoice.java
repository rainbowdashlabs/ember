/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.jspecify.annotations.Nullable;

/**
 * How a family a Word form names is drawn instead.
 *
 * @param family     the family put in its place
 * @param size       the size its text is drawn in, in percent of the size the form sets
 * @param boldFamily the family its bold text is drawn in where that is another one, such as Open Sans for
 *                   Open Sans Light, which ships no bold face of its own; null for the family itself
 */
public record FontChoice(String family, int size, @Nullable String boldFamily) {
    /** The size that leaves text as large as the form sets it. */
    public static final int SAME_SIZE = 100;

    /** The smallest size a family may be drawn in, in percent. */
    public static final int MIN_SIZE = 50;

    /** The largest size a family may be drawn in, in percent. */
    public static final int MAX_SIZE = 150;

    /**
     * A family whose bold text is drawn in the family itself.
     *
     * @param family the family put in its place
     * @param size   the size its text is drawn in, in percent of the size the form sets
     */
    public FontChoice(String family, int size) {
        this(family, size, null);
    }
}
