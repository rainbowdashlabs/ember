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
 * Append-only ledger entry for container lifecycle events.
 *
 * @param id          the unique history entry identifier
 * @param containerId the subject container, or {@code null} after the subject was deleted
 * @param stationId   the station the event happened in
 * @param eventKind   the kind of lifecycle event recorded
 * @param eventTs     when the event occurred
 * @param actorId     the member who performed the action, or {@code null} for system events
 * @param details     event-specific JSON payload (e.g. previous parent id on MOVED)
 */
public record InventoryContainerHistory(
        int id,
        Integer containerId,
        int stationId,
        ContainerEventKind eventKind,
        Instant eventTs,
        Integer actorId,
        String details) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryContainerHistory> map() {
        return row -> new InventoryContainerHistory(
                row.getInt("id"),
                row.getObject("container_id", Integer.class),
                row.getInt("station_id"),
                row.getEnum("event_kind", ContainerEventKind.class),
                row.get("event_ts", INSTANT_TIMESTAMP),
                row.getObject("actor_id", Integer.class),
                row.getString("details"));
    }
}
