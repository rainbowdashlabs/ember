/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
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
public class EventRegistrationStatusHandler implements DomainEventHandler<EventRegistrationStatusChanged> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public EventRegistrationStatusHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<EventRegistrationStatusChanged> eventType() {
        return EventRegistrationStatusChanged.class;
    }

    @Override
    public void handle(EventRegistrationStatusChanged event) {
        var data = NotificationData.of(
                new NotificationParams.EventRegistrationStatus(event.eventName(), event.newStatus(), null),
                new NotificationData.NotificationLink("event-detail", Map.of("id", event.eventId())));

        notificationService.notify(event.memberId(), NotificationType.EVENT_REGISTRATION_STATUS, data);

        var eventMgmtIds =
                stationMemberRepository
                        .findMembersWithPermission(event.stationId(), StationPermission.EVENT_MANAGER)
                        .stream()
                        .map(StationMember::id)
                        .toList();
        notificationService.notifyMembersIfAbsent(
                eventMgmtIds, NotificationType.EVENT_REGISTRATION_STATUS, data, event.memberId());
    }
}
