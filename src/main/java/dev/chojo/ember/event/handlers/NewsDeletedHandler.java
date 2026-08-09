/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.NewsDeleted;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NewsDeletedHandler implements DomainEventHandler<NewsDeleted> {
    private final NotificationService notificationService;

    @Inject
    public NewsDeletedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<NewsDeleted> eventType() {
        return NewsDeleted.class;
    }

    @Override
    public void handle(NewsDeleted event) {
        notificationService.deleteByTypeContaining(
                NotificationType.NEW_NEWS,
                NotificationData.of(new NotificationParams.NewNews(event.title(), null, null)));
        notificationService.deleteByTypeContaining(
                NotificationType.NEWS_COMMENT,
                NotificationData.of(new NotificationParams.NewsComment(event.title(), null, null)));
    }
}
