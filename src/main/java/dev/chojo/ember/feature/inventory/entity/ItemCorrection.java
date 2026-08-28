/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What a check found instead of what was written down.
 *
 * <p>A correction is not a movement. Nothing changes hands: the member is already holding the piece
 * named here, and the record catches up. That is also why the size of the piece already on the
 * record is never touched. Gear is assumed to be described correctly, so a wrong size means the
 * wrong piece, and the wrong piece is replaced rather than edited.
 *
 * @param inventoryId  the inventory both pieces sit in
 * @param oldItemId    the piece the record wrongly has the member holding, or {@code null} where the
 *                     record had them holding nothing
 * @param pickedItemId a piece already in the free stock, or {@code null} to make a new one
 * @param sizeId       the size of the new piece, or {@code null} in an inventory without sizes
 * @param ownerKind    who owns the new piece, which only a mixed inventory has to be told
 * @param internalId   the number on the new piece, or {@code null} where it carries none
 * @param metadata     the inventory's own fields for the new piece
 */
public record ItemCorrection(
        int inventoryId,
        Integer oldItemId,
        Integer pickedItemId,
        Integer sizeId,
        ItemOwner ownerKind,
        String internalId,
        InventoryItemMetadata metadata) {
    /**
     * Whether the member's piece is one the station already has on the shelf.
     *
     * @return {@code true} when a piece from the free stock was named
     */
    public boolean picksFromStock() {
        return pickedItemId != null && pickedItemId > 0;
    }

    /**
     * Whether the record had the member holding something at all.
     *
     * @return {@code true} when a piece is being replaced
     */
    public boolean replacesAPiece() {
        return oldItemId != null && oldItemId > 0;
    }
}
