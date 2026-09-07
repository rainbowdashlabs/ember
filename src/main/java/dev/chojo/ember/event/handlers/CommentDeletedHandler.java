/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
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

    /**
     * Takes the notifications about that one comment with it, read or not, and leaves the ones
     * about its neighbours standing.
     *
     * <p>The page survives the comment, so matching on the page would sweep up every notification
     * written under it. The match names the comment instead, and every notification that names a
     * comment is about that comment: it quotes an excerpt that is now gone and opens on a spot that
     * no longer says anything, whether the removal left a placeholder behind or not.
     *
     * <p>Notifications written before comments had an address of their own name only the page, so
     * nothing here reaches them. They still open on something that exists, and any match wide
     * enough to catch them would take valid neighbours along.
     */
    @Override
    public void handle(CommentDeleted event) {
        notificationService.deleteAllPointingAt(NotificationLinks.commentAlone(event.entityType(), event.commentId()));
    }
}
