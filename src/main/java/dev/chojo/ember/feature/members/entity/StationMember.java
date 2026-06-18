/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.auth.StationUserType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a member's association with a station.
 *
 * @param id          the station member identifier
 * @param stationId   the station this member belongs to
 * @param uid         the stable UUID for federation identity (unique within a station)
 * @param accountId   the linked account identifier, or null for decoupled former members
 * @param former      whether this member has been marked as a former member
 * @param formerAt    when the member was marked as former, or null
 * @param displayName the cached display name, used for former members after account decoupling
 * @param userType    the station user type (TRIAL, MEMBER, GUARDIAN, TEAM, MANAGER)
 * @param joinDate    the date the member joined the station; editable by managers
 */
public record StationMember(
        int id,
        int stationId,
        UUID uid,
        Integer accountId,
        boolean former,
        Instant formerAt,
        String displayName,
        StationUserType userType,
        LocalDate joinDate) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<StationMember> map() {
        return row -> new StationMember(
                row.getInt("id"),
                row.getInt("station_id"),
                row.get("uid", StandardValueConverter.UUID_STRING),
                row.getObject("account_id", Integer.class),
                row.getBoolean("former"),
                row.getTimestamp("former_at") != null
                        ? row.getTimestamp("former_at").toInstant()
                        : null,
                row.getString("display_name"),
                row.getEnum("user_type", StationUserType.class),
                row.getDate("join_date") != null ? row.getDate("join_date").toLocalDate() : null);
    }
}
