/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record FederationBoardShareTarget(int shareId, int partnerId, BoardShareMode shareMode) {

    public static RowMapping<FederationBoardShareTarget> map() {
        return row -> new FederationBoardShareTarget(
                row.getInt("share_id"), row.getInt("partner_id"), BoardShareMode.valueOf(row.getString("share_mode")));
    }
}
