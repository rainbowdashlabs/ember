/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ExchangeStatusChanged;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ExchangeStatusChangedHandler implements DomainEventHandler<ExchangeStatusChanged> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public ExchangeStatusChangedHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<ExchangeStatusChanged> eventType() {
        return ExchangeStatusChanged.class;
    }

    @Override
    public void handle(ExchangeStatusChanged event) {
        var data = NotificationData.of(
                new NotificationParams.ExchangeStatusChange(event.newStatus().name(), event.inventoryName(), null),
                new NotificationData.NotificationLink("inventory-exchanges"));
        notificationService.notify(event.memberId(), NotificationType.EXCHANGE_STATUS_CHANGE, data);
        var invMgmtIds =
                stationMemberRepository.findMembersWithRole(event.stationId(), Roles.INVENTORY_MANAGER).stream()
                        .map(StationMember::id)
                        .toList();
        notificationService.notifyMembersIfAbsent(
                invMgmtIds, NotificationType.EXCHANGE_STATUS_CHANGE, data, event.memberId());
    }
}
