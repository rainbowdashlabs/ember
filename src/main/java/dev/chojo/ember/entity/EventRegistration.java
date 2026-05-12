/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record EventRegistration(
        int id, int eventId, int memberId, LocalDate eventDate, RegistrationStatus status, Instant createdAt) {

    public static RowMapping<EventRegistration> map() {
        return row -> new EventRegistration(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getInt("member_id"),
                row.getObject("event_date", LocalDate.class),
                RegistrationStatus.valueOf(row.getString("status")),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    public enum RegistrationStatus {
        PENDING,
        ACCEPTED,
        DENIED,
        DECLINED
    }
}
