/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.feature.feed.render.FeedDetails;
import dev.chojo.ember.feature.feed.render.FeedDetailsContributor;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import jakarta.inject.Singleton;

/**
 * What a feed entry says about a mention in a comment: who wrote it, where, and how it begins.
 */
@Singleton
public class CommentFeedDetails implements FeedDetailsContributor {

    @Override
    public void contribute(NotificationParams params, Notification notification, FeedDetails details) {
        if (params instanceof NotificationParams.CommentMention(String entityTitle, String author, String preview)) {
            details.putIfPresent(details.label("by"), author);
            details.putIfPresent(details.label("entity", "Entity"), entityTitle);
            details.putSnippetIfPresent(details.label("preview", "Preview"), preview);
        }
    }

    @Override
    public String author(NotificationParams params) {
        return params instanceof NotificationParams.CommentMention p ? p.author() : null;
    }
}
