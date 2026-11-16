/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.WaitlistInvitationDatesGone;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/** Tells whoever looks after the waiting lists that an open invitation has no date left. */
@Singleton
public class WaitlistInvitationDatesGoneHandler implements DomainEventHandler<WaitlistInvitationDatesGone> {
    private final Notifier notifier;

    @Inject
    public WaitlistInvitationDatesGoneHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<WaitlistInvitationDatesGone> eventType() {
        return WaitlistInvitationDatesGone.class;
    }

    @Override
    public void handle(WaitlistInvitationDatesGone event) {
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.WAITLIST_EDIT),
                NotificationType.WAITLIST_INVITATION_DATES_GONE,
                NotificationData.of(
                        new NotificationParams.WaitlistInvitationDatesGone(event.applicantName(), event.listName()),
                        new NotificationData.NotificationLink("waiting-lists", Map.of())),
                Delivery.EVERY_TIME);
    }
}
