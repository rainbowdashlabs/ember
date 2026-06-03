/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
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
public class RegistrationDeadlineExpiredHandler implements DomainEventHandler<RegistrationDeadlineExpired> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public RegistrationDeadlineExpiredHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<RegistrationDeadlineExpired> eventType() {
        return RegistrationDeadlineExpired.class;
    }

    @Override
    public void handle(RegistrationDeadlineExpired event) {
        var data = NotificationData.of(
                new NotificationParams.RegistrationDeadlineExpired(event.eventName(), event.pendingCount()),
                new NotificationData.NotificationLink("events-registrations", Map.of("id", event.eventId())));
        var eventMgmtIds =
                stationMemberRepository
                        .findMembersWithPermission(event.stationId(), StationPermission.EVENT_MANAGER)
                        .stream()
                        .map(StationMember::id)
                        .toList();
        notificationService.notifyMembers(eventMgmtIds, NotificationType.REGISTRATION_DEADLINE_EXPIRED, data);
    }
}
