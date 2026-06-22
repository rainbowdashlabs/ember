/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

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
import java.util.List;

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
        assertTrue(notificationRepo.exists(member.id(), NotificationType.NEW_NEWS, data.toJson()));
    }

    @Test
    @Order(3)
    void existsFalse() {
        var data = NotificationData.of(new NotificationParams.NewNews(null, null, null));
        assertFalse(notificationRepo.exists(member.id(), NotificationType.NEW_NEWS, data.toJson()));
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
}
