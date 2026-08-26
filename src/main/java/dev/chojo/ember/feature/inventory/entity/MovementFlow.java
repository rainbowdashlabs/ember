/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A named chain of steps that a movement between two parties walks.
 *
 * @param id        the unique flow identifier
 * @param stationId the station whose flow this is, or {@code null} when the body above owns it
 * @param clusterId the owning body when one runs on this instance, or {@code null}
 * @param name      what the flow is called where it is configured
 * @param purpose   what kind of movement the flow is for
 * @param archived  whether the flow is retired from new movements
 */
public record MovementFlow(
        int id, Integer stationId, Integer clusterId, String name, MovementPurpose purpose, boolean archived) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<MovementFlow> map() {
        return row -> new MovementFlow(
                row.getInt("id"),
                row.getObject("station_id", Integer.class),
                row.getObject("cluster_id", Integer.class),
                row.getString("name"),
                row.getEnum("purpose", MovementPurpose.class),
                row.getBoolean("archived"));
    }
}
