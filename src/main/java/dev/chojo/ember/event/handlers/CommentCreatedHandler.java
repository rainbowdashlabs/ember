/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Objects;

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
        var link =
                switch (event.entityType()) {
                    case NEWS -> new NotificationData.NotificationLink("news-detail", Map.of("id", event.entityId()));
                    case KB -> new NotificationData.NotificationLink("kb-file", Map.of("id", event.entityId()));
                    default -> new NotificationData.NotificationLink("events");
                };
        var data = NotificationData.of(
                new NotificationParams.NewsComment(event.entityTitle(), event.authorName(), event.preview()), link);

        // Notify the parent comment author (if this is a reply and they didn't write this comment)
        if (event.parentAuthorId() != null && !Objects.equals(event.parentAuthorId(), event.authorMemberId())) {
            notificationService.notifyIfAbsent(event.parentAuthorId(), NotificationType.NEWS_COMMENT, data);
        }

        // Notify managers only for news comments
        if (CommentEntityType.NEWS.equals(event.entityType())) {
            var newsMgmtIds =
                    stationMemberRepository
                            .findMembersWithPermission(event.stationId(), StationPermission.NEWS_MANAGER)
                            .stream()
                            .map(StationMember::id)
                            .toList();
            notificationService.notifyMembersIfAbsent(
                    newsMgmtIds,
                    NotificationType.NEWS_COMMENT,
                    data,
                    event.authorMemberId() != null ? event.authorMemberId() : -1);
        }
    }
}
