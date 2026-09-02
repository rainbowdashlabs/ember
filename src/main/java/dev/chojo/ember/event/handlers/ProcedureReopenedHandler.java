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

import java.util.Map;

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
                // procedureId rides on the link so the feed renderer can surface progress.
                new NotificationData.NotificationLink("procedure-detail", Map.of("id", event.procedureId())));
        notificationService.notifyMembersIfAbsent(
                event.assigneeMemberIds(), NotificationType.PROCEDURE_REOPENED, data, event.reopenedByMemberId());
    }
}
