/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.Roles;
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
                new NotificationData.NotificationLink("inventory-exchanges"));
        var inventoryMgmtIds =
                stationMemberRepository.findMembersWithRole(event.stationId(), Roles.INVENTORY_MANAGER).stream()
                        .map(StationMember::id)
                        .toList();
        notificationService.notifyMembersIfAbsent(
                inventoryMgmtIds, NotificationType.EXCHANGE_NEW_REQUEST, data, event.memberId());
    }
}
