/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One line of a member's own inventory: an item they hold, or one that is on its way to or from them.
 *
 * <p>Gear taken back for an exchange stops being theirs the moment the station takes it, and the
 * replacement is not theirs until it is handed over. Between those two moments the member would see
 * nothing at all, which is the worst time to show them nothing: that is exactly the stretch where
 * they want to know what is happening to their jacket. So an item on a movement of theirs stays on
 * the list, carrying the step the movement is standing on.
 *
 * @param item             the item itself
 * @param movementId       the open movement it is on, or {@code null} when it is simply theirs
 * @param movementStep     the words of the step that movement is standing on
 * @param movementIncoming whether it is the item coming to them rather than the one leaving
 */
public record MemberInventoryEntry(
        InventoryItem item, Integer movementId, String movementStep, boolean movementIncoming) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<MemberInventoryEntry> map() {
        return row -> new MemberInventoryEntry(
                InventoryItem.map().map(row),
                row.getObject("movement_id", Integer.class),
                row.getString("movement_step"),
                row.getBoolean("movement_incoming"));
    }
}
