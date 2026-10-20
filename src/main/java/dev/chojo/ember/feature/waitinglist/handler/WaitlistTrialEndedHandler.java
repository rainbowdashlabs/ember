/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.WaitlistTrialEnded;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/** Tells whoever looks after the waiting lists that a family ended a trial. */
@Singleton
public class WaitlistTrialEndedHandler implements DomainEventHandler<WaitlistTrialEnded> {
    private final Notifier notifier;

    @Inject
    public WaitlistTrialEndedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<WaitlistTrialEnded> eventType() {
        return WaitlistTrialEnded.class;
    }

    @Override
    public void handle(WaitlistTrialEnded event) {
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.WAITLIST_EDIT),
                NotificationType.WAITLIST_TRIAL_ENDED,
                NotificationData.of(
                        new NotificationParams.WaitlistTrialEnded(event.applicantName(), event.listName()),
                        new NotificationData.NotificationLink("waiting-lists", Map.of())),
                Delivery.EVERY_TIME);
    }
}
