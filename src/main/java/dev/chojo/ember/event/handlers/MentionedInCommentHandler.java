/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MentionedInComment;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Handles {@link MentionedInComment} events by sending a notification to the mentioned member.
 */
@Singleton
public class MentionedInCommentHandler implements DomainEventHandler<MentionedInComment> {
    private final NotificationService notificationService;

    @Inject
    public MentionedInCommentHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<MentionedInComment> eventType() {
        return MentionedInComment.class;
    }

    @Override
    public void handle(MentionedInComment event) {
        var link = "news".equals(event.entityType())
                ? new NotificationData.NotificationLink("news-detail", Map.of("id", event.entityId()))
                : new NotificationData.NotificationLink("events");
        var data = NotificationData.of(
                new NotificationParams.NewsComment("", event.authorName(), "mentioned you in a comment"), link);
        notificationService.notifyIfAbsent(event.mentionedMemberId(), NotificationType.NEWS_COMMENT, data);
    }
}
