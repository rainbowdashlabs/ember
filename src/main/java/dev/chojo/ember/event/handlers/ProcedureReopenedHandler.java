/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcedureReopened;
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
public class ProcedureReopenedHandler implements DomainEventHandler<ProcedureReopened> {
    private final Notifier notifier;

    @Inject
    public ProcedureReopenedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<ProcedureReopened> eventType() {
        return ProcedureReopened.class;
    }

    @Override
    public void handle(ProcedureReopened event) {
        var data = NotificationData.of(
                new NotificationParams.ProcedureReopenedParams(event.procedureName()),
                new NotificationData.NotificationLink("procedure-detail", Map.of("id", event.procedureId())));
        notifier.notify(
                StationAudience.members(event.assigneeMemberIds()).except(event.reopenedByMemberId()),
                NotificationType.PROCEDURE_REOPENED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
