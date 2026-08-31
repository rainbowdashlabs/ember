/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One statement a station makes about what it offers its partners for lending: a whole inventory, a
 * kind of thing in it, or a single piece, granted or withheld, to everybody or to named partners.
 *
 * @param id          the row
 * @param stationId   the station making the offer
 * @param inventoryId the inventory it is about, or {@code null} when it is about something narrower
 * @param artId       the kind of thing it is about, or {@code null} when it is not about one
 * @param itemId      the piece it is about, or {@code null} when it is about something wider
 * @param shareScope  whether it reaches every partner or only the named ones
 * @param shareGrant  whether it offers the gear or holds it back
 */
public record InventoryShare(
        int id,
        int stationId,
        Integer inventoryId,
        Integer artId,
        Integer itemId,
        ShareScope shareScope,
        ShareGrant shareGrant) {

    public static final String COLUMNS = "id, station_id, inventory_id, art_id, item_id, share_scope, share_grant";

    public static RowMapping<InventoryShare> map() {
        return row -> new InventoryShare(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("inventory_id", Integer.class),
                row.getObject("art_id", Integer.class),
                row.getObject("item_id", Integer.class),
                row.getEnum("share_scope", ShareScope.class),
                row.getEnum("share_grant", ShareGrant.class));
    }

    /** Which of the three levels this row speaks at, which is what decides between two rows. */
    public ShareLevel level() {
        if (itemId != null) return ShareLevel.ITEM;
        if (artId != null) return ShareLevel.ART;
        return ShareLevel.INVENTORY;
    }
}
