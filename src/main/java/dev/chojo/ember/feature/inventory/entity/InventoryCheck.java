/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a completed inventory check. Two scopes are supported via
 * {@link InventoryCheckScope}: a MEMBER check covers one member's assigned
 * items; a CONTAINER check covers the items held in a storage container
 * (optionally walked recursively through descendant containers).
 *
 * @param id          the unique check identifier
 * @param stationId   the station where the check was performed
 * @param memberId    the member whose inventory was checked, or {@code null} on container-scope checks
 * @param checkedBy   the member who performed the check
 * @param checkedAt   when the check was completed
 * @param scope       discriminator for the check target
 * @param containerId target container for container-scope checks, or {@code null}
 * @param deep        whether a container-scope check walks descendant containers
 * @param reportedBy  who reported what the check records, where that is somebody other than the
 *                    person who signed it off, and {@code null} on a check somebody walked
 *                    themselves. A piece checked at arm's length can be told from one somebody held.
 */
public record InventoryCheck(
        int id,
        int stationId,
        Integer memberId,
        int checkedBy,
        Instant checkedAt,
        InventoryCheckScope scope,
        Integer containerId,
        boolean deep,
        Integer reportedBy) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryCheck> map() {
        return row -> new InventoryCheck(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("member_id", Integer.class),
                row.getInt("checked_by"),
                row.get("checked_at", INSTANT_TIMESTAMP),
                row.getEnum("scope", InventoryCheckScope.class),
                row.getObject("container_id", Integer.class),
                row.getBoolean("deep"),
                row.getObject("reported_by", Integer.class));
    }
}
