/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
        var link = NotificationLinks.comment(event.entityType(), event.entityId(), event.commentId());
        var data = NotificationData.of(
                new NotificationParams.NewsComment(event.entityTitle(), event.authorName(), event.preview()), link);

        if (event.parentAuthorId() != null && !Objects.equals(event.parentAuthorId(), event.authorMemberId())) {
            notificationService.notifyIfAbsent(event.parentAuthorId(), NotificationType.NEWS_COMMENT, data);
        }

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
