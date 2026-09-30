/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcedureAssigned;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
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
public class ProcedureAssignedHandler implements DomainEventHandler<ProcedureAssigned> {
    private final Notifier notifier;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public ProcedureAssignedHandler(Notifier notifier, StationMemberRepository stationMemberRepository) {
        this.notifier = notifier;
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
                .map(StationMember::displayName)
                .orElse("?");
        var data = NotificationData.of(
                new NotificationParams.ProcedureAssigned(event.procedureName(), assignedByName),
                new NotificationData.NotificationLink("procedure-detail", Map.of("id", event.procedureId())));
        notifier.notify(
                StationAudience.members(event.assigneeMemberIds()).except(event.assignedByMemberId()),
                NotificationType.PROCEDURE_ASSIGNED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
