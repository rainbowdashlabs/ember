/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a federated member's registration for an event occurrence.
 *
 * @param id             the unique identifier of the registration
 * @param eventId        the event being registered for
 * @param partnerId      the federation partner ID
 * @param remoteMemberId the member UUID from the partner station
 * @param eventDate      the specific date of the event occurrence
 * @param status         the current registration status
 * @param createdAt      when the registration was created
 */
public record EventFederationRegistration(
        int id,
        int eventId,
        int partnerId,
        UUID remoteMemberId,
        LocalDate eventDate,
        RegistrationStatus status,
        Instant createdAt) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<EventFederationRegistration> map() {
        return row -> new EventFederationRegistration(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getInt("partner_id"),
                row.get("remote_member_id", StandardValueConverter.UUID_STRING),
                row.getObject("event_date", LocalDate.class),
                row.getEnum("status", RegistrationStatus.class),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
