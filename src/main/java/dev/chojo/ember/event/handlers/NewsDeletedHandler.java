/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.NewsDeleted;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
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

    /**
     * Takes the article's notifications with it. The announcement of the article and everything
     * written under it point at the same page, which is the page that has just gone.
     */
    @Override
    public void handle(NewsDeleted event) {
        notificationService.deleteAllPointingAt(NotificationLinks.news(event.newsId()));
    }
}
