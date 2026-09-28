/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.NewsCreated;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class NewsCreatedHandler implements DomainEventHandler<NewsCreated> {
    private final NotificationService notificationService;
    private final RestrictionService restrictionService;

    @Inject
    public NewsCreatedHandler(NotificationService notificationService, RestrictionService restrictionService) {
        this.notificationService = notificationService;
        this.restrictionService = restrictionService;
    }

    @Override
    public Class<NewsCreated> eventType() {
        return NewsCreated.class;
    }

    /**
     * Announces a new blog entry to the members who may open it.
     */
    @Override
    public void handle(NewsCreated event) {
        notificationService.notifyAudience(
                event.stationId(),
                restrictionService.findMembersPassingRestriction(
                        RestrictionType.NEWS, event.newsId(), event.stationId()),
                NotificationType.NEW_NEWS,
                NotificationData.of(
                        new NotificationParams.NewNews(event.title(), event.authorName(), event.preview()),
                        NotificationLinks.news(event.newsId())));
    }
}
