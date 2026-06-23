/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record WaitingListEntry(
        int id,
        int listId,
        String firstname,
        String lastname,
        String parentName,
        String email,
        String accessToken,
        WaitingListEntryStatus status,
        Instant confirmedAt,
        Instant reminderSentAt,
        Instant createdAt,
        String notes,
        Integer memberId,
        Instant invitedAt,
        Instant testingAt,
        Instant joinedAt,
        Instant withdrawnAt,
        int attendanceCount) {

    public static RowMapping<WaitingListEntry> map() {
        return row -> new WaitingListEntry(
                row.getInt("id"),
                row.getInt("list_id"),
                row.getString("firstname"),
                row.getString("lastname"),
                row.getString("parent_name"),
                row.getString("email"),
                row.getString("access_token"),
                row.getEnum("status", WaitingListEntryStatus.class),
                row.get("confirmed_at", INSTANT_TIMESTAMP),
                row.get("reminder_sent_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getString("notes"),
                row.getObject("member_id", Integer.class),
                row.get("invited_at", INSTANT_TIMESTAMP),
                row.get("testing_at", INSTANT_TIMESTAMP),
                row.get("joined_at", INSTANT_TIMESTAMP),
                row.get("withdrawn_at", INSTANT_TIMESTAMP),
                row.getInt("attendance_count"));
    }

    public String fullName() {
        if (lastname == null || lastname.isBlank()) return firstname;
        return firstname + " " + lastname;
    }
}
