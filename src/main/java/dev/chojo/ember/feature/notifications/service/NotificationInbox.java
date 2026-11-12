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
 *
 * <p>A station member's feed leaves out what the station's switched-off modules hide; marking everything
 * read leaves those unread, so they are waiting once the module is back.
 */
@Singleton
public class NotificationInbox {
    private final NotificationRepository notificationRepository;
    private final NotificationVisibility visibility;

    @Inject
    public NotificationInbox(NotificationRepository notificationRepository, NotificationVisibility visibility) {
        this.notificationRepository = notificationRepository;
        this.visibility = visibility;
    }

    /**
     * The fifty most recent notifications, read or not.
     *
     * @param recipient whose feed
     * @return newest first
     */
    public List<Notification> recent(Recipient recipient) {
        return notificationRepository.findRecent(recipient, visibility.hiddenFor(recipient));
    }

    /**
     * Everything not read yet.
     *
     * @param recipient whose feed
     * @return newest first
     */
    public List<Notification> unread(Recipient recipient) {
        return notificationRepository.findUnacknowledged(recipient, visibility.hiddenFor(recipient));
    }

    /**
     * How much has not been read yet.
     *
     * @param recipient whose feed
     * @return the count
     */
    public int countUnread(Recipient recipient) {
        return notificationRepository.countUnacknowledged(recipient, visibility.hiddenFor(recipient));
    }

    /**
     * Marks one notification read, where it is the recipient's.
     *
     * @param recipient whose feed it must be in
     * @param id        the notification
     */
    public void acknowledge(Recipient recipient, int id) {
        notificationRepository.acknowledge(recipient, id);
    }

    /**
     * Marks one notification read that was opened through its link, where it is one of the
     * account's own, in any station or association.
     *
     * @param accountId the account that opened it
     * @param id        the notification
     */
    public void markOpened(int accountId, int id) {
        notificationRepository.acknowledgeForAccount(accountId, id);
    }

    /**
     * Marks everything waiting as read.
     *
     * @param recipient whose feed
     * @return how many were marked
     */
    public int acknowledgeAll(Recipient recipient) {
        return notificationRepository.acknowledgeAll(recipient, visibility.hiddenFor(recipient));
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
