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
 * One movement of gear between two parties, walking the flow pinned on it at creation.
 *
 * @param stationId      the station the movement runs at
 * @param purpose        what the movement is for
 * @param flowId         the flow it walks, or {@code null} once that flow is gone
 * @param currentStepId  the step it stands on, or {@code null} once it is closed
 * @param memberId       the member it starts or ends at, if any
 * @param outgoingItemId the item leaving, if any
 * @param incomingItemId the item arriving, once somebody has named it
 * @param inventoryId    the inventory it is about
 * @param oldSizeId      the size being replaced, if any
 * @param newSizeId      the size asked for, if any
 * @param state          where it stands as a whole
 * @param reason         why it was started, in the words of whoever started it
 * @param createdBy      who started it, when that is somebody other than the member it concerns
 * @param createdAt      when it was started
 * @param closedAt       when it reached its end, however it ended
 * @param closeReason    why it was declined or cancelled
 */
public record ItemMovement(
        int id,
        int stationId,
        MovementPurpose purpose,
        Integer flowId,
        Integer currentStepId,
        Integer memberId,
        Integer outgoingItemId,
        Integer incomingItemId,
        Integer inventoryId,
        Integer oldSizeId,
        Integer newSizeId,
        MovementState state,
        String reason,
        Integer createdBy,
        Instant createdAt,
        Instant closedAt,
        String closeReason) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ItemMovement> map() {
        return row -> new ItemMovement(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getEnum("purpose", MovementPurpose.class),
                row.getObject("flow_id", Integer.class),
                row.getObject("current_step_id", Integer.class),
                row.getObject("member_id", Integer.class),
                row.getObject("outgoing_item_id", Integer.class),
                row.getObject("incoming_item_id", Integer.class),
                row.getObject("inventory_id", Integer.class),
                row.getObject("old_size_id", Integer.class),
                row.getObject("new_size_id", Integer.class),
                row.getEnum("state", MovementState.class),
                row.getString("reason"),
                row.getObject("created_by", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("closed_at", INSTANT_TIMESTAMP),
                row.getString("close_reason"));
    }

    /**
     * The item a step is about.
     *
     * @param subject which of the two items the step names
     * @return the item ID, or {@code null} when nobody has named it yet
     */
    public Integer itemFor(StepSubject subject) {
        return subject == StepSubject.INCOMING ? incomingItemId : outgoingItemId;
    }
}
