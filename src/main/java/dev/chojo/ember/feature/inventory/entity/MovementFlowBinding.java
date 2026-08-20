/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Which flow a station uses for a given owner and purpose. A binding with an inventory beats the
 * station-wide one, which is what lets one mixed inventory reach different flows for different rows.
 *
 * @param stationId   the station the binding belongs to
 * @param inventoryId the inventory it is for, or {@code null} for the station-wide binding
 * @param ownerKind   which owner it applies to
 * @param purpose     which purpose it applies to
 * @param flowId      the flow to walk
 */
public record MovementFlowBinding(
        int stationId, Integer inventoryId, ItemOwner ownerKind, MovementPurpose purpose, int flowId) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<MovementFlowBinding> map() {
        return row -> new MovementFlowBinding(
                row.getInt("station_id"),
                row.getObject("inventory_id", Integer.class),
                row.getEnum("owner_kind", ItemOwner.class),
                row.getEnum("purpose", MovementPurpose.class),
                row.getInt("flow_id"));
    }
}
