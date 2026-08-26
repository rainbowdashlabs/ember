/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One line of a member's own inventory: a piece they hold.
 *
 * <p>Only what is in their hands. A piece handed in for an exchange stops being theirs the moment the
 * station takes it, and a replacement is not theirs until it is handed over; neither belongs on this
 * list, because the question it answers is "what do I have". What is on its way in either direction
 * is a movement rather than a possession, and the movements of a member are listed as such.
 *
 * <p>A piece they still hold can nonetheless have something running on it: an exchange asked for this
 * morning is open while the jacket is still on the member. That is what the step is for, and it is
 * why the list joins the movement at all.
 *
 * @param item         the item itself
 * @param movementId   the open movement it is on, or {@code null} when nothing is running
 * @param movementStep the words of the step that movement is standing on
 */
public record MemberInventoryEntry(InventoryItem item, Integer movementId, String movementStep) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<MemberInventoryEntry> map() {
        return row -> new MemberInventoryEntry(
                InventoryItem.map().map(row),
                row.getObject("movement_id", Integer.class),
                row.getString("movement_step"));
    }
}
