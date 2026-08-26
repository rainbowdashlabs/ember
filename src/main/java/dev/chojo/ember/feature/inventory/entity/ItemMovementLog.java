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
 * What was acknowledged on a movement, when, by whom and on whose behalf.
 *
 * @param id         the unique log entry identifier
 * @param movementId the movement this entry belongs to
 * @param stepId     the step acknowledged, or {@code null} once that step is gone
 * @param stepLabel  the words the step carried at the time, kept so a finished movement still reads
 *                   the way it was walked even after the flow is renamed
 * @param ackKind    whether the party confirmed it, the station asserted it, or it was forced
 * @param changedBy  who pressed the button
 * @param changedAt  when they pressed it
 * @param note       what they wrote alongside
 */
public record ItemMovementLog(
        int id,
        int movementId,
        Integer stepId,
        String stepLabel,
        AckKind ackKind,
        Integer changedBy,
        Instant changedAt,
        String note) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ItemMovementLog> map() {
        return row -> new ItemMovementLog(
                row.getInt("id"),
                row.getInt("movement_id"),
                row.getObject("step_id", Integer.class),
                row.getString("step_label"),
                row.getEnum("ack_kind", AckKind.class),
                row.getObject("changed_by", Integer.class),
                row.get("changed_at", INSTANT_TIMESTAMP),
                row.getString("note"));
    }
}
