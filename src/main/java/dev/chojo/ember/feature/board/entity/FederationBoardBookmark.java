/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record FederationBoardBookmark(
        int id,
        int memberId,
        int partnerId,
        UUID remoteBoardUid,
        String remoteBoardName,
        String remoteBoardShortKey,
        BoardShareMode shareMode,
        Instant createdAt) {

    public static RowMapping<FederationBoardBookmark> map() {
        return row -> new FederationBoardBookmark(
                row.getInt("id"),
                row.getInt("member_id"),
                row.getInt("partner_id"),
                row.get("remote_board_uid", StandardValueConverter.UUID_STRING),
                row.getString("remote_board_name"),
                row.getString("remote_board_short_key"),
                row.getEnum("share_mode", BoardShareMode.class),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
