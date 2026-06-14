/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StationUserType;

/**
 * A single restriction entry. Exactly one of userType, groupId, tagId, memberId is set.
 * Used uniformly across events, quiz tests, forms, news, and KB.
 */
public record Restriction(int id, StationUserType userType, Integer groupId, Integer tagId, Integer memberId) {

    public static RowMapping<Restriction> map() {
        return row -> new Restriction(
                row.getInt("id"),
                row.getEnum("user_type", StationUserType.class),
                row.getObject("group_id", Integer.class),
                row.getObject("tag_id", Integer.class),
                row.getObject("member_id", Integer.class));
    }
}
