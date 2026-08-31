/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What one line of a list asks for: a named piece, a count of one kind of thing, or a count out of a
 * whole inventory.
 *
 * <p>The same three targets appear on a collection line, on the needs of an appointment and on a
 * lending request line, which are three versions of one thing rather than three things. This record
 * is that one thing, so a fourth list can be written without a fourth idea of what a line points at.
 *
 * <p>Exactly one target is set, and the constructor refuses anything else, the same way the database
 * refuses it.
 *
 * @param itemId      the named piece, or {@code null}
 * @param artId       the kind of thing counted, or {@code null}
 * @param inventoryId the inventory counted out of, or {@code null}
 */
public record LineTarget(Integer itemId, Integer artId, Integer inventoryId) {

    public LineTarget {
        int named = (itemId == null ? 0 : 1) + (artId == null ? 0 : 1) + (inventoryId == null ? 0 : 1);
        if (named != 1) {
            throw new IllegalArgumentException("A line names one piece, one kind of thing or one inventory");
        }
    }

    /**
     * A line naming one piece.
     *
     * @param itemId the piece
     * @return the target
     */
    public static LineTarget item(int itemId) {
        return new LineTarget(itemId, null, null);
    }

    /**
     * A line asking for a count of one kind of thing.
     *
     * @param artId the kind
     * @return the target
     */
    public static LineTarget art(int artId) {
        return new LineTarget(null, artId, null);
    }

    /**
     * A line asking for a count out of a whole inventory.
     *
     * @param inventoryId the inventory
     * @return the target
     */
    public static LineTarget inventory(int inventoryId) {
        return new LineTarget(null, null, inventoryId);
    }

    /**
     * Reads a target off three nullable columns.
     *
     * @param itemId      the piece column
     * @param artId       the kind column
     * @param inventoryId the inventory column
     * @return the target
     */
    public static LineTarget of(Integer itemId, Integer artId, Integer inventoryId) {
        return new LineTarget(itemId, artId, inventoryId);
    }

    /**
     * Whether this line names one piece rather than asking for a count.
     *
     * @return {@code true} when the line names a piece
     */
    public boolean namesItem() {
        return itemId != null;
    }
}
