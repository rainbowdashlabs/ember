/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CommentCreatedHandler implements DomainEventHandler<CommentCreated> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public CommentCreatedHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<CommentCreated> eventType() {
        return CommentCreated.class;
    }

    @Override
    public void handle(CommentCreated event) {
        var data = NotificationData.of(
                new NotificationParams.NewsComment(event.newsTitle(), event.authorName(), event.preview()),
                new NotificationData.NotificationLink("news-list"));

        // Notify the news author (unless they wrote the comment)
        if (event.newsAuthorId() != event.authorMemberId()) {
            notificationService.notifyIfAbsent(event.newsAuthorId(), NotificationType.NEWS_COMMENT, data);
        }
        // Notify the parent comment author (if this is a reply)
        if (event.parentAuthorId() != null
                && event.parentAuthorId() != event.authorMemberId()
                && event.parentAuthorId() != event.newsAuthorId()) {
            notificationService.notifyIfAbsent(event.parentAuthorId(), NotificationType.NEWS_COMMENT, data);
        }
        // Notify all NEWS_MANAGER members
        var newsMgmtIds = stationMemberRepository.findMembersWithRole(event.stationId(), Roles.NEWS_MANAGER).stream()
                .map(StationMember::id)
                .toList();
        notificationService.notifyMembersIfAbsent(
                newsMgmtIds, NotificationType.NEWS_COMMENT, data, event.authorMemberId());
    }
}
