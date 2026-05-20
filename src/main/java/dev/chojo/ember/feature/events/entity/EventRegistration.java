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
        Integer createdBy) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<EventRegistration> map() {
        return row -> new EventRegistration(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getInt("member_id"),
                row.getObject("event_date", LocalDate.class),
                RegistrationStatus.valueOf(row.getString("status")),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class));
    }

    /**
     * The possible states of an event registration.
     */
    public enum RegistrationStatus {
        PENDING,
        ACCEPTED,
        DENIED,
        DECLINED
    }
}
