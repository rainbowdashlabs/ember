/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One statement a station makes about what it offers its partners for lending: a whole inventory or
 * a single item, granted or withheld, to everybody or to named partners.
 *
 * @param id          the row
 * @param stationId   the station making the offer
 * @param inventoryId the inventory it is about, or {@code null} when it is about one item
 * @param itemId      the item it is about, or {@code null} when it is about a whole inventory
 * @param shareScope  whether it reaches every partner or only the named ones
 * @param shareGrant  whether it offers the gear or holds it back
 */
public record InventoryShare(
        int id, int stationId, Integer inventoryId, Integer itemId, ShareScope shareScope, ShareGrant shareGrant) {

    public static final String COLUMNS = "id, station_id, inventory_id, item_id, share_scope, share_grant";

    public static RowMapping<InventoryShare> map() {
        return row -> new InventoryShare(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("inventory_id", Integer.class),
                row.getObject("item_id", Integer.class),
                row.getEnum("share_scope", ShareScope.class),
                row.getEnum("share_grant", ShareGrant.class));
    }

    /**
     * Whether this row speaks about one item rather than a whole inventory, which is what makes it
     * the narrower of two rows that both reach the same piece of gear.
     */
    public boolean aboutItem() {
        return itemId != null;
    }
}
