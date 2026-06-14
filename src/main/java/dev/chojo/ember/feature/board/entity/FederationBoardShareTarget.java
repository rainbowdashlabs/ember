/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StationUserType;

public record FederationBoardShareTarget(
        int shareId, int partnerId, BoardShareMode shareMode, StationUserType requiredUserType) {

    public static RowMapping<FederationBoardShareTarget> map() {
        return row -> new FederationBoardShareTarget(
                row.getInt("share_id"),
                row.getInt("partner_id"),
                row.getEnum("share_mode", BoardShareMode.class),
                row.getEnum("required_user_type", StationUserType.class));
    }
}
