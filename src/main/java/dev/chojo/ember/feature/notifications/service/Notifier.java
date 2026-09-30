/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.notifications.entity.Audience;
import dev.chojo.ember.feature.notifications.entity.ClusterAudience;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;

/**
 * The one way to tell people something.
 *
 * <p>The audience says who, the delivery says whether an identical unread notification suppresses
 * this one, and one statement writes every row. Preferences, the people who left and the actor are
 * applied inside that statement, and the unique index on unread keys makes "once while unread" hold
 * when two requests send the same notification at the same moment.
 *
 * <p>The cluster service arrives as a provider: it needs the event bus several steps down, and the
 * bus is built from the handlers that need this class.
 */
@Singleton
public class Notifier {
    private static final Logger log = LoggerFactory.getLogger(Notifier.class);

    private final NotificationRepository notificationRepository;
    private final Provider<ClusterService> clusterService;

    @Inject
    public Notifier(NotificationRepository notificationRepository, Provider<ClusterService> clusterService) {
        this.notificationRepository = notificationRepository;
        this.clusterService = clusterService;
    }

    /**
     * Writes one notification per person the audience reaches.
     *
     * @param audience who is meant
     * @param type     the notification category
     * @param data     the message data, which must carry a link
     * @param delivery whether an identical unread notification suppresses this one
     * @return how many notifications were written
     * @throws IllegalArgumentException when the data carries no link
     */
    public int notify(Audience audience, NotificationType type, NotificationData data, Delivery delivery) {
        requireLink(type, data);
        return switch (audience) {
            case StationAudience station ->
                station.reachesNobody() ? 0 : notificationRepository.insertForStation(station, type, data, delivery);
            case ClusterAudience cluster -> notifyCluster(cluster, type, data, delivery);
        };
    }

    /**
     * Withdraws the unread notifications of a type that point at one particular entity, for the
     * case where the entity is still there and only the message has stopped being true.
     *
     * @param type the notification type
     * @param link the link the notification must carry
     */
    public void withdraw(NotificationType type, NotificationData.NotificationLink link) {
        notificationRepository.deleteByTypeAndLink(type, link);
        log.debug("Withdrew the unread {} notifications pointing at {}", type, link.routeParams());
    }

    /**
     * Takes every notification pointing at one entity away with it, read or not, for the case where
     * the entity itself has been deleted and nothing they link to is left.
     *
     * @param link the link the notification must carry
     */
    public void withdrawAll(NotificationData.NotificationLink link) {
        int removed = notificationRepository.deleteAllPointingAt(link);
        log.debug("Removed {} notifications pointing at the deleted {}", removed, link.routeParams());
    }

    private int notifyCluster(
            ClusterAudience audience, NotificationType type, NotificationData data, Delivery delivery) {
        var ids = new HashSet<>(audience.memberIds());
        if (audience.holdersClusterId() != null) {
            for (ClusterPermission permission : audience.permissions()) {
                ids.addAll(clusterService.get().findMemberIdsWith(audience.holdersClusterId(), permission));
            }
        }
        if (ids.isEmpty()) return 0;
        return notificationRepository.insertForCluster(ids, audience.excluded(), type, data, delivery);
    }

    /**
     * Every notification must carry a link, so the in-app list, the mail and the feed all have
     * somewhere to lead. Failing here is what makes a sender that forgot one fail in its tests.
     */
    private static void requireLink(NotificationType type, NotificationData data) {
        if (data == null || data.link() == null) {
            throw new IllegalArgumentException("Notification " + type + " requires a NotificationLink; got " + data);
        }
    }
}
