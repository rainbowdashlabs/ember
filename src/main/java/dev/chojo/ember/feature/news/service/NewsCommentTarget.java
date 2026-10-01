/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.CreatedAudience;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.comment.service.CommentTarget;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

/**
 * Comments on a news entry.
 *
 * <p>They are read and written by whoever may read the entry, restrictions included. Only their
 * author changes them; their author or a news manager removes them. Every comment tells the news
 * managers of the station besides the author of the answered comment; a comment from a partner
 * station tells the same people but announces no mentions.
 *
 * <p>An entry the instance published to every station belongs to no station. A comment under it
 * belongs to the station its author wrote from, and whatever it tells stays in that station.
 */
@Singleton
public class NewsCommentTarget implements CommentTarget {
    private final NewsService news;

    @Inject
    public NewsCommentTarget(NewsService news) {
        this.news = news;
    }

    @Override
    public CommentEntityType type() {
        return CommentEntityType.NEWS;
    }

    @Override
    public Optional<TargetInfo> find(int targetId) {
        return news.findById(targetId)
                .map(entry -> new TargetInfo(
                        CommentEntityType.NEWS,
                        entry.id(),
                        entry.systemEntry() ? null : entry.stationId(),
                        entry.title(),
                        null,
                        entry.systemEntry()));
    }

    @Override
    public Refusal missing() {
        return Refusal.NEWS_NOT_HERE_OR_NOT_YOURS;
    }

    @Override
    public void requireReadable(StationSession session, TargetInfo target) {
        news.requireReadable(session, target.id());
    }

    @Override
    public void requireWritable(StationSession session, TargetInfo target) {
        news.requireReadable(session, target.id());
    }

    @Override
    public boolean mayModerate(StationSession session, TargetInfo target, Moderation action) {
        return action == Moderation.DELETE && session.hasPermission(StationPermission.NEWS_MANAGER);
    }

    @Override
    public CreatedAudience audienceFor(TargetInfo target, CommentOrigin origin) {
        Integer stationId = target.stationId();
        StationAudience managers =
                stationId == null ? null : StationAudience.holders(stationId, StationPermission.NEWS_MANAGER);
        return new CreatedAudience(true, origin == CommentOrigin.LOCAL, managers);
    }

    @Override
    public NotificationLink link(TargetInfo target, int commentId) {
        return NotificationLinks.comment(NotificationLinks.news(target.id()), commentId);
    }
}
