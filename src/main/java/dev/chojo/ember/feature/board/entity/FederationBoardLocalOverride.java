/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record FederationBoardLocalOverride(
        int id, int partnerId, int remoteBoardId, Integer roleId, Integer groupId, Integer tagId) {

    public static RowMapping<FederationBoardLocalOverride> map() {
        return row -> new FederationBoardLocalOverride(
                row.getInt("id"),
                row.getInt("partner_id"),
                row.getInt("remote_board_id"),
                row.getObject("role_id", Integer.class),
                row.getObject("group_id", Integer.class),
                row.getObject("tag_id", Integer.class));
    }
}
