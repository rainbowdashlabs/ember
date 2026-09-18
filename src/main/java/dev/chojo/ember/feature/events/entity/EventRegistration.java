/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a member's registration for a specific event occurrence.
 *
 * @param id        the unique identifier of the registration
 * @param eventId   the event being registered for
 * @param memberId  the member who is registered
 * @param eventDate the specific date of the event occurrence
 * @param status    the current registration status
 * @param createdAt when the registration was created or last updated
 * @param createdBy the member ID of whoever created the registration, or null if self-registered
 */
public record EventRegistration(
        int id,
        int eventId,
        int memberId,
        LocalDate eventDate,
        RegistrationStatus status,
        Instant createdAt,
        Integer createdBy,
        Instant statusChangedAt,
        RegistrationStatus previousStatus) {

    /**
     * A registration whose answer has never moved, which is one nobody can take back.
     *
     * <p>For the callers that build a registration rather than read one, where when the status last
     * changed is the moment it was written and nothing was held before it.
     */
    public EventRegistration(
            int id,
            int eventId,
            int memberId,
            LocalDate eventDate,
            RegistrationStatus status,
            Instant createdAt,
            Integer createdBy) {
        this(id, eventId, memberId, eventDate, status, createdAt, createdBy, createdAt, null);
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<EventRegistration> map() {
        return row -> new EventRegistration(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getInt("member_id"),
                row.getObject("event_date", LocalDate.class),
                row.getEnum("status", RegistrationStatus.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class),
                row.get("status_changed_at", INSTANT_TIMESTAMP),
                row.getEnum("previous_status", RegistrationStatus.class));
    }
}
