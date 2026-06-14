/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ExchangeRequested;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class ExchangeRequestedHandler implements DomainEventHandler<ExchangeRequested> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public ExchangeRequestedHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<ExchangeRequested> eventType() {
        return ExchangeRequested.class;
    }

    @Override
    public void handle(ExchangeRequested event) {
        var data = NotificationData.of(
                new NotificationParams.ExchangeNewRequest(event.memberName(), event.inventoryName(), event.reason()),
                // Inventory id rides with the link so the feed renderer can surface the
                // inventory's ownership flow in the body without a separate lookup table.
                new NotificationData.NotificationLink("inventory-exchanges", Map.of("id", event.inventoryId())));
        var inventoryMgmtIds =
                stationMemberRepository
                        .findMembersWithPermission(event.stationId(), StationPermission.INVENTORY_MANAGER)
                        .stream()
                        .map(StationMember::id)
                        .toList();
        notificationService.notifyMembersIfAbsent(
                inventoryMgmtIds, NotificationType.EXCHANGE_NEW_REQUEST, data, event.memberId());
    }
}
