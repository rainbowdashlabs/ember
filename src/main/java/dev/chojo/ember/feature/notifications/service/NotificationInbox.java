/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * What is waiting for somebody, and marking it read.
 *
 * <p>One set of methods for both kinds of recipient: the recipient says whose feed is meant, so a
 * route for station members and one for cluster members ask the same questions the same way.
 */
@Singleton
public class NotificationInbox {
    private final NotificationRepository notificationRepository;

    @Inject
    public NotificationInbox(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * The fifty most recent notifications, read or not.
     *
     * @param recipient whose feed
     * @return newest first
     */
    public List<Notification> recent(Recipient recipient) {
        return switch (recipient) {
            case Recipient.OfStation station -> notificationRepository.findAll(station.memberId());
            case Recipient.OfCluster cluster ->
                notificationRepository.findAllForClusterMember(cluster.clusterMemberId());
        };
    }

    /**
     * Everything not read yet.
     *
     * @param recipient whose feed
     * @return newest first
     */
    public List<Notification> unread(Recipient recipient) {
        return switch (recipient) {
            case Recipient.OfStation station -> notificationRepository.findUnacknowledged(station.memberId());
            case Recipient.OfCluster cluster ->
                notificationRepository.findUnacknowledgedForClusterMember(cluster.clusterMemberId());
        };
    }

    /**
     * How much has not been read yet.
     *
     * @param recipient whose feed
     * @return the count
     */
    public int countUnread(Recipient recipient) {
        return switch (recipient) {
            case Recipient.OfStation station -> notificationRepository.countUnacknowledged(station.memberId());
            case Recipient.OfCluster cluster ->
                notificationRepository.countUnacknowledgedForClusterMember(cluster.clusterMemberId());
        };
    }

    /**
     * Marks one notification read, where it is the recipient's.
     *
     * @param recipient whose feed it must be in
     * @param id        the notification
     */
    public void acknowledge(Recipient recipient, int id) {
        switch (recipient) {
            case Recipient.OfStation station -> notificationRepository.acknowledge(id, station.memberId());
            case Recipient.OfCluster cluster ->
                notificationRepository.acknowledgeForClusterMember(id, cluster.clusterMemberId());
        }
    }

    /**
     * Marks everything waiting as read.
     *
     * @param recipient whose feed
     * @return how many were marked
     */
    public int acknowledgeAll(Recipient recipient) {
        return switch (recipient) {
            case Recipient.OfStation station -> notificationRepository.acknowledgeAll(station.memberId());
            case Recipient.OfCluster cluster ->
                notificationRepository.acknowledgeAllForClusterMember(cluster.clusterMemberId());
        };
    }

    /**
     * The newest notification of a station member, for telling whether their personal feed changed.
     *
     * @param memberId the station member
     * @return the stamp, with id 0 where they have none
     */
    public NotificationRepository.Stamp latestStamp(int memberId) {
        return notificationRepository.findMaxStamp(memberId);
    }
}
