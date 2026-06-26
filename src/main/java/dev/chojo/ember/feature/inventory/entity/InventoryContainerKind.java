/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Station-defined container kind (room, drawer, box, …). The seven defaults
 * are seeded on first use; stations can rename, disable, or add custom kinds.
 *
 * @param id        the unique kind identifier
 * @param stationId the station this kind belongs to
 * @param key       stable machine identifier used in API responses and CSV exports
 * @param label     display name for pickers and badges
 * @param icon      FontAwesome icon name rendered next to the kind chip
 * @param sortOrder ordering hint for the kind picker
 * @param enabled   whether new containers can pick this kind
 */
public record InventoryContainerKind(
        int id, int stationId, String key, String label, String icon, int sortOrder, boolean enabled) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryContainerKind> map() {
        return row -> new InventoryContainerKind(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("key"),
                row.getString("label"),
                row.getString("icon"),
                row.getInt("sort_order"),
                row.getBoolean("enabled"));
    }
}
