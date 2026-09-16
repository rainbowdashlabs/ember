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
        LocalDate joinDate,
        String nickname) {

    /**
     * A member as they are read where the name they are called by does not matter.
     *
     * <p>Kept so that the places building a member row by hand, chiefly tests and the seeders, do
     * not each have to say "and no nickname" to mean an ordinary member.
     */
    public StationMember(
            int id,
            int stationId,
            UUID uid,
            Integer accountId,
            boolean former,
            Instant formerAt,
            String displayName,
            StationUserType userType,
            LocalDate joinDate) {
        this(id, stationId, uid, accountId, former, formerAt, displayName, userType, joinDate, null);
    }
    /**
     * The columns {@link #map()} reads, so that a statement selecting a member and the mapping that
     * reads one cannot drift apart.
     */
    public static final String COLUMNS =
            "id, station_id, uid, account_id, former, former_at, display_name, user_type, join_date, nickname";

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
                row.getDate("join_date") != null ? row.getDate("join_date").toLocalDate() : null,
                row.getString("nickname"));
    }
}
