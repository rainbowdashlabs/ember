/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CommentDeletedHandler implements DomainEventHandler<CommentDeleted> {
    private final NotificationService notificationService;

    @Inject
    public CommentDeletedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<CommentDeleted> eventType() {
        return CommentDeleted.class;
    }

    @Override
    public void handle(CommentDeleted event) {
        notificationService.deleteByTypeContaining(
                NotificationType.NEWS_COMMENT,
                NotificationData.of(new NotificationParams.NewsComment(null, null, event.preview())));
    }
}
