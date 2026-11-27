/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import org.jspecify.annotations.Nullable;

/**
 * What partner stations read about an inventory, a kind or a single piece.
 *
 * @param description the text in Markdown; missing or blank clears it
 */
public record DescriptionRequest(@Nullable String description) {
    /** More than any product text needs, and little enough that nobody stores a manual in it. */
    public static final int MAX_LENGTH = 20_000;

    /**
     * The text as it is kept: trimmed, empty where nothing was written.
     *
     * @return the text
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when it is longer than {@link #MAX_LENGTH}
     */
    public String text() {
        String text = description == null ? "" : description.strip();
        if (text.length() > MAX_LENGTH) throw InventoryRefusal.DESCRIPTION_TOO_LONG.raise();
        return text;
    }
}
