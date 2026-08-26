/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterFieldValueChanged;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells a member that somebody at their cluster filled something in about them.
 *
 * <p>Addressed to the member themselves rather than to their station's managers: the managers see it in the
 * ordinary change list, which this write goes into like any other, while the member has no other way of
 * finding out that a change came from outside their station.
 */
@Singleton
public class ClusterFieldValueChangedHandler implements DomainEventHandler<ClusterFieldValueChanged> {
    private final NotificationService notificationService;

    @Inject
    public ClusterFieldValueChangedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<ClusterFieldValueChanged> eventType() {
        return ClusterFieldValueChanged.class;
    }

    @Override
    public void handle(ClusterFieldValueChanged event) {
        var data = NotificationData.of(
                new NotificationParams.ClusterFieldValueChanged(event.clusterName(), event.fieldNames()),
                new NotificationData.NotificationLink("profile"));
        notificationService.notifyIfAbsent(event.memberId(), NotificationType.CLUSTER_FIELD_VALUE_CHANGED, data);
    }
}
