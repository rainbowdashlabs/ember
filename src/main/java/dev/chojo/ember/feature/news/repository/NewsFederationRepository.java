/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.news.entity.NewsFederationShare;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing federated news sharing and comment author tracking.
 */
@Singleton
public class NewsFederationRepository {

    private static final String SHARE_COLUMNS = "id, news_id, scope, visibility_role";

    /**
     * Finds the federation share configuration for a news article.
     *
     * @param newsId the news article ID
     * @return the share, if configured
     */
    public Optional<NewsFederationShare> findShareByNews(int newsId) {
        return query("SELECT %s FROM news_federation_share WHERE news_id = :news_id;", SHARE_COLUMNS)
                .single(call().bind("news_id", newsId))
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
    public NewsFederationShare setShare(int newsId, ShareScope scope, NewsVisibilityRole visibilityRole) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO news_federation_share(news_id, scope, visibility_role)
                VALUES (:news_id, :scope, :visibility_role)
                ON CONFLICT (news_id) DO UPDATE SET scope = :scope, visibility_role = :visibility_role
                RETURNING %s;""".formatted(SHARE_COLUMNS),
                call().bind("news_id", newsId).bind("scope", scope).bind("visibility_role", visibilityRole),
                NewsFederationShare.map());
    }

    /**
     * Replaces all share targets for a given share by deleting existing ones and inserting the given partner IDs.
     *
     * @param shareId    the share ID
     * @param partnerIds the partner IDs to target
     */
    public void setShareTargets(int shareId, List<Integer> partnerIds) {
        query("DELETE FROM news_federation_share_target WHERE share_id = :share_id;")
                .single(call().bind("share_id", shareId))
                .delete();
        for (int partnerId : partnerIds) {
            query("INSERT INTO news_federation_share_target(share_id, partner_id) VALUES (:share_id, :partner_id);")
                    .single(call().bind("share_id", shareId).bind("partner_id", partnerId))
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
        return query("SELECT partner_id FROM news_federation_share_target WHERE share_id = :share_id;")
                .single(call().bind("share_id", shareId))
                .map(row -> row.getInt("partner_id"))
                .all();
    }

    /**
     * Removes the federation share for a news article. Cascades to share targets.
     *
     * @param newsId the news article ID
     */
    public void removeShare(int newsId) {
        query("DELETE FROM news_federation_share WHERE news_id = :news_id;")
                .single(call().bind("news_id", newsId))
                .delete();
    }

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
        return query("""
                SELECT nfs.news_id
                FROM news_federation_share nfs
                    JOIN news n ON n.id = nfs.news_id
                WHERE n.station_id = :station_id
                  AND n.published_at IS NOT NULL
                  AND (nfs.scope = 'ALL_PARTNERS'
                       OR (nfs.scope = 'SPECIFIC'
                           AND exists (SELECT 1 FROM news_federation_share_target nfst
                                       WHERE nfst.share_id = nfs.id AND nfst.partner_id = :partner_id)));""")
                .single(call().bind("station_id", stationId).bind("partner_id", partnerId))
                .map(row -> row.getInt("news_id"))
                .all();
    }

    /**
     * Finds the visibility role for a shared news article.
     *
     * @param newsId the news article ID
     * @return the visibility role, if the news is shared
     */
    public Optional<NewsVisibilityRole> findVisibilityRole(int newsId) {
        return query("SELECT visibility_role FROM news_federation_share WHERE news_id = :news_id;")
                .single(call().bind("news_id", newsId))
                .map(row -> row.getEnum("visibility_role", NewsVisibilityRole.class))
                .first();
    }
}
