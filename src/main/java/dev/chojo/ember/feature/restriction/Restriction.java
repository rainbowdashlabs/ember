/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A single restriction entry. Exactly one of userType, groupId, tagId, memberId is set.
 * Used uniformly across events, quiz tests, forms, news, and KB.
 */
public record Restriction(int id, String userType, Integer groupId, Integer tagId, Integer memberId) {

    public static RowMapping<Restriction> map() {
        return row -> new Restriction(
                row.getInt("id"),
                row.getString("user_type"),
                row.getObject("group_id", Integer.class),
                row.getObject("tag_id", Integer.class),
                row.getObject("member_id", Integer.class));
    }
}
