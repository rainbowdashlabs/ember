/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.repository;

import dev.chojo.ember.feature.mailimport.entity.MailImportOutcome;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.mailimport.entity.MailTitleSource;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MailImportLogRepositoryTest extends RepositoryTestBase {

    private static MailImportLogRepository repository;
    private static Station station;
    private static int mailboxId;
    private static int ruleId;

    private static final String HASH = "a".repeat(64);

    @BeforeAll
    static void setup() {
        repository = new MailImportLogRepository();
        station = stationRepo.create("Import Log Station");
        mailboxId = new MailMailboxRepository()
                .create(
                        station.id(),
                        "Archiv",
                        "imap.musterstadt.de",
                        993,
                        MailSecurity.SSL,
                        "archive",
                        new EncryptedBlob(new byte[12], new byte[16]),
                        "INBOX",
                        false,
                        15,
                        Instant.parse("2026-09-01T00:00:00Z"))
                .id();
        ruleId = new MailRuleRepository()
                .create(
                        mailboxId,
                        "Bescheinigungen",
                        0,
                        null,
                        null,
                        List.of("application/pdf"),
                        0L,
                        false,
                        MailTitleSource.SUBJECT,
                        false,
                        false,
                        false,
                        MailRuleAction.MARK_SEEN,
                        null,
                        List.of("*@musterstadt.de"),
                        List.of())
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void anEntryNamesTheRuleThatTookTheMessage() {
        var entry = repository.record(
                mailboxId,
                station.id(),
                ruleId,
                "<one@musterstadt.de>",
                "post@musterstadt.de",
                "Attest Anna",
                "attest.pdf",
                HASH,
                MailImportOutcome.IMPORTED,
                null,
                null);

        assertNotNull(entry);
        assertEquals("Bescheinigungen", entry.ruleName(), "the question anybody debugging an import asks");
        assertEquals(MailImportOutcome.IMPORTED, entry.outcome());
        assertEquals("attest.pdf", entry.attachmentName());
        assertFalse(entry.pruned());
    }

    /**
     * The grain is the attachment and not the message, so one mail can report three different outcomes.
     */
    @Test
    @Order(2)
    void oneMessageCanCarryThreeAttachmentsWithThreeDifferentAnswers() {
        repository.record(
                mailboxId,
                station.id(),
                ruleId,
                "<three@musterstadt.de>",
                "post@musterstadt.de",
                "Drei Scans",
                "logo.png",
                "b".repeat(64),
                MailImportOutcome.TOO_SMALL,
                "1024 bytes, under the 2048 this rule counts as a document",
                null);
        repository.record(
                mailboxId,
                station.id(),
                ruleId,
                "<three@musterstadt.de>",
                "post@musterstadt.de",
                "Drei Scans",
                "video.mp4",
                null,
                MailImportOutcome.TOO_LARGE,
                "over the station's limit",
                null);

        var entries = repository.findByStation(station.id(), 50, 0);
        assertEquals(3, entries.size());
        assertEquals(3, repository.countByStation(station.id()));
        assertTrue(entries.stream().anyMatch(e -> e.outcome() == MailImportOutcome.TOO_SMALL));
        assertTrue(entries.stream().anyMatch(e -> e.outcome() == MailImportOutcome.TOO_LARGE));
    }

    /**
     * The content hash on its own, which is what catches a forward: a forwarded mail gets a new
     * identifier, so a key needing both to match would let it through.
     */
    @Test
    @Order(3)
    void theSameBytesAreADuplicateHoweverTheyArrived() {
        assertTrue(repository.hasImportedContent(station.id(), HASH));
        assertFalse(repository.hasImportedContent(station.id(), "c".repeat(64)));
        assertFalse(
                repository.hasImportedContent(station.id(), "b".repeat(64)),
                "something refused was never imported, so its bytes are not spoken for");
    }

    /** The other key: this message was dealt with, whatever came of it. */
    @Test
    @Order(4)
    void aMessageIsRememberedAsHandledWhateverBecameOfIt() {
        assertFalse(repository.hasHandledMessage(mailboxId, "<one@musterstadt.de>"));

        repository.markMessageHandled(mailboxId, "<one@musterstadt.de>", false);

        assertTrue(repository.hasHandledMessage(mailboxId, "<one@musterstadt.de>"));
        assertFalse(repository.hasHandledMessage(mailboxId + 1, "<one@musterstadt.de>"), "per mailbox");
    }

    @Test
    @Order(5)
    void rememberingAMessageTwiceChangesNothing() {
        repository.markMessageHandled(mailboxId, "<one@musterstadt.de>", false);

        assertTrue(repository.hasHandledMessage(mailboxId, "<one@musterstadt.de>"));
    }

    @Test
    @Order(6)
    void theLogIsReadAPageAtATimeNewestFirst() {
        var firstPage = repository.findByStation(station.id(), 2, 0);
        var secondPage = repository.findByStation(station.id(), 2, 2);

        assertEquals(2, firstPage.size());
        assertEquals(1, secondPage.size());
        assertTrue(firstPage.getFirst().id() > firstPage.get(1).id(), "newest first");
    }

    /**
     * The pruning clears what a reader looks at and keeps the keys, because a mail redelivered after it
     * has run must still be recognised rather than filed a second time.
     */
    @Test
    @Order(7)
    void pruningLeavesTheKeysBehind() {
        int reduced = repository.pruneReadableBefore(Instant.now().plusSeconds(60));

        assertEquals(3, reduced);
        var entries = repository.findByStation(station.id(), 50, 0);
        assertTrue(entries.stream().allMatch(e -> e.pruned()));
        assertTrue(entries.stream().allMatch(e -> e.subject() == null));
        assertTrue(entries.stream().allMatch(e -> e.sender() == null));
        assertTrue(entries.stream().allMatch(e -> e.reason() == null));

        assertTrue(
                repository.hasImportedContent(station.id(), HASH),
                "the duplicate key outlives what was readable about it");
        assertTrue(repository.hasHandledMessage(mailboxId, "<one@musterstadt.de>"));
    }

    @Test
    @Order(8)
    void pruningWhatIsAlreadyPrunedChangesNothing() {
        assertEquals(0, repository.pruneReadableBefore(Instant.now().plusSeconds(60)));
    }

    @Test
    @Order(9)
    void anEntryThatIsNotThereIsNotFound() {
        assertTrue(repository.findById(-1).isEmpty());
    }

    /**
     * Filing under nobody is the ordinary outcome, so this is not an error count. It is what the digest
     * reports, because a store quietly filling with unsorted paperwork is what nobody notices.
     */
    @Test
    @Order(10)
    void documentsNobodyHasSortedYetAreCounted() {
        var document = memberDocumentRepo.create(
                station.id(),
                "Pruefbescheinigung",
                "pruefung.pdf",
                "application/pdf",
                4096,
                false,
                false,
                null,
                List.of());
        repository.record(
                mailboxId,
                station.id(),
                ruleId,
                "<unbound@musterstadt.de>",
                "post@musterstadt.de",
                "Pruefbescheinigung",
                "pruefung.pdf",
                "d".repeat(64),
                MailImportOutcome.IMPORTED,
                null,
                document.id());

        assertEquals(1, repository.countUnboundSince(station.id(), Instant.now().minusSeconds(600)));
        assertEquals(
                0,
                repository.countUnboundSince(station.id(), Instant.now().plusSeconds(600)),
                "and nothing arrived after now");
        assertNull(repository.findById(-1).orElse(null));
    }
}
