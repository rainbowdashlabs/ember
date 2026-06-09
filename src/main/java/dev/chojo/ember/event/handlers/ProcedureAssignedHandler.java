/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcedureAssigned;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProcedureAssignedHandler implements DomainEventHandler<ProcedureAssigned> {
    private final NotificationService notificationService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public ProcedureAssignedHandler(
            NotificationService notificationService, StationMemberRepository stationMemberRepository) {
        this.notificationService = notificationService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<ProcedureAssigned> eventType() {
        return ProcedureAssigned.class;
    }

    @Override
    public void handle(ProcedureAssigned event) {
        String assignedByName = stationMemberRepository
                .findById(event.assignedByMemberId())
                .map(m -> m.displayName())
                .orElse("?");
        var data = NotificationData.of(
                new NotificationParams.ProcedureAssigned(event.procedureName(), assignedByName),
                new NotificationData.NotificationLink("procedures"));
        notificationService.notifyMembersIfAbsent(
                event.assigneeMemberIds(), NotificationType.PROCEDURE_ASSIGNED, data, event.assignedByMemberId());
    }
}
