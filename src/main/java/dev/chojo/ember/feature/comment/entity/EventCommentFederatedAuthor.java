/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.util.UUID;

/**
 * Maps an event comment created by a federated user to their remote identity.
 *
 * @param commentId      the local comment ID
 * @param partnerId      the federation partner ID
 * @param remoteMemberId the member UUID on the remote station
 */
public record EventCommentFederatedAuthor(int commentId, int partnerId, UUID remoteMemberId) {
    public static RowMapping<EventCommentFederatedAuthor> map() {
        return row -> new EventCommentFederatedAuthor(
                row.getInt("comment_id"),
                row.getInt("partner_id"),
                row.get("remote_member_id", StandardValueConverter.UUID_STRING));
    }
}
