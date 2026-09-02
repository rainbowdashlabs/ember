/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int notificationId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Notif Station");
        account = accountRepo.create("notif@test.com", "Notif", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var data = NotificationData.of(new NotificationParams.NewNews("Test", null, null));
        var notif = notificationRepo.create(member.id(), NotificationType.NEW_NEWS, data);
        assertNotNull(notif);
        assertEquals(member.id(), notif.memberId());
        assertEquals(NotificationType.NEW_NEWS, notif.type());
        notificationId = notif.id();
    }

    @Test
    @Order(2)
    void exists() {
        var data = NotificationData.of(new NotificationParams.NewNews("Test", null, null));
        assertTrue(notificationRepo.exists(member.id(), NotificationType.NEW_NEWS, data));
    }

    @Test
    @Order(3)
    void existsFalse() {
        var data = NotificationData.of(new NotificationParams.NewNews(null, null, null));
        assertFalse(notificationRepo.exists(member.id(), NotificationType.NEW_NEWS, data));
    }

    @Test
    @Order(4)
    void findUnacknowledged() {
        var unack = notificationRepo.findUnacknowledged(member.id());
        assertEquals(1, unack.size());
    }

    @Test
    @Order(5)
    void findAll() {
        var all = notificationRepo.findAll(member.id());
        assertEquals(1, all.size());
    }

    @Test
    @Order(6)
    void countUnacknowledged() {
        assertEquals(1, notificationRepo.countUnacknowledged(member.id()));
    }

    @Test
    @Order(7)
    void findUnemailed() {
        var unemailed = notificationRepo.findUnemailed();
        assertFalse(unemailed.isEmpty());
    }

    @Test
    @Order(8)
    void markEmailed() {
        notificationRepo.markEmailed(List.of(notificationId));
        var unemailed = notificationRepo.findUnemailed();
        assertTrue(unemailed.stream().noneMatch(n -> n.id() == notificationId));
    }

    @Test
    @Order(10)
    void acknowledge() {
        assertTrue(notificationRepo.acknowledge(notificationId, member.id()));
        assertEquals(0, notificationRepo.countUnacknowledged(member.id()));
    }

    @Test
    @Order(11)
    void acknowledgeAlreadyAcknowledged() {
        assertFalse(notificationRepo.acknowledge(notificationId, member.id()));
    }

    @Test
    @Order(12)
    void acknowledgeAll() {
        // Create another notification to test bulk acknowledge
        var data = NotificationData.of(new NotificationParams.NewEvent(null, null));
        notificationRepo.create(member.id(), NotificationType.NEW_EVENT, data);
        assertEquals(1, notificationRepo.countUnacknowledged(member.id()));
        int count = notificationRepo.acknowledgeAll(member.id());
        assertEquals(1, count);
        assertEquals(0, notificationRepo.countUnacknowledged(member.id()));
    }

    @Test
    @Order(13)
    void deleteOldAcknowledged() {
        // Should not throw
        notificationRepo.deleteOldAcknowledged();
    }

    @Test
    @Order(20)
    void findMaxStampForMemberWithoutNotificationsReturnsZeroAndEpoch() {
        var freshAcc = accountRepo.create("stamp-empty@test.com", "Stamp", "Empty");
        var freshMember = stationMemberRepo.create(station.id(), freshAcc.id());
        try {
            var stamp = notificationRepo.findMaxStamp(freshMember.id());
            assertEquals(0, stamp.maxId());
            assertEquals(Instant.EPOCH, stamp.maxCreatedAt());
        } finally {
            stationMemberRepo.delete(freshMember.id());
            accountRepo.delete(freshAcc.id());
        }
    }

    @Test
    @Order(21)
    void findMaxStampAdvancesAfterInsert() {
        var freshAcc = accountRepo.create("stamp-fresh@test.com", "Stamp", "Fresh");
        var freshMember = stationMemberRepo.create(station.id(), freshAcc.id());
        try {
            var n = notificationRepo.create(
                    freshMember.id(),
                    NotificationType.MEMBER_ADDED_TO_GROUP,
                    NotificationData.of(new NotificationParams.MemberAddedToGroup("Alpha", null)));
            var stamp = notificationRepo.findMaxStamp(freshMember.id());
            assertEquals(n.id(), stamp.maxId());
            assertTrue(stamp.maxCreatedAt().isAfter(Instant.EPOCH));
        } finally {
            stationMemberRepo.delete(freshMember.id());
            accountRepo.delete(freshAcc.id());
        }
    }

    /**
     * The cluster side of the feed. A cluster member is not a station member, so everything about their
     * notifications is a separate path, and the point worth checking is that the two never see each other.
     */
    @Test
    @Order(22)
    void aClusterMemberHasATotallySeparateFeed() {
        var cluster = clusterService.create("Kreisverband Post", null);
        var clusterAccount = accountRepo.create("cluster-notif@test.com", "Clus", "Post");
        var clusterMember = clusterService.addMember(cluster.id(), clusterAccount.id(), ClusterUserType.CLUSTER_ADMIN);

        var data = NotificationData.of(
                new NotificationParams.ClusterApplicationSubmitted("Wache Nord"),
                new NotificationData.NotificationLink("cluster-applications"));

        var created = notificationRepo.createForClusterMember(
                clusterMember.id(), NotificationType.CLUSTER_APPLICATION_SUBMITTED, data);
        assertNull(created.memberId(), "a cluster notification names no station member");
        assertEquals(clusterMember.id(), created.clusterMemberId());

        assertTrue(notificationRepo.existsForClusterMember(
                clusterMember.id(), NotificationType.CLUSTER_APPLICATION_SUBMITTED, data));
        assertEquals(1, notificationRepo.countUnacknowledgedForClusterMember(clusterMember.id()));
        assertEquals(
                1,
                notificationRepo
                        .findUnacknowledgedForClusterMember(clusterMember.id())
                        .size());
        assertEquals(
                1, notificationRepo.findAllForClusterMember(clusterMember.id()).size());

        // The station member's own feed is untouched by any of it
        assertFalse(notificationRepo.findAll(member.id()).stream().anyMatch(n -> n.id() == created.id()));

        assertTrue(notificationRepo.acknowledgeForClusterMember(created.id(), clusterMember.id()));
        assertFalse(
                notificationRepo.acknowledgeForClusterMember(created.id(), clusterMember.id()),
                "acknowledging twice changes nothing");
        assertEquals(0, notificationRepo.countUnacknowledgedForClusterMember(clusterMember.id()));

        notificationRepo.createForClusterMember(
                clusterMember.id(),
                NotificationType.CLUSTER_APPLICATION_WITHDRAWN,
                NotificationData.of(
                        new NotificationParams.ClusterApplicationWithdrawn("Wache Nord"),
                        new NotificationData.NotificationLink("cluster-applications")));
        assertEquals(1, notificationRepo.acknowledgeAllForClusterMember(clusterMember.id()));

        // And it is never picked up for a digest, because a cluster member has no station mailbox
        assertFalse(notificationRepo.findUnemailed().stream().anyMatch(n -> n.id() == created.id()));

        clusterService.removeMember(clusterMember.id());
        accountRepo.delete(clusterAccount.id());
        clusterService.delete(cluster.id());
    }

    /**
     * Withdrawing by what a notification points at reaches exactly that one, where withdrawing by
     * the words it carries reaches every notification worded the same.
     */
    @Test
    @Order(60)
    void deleteByTypeAndLinkTakesOnlyTheOneItPointsAt() {
        var about7 = new NotificationData.NotificationLink("lost-and-found", Map.of("id", 7));
        var about8 = new NotificationData.NotificationLink("lost-and-found", Map.of("id", 8));
        notificationRepo.create(
                member.id(),
                NotificationType.LOST_AND_FOUND_NEW,
                NotificationData.of(new NotificationParams.LostAndFoundNew(""), about7));
        notificationRepo.create(
                member.id(),
                NotificationType.LOST_AND_FOUND_NEW,
                NotificationData.of(new NotificationParams.LostAndFoundNew(""), about8));

        assertEquals(1, notificationRepo.deleteByTypeAndLink(NotificationType.LOST_AND_FOUND_NEW, about7));

        var left = notificationRepo.findUnacknowledged(member.id()).stream()
                .filter(n -> n.type() == NotificationType.LOST_AND_FOUND_NEW)
                .toList();
        assertEquals(1, left.size());
        assertEquals(
                8,
                Integer.parseInt(String.valueOf(
                        left.getFirst().data().link().routeParams().get("id"))));

        assertEquals(0, notificationRepo.deleteByTypeAndLink(NotificationType.LOST_AND_FOUND_CLAIMED, about8));
        assertEquals(1, notificationRepo.deleteByTypeAndLink(NotificationType.LOST_AND_FOUND_NEW, about8));
    }

    /**
     * Where the thing itself is gone, everything pointing at it goes: of whatever type, and read as
     * well as unread, because a read notification leads to the same missing page as an unread one.
     */
    @Test
    @Order(61)
    void deleteAllPointingAtTakesTheReadOnesTooButOnlyForThatOneThing() {
        var about21 = new NotificationData.NotificationLink("event-detail", Map.of("id", 21));
        var about22 = new NotificationData.NotificationLink("event-detail", Map.of("id", 22));
        notificationRepo.create(
                member.id(),
                NotificationType.NEW_EVENT,
                NotificationData.of(new NotificationParams.NewEvent("Probe", ""), about21));
        var read = notificationRepo.create(
                member.id(),
                NotificationType.EVENT_CANCELLED,
                NotificationData.of(new NotificationParams.EventCancelled("Probe", "Krank"), about21));
        assertTrue(notificationRepo.acknowledge(read.id(), member.id()));
        notificationRepo.create(
                member.id(),
                NotificationType.NEW_EVENT,
                NotificationData.of(new NotificationParams.NewEvent("Probe", ""), about22));

        assertEquals(2, notificationRepo.deleteAllPointingAt(about21));
        assertEquals(1, notificationRepo.deleteAllPointingAt(about22));
    }

    /**
     * A link that leaves a route parameter out reaches every notification carrying at least the
     * ones it names, which is how all the reminders for one appointment are withdrawn at once.
     */
    @Test
    @Order(62)
    void deleteAllPointingAtReachesEveryDateOfOneAppointment() {
        notificationRepo.create(
                member.id(),
                NotificationType.EVENT_REMINDER,
                NotificationData.of(
                        new NotificationParams.EventReminder("Probe", 2, LocalDate.of(2026, 5, 4)),
                        new NotificationData.NotificationLink(
                                "event-detail-date", Map.of("id", "31", "date", "2026-05-04"))));
        notificationRepo.create(
                member.id(),
                NotificationType.EVENT_REMINDER,
                NotificationData.of(
                        new NotificationParams.EventReminder("Probe", 2, LocalDate.of(2026, 5, 11)),
                        new NotificationData.NotificationLink(
                                "event-detail-date", Map.of("id", "31", "date", "2026-05-11"))));
        notificationRepo.create(
                member.id(),
                NotificationType.EVENT_REMINDER,
                NotificationData.of(
                        new NotificationParams.EventReminder("Andere", 2, LocalDate.of(2026, 5, 4)),
                        new NotificationData.NotificationLink(
                                "event-detail-date", Map.of("id", "32", "date", "2026-05-04"))));

        assertEquals(
                2,
                notificationRepo.deleteAllPointingAt(
                        new NotificationData.NotificationLink("event-detail-date", Map.of("id", "31"))));
        assertEquals(
                1,
                notificationRepo.deleteAllPointingAt(
                        new NotificationData.NotificationLink("event-detail-date", Map.of("id", "32"))));
    }
}
