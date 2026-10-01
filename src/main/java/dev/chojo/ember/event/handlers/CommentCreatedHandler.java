/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;

/**
 * Tells the author of the answered comment about a reply, and whoever the comment's target names
 * besides about every comment. Who that is the target decided when the comment was written; the
 * author is never told about their own comment.
 */
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
        var data = NotificationData.of(
                new NotificationParams.NewsComment(event.entityTitle(), event.authorName(), event.preview()),
                event.link());

        Integer parentAuthorId = event.parentAuthorId();
        if (parentAuthorId != null && !Objects.equals(parentAuthorId, event.authorMemberId())) {
            notifier.notify(
                    StationAudience.member(parentAuthorId),
                    NotificationType.NEWS_COMMENT,
                    data,
                    Delivery.ONCE_WHILE_UNREAD);
        }

        var alsoTold = event.alsoTold();
        if (alsoTold != null) {
            notifier.notify(
                    alsoTold.except(event.authorMemberId()),
                    NotificationType.NEWS_COMMENT,
                    data,
                    Delivery.ONCE_WHILE_UNREAD);
        }
    }
}
