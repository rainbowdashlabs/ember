/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One member's sign-up for an event group. The parts the member chose carry an appointment registration
 * each with the same status.
 *
 * @param id           the sign-up
 * @param eventGroupId     the event group
 * @param memberId     the member
 * @param status       where the sign-up stands
 * @param registeredAt when the member signed up, or last signed up again
 * @param createdBy    the member who signed somebody else up, or null
 */
public record EventGroupRegistration(
        int id,
        int eventGroupId,
        int memberId,
        RegistrationStatus status,
        Instant registeredAt,
        @Nullable Integer createdBy) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS = "id, group_id, member_id, status, registered_at, created_by";

    /** Reads a sign-up from a row holding {@link #COLUMNS}. */
    public static RowMapping<EventGroupRegistration> map() {
        return row -> new EventGroupRegistration(
                row.getInt("id"),
                row.getInt("group_id"),
                row.getInt("member_id"),
                row.getEnum("status", RegistrationStatus.class),
                row.get("registered_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class));
    }

    /**
     * Whether the sign-up still holds or waits for a place.
     *
     * @return true while pending or accepted
     */
    public boolean active() {
        return status == RegistrationStatus.PENDING || status == RegistrationStatus.ACCEPTED;
    }
}
