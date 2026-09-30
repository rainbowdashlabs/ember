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
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;

@Singleton
public class CommentCreatedHandler implements DomainEventHandler<CommentCreated> {
    private final Notifier notifier;

    @Inject
    public CommentCreatedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<CommentCreated> eventType() {
        return CommentCreated.class;
    }

    @Override
    public void handle(CommentCreated event) {
        var link = NotificationLinks.comment(
                event.entityType(), event.entityId(), event.ticketAddress(), event.commentId());
        var data = NotificationData.of(
                new NotificationParams.NewsComment(event.entityTitle(), event.authorName(), event.preview()), link);

        if (event.parentAuthorId() != null && !Objects.equals(event.parentAuthorId(), event.authorMemberId())) {
            notifier.notify(
                    StationAudience.member(event.parentAuthorId()),
                    NotificationType.NEWS_COMMENT,
                    data,
                    Delivery.ONCE_WHILE_UNREAD);
        }

        if (CommentEntityType.NEWS.equals(event.entityType())) {
            notifier.notify(
                    StationAudience.holders(event.stationId(), StationPermission.NEWS_MANAGER)
                            .except(event.authorMemberId()),
                    NotificationType.NEWS_COMMENT,
                    data,
                    Delivery.ONCE_WHILE_UNREAD);
        }
    }
}
