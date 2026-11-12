/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.service;

import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Notifications of a module a station switched off: none are written while it is off, the ones already
 * there are kept but neither listed nor counted, and they are back with the module.
 */
class SwitchedOffModuleNotificationsTest extends RepositoryTestBase {
    private Station station;
    private StationMember reader;
    private NotificationInbox inbox;
    private Notifier notifier;

    @BeforeEach
    void setup() {
        station = stationRepo.create("Wache Schalter " + System.nanoTime());
        reader = stationMemberRepo.create(
                station.id(),
                accountRepo
                        .create("schalter-" + System.nanoTime() + "@test.com", "Sina", "Schalter")
                        .id());
        var modules = new ModuleAvailability(stationRepo, clusterRepo);
        inbox = new NotificationInbox(notificationRepo, new NotificationVisibility(modules, stationMemberRepo));
        notifier = new Notifier(notificationRepo, () -> clusterService, modules);
    }

    @AfterEach
    void cleanup() {
        stationRepo.delete(station.id());
    }

    private Recipient feed() {
        return Recipient.stationMember(reader.id());
    }

    private int tell(NotificationType type, NotificationData data) {
        return notifier.notify(StationAudience.member(reader.id()), type, data, Delivery.EVERY_TIME);
    }

    private static NotificationData news(String title) {
        return NotificationData.of(
                new NotificationParams.NewNews(title, "Anna", "Vorschau"), NotificationLinks.news(1));
    }

    private static NotificationData mentionOnNews() {
        return NotificationData.of(
                new NotificationParams.CommentMention("Dienstplan", "Ben", "@Sina"),
                NotificationLinks.comment(NotificationLinks.news(1), 7));
    }

    private static NotificationData lending() {
        return NotificationData.of(
                new NotificationParams.LendingNewRequest("Wache Nord", "2 Jacken"),
                NotificationLinks.lendingRequest(3));
    }

    /** Kept notifications of a module are hidden while it is off and back once it is on. */
    @Test
    void keptNotificationsAreHiddenAndComeBack() {
        tell(NotificationType.NEW_NEWS, news("Vorher"));
        tell(NotificationType.COMMENT_MENTION, mentionOnNews());
        tell(NotificationType.LENDING_NEW_REQUEST, lending());
        assertEquals(3, inbox.countUnread(feed()));

        stationRepo.setDisabledModules(station.id(), Set.of(StationModule.NEWS));
        assertEquals(1, inbox.countUnread(feed()));
        assertEquals(1, inbox.unread(feed()).size());
        assertEquals(
                NotificationType.LENDING_NEW_REQUEST,
                inbox.recent(feed()).getFirst().type());
        assertEquals(1, inbox.acknowledgeAll(feed()), "marking all read leaves the hidden ones alone");

        stationRepo.setDisabledModules(station.id(), Set.of());
        assertEquals(2, inbox.countUnread(feed()), "the news and the mention are back, still unread");
        assertEquals(3, inbox.recent(feed()).size());
    }

    /** Nothing of a switched-off module or part is written; other modules go on as before. */
    @Test
    void nothingOfASwitchedOffModuleIsWritten() {
        stationRepo.setDisabledModules(station.id(), Set.of(StationModule.INVENTORY));

        assertEquals(0, tell(NotificationType.LENDING_NEW_REQUEST, lending()), "a part goes with its module");
        assertEquals(
                0,
                tell(
                        NotificationType.CLUSTER_ITEM_ISSUED,
                        NotificationData.of(
                                new NotificationParams.ClusterItemIssued("Kreisverband", "Helm"),
                                new NotificationData.NotificationLink("inventory-movement-detail", Map.of("id", 4)))),
                "an item the cluster issues is a movement");
        assertEquals(1, tell(NotificationType.NEW_NEWS, news("Weiter")));
        assertEquals(1, tell(NotificationType.COMMENT_MENTION, mentionOnNews()));

        stationRepo.setDisabledModules(station.id(), Set.of(StationModule.NEWS));
        assertEquals(0, tell(NotificationType.COMMENT_MENTION, mentionOnNews()), "a mention follows its page");
        assertEquals(
                0,
                notifier.notify(
                        StationAudience.wholeStation(station.id()),
                        NotificationType.NEW_NEWS,
                        news("Alle"),
                        Delivery.EVERY_TIME));
        assertEquals(1, tell(NotificationType.LENDING_NEW_REQUEST, lending()));

        stationRepo.setDisabledModules(station.id(), Set.of());
        assertTrue(
                inbox.unread(feed()).stream()
                        .noneMatch(n -> "Alle".equals(n.data().paramsAsMap().get("title"))),
                "nothing written while off shows up later");
    }

    /** A module the cluster denies writes nothing either. */
    @Test
    void aClusterDenialWritesNothing() {
        var home = stationRepo.create("Träger Schalter " + System.nanoTime());
        var cluster = clusterRepo.create("Kreisverband Schalter " + System.nanoTime(), null, home.id());
        stationRepo.setCluster(station.id(), cluster.id());
        clusterRepo.setDeniedModules(cluster.id(), null, Set.of(StationModule.INVENTORY_LENDING));
        try {
            assertEquals(0, tell(NotificationType.LENDING_NEW_REQUEST, lending()));
            assertFalse(inbox.unread(feed()).stream().anyMatch(n -> n.type() == NotificationType.LENDING_NEW_REQUEST));
        } finally {
            stationRepo.setCluster(station.id(), null);
            clusterRepo.delete(cluster.id());
            stationRepo.delete(home.id());
        }
    }

    /** The settings page offers no type of a switched-off module. */
    @Test
    void theSettingsOfferNothingOfASwitchedOffModule() {
        var visibility =
                new NotificationVisibility(new ModuleAvailability(stationRepo, clusterRepo), stationMemberRepo);
        stationRepo.setDisabledModules(station.id(), Set.of(StationModule.INVENTORY_MOVEMENTS));

        var offered = visibility.offeredAt(station.id());

        assertFalse(offered.contains(NotificationType.MOVEMENT_RAISED));
        assertTrue(offered.contains(NotificationType.PROCUREMENT_REQUESTED));
        assertTrue(offered.contains(NotificationType.COMMENT_MENTION));
        assertEquals(
                Set.of(), visibility.hiddenFor(Recipient.clusterMember(1)).types(), "a cluster feed hides nothing");
        assertTrue(visibility.hiddenFor(Recipient.stationMember(-1)).isEmpty(), "nobody hides nothing");
    }
}
