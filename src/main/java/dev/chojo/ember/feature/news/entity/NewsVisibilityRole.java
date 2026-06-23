/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.entity;

/**
 * The minimum user type on a partner station that may see a federated news article.
 */
public enum NewsVisibilityRole {
    /**
     * All members can see the news (lowest visibility threshold).
     */
    MEMBER,
    /**
     * Only team members and above can see the news.
     */
    TEAM,
    /**
     * Only managers can see the news.
     */
    MANAGER
}
