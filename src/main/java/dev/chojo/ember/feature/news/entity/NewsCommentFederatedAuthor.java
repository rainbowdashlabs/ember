/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.util.UUID;

/**
 * Maps a news comment from a federated user to their remote identity.
 *
 * @param commentId      the local news comment ID
 * @param partnerId      the federation partner ID
 * @param remoteMemberId the member UUID on the remote station
 */
public record NewsCommentFederatedAuthor(int commentId, int partnerId, UUID remoteMemberId) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<NewsCommentFederatedAuthor> map() {
        return row -> new NewsCommentFederatedAuthor(
                row.getInt("comment_id"),
                row.getInt("partner_id"),
                row.get("remote_member_id", StandardValueConverter.UUID_STRING));
    }
}
