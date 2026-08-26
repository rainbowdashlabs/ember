/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.MovementCancelled;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Map;

/**
 * Tells everybody who was part of a movement that it was called off, except whoever called it off.
 *
 * <p>The one who pressed it knows. The others do not, and for them it is the end of something they
 * were waiting on: a member who was promised a replacement, a station that had planned around the
 * piece coming back. The message names the piece and says where it stayed, because a movement called
 * off during the post does not bring anything home.
 */
@Singleton
public class MovementCancelledHandler implements DomainEventHandler<MovementCancelled> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public MovementCancelledHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<MovementCancelled> eventType() {
        return MovementCancelled.class;
    }

    @Override
    public void handle(MovementCancelled event) {
        var data = NotificationData.of(
                new NotificationParams.MovementCancelled(
                        event.inventoryName(), event.itemName(), event.reason(), event.itemStayedAway()),
                new NotificationData.NotificationLink("inventory-movement-detail", Map.of("id", event.movementId())));
        var recipients =
                new ArrayList<>(MovementNotificationRouting.stationTeam(stationMemberRepository, event.stationId()));
        if (event.memberId() != null) recipients.add(event.memberId());
        notificationService.notifyMembersIfAbsent(
                recipients, NotificationType.MOVEMENT_CANCELLED, data, event.actorMemberId());
    }
}
