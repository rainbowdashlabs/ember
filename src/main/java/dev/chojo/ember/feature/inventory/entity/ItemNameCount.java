/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One name written on the pieces of an inventory, and how many pieces carry it.
 *
 * <p>What the tidying screen reads. Six rows saying {@code Funkgerät blau} and one saying
 * {@code Funkgerät organge} is the shape that shows somebody where the typo is, which no list of
 * eighteen pieces does.
 *
 * @param name       the name as it is written on the pieces
 * @param pieces     how many pieces carry it
 * @param unassigned how many of those have no kind yet, which is what tidying them up would change
 */
public record ItemNameCount(String name, int pieces, int unassigned) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ItemNameCount> map() {
        return row -> new ItemNameCount(row.getString("name"), row.getInt("pieces"), row.getInt("unassigned"));
    }
}
