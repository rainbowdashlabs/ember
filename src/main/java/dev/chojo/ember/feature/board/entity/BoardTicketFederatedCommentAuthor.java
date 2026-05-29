/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.util.UUID;

public record BoardTicketFederatedCommentAuthor(int commentId, int partnerId, UUID remoteMemberId) {

    public static RowMapping<BoardTicketFederatedCommentAuthor> map() {
        return row -> new BoardTicketFederatedCommentAuthor(
                row.getInt("comment_id"),
                row.getInt("partner_id"),
                row.get("remote_member_id", StandardValueConverter.UUID_STRING));
    }
}
