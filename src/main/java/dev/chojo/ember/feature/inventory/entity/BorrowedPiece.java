/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import org.jspecify.annotations.Nullable;

/**
 * A partner's piece as the station borrowing it writes it down: the snapshot taken at handover.
 *
 * <p>A piece lent by a station of this installation carries its fields as they stood. A piece lent
 * from another installation carries its identifier and its name only, since its fields are defined by
 * an inventory that is not here to read them.
 *
 * @param internalId the identifier as it stood at handover, or {@code null} where it had none
 * @param name       the name as it stood at handover
 * @param metadata   the fields as they stood at handover
 */
public record BorrowedPiece(@Nullable String internalId, String name, InventoryItemMetadata metadata) {

    /**
     * The snapshot of an owner's row.
     *
     * @param source the owner's row, read once
     * @return the piece as the borrower writes it down
     */
    public static BorrowedPiece of(InventoryItem source) {
        return new BorrowedPiece(source.internalId(), source.name(), source.metadata());
    }

    /**
     * A piece lent from another installation, named and nothing more.
     *
     * @param internalId the identifier the lender gave, or {@code null}
     * @param name       the name the lender gave
     * @return the piece as the borrower writes it down
     */
    public static BorrowedPiece named(@Nullable String internalId, String name) {
        return new BorrowedPiece(internalId, name, InventoryItemMetadata.empty());
    }
}
