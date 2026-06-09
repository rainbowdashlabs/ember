/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ProcedureResolved;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ProcedureResolvedHandler implements DomainEventHandler<ProcedureResolved> {
    private final NotificationService notificationService;

    @Inject
    public ProcedureResolvedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<ProcedureResolved> eventType() {
        return ProcedureResolved.class;
    }

    @Override
    public void handle(ProcedureResolved event) {
        var data = NotificationData.of(
                new NotificationParams.ProcedureResolvedParams(event.procedureName()),
                new NotificationData.NotificationLink("procedures"));
        notificationService.notifyMembersIfAbsent(
                event.assigneeMemberIds(), NotificationType.PROCEDURE_RESOLVED, data, event.resolvedByMemberId());
    }
}
