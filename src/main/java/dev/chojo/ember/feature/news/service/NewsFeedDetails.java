/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Singleton;

/**
 * What a feed entry says about a news entry or a comment on one: who wrote it and how it begins.
 */
@Singleton
public class NewsFeedDetails implements FeedDetailsContributor {

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        switch (params) {
            case NotificationParams.NewNews p -> {
                details.putIfPresent(details.label("by"), p.author());
                details.putSnippetIfPresent(details.label("preview", "Preview"), p.preview());
            }
            case NotificationParams.NewsComment(String newsTitle, String author, String preview) -> {
                details.putIfPresent(details.label("by"), author);
                details.putIfPresent(details.label("newsTitle", "News"), newsTitle);
                details.putSnippetIfPresent(details.label("preview", "Preview"), preview);
            }
            default -> {}
        }
    }

    @Override
    public String author(NotificationParams params) {
        return switch (params) {
            case NotificationParams.NewNews p -> p.author();
            case NotificationParams.NewsComment p -> p.author();
            default -> null;
        };
    }
}
