/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * A {@link LineTarget} with the levels above it filled in, which is what lets two lines be compared.
 *
 * <p>A line naming a piece also draws on that piece's kind and on its inventory; a line asking for a
 * kind also draws on the inventory the kind lives in. Without those, a claim on one blue radio and a
 * question about the blue radios would never meet.
 *
 * @param itemId      the piece, where the line names one
 * @param artId       the kind, either named or the named piece's
 * @param inventoryId the inventory, either named or the one the piece or the kind lives in
 * @param label       what to call it on screen
 */
public record ResolvedTarget(Integer itemId, Integer artId, Integer inventoryId, String label) {

    /**
     * Whether a claim on this target takes anything away from a question about the given one.
     *
     * <p>Two targets meet when the sets of pieces they stand for overlap, which is why the test is
     * symmetric rather than read from one level down. One piece meets its own kind and its own
     * inventory; one kind meets the inventory it lives in; two kinds meet only when they are the same
     * kind. A count out of a whole drawer therefore does compete with a count of one kind inside it,
     * which is the safe direction and in practice rare: an inventory carrying kinds is a drawer of
     * different things, and a count out of a whole drawer is only offered where there are no kinds.
     *
     * @param question the target being asked about
     * @return {@code true} when this claim counts against that question
     */
    public boolean counts(ResolvedTarget question) {
        if (itemId != null || question.itemId != null) {
            if (itemId != null && question.itemId != null) return itemId.equals(question.itemId);
            ResolvedTarget piece = itemId != null ? this : question;
            ResolvedTarget group = itemId != null ? question : this;
            if (group.artId != null) return group.artId.equals(piece.artId);
            return group.inventoryId != null && group.inventoryId.equals(piece.inventoryId);
        }
        if (artId != null && question.artId != null) return artId.equals(question.artId);
        return inventoryId != null && inventoryId.equals(question.inventoryId);
    }
}
