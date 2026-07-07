/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EmailQueueRepositoryTest extends RepositoryTestBase {
    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Email Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void enqueueWithoutStation() {
        emailQueueRepo.enqueue("test@example.com", "Subject 1", "Body 1");
        assertEquals(1, emailQueueRepo.pendingCount());
    }

    @Test
    @Order(2)
    void enqueueWithStation() {
        emailQueueRepo.enqueue("test2@example.com", "Subject 2", "Body 2", station.id());
        assertEquals(2, emailQueueRepo.pendingCount());
    }

    @Test
    @Order(3)
    void fetchPending() {
        var pending = emailQueueRepo.fetchPending(10, true);
        assertEquals(2, pending.size());
        assertEquals("test@example.com", pending.getFirst().recipient());
        // After fetch, status is SENDING, so pending count is 0
        assertEquals(0, emailQueueRepo.pendingCount());
    }

    @Test
    @Order(4)
    void markSent() {
        // Re-enqueue to test markSent
        emailQueueRepo.enqueue("sent@example.com", "Sent Test", "Body");
        var pending = emailQueueRepo.fetchPending(1, true);
        assertFalse(pending.isEmpty());
        emailQueueRepo.markSent(pending.getFirst().id());
        assertEquals(0, emailQueueRepo.pendingCount());
    }

    @Test
    @Order(5)
    void markFailed() {
        emailQueueRepo.enqueue("fail@example.com", "Fail Test", "Body");
        var pending = emailQueueRepo.fetchPending(1, true);
        emailQueueRepo.markFailed(pending.getFirst().id());
        assertEquals(0, emailQueueRepo.pendingCount());
    }

    @Test
    @Order(6)
    void requeue() {
        emailQueueRepo.enqueue("requeue@example.com", "Requeue Test", "Body");
        var pending = emailQueueRepo.fetchPending(1, true);
        emailQueueRepo.markFailed(pending.getFirst().id());
        emailQueueRepo.requeue(pending.getFirst().id());
        assertEquals(1, emailQueueRepo.pendingCount());
        // Clean up by fetching and marking sent
        var refetched = emailQueueRepo.fetchPending(1, true);
        emailQueueRepo.markSent(refetched.getFirst().id());
    }

    @Test
    @Order(7)
    void fetchPendingWithoutGlobalFetchesOnlyStationMails() {
        emailQueueRepo.enqueue("global@example.com", "Global", "Body");
        emailQueueRepo.enqueue("station@example.com", "Station", "Body", station.id());
        var stationOnly = emailQueueRepo.fetchPending(10, false);
        assertEquals(1, stationOnly.size());
        assertEquals("station@example.com", stationOnly.getFirst().recipient());
        assertEquals(1, emailQueueRepo.pendingCount());
        var rest = emailQueueRepo.fetchPending(10, true);
        assertEquals(1, rest.size());
        assertEquals("global@example.com", rest.getFirst().recipient());
    }

    @Test
    @Order(10)
    void dailyCount() {
        LocalDate today = LocalDate.now();
        assertEquals(0, emailQueueRepo.getDailyCount(today));
        emailQueueRepo.incrementDailyCount(today);
        assertEquals(1, emailQueueRepo.getDailyCount(today));
        emailQueueRepo.incrementDailyCount(today);
        assertEquals(2, emailQueueRepo.getDailyCount(today));
    }

    @Test
    @Order(11)
    void cleanupOldEntries() {
        // Should not throw
        emailQueueRepo.cleanupOldEntries(1);
    }
}
