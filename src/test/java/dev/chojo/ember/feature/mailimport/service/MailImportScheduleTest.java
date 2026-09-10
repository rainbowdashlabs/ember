/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailImportScheduleTest {

    private static final Instant NOW = Instant.parse("2026-09-10T12:00:00Z");

    private static MailMailbox mailbox(
            boolean enabled, boolean suspended, int intervalMinutes, Instant lastCheck, int failures) {
        return new MailMailbox(
                1,
                1,
                "Archiv",
                "imap.musterstadt.de",
                993,
                MailSecurity.SSL,
                "archive",
                new EncryptedBlob(new byte[12], new byte[16]),
                "INBOX",
                enabled,
                intervalMinutes,
                NOW.minus(Duration.ofDays(30)),
                lastCheck,
                null,
                failures,
                suspended,
                NOW.minus(Duration.ofDays(30)));
    }

    /** So somebody who has just written their first rule is not left waiting a quarter of an hour. */
    @Test
    void aMailboxNeverVisitedIsDueAtOnce() {
        assertTrue(MailImportSchedule.due(mailbox(true, false, 15, null, 0), 15, NOW));
    }

    @Test
    void aMailboxVisitedRecentlyIsNotDue() {
        var justChecked = mailbox(true, false, 15, NOW.minus(Duration.ofMinutes(5)), 0);

        assertFalse(MailImportSchedule.due(justChecked, 15, NOW));
    }

    @Test
    void aMailboxVisitedLongEnoughAgoIsDue() {
        var stale = mailbox(true, false, 15, NOW.minus(Duration.ofMinutes(15)), 0);

        assertTrue(MailImportSchedule.due(stale, 15, NOW));
    }

    /**
     * The floor belongs to the operator, so a station asking for one minute is held to it without anybody
     * editing their mailbox.
     */
    @Test
    void aStationCannotAskToBeVisitedMoreOftenThanTheFloorAllows() {
        var eager = mailbox(true, false, 1, NOW.minus(Duration.ofMinutes(5)), 0);

        assertFalse(MailImportSchedule.due(eager, 15, NOW));
        assertEquals(Duration.ofMinutes(15), MailImportSchedule.waitFor(eager, 15));
    }

    @Test
    void aStationAskingForLessOftenThanTheFloorGetsWhatItAsked() {
        var patient = mailbox(true, false, 60, NOW.minus(Duration.ofMinutes(30)), 0);

        assertFalse(MailImportSchedule.due(patient, 15, NOW));
        assertEquals(Duration.ofMinutes(60), MailImportSchedule.waitFor(patient, 15));
    }

    @Test
    void aMailboxSwitchedOffOrSuspendedIsNeverDue() {
        assertFalse(MailImportSchedule.due(mailbox(false, false, 15, null, 0), 15, NOW));
        assertFalse(MailImportSchedule.due(mailbox(true, true, 15, null, 0), 15, NOW));
    }

    @Test
    void eachFailureInARowDoublesTheWait() {
        assertEquals(Duration.ofMinutes(15), MailImportSchedule.waitFor(mailbox(true, false, 15, NOW, 0), 15));
        assertEquals(Duration.ofMinutes(30), MailImportSchedule.waitFor(mailbox(true, false, 15, NOW, 1), 15));
        assertEquals(Duration.ofMinutes(60), MailImportSchedule.waitFor(mailbox(true, false, 15, NOW, 2), 15));
    }

    /** Otherwise a mailbox that failed twenty times would be due again some time next year. */
    @Test
    void theWaitStopsDoublingAtACeiling() {
        var manyFailures = mailbox(true, false, 15, NOW, 20);
        var fiveFailures = mailbox(true, false, 15, NOW, 5);

        assertEquals(MailImportSchedule.waitFor(fiveFailures, 15), MailImportSchedule.waitFor(manyFailures, 15));
    }

    @Test
    void enoughFailuresInARowTakeAMailboxOutOfTheRotation() {
        assertFalse(MailImportSchedule.shouldSuspend(1));
        assertFalse(MailImportSchedule.shouldSuspend(MailImportSchedule.FAILURES_BEFORE_SUSPENSION - 1));
        assertTrue(MailImportSchedule.shouldSuspend(MailImportSchedule.FAILURES_BEFORE_SUSPENSION));
        assertTrue(MailImportSchedule.shouldSuspend(MailImportSchedule.FAILURES_BEFORE_SUSPENSION + 10));
    }
}
