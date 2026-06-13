/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.NewsCreated;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class NewsCreatedHandler implements DomainEventHandler<NewsCreated> {
    private final NotificationService notificationService;

    @Inject
    public NewsCreatedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<NewsCreated> eventType() {
        return NewsCreated.class;
    }

    @Override
    public void handle(NewsCreated event) {
        notificationService.notifyStation(
                event.stationId(),
                NotificationType.NEW_NEWS,
                NotificationData.of(
                        new NotificationParams.NewNews(event.title(), event.authorName(), event.preview()),
                        new NotificationData.NotificationLink("news-detail", Map.of("id", event.newsId()))));
    }
}
