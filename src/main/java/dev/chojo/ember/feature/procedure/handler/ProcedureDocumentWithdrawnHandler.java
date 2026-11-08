/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcedureDocumentWithdrawn;
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

/**
 * Tells whoever resolved a procedure that a signature one of its document steps rested on was withdrawn,
 * so the step is open again while the procedure still reads as resolved.
 */
@Singleton
public class ProcedureDocumentWithdrawnHandler implements DomainEventHandler<ProcedureDocumentWithdrawn> {
    private final Notifier notifier;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public ProcedureDocumentWithdrawnHandler(Notifier notifier, StationMemberRepository stationMemberRepository) {
        this.notifier = notifier;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public Class<ProcedureDocumentWithdrawn> eventType() {
        return ProcedureDocumentWithdrawn.class;
    }

    @Override
    public void handle(ProcedureDocumentWithdrawn event) {
        String withdrawnByName = stationMemberRepository
                .findById(event.withdrawnBy())
                .map(StationMember::displayName)
                .orElse("?");
        var data = NotificationData.of(
                new NotificationParams.ProcedureDocumentWithdrawnParams(
                        event.procedureName(), event.itemTitle(), withdrawnByName),
                new NotificationData.NotificationLink("procedure-detail", Map.of("id", event.procedureId())));
        notifier.notify(
                StationAudience.members(event.toldMemberIds()).except(event.withdrawnBy()),
                NotificationType.PROCEDURE_DOCUMENT_WITHDRAWN,
                data,
                Delivery.EVERY_TIME);
    }
}
