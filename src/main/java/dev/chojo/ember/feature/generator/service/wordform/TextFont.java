/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.jspecify.annotations.Nullable;

/**
 * The font a piece of text of a Word form is set in.
 *
 * @param family the family as the form names it, or null where the form names none for Latin text
 * @param size   the size in points
 * @param bold   whether it is set bold
 */
record TextFont(@Nullable String family, double size, boolean bold) {

    /**
     * Text that is not set bold.
     *
     * @param family the family as the form names it, or null where the form names none for Latin text
     * @param size   the size in points
     */
    TextFont(@Nullable String family, double size) {
        this(family, size, false);
    }
}
