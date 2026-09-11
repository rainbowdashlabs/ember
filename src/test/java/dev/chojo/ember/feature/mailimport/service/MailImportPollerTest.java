/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.conf.file.elements.MailImport;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.mailimport.repository.MailImportLogRepository;
import dev.chojo.ember.feature.mailimport.repository.MailMailboxRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The thread that walks the mailboxes, without waiting for a thread.
 *
 * <p>What is worth testing here is which mailboxes a turn visits and that a turn survives whatever goes
 * wrong in it, because an exception escaping a scheduled task stops the task for good and an import that
 * quietly stopped a month ago is worse than one that logs a failure every quarter of an hour.
 */
class MailImportPollerTest extends RepositoryTestBase {

    private static MailMailboxRepository mailboxRepository;
    private static Station station;
    private static Account account;

    @BeforeAll
    static void setup() {
        mailboxRepository = new MailMailboxRepository();
        station = stationRepo.create("Poller Station");
        account = accountRepo.create("poller@test.com", "Poll", "Tester");
        stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private MailMailbox enabledMailbox(String name) {
        var mailbox = mailboxRepository.create(
                station.id(),
                name,
                "imap.musterstadt.de",
                993,
                MailSecurity.SSL,
                "archive",
                new EncryptedBlob(new byte[12], new byte[16]),
                "INBOX",
                false,
                15,
                Instant.now().minusSeconds(3600));
        mailboxRepository.update(
                mailbox.id(),
                name,
                "imap.musterstadt.de",
                993,
                MailSecurity.SSL,
                "archive",
                "INBOX",
                false,
                true,
                15,
                Instant.now().minusSeconds(3600));
        return mailboxRepository.findById(mailbox.id()).orElseThrow();
    }

    private static MailImportPoller poller(MailImportService importService, MailImport settings) {
        return new MailImportPoller(mailboxRepository, new MailImportLogRepository(), importService, settings);
    }

    @Test
    void aTurnVisitsAMailboxThatHasNeverBeenChecked() {
        var mailbox = enabledMailbox("Frisch");
        var importService = mock(MailImportService.class);
        when(importService.run(any(), any())).thenReturn(new MailImportService.Cycle(0, 0, 0));

        poller(importService, new MailImport()).tick();

        verify(importService).run(argThatIs(mailbox.id()), any());
    }

    private static MailMailbox argThatIs(int id) {
        return org.mockito.ArgumentMatchers.argThat(box -> box != null && box.id() == id);
    }

    @Test
    void aTurnLeavesAMailboxThatWasJustCheckedAlone() {
        var mailbox = enabledMailbox("Gerade geprueft");
        mailboxRepository.recordSuccess(mailbox.id(), Instant.now());
        var importService = mock(MailImportService.class);
        when(importService.run(any(), any())).thenReturn(new MailImportService.Cycle(0, 0, 0));

        poller(importService, new MailImport()).tick();

        verify(importService, never()).run(argThatIs(mailbox.id()), any());
    }

    /**
     * An exception escaping a scheduled task stops it for good, so a turn swallows what went wrong and the
     * next one still happens.
     */
    @Test
    void aTurnThatGoesWrongDoesNotStopTheOnesAfterIt() {
        enabledMailbox("Wirft");
        var importService = mock(MailImportService.class);
        when(importService.run(any(), any())).thenThrow(new IllegalStateException("something broke"));

        var poller = poller(importService, new MailImport());

        poller.tick();
        poller.tick();
    }

    @Test
    void thePruningClearsTheReadableHalfAndSurvivesItsOwnFailures() {
        var importService = mock(MailImportService.class);
        var logRepository = mock(MailImportLogRepository.class);
        when(logRepository.pruneReadableBefore(any())).thenReturn(3);
        var poller = new MailImportPoller(mailboxRepository, logRepository, importService, new MailImport());

        poller.prune();

        verify(logRepository).pruneReadableBefore(any());

        when(logRepository.pruneReadableBefore(any())).thenThrow(new IllegalStateException("no"));
        poller.prune();
    }

    /** The operator's switch stops the whole thing, whatever any station has configured. */
    @Test
    void theWholeThingCanBeSwitchedOffForTheInstance() {
        var importService = mock(MailImportService.class);
        var off = new MailImport() {
            @Override
            public boolean enabled() {
                return false;
            }
        };

        poller(importService, off).start();

        verify(importService, never()).run(any(), any());
    }

    @Test
    void startingTheWalkSchedulesItWithoutVisitingAnythingYet() {
        var importService = mock(MailImportService.class);
        when(importService.run(any(), any())).thenReturn(new MailImportService.Cycle(0, 0, 0));

        poller(importService, new MailImport()).start();

        verify(importService, never()).run(any(), any());
    }

    @Test
    void everyEnabledMailboxOfEveryStationIsOfferedToATurn() {
        enabledMailbox("Eins");
        var suspended = enabledMailbox("Zwei");
        mailboxRepository.suspend(suspended.id());

        var pollable = mailboxRepository.findPollable();

        assertTrue(pollable.size() >= 1);
        assertTrue(pollable.stream().allMatch(box -> box.enabled() && !box.suspended()));
        assertEquals(
                List.of(),
                pollable.stream().filter(box -> box.id() == suspended.id()).toList());
    }
}
