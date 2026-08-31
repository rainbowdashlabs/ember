/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.WaitlistInvitationAnswered;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/** Tells whoever looks after the waiting lists that an invitation has been answered. */
@Singleton
public class WaitlistInvitationAnsweredHandler implements DomainEventHandler<WaitlistInvitationAnswered> {
    private final NotificationService notificationService;

    @Inject
    public WaitlistInvitationAnsweredHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<WaitlistInvitationAnswered> eventType() {
        return WaitlistInvitationAnswered.class;
    }

    @Override
    public void handle(WaitlistInvitationAnswered event) {
        notificationService.notifyMembersWithRole(
                event.stationId(),
                "WAITLIST_EDIT",
                NotificationType.WAITLIST_INVITATION_ANSWERED,
                NotificationData.of(
                        new NotificationParams.WaitlistInvitationAnswered(
                                event.applicantName(),
                                event.listName(),
                                event.answer().name()),
                        new NotificationData.NotificationLink("waiting-lists", Map.of())));
    }
}
