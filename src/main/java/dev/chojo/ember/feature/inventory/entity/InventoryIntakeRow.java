/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import org.jspecify.annotations.Nullable;

/**
 * One line of a stock-taking: a piece of gear as it already exists, and who holds it.
 *
 * <p>What a station writes down when it puts an inventory it has long owned into Ember for the first
 * time. Nothing here is a movement: the jacket has hung in that member's locker for two years, and
 * saying so is a record rather than a handover.
 *
 * @param memberId   who holds the piece, or null where it lies in the store
 * @param internalId the number written on it, or null where it carries none
 * @param sizeId     its size, or null where the inventory keeps no sizes
 * @param ownerKind  whether the station or the association owns it
 * @param metadata   the inventory's own fields, empty where none were filled in
 */
public record InventoryIntakeRow(
        @Nullable Integer memberId,
        @Nullable String internalId,
        @Nullable Integer sizeId,
        @Nullable ItemOwner ownerKind,
        @Nullable InventoryItemMetadata metadata) {

    /**
     * Whether this line describes a piece at all.
     *
     * <p>The table opens with a row per member, and most stock-takings leave some of them empty: a
     * member who has not been given anything yet is a row with nothing in it, not a piece with
     * nothing written on it. Such a row is passed over rather than having to be deleted first.
     */
    public boolean namesAPiece() {
        boolean hasField = metadata != null && !metadata.fields().values().isEmpty();
        return sizeId != null || (internalId != null && !internalId.isBlank()) || hasField;
    }
}
