/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.ClusterMemberRoleChanged;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Tells a cluster member that what they may do has changed.
 *
 * <p>Only they hear. Whether somebody else's standing moved is nobody's business but the people who decided
 * it, and they already know.
 */
@Singleton
public class ClusterMemberRoleChangedHandler implements DomainEventHandler<ClusterMemberRoleChanged> {
    private final Notifier notifier;

    @Inject
    public ClusterMemberRoleChangedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<ClusterMemberRoleChanged> eventType() {
        return ClusterMemberRoleChanged.class;
    }

    @Override
    public void handle(ClusterMemberRoleChanged event) {
        var data = NotificationData.of(
                new NotificationParams.ClusterMemberRoleChanged(event.clusterName()),
                new NotificationData.NotificationLink("cluster-overview"));
        notifier.notify(
                ClusterAudience.members(List.of(event.clusterMemberId())),
                NotificationType.CLUSTER_MEMBER_ROLE_CHANGED,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
