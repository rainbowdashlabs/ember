/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcedureResolved;
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
public class ProcedureResolvedHandler implements DomainEventHandler<ProcedureResolved> {
    private final Notifier notifier;

    @Inject
    public ProcedureResolvedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<ProcedureResolved> eventType() {
        return ProcedureResolved.class;
    }

    @Override
    public void handle(ProcedureResolved event) {
        var data = NotificationData.of(
                new NotificationParams.ProcedureResolvedParams(event.procedureName()),
                new NotificationData.NotificationLink("procedure-detail", Map.of("id", event.procedureId())));
        notifier.notify(
                StationAudience.members(event.assigneeMemberIds()).except(event.resolvedByMemberId()),
                NotificationType.PROCEDURE_RESOLVED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
