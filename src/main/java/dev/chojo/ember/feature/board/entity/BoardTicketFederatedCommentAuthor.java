/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record BoardTicketFederatedCommentAuthor(int commentId, int partnerId, String remoteMemberId) {

    public static RowMapping<BoardTicketFederatedCommentAuthor> map() {
        return row -> new BoardTicketFederatedCommentAuthor(
                row.getInt("comment_id"), row.getInt("partner_id"), row.getString("remote_member_id"));
    }
}
