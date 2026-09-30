/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StationUserType;

import java.util.Arrays;
import java.util.List;

/**
 * A named group of station members, optionally with a color and associated roles.
 *
 * @param id         the group identifier
 * @param stationId  the station this group belongs to
 * @param name       the group display name
 * @param color      optional hex color (e.g. "#FF6421"). Null means no color.
 * @param position   sort priority. Higher = higher priority for name color resolution.
 * @param groupSetId the set the group belongs to, which allows a member in only one of its groups,
 *                   or {@code null} for a group in no set
 * @param userTypes  the user types the group is bound to. Empty means every type may join.
 */
public record MemberGroup(
        int id,
        int stationId,
        String name,
        String color,
        int position,
        Integer groupSetId,
        List<StationUserType> userTypes) {

    /**
     * The columns {@link #map()} reads, for a statement that names the group table {@code mg}.
     */
    public static final String COLUMNS = """
            mg.id, mg.station_id, mg.name, mg.color, mg.position, mg.group_set_id,
            ARRAY(SELECT t.user_type FROM member_group_user_type t WHERE t.group_id = mg.id ORDER BY t.user_type) AS user_types""";

    public MemberGroup {
        userTypes = userTypes == null ? List.of() : List.copyOf(userTypes);
    }

    /**
     * Whether a member of this type may be in the group.
     *
     * @param userType the member's type
     * @return {@code true} for an unbound group, or where the type is one it is bound to
     */
    public boolean admits(StationUserType userType) {
        return userTypes.isEmpty() || userTypes.contains(userType);
    }

    public static RowMapping<MemberGroup> map() {
        return row -> new MemberGroup(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("color"),
                row.getInt("position"),
                row.getObject("group_set_id", Integer.class),
                Arrays.stream((String[]) row.getArray("user_types").getArray())
                        .map(StationUserType::valueOf)
                        .sorted()
                        .toList());
    }
}
