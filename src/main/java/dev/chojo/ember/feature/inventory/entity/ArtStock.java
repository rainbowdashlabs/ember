/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * How many pieces an inventory holds of one kind, and how many of those are free.
 *
 * <p>Counted in the database rather than over a list fetched into memory, because the question is
 * how many blue radios there are and not which rows they happen to be.
 *
 * <p>Pieces with no kind are not in this list and get no row of their own. Having no kind is the
 * ordinary state rather than a group, so inventing one for them would put a heading above a set of
 * unrelated things.
 *
 * @param artId  the kind
 * @param name   what the kind is called
 * @param pieces how many pieces carry it, whatever state they are in
 * @param free   how many of those are here, unlent and unassigned
 */
public record ArtStock(int artId, String name, int pieces, int free) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ArtStock> map() {
        return row ->
                new ArtStock(row.getInt("art_id"), row.getString("name"), row.getInt("pieces"), row.getInt("free"));
    }
}
