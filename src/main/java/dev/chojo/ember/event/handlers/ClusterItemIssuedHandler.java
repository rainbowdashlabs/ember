/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterItemIssued;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/**
 * Tells a station that its cluster is sending it something.
 *
 * <p>Separate from the movement's own traffic on purpose: the station is not being asked to
 * acknowledge anything yet, it is being told that a parcel is on its way. The step it will have to
 * answer announces itself later, through the movement.
 */
@Singleton
public class ClusterItemIssuedHandler implements DomainEventHandler<ClusterItemIssued> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public ClusterItemIssuedHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<ClusterItemIssued> eventType() {
        return ClusterItemIssued.class;
    }

    @Override
    public void handle(ClusterItemIssued event) {
        var data = NotificationData.of(
                new NotificationParams.ClusterItemIssued(event.clusterName(), event.itemName()),
                new NotificationData.NotificationLink("inventory-movement-detail", Map.of("id", event.movementId())));
        var recipients =
                stationMemberRepository
                        .findMembersWithPermission(event.stationId(), StationPermission.INVENTORY_MANAGER)
                        .stream()
                        .map(StationMember::id)
                        .toList();
        // Nobody at the station started this, so there is nobody here to leave out. Member ids start at
        // one, so zero excludes none of them.
        notificationService.notifyMembersIfAbsent(recipients, NotificationType.CLUSTER_ITEM_ISSUED, data, 0);
    }
}
