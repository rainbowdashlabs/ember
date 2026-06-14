/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.federation.entity.ShareScope;

/**
 * Represents a federation sharing configuration for a news article.
 *
 * @param id             the unique identifier of the share
 * @param newsId         the news article being shared
 * @param scope          the sharing scope (ALL_PARTNERS or SPECIFIC)
 * @param visibilityRole the minimum role on partner station (MEMBER, TEAM, or MANAGER)
 */
public record NewsFederationShare(int id, int newsId, ShareScope scope, NewsVisibilityRole visibilityRole) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<NewsFederationShare> map() {
        return row -> new NewsFederationShare(
                row.getInt("id"),
                row.getInt("news_id"),
                row.getEnum("scope", ShareScope.class),
                row.getEnum("visibility_role", NewsVisibilityRole.class));
    }
}
