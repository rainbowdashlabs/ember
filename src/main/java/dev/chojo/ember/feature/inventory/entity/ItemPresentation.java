/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * How a single piece presents itself to partner stations: its own text and photo.
 *
 * <p>Kept apart from {@link InventoryItem} on purpose. The piece row is read by many queries that care
 * about custody and ownership, and none of them needs the text; only the lending shop and the screens
 * that edit it do.
 *
 * @param itemId      the piece
 * @param description what partner stations read about it, in Markdown, empty when nobody wrote one
 * @param pictureKey  the storage key of its own photo, or {@code null} where it shows its kind's or
 *                    its inventory's
 */
public record ItemPresentation(
        int itemId, String description, @Nullable String pictureKey) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ItemPresentation> map() {
        return row ->
                new ItemPresentation(row.getInt("id"), row.getString("description"), row.getString("picture_key"));
    }
}
