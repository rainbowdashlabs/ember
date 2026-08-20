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

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
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

    /**
     * A daily allowance belongs to one provider of one list, so the count has to separate them:
     * what the instance sent through its second provider is nothing to do with what a station sent
     * through its first.
     */
    @Test
    @Order(12)
    void providerDailyCountSeparatesListsAndPositions() {
        LocalDate today = LocalDate.now();
        int before = emailQueueRepo.getProviderDailyCount(today, null, 0);

        emailQueueRepo.enqueue("counted@example.com", "Counted", "Body");
        var pending = emailQueueRepo.fetchPending(1, true);
        emailQueueRepo.markSent(pending.getFirst().id());

        assertEquals(before + 1, emailQueueRepo.getProviderDailyCount(today, null, 0));
        assertEquals(0, emailQueueRepo.getProviderDailyCount(today, null, 1), "another provider of the same list");
        assertEquals(0, emailQueueRepo.getProviderDailyCount(today, station.id(), 0), "a station's own list");
        assertEquals(0, emailQueueRepo.getProviderDailyCount(today.minusDays(1), null, 0), "yesterday");
    }

    /**
     * The overview reads what was always recorded and never readable. It has to separate the
     * instance's post from a station's, because they travel through different lists.
     */
    @Test
    @Order(13)
    void summarySeparatesTheInstanceFromAStation() {
        emailQueueRepo.enqueue("waiting@example.com", "Waiting", "Body");
        emailQueueRepo.enqueue("station@example.com", "Station", "Body", station.id());

        var instance = emailQueueRepo.summary(null);
        var stationSummary = emailQueueRepo.summary(station.id());

        assertTrue(instance.pending() >= 1, "the instance mail waits");
        assertEquals(1, stationSummary.pending(), "the station has exactly the one just written");
        assertNotNull(instance.oldestPendingAt(), "something waits, so there is an oldest");
        assertEquals(0, stationSummary.stuck(), "nothing has been left behind by a dead worker");
    }

    @Test
    @Order(14)
    void pendingByProviderCountsWhereTheMailWaits() {
        var counts = emailQueueRepo.pendingByProvider(null);

        assertTrue(counts.getOrDefault(0, 0) >= 1, "a fresh mail waits at the first provider");
        assertEquals(0, counts.getOrDefault(5, 0), "nothing waits at a provider that does not exist");
    }

    @Test
    @Order(15)
    void recentReturnsTheStationsOwnPostNewestFirst() {
        var recent = emailQueueRepo.recent(station.id(), 10);

        assertFalse(recent.isEmpty());
        assertEquals("station@example.com", recent.getFirst().recipient());
        assertEquals("Station", recent.getFirst().subject());
        assertNotNull(recent.getFirst().createdAt());
        assertTrue(emailQueueRepo.recent(station.id(), 10).size() <= 10, "the limit is honoured");
    }

    /**
     * Puts a mail in the state a worker leaves behind when it dies: handed out and never answered
     * for. Nothing in the application can produce that on purpose, which is why it is written here.
     */
    private static void leaveBehind(String recipient, String age) {
        query("UPDATE email_queue SET status = 'SENDING', created_at = now() - CAST(:age AS interval) "
                        + "WHERE recipient = :recipient;")
                .single(call().bind("recipient", recipient).bind("age", age))
                .update();
    }

    private static String statusOf(String recipient) {
        return query("SELECT status FROM email_queue WHERE recipient = :recipient;")
                .single(call().bind("recipient", recipient))
                .map(row -> row.getString("status"))
                .first()
                .orElseThrow();
    }

    @Test
    @Order(16)
    void stuckNamesTheMailsADeadWorkerLeftBehind() {
        emailQueueRepo.enqueue("left@example.com", "Left behind", "Body", station.id());
        leaveBehind("left@example.com", "30 minutes");

        var stuck = emailQueueRepo.stuck(station.id(), 10);

        assertEquals(1, stuck.size(), "the one left behind is named");
        assertEquals("left@example.com", stuck.getFirst().recipient());
        assertEquals(1, emailQueueRepo.summary(station.id()).stuck(), "and counted the same way");
        assertTrue(
                emailQueueRepo.stuck(null, 10).stream()
                        .noneMatch(entry -> "left@example.com".equals(entry.recipient())),
                "the instance does not see the station's post");
    }

    @Test
    @Order(17)
    void requeueStuckSparesTheMailsAWorkerStillHolds() {
        emailQueueRepo.enqueue("busy@example.com", "Still going", "Body", station.id());
        leaveBehind("busy@example.com", "1 minute");

        assertEquals(1, emailQueueRepo.requeueStuck(station.id(), null), "only the left-behind one moves");

        assertEquals("PENDING", statusOf("left@example.com"), "the left-behind mail waits again");
        assertEquals("SENDING", statusOf("busy@example.com"), "a mail still being sent is untouched");
        assertEquals(0, emailQueueRepo.summary(station.id()).stuck(), "nothing is left behind any more");
    }

    @Test
    @Order(18)
    void requeueStuckTakesOneMailWhenNamed() {
        leaveBehind("left@example.com", "30 minutes");
        leaveBehind("busy@example.com", "30 minutes");
        int other = emailQueueRepo.stuck(station.id(), 10).stream()
                .filter(entry -> "busy@example.com".equals(entry.recipient()))
                .findFirst()
                .orElseThrow()
                .id();

        assertEquals(1, emailQueueRepo.requeueStuck(station.id(), other), "the named mail alone");

        assertEquals("PENDING", statusOf("busy@example.com"));
        assertEquals("SENDING", statusOf("left@example.com"), "the other stays where it was");
    }
}
