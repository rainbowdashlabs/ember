/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * What the instance administration knows about a station: its state, its manager, its size and the last
 * reminder its manager was sent.
 *
 * @param id                  the station
 * @param uid                 the station's public identifier
 * @param name                the station's name
 * @param locale              the station's language, such as {@code de-DE}
 * @param state               how far the station has come
 * @param memberCount         its members, without the archived ones
 * @param managerAccountId    the manager's account, or {@code null} when the station has none
 * @param managerFirstName    the manager's first name, or {@code null} without a manager
 * @param managerLastName     the manager's last name, or {@code null} without a manager
 * @param managerEmail        the manager's address, or {@code null} without a manager
 * @param waitingSince        since when the station waits for its manager, or {@code null}
 * @param createdAt           when the station was made, or {@code null} for one made before this was kept
 * @param associationName     the association the station belongs to, or {@code null}
 * @param lastReminderAt      when the newest reminder went out, or {@code null} when none did
 * @param lastReminderTrigger what sent the newest reminder, or {@code null} when none went out
 * @param movedAway           whether this is only the copy the station left here when it moved away
 */
public record StationOverview(
        int id,
        UUID uid,
        String name,
        String locale,
        StationState state,
        int memberCount,
        @Nullable Integer managerAccountId,
        @Nullable String managerFirstName,
        @Nullable String managerLastName,
        @Nullable String managerEmail,
        @Nullable Instant waitingSince,
        @Nullable Instant createdAt,
        @Nullable String associationName,
        @Nullable Instant lastReminderAt,
        @Nullable ReminderTrigger lastReminderTrigger,
        boolean movedAway) {

    /**
     * Reads one row of the overview query.
     *
     * @return the mapping
     */
    public static RowMapping<StationOverview> map() {
        return row -> new StationOverview(
                row.getInt("id"),
                row.get("uid", UUID_STRING),
                row.getString("name"),
                row.getString("locale"),
                row.getEnum("state", StationState.class),
                row.getInt("member_count"),
                row.getObject("manager_account_id") != null ? row.getInt("manager_account_id") : null,
                row.getString("manager_first_name"),
                row.getString("manager_last_name"),
                row.getString("manager_email"),
                row.get("waiting_since", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getString("association_name"),
                row.get("last_reminder_at", INSTANT_TIMESTAMP),
                row.getString("last_reminder_trigger") != null
                        ? row.getEnum("last_reminder_trigger", ReminderTrigger.class)
                        : null,
                row.getBoolean("moved_away"));
    }
}
