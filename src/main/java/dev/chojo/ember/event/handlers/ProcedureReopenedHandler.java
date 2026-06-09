/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcedureReopened;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProcedureReopenedHandler implements DomainEventHandler<ProcedureReopened> {
    private final NotificationService notificationService;

    @Inject
    public ProcedureReopenedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<ProcedureReopened> eventType() {
        return ProcedureReopened.class;
    }

    @Override
    public void handle(ProcedureReopened event) {
        var data = NotificationData.of(
                new NotificationParams.ProcedureReopenedParams(event.procedureName()),
                new NotificationData.NotificationLink("procedures"));
        notificationService.notifyMembersIfAbsent(
                event.assigneeMemberIds(), NotificationType.PROCEDURE_REOPENED, data, event.reopenedByMemberId());
    }
}
