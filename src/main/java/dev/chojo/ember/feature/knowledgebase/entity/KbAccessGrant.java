/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StationUserType;

import java.util.List;

/**
 * One statement about who may reach a knowledge-base folder or file. Exactly one of the subject
 * columns is set: a user type, a group, a user tag, or a single member.
 *
 * @param level what that audience may do, or {@code null} for a row that names an audience and
 *              leaves the level to the station permission the member holds
 */
public record KbAccessGrant(
        int id,
        Integer folderId,
        Integer fileId,
        StationUserType userType,
        Integer groupId,
        Integer tagId,
        Integer memberId,
        KbAccessLevel level) {

    public static RowMapping<KbAccessGrant> map() {
        return row -> new KbAccessGrant(
                row.getInt("id"),
                row.getObject("folder_id", Integer.class),
                row.getObject("file_id", Integer.class),
                row.getEnum("user_type", StationUserType.class),
                row.getObject("group_id", Integer.class),
                row.getObject("tag_id", Integer.class),
                row.getObject("member_id", Integer.class),
                row.getEnum("level", KbAccessLevel.class));
    }

    /**
     * Tells whether this grant names the given member, through any of its subject shapes.
     */
    public boolean matches(int memberId, StationUserType memberUserType, List<Integer> groupIds, List<Integer> tagIds) {
        if (userType != null) return userType == memberUserType;
        if (groupId != null) return groupIds.contains(groupId);
        if (tagId != null) return tagIds.contains(tagId);
        if (this.memberId != null) return this.memberId == memberId;
        return false;
    }
}
