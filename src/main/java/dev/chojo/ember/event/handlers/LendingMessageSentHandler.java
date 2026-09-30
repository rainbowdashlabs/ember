/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.LendingMessageSent;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class LendingMessageSentHandler implements DomainEventHandler<LendingMessageSent> {
    private final Notifier notifier;

    @Inject
    public LendingMessageSentHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<LendingMessageSent> eventType() {
        return LendingMessageSent.class;
    }

    @Override
    public void handle(LendingMessageSent event) {
        notifier.notify(
                StationAudience.holders(event.targetStationId(), StationPermission.INVENTORY_MANAGER),
                NotificationType.LENDING_NEW_MESSAGE,
                NotificationData.of(
                        new NotificationParams.LendingNewMessage(event.senderStationName(), event.senderName()),
                        new NotificationData.NotificationLink(
                                "inventory-lending-detail", Map.of("id", event.requestId()))),
                Delivery.EVERY_TIME);
    }
}
