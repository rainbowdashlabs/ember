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
        var link =
                switch (event.entityType()) {
                    case NEWS -> new NotificationData.NotificationLink("news-detail", Map.of("id", event.entityId()));
                    case BOARD_TICKET ->
                        new NotificationData.NotificationLink("ticket-detail", Map.of("ticketId", event.entityId()));
                    case KB -> new NotificationData.NotificationLink("kb-file", Map.of("id", event.entityId()));
                    case EVENT -> new NotificationData.NotificationLink("event-detail", Map.of("id", event.entityId()));
                };
        var data = NotificationData.of(
                new NotificationParams.CommentMention(event.entityTitle(), event.authorName(), event.preview()), link);
        notificationService.notifyIfAbsent(event.mentionedMemberId(), NotificationType.COMMENT_MENTION, data);
    }
}
