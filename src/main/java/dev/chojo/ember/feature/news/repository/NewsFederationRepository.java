/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.news.entity.NewsCommentFederatedAuthor;
import dev.chojo.ember.feature.news.entity.NewsFederationShare;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing federated news sharing and comment author tracking.
 */
@Singleton
public class NewsFederationRepository {

    // -- Share management --

    /**
     * Finds the federation share configuration for a news article.
     *
     * @param newsId the news article ID
     * @return the share, if configured
     */
    public Optional<NewsFederationShare> findShareByNews(int newsId) {
        return Query.query(
                        "SELECT id, news_id, scope, visibility_role FROM news_federation_share WHERE news_id = :news_id;")
                .single(Call.of().bind("news_id", newsId))
                .map(NewsFederationShare.map())
                .first();
    }

    /**
     * Creates or updates the federation share for a news article.
     *
     * @param newsId         the news article ID
     * @param scope          the sharing scope
     * @param visibilityRole the minimum visibility role on partner stations
     * @return the created or updated share
     */
    public NewsFederationShare setShare(int newsId, String scope, String visibilityRole) {
        return Query.query("""
                        INSERT INTO news_federation_share(news_id, scope, visibility_role)
                        VALUES (:news_id, :scope, :visibility_role)
                        ON CONFLICT (news_id) DO UPDATE SET scope = :scope, visibility_role = :visibility_role
                        RETURNING id, news_id, scope, visibility_role;""")
                .single(Call.of().bind("news_id", newsId).bind("scope", scope).bind("visibility_role", visibilityRole))
                .map(NewsFederationShare.map())
                .first()
                .orElseThrow();
    }

    /**
     * Replaces all share targets for a given share by deleting existing ones and inserting the given partner IDs.
     *
     * @param shareId    the share ID
     * @param partnerIds the partner IDs to target
     */
    public void setShareTargets(int shareId, List<Integer> partnerIds) {
        Query.query("DELETE FROM news_federation_share_target WHERE share_id = :share_id;")
                .single(Call.of().bind("share_id", shareId))
                .delete();
        for (int partnerId : partnerIds) {
            Query.query(
                            "INSERT INTO news_federation_share_target(share_id, partner_id) VALUES (:share_id, :partner_id);")
                    .single(Call.of().bind("share_id", shareId).bind("partner_id", partnerId))
                    .insert();
        }
    }

    /**
     * Retrieves the partner IDs targeted by a share.
     *
     * @param shareId the share ID
     * @return the list of partner IDs
     */
    public List<Integer> findShareTargets(int shareId) {
        return Query.query("SELECT partner_id FROM news_federation_share_target WHERE share_id = :share_id;")
                .single(Call.of().bind("share_id", shareId))
                .map(row -> row.getInt("partner_id"))
                .all();
    }

    /**
     * Removes the federation share for a news article. Cascades to share targets.
     *
     * @param newsId the news article ID
     */
    public void removeShare(int newsId) {
        Query.query("DELETE FROM news_federation_share WHERE news_id = :news_id;")
                .single(Call.of().bind("news_id", newsId))
                .delete();
    }

    // -- Finding shared news for a partner --

    /**
     * Finds news IDs shared with a partner for a given station.
     * A news article is shared if it has scope='ALL_PARTNERS', or scope='SPECIFIC' with the partner in targets.
     * Only published news articles are returned.
     *
     * @param partnerId the federation partner ID
     * @param stationId the station ID
     * @return the list of shared news IDs
     */
    public List<Integer> findSharedNewsIds(int partnerId, int stationId) {
        return Query.query("""
                        SELECT nfs.news_id
                        FROM news_federation_share nfs
                            JOIN news n ON n.id = nfs.news_id
                        WHERE n.station_id = :station_id
                          AND n.published_at IS NOT NULL
                          AND (nfs.scope = 'ALL_PARTNERS'
                               OR (nfs.scope = 'SPECIFIC'
                                   AND EXISTS (SELECT 1 FROM news_federation_share_target nfst
                                               WHERE nfst.share_id = nfs.id AND nfst.partner_id = :partner_id)));""")
                .single(Call.of().bind("station_id", stationId).bind("partner_id", partnerId))
                .map(row -> row.getInt("news_id"))
                .all();
    }

    /**
     * Finds the visibility role for a shared news article.
     *
     * @param newsId the news article ID
     * @return the visibility role, if the news is shared
     */
    public Optional<String> findVisibilityRole(int newsId) {
        return Query.query("SELECT visibility_role FROM news_federation_share WHERE news_id = :news_id;")
                .single(Call.of().bind("news_id", newsId))
                .map(row -> row.getString("visibility_role"))
                .first();
    }

    // -- Federated comment author tracking --

    /**
     * Records the federated author for a news comment.
     *
     * @param commentId      the local comment ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     */
    public void setFederatedCommentAuthor(int commentId, int partnerId, UUID remoteMemberId) {
        Query.query("""
                        INSERT INTO news_comment_federated_author(comment_id, partner_id, remote_member_id)
                        VALUES (:comment_id, :partner_id, :remote_member_id)
                        ON CONFLICT (comment_id) DO UPDATE SET partner_id = :partner_id, remote_member_id = :remote_member_id;""")
                .single(Call.of()
                        .bind("comment_id", commentId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING))
                .insert();
    }

    /**
     * Finds the federated author for a news comment.
     *
     * @param commentId the local comment ID
     * @return the federated author, if present
     */
    public Optional<NewsCommentFederatedAuthor> findFederatedCommentAuthor(int commentId) {
        return Query.query(
                        "SELECT comment_id, partner_id, remote_member_id FROM news_comment_federated_author WHERE comment_id = :comment_id;")
                .single(Call.of().bind("comment_id", commentId))
                .map(NewsCommentFederatedAuthor.map())
                .first();
    }
}
