/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

/**
 * What one line of a collection would find if it were fetched now, or over a given window.
 *
 * <p>The line kinds fall short differently and the numbers say which: a counted line that finds
 * three of four is a fraction, a named line that finds nothing is simply gone, because there is no
 * other piece that would do.
 *
 * <p>{@code clusterOwned} counts the pieces the body above the station owns. They are here and can be
 * fetched, which is why they count as found, but they cannot be lent on, so a screen that offers this
 * collection to a partner has to say so before the refusal arrives.
 *
 * @param lineId       the line this answers for
 * @param itemId       the named piece, or {@code null}
 * @param artId        the kind of thing counted, or {@code null}
 * @param inventoryId  the inventory counted out of, or {@code null}
 * @param label        the piece's name, the kind's name, or the inventory's
 * @param requested    how many pieces the line asks for
 * @param available    how many the station could actually put its hands on
 * @param clusterOwned how many of the available pieces belong to the body above the station
 */
public record ResolvedCollectionLine(
        int lineId,
        Integer itemId,
        Integer artId,
        Integer inventoryId,
        String label,
        int requested,
        int available,
        int clusterOwned) {

    /**
     * Whether the station can fill this line.
     *
     * @return {@code true} when at least as many pieces are available as the line asks for
     */
    public boolean filled() {
        return available >= requested;
    }

    /**
     * How many pieces the line is short of.
     *
     * @return the shortfall, or zero when the line is filled
     */
    public int missing() {
        return Math.max(0, requested - available);
    }
}
