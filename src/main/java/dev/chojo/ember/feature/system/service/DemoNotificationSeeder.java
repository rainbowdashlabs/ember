/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.repository.NotificationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Seeder for demo notification data displayed on the dashboard.
 */
@Singleton
public class DemoNotificationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoNotificationSeeder.class);

    private final NotificationRepository notificationRepository;

    @Inject
    public DemoNotificationSeeder(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public void seedNotifications(
            int stationId,
            StationMember admin,
            List<StationMember> betreuer,
            List<StationMember> eltern,
            List<StationMember> anfaenger,
            List<StationMember> fortgeschritten,
            int tagDerOffenenTuerEventId,
            int stadtfestEventId,
            int newsId) {
        // News notification for all members
        for (var m : betreuer) {
            notificationRepository.create(
                    m.id(),
                    NotificationType.NEW_NEWS,
                    NotificationData.of(
                            new NotificationParams.NewNews(
                                    "Neue Ausrüstung eingetroffen",
                                    "Anna Schmidt",
                                    "Die bestellten Helme und Handschuhe sind eingetroffen..."),
                            new NotificationData.NotificationLink("news-detail", Map.of("id", newsId))));
        }

        // Comment notifications for Betreuer (news author gets comment notification)
        notificationRepository.create(
                betreuer.get(1).id(),
                NotificationType.NEWS_COMMENT,
                NotificationData.of(
                        new NotificationParams.NewsComment(
                                "Neue Ausrüstung eingetroffen",
                                "Klaus Schulze",
                                "Werden die alten Helme eingesammelt?"),
                        new NotificationData.NotificationLink("news-detail", Map.of("id", newsId))));

        // Exchange request notification for Betreuer (INVENTORY_MANAGER)
        for (var m : betreuer) {
            notificationRepository.create(
                    m.id(),
                    NotificationType.EXCHANGE_NEW_REQUEST,
                    NotificationData.of(
                            new NotificationParams.ExchangeNewRequest("Tim Berger", "Blouson", "Zu klein geworden"),
                            new NotificationData.NotificationLink("inventory-exchanges")));
        }

        // Event registration status for some kids
        for (int i = 0; i < 3 && i < fortgeschritten.size(); i++) {
            notificationRepository.create(
                    fortgeschritten.get(i).id(),
                    NotificationType.EVENT_REGISTRATION_STATUS,
                    NotificationData.of(
                            new NotificationParams.EventRegistrationStatus("Tag der offenen Tür", "ACCEPTED", null),
                            new NotificationData.NotificationLink(
                                    "event-detail", Map.of("id", tagDerOffenenTuerEventId))));
        }

        // New event notification for some members
        for (int i = 0; i < 5 && i < anfaenger.size(); i++) {
            notificationRepository.create(
                    anfaenger.get(i).id(),
                    NotificationType.NEW_EVENT,
                    NotificationData.of(
                            new NotificationParams.NewEvent(
                                    "Stadtfest Musterstadt", "Stand der Jugendfeuerwehr beim Stadtfest"),
                            new NotificationData.NotificationLink("event-detail", Map.of("id", stadtfestEventId))));
        }

        // Group membership notification for some Eltern
        for (int i = 0; i < 3 && i < eltern.size(); i++) {
            notificationRepository.create(
                    eltern.get(i).id(),
                    NotificationType.MEMBER_ADDED_TO_GROUP,
                    NotificationData.of(
                            new NotificationParams.MemberAddedToGroup("Eltern"),
                            new NotificationData.NotificationLink("dashboard-overview")));
        }

        // Procurement notification for a kid
        notificationRepository.create(
                anfaenger.get(2).id(),
                NotificationType.PROCUREMENT_REQUESTED,
                NotificationData.of(
                        new NotificationParams.ProcurementRequested("Handschuhe"),
                        new NotificationData.NotificationLink("dashboard-overview")));

        // Profile change notification for Betreuer
        notificationRepository.create(
                betreuer.getFirst().id(),
                NotificationType.PROFILE_FIELD_CHANGED,
                NotificationData.of(
                        new NotificationParams.ProfileFieldChanged("Lukas Frank", "Allergien"),
                        new NotificationData.NotificationLink(
                                "members-detail", Map.of("id", anfaenger.get(0).id()))));

        log.info("Demo: Created sample notifications for dashboard");
    }
}
