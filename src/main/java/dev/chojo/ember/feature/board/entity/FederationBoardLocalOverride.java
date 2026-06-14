/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.auth.StationUserType;

import java.util.UUID;

public record FederationBoardLocalOverride(
        int id, int partnerId, UUID remoteBoardUid, StationUserType userType, Integer groupId, Integer tagId) {

    public static RowMapping<FederationBoardLocalOverride> map() {
        return row -> new FederationBoardLocalOverride(
                row.getInt("id"),
                row.getInt("partner_id"),
                row.get("remote_board_uid", StandardValueConverter.UUID_STRING),
                row.getEnum("user_type", StationUserType.class),
                row.getObject("group_id", Integer.class),
                row.getObject("tag_id", Integer.class));
    }
}
