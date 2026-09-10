/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.repository;

import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MailMailboxRepositoryTest extends RepositoryTestBase {

    private static MailMailboxRepository repository;
    private static Station station;
    private static int mailboxId;

    private static final byte[] IV = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
    private static final byte[] CIPHERTEXT = "not a real password".getBytes();

    @BeforeAll
    static void setup() {
        repository = new MailMailboxRepository();
        station = stationRepo.create("Mailbox Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void aMailboxComesBackAsItWentIn() {
        var mailbox = repository.create(
                station.id(),
                "Archiv",
                "imap.musterstadt.de",
                993,
                MailSecurity.SSL,
                "archive",
                new EncryptedBlob(IV, CIPHERTEXT),
                "INBOX",
                15,
                Instant.parse("2026-09-01T00:00:00Z"));

        assertNotNull(mailbox);
        assertEquals("Archiv", mailbox.name());
        assertEquals(MailSecurity.SSL, mailbox.security());
        assertArrayEquals(IV, mailbox.password().iv());
        assertArrayEquals(CIPHERTEXT, mailbox.password().ciphertext());
        assertFalse(mailbox.enabled(), "a mailbox is off until a test has answered");
        assertEquals(0, mailbox.failureCount());
        assertNull(mailbox.lastCheckAt());
        mailboxId = mailbox.id();
    }

    @Test
    @Order(2)
    void aMailboxIsFoundByItsIdAndByItsStation() {
        assertTrue(repository.findById(mailboxId).isPresent());
        assertTrue(repository.findByStation(station.id()).stream().anyMatch(box -> box.id() == mailboxId));
        assertTrue(repository.findById(-1).isEmpty());
    }

    /** A mailbox that is off is not visited, which is what keeps a half-configured one from being read. */
    @Test
    @Order(3)
    void onlyAnEnabledMailboxIsOfferedToThePoller() {
        assertFalse(repository.findPollable().stream().anyMatch(box -> box.id() == mailboxId));

        repository.update(
                mailboxId,
                "Archiv",
                "imap.musterstadt.de",
                993,
                MailSecurity.SSL,
                "archive",
                "Eingang",
                true,
                30,
                Instant.parse("2026-09-01T00:00:00Z"));

        assertTrue(repository.findPollable().stream().anyMatch(box -> box.id() == mailboxId));
        var updated = repository.findById(mailboxId).orElseThrow();
        assertEquals("Eingang", updated.folder());
        assertEquals(30, updated.intervalMinutes());
    }

    /**
     * The password has a method of its own so that rewriting the connection settings cannot clear a
     * credential the caller was never given.
     */
    @Test
    @Order(4)
    void rewritingTheSettingsLeavesThePasswordAlone() {
        var afterUpdate = repository.findById(mailboxId).orElseThrow();

        assertArrayEquals(CIPHERTEXT, afterUpdate.password().ciphertext());

        byte[] fresh = "a different password".getBytes();
        assertTrue(repository.updatePassword(mailboxId, new EncryptedBlob(IV, fresh)));
        assertArrayEquals(
                fresh, repository.findById(mailboxId).orElseThrow().password().ciphertext());
    }

    @Test
    @Order(5)
    void aFailureIsCountedAndStaysOnTheMailbox() {
        Instant at = Instant.parse("2026-09-10T10:00:00Z");

        repository.recordFailure(mailboxId, at, "Connection refused");

        var failed = repository.findById(mailboxId).orElseThrow();
        assertEquals(1, failed.failureCount());
        assertEquals("Connection refused", failed.lastError());
        assertEquals(at.truncatedTo(ChronoUnit.MILLIS), failed.lastCheckAt().truncatedTo(ChronoUnit.MILLIS));

        repository.recordFailure(mailboxId, at, "Connection refused");
        assertEquals(2, repository.findById(mailboxId).orElseThrow().failureCount());
    }

    @Test
    @Order(6)
    void aCycleThatWorksClearsWhateverWasStanding() {
        repository.recordSuccess(mailboxId, Instant.parse("2026-09-10T11:00:00Z"));

        var healthy = repository.findById(mailboxId).orElseThrow();
        assertEquals(0, healthy.failureCount());
        assertNull(healthy.lastError());
    }

    @Test
    @Order(7)
    void aSuspendedMailboxIsNotVisitedUntilSomebodyPutsItBack() {
        repository.recordFailure(mailboxId, Instant.parse("2026-09-10T12:00:00Z"), "Still refused");
        repository.suspend(mailboxId);

        assertTrue(repository.findById(mailboxId).orElseThrow().suspended());
        assertFalse(repository.findPollable().stream().anyMatch(box -> box.id() == mailboxId));

        assertTrue(repository.resume(mailboxId));
        var resumed = repository.findById(mailboxId).orElseThrow();
        assertFalse(resumed.suspended());
        assertEquals(0, resumed.failureCount(), "putting it back forgives what went before");
        assertNull(resumed.lastError());
        assertTrue(repository.findPollable().stream().anyMatch(box -> box.id() == mailboxId));
    }

    @Test
    @Order(8)
    void aMailboxCanBeTakenAway() {
        assertTrue(repository.delete(mailboxId));
        assertTrue(repository.findById(mailboxId).isEmpty());
        assertFalse(repository.delete(mailboxId), "and taking it away twice changes nothing");
    }
}
