/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record KbAccessRestriction(
        int id, Integer folderId, Integer fileId, String userType, Integer groupId, Integer tagId, Integer memberId) {

    public static RowMapping<KbAccessRestriction> map() {
        return row -> new KbAccessRestriction(
                row.getInt("id"),
                row.getObject("folder_id", Integer.class),
                row.getObject("file_id", Integer.class),
                row.getString("user_type"),
                row.getObject("group_id", Integer.class),
                row.getObject("tag_id", Integer.class),
                row.getObject("member_id", Integer.class));
    }
}
