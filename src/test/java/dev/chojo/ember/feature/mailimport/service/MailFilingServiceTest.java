/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.mailimport.entity.MailImportOutcome;
import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.entity.MailRule;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.mailimport.entity.MailTitleSource;
import dev.chojo.ember.feature.mailimport.repository.MailImportLogRepository;
import dev.chojo.ember.feature.mailimport.repository.MailMailboxRepository;
import dev.chojo.ember.feature.mailimport.repository.MailOriginRepository;
import dev.chojo.ember.feature.mailimport.service.MailboxReader.Attachment;
import dev.chojo.ember.feature.mailimport.service.MailboxReader.Envelope;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MailFilingServiceTest extends RepositoryTestBase {

    @TempDir
    static Path storageRoot;

    private static MailFilingService service;
    private static StorageQuotaService quotaService;
    private static MailOriginRepository originRepository;
    private static MailImportLogRepository logRepository;
    private static Station station;
    private static Account account;
    private static int memberId;
    private static MailMailbox mailbox;
    private static int ruleId;

    private static final Instant ARRIVED = Instant.parse("2026-09-10T08:15:00Z");

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend(storageRoot);
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var documentService =
                new DocumentService(memberDocumentRepo, storage, new ImageVariantService(storage), stationRepo);
        quotaService = new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of()));
        originRepository = new MailOriginRepository();
        logRepository = new MailImportLogRepository();
        station = stationRepo.create("Filing Station");
        account = accountRepo.create("filing@test.com", "Anna", "Weber");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();
        service = new MailFilingService(
                documentService,
                logRepository,
                originRepository,
                quotaService,
                stationId -> List.of(new SubjectMemberMatch.Candidate(memberId, "Anna Weber")));
        mailbox = new MailMailboxRepository()
                .create(
                        station.id(),
                        "Archiv",
                        "imap.musterstadt.de",
                        993,
                        MailSecurity.SSL,
                        "archive",
                        new EncryptedBlob(new byte[12], new byte[16]),
                        "INBOX",
                        15,
                        Instant.parse("2026-09-01T00:00:00Z"));
        ruleId = new dev.chojo.ember.feature.mailimport.repository.MailRuleRepository()
                .create(
                        mailbox.id(),
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
                        List.of("Attest"),
                        List.of())
                .id();
    }

    @AfterAll
    static void cleanup() throws IOException {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
        if (storageRoot != null && Files.exists(storageRoot)) {
            try (var walk = Files.walk(storageRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                        // A leftover temporary file is not worth failing a test over.
                    }
                });
            }
        }
    }

    private static byte[] pdf(String marker) {
        return ("%PDF-1.7\n" + marker).getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] png() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    }

    private static Envelope envelope(String subject, Attachment... attachments) {
        return new Envelope(
                "<msg@musterstadt.de>", "post@musterstadt.de", subject, ARRIVED, null, List.of(attachments));
    }

    private static Attachment attachment(String name, byte[] data) {
        return new Attachment(name, "application/pdf", data == null ? -1 : data.length, false, data);
    }

    private static MailRule rule(
            boolean readSubject, List<Integer> members, long minSize, List<String> types, MailTitleSource title) {
        return new MailRule(
                ruleId,
                mailbox.id(),
                "Bescheinigungen",
                0,
                true,
                null,
                null,
                types,
                minSize,
                false,
                title,
                false,
                false,
                readSubject,
                MailRuleAction.MARK_SEEN,
                null,
                false,
                List.of("*@musterstadt.de"),
                List.of("Attest"),
                members,
                ARRIVED);
    }

    private static MailRule plainRule() {
        return rule(false, List.of(), 0L, List.of("application/pdf"), MailTitleSource.SUBJECT);
    }

    /**
     * Filing under nobody is the ordinary outcome of this feature, not a failure: the mail comes from the
     * office and says nothing about whom it concerns.
     */
    @Test
    @Order(1)
    void anAttachmentBecomesADocumentBoundToNobody() {
        var outcome = service.file(
                mailbox,
                plainRule(),
                envelope("Pruefbescheinigung Leiter", attachment("pruefung.pdf", pdf("one"))),
                attachment("pruefung.pdf", pdf("one")),
                "<msg@musterstadt.de>");

        assertEquals(MailImportOutcome.IMPORTED, outcome);
        var filed =
                memberDocumentRepo.findByStation(station.id(), List.of(), null, true, false, "simple", 10, 0).stream()
                        .filter(document -> "Pruefbescheinigung Leiter".equals(document.title()))
                        .findFirst()
                        .orElseThrow();
        assertEquals("application/pdf", filed.mimeType());
        assertTrue(memberDocumentRepo.hasNoMembers(filed.id()), "nobody in particular, which is normal");
        assertEquals(
                List.of("Attest"),
                memberDocumentRepo.findTags(filed.id()).stream()
                        .map(tag -> tag.name())
                        .toList());
    }

    /**
     * A document with no uploader would show an empty one and say nothing, so where it came from is
     * written onto the document itself and outlives the log.
     */
    @Test
    @Order(2)
    void aFiledDocumentSaysItArrivedByMail() {
        var filed =
                memberDocumentRepo.findByStation(station.id(), List.of(), null, true, false, "simple", 10, 0).stream()
                        .filter(document -> "Pruefbescheinigung Leiter".equals(document.title()))
                        .findFirst()
                        .orElseThrow();

        var origin = originRepository.findByDocument(filed.id()).orElseThrow();
        assertEquals("post@musterstadt.de", origin.sender());
        assertEquals("Pruefbescheinigung Leiter", origin.subject());
        assertEquals(mailbox.id(), origin.mailboxId());
        assertEquals(1, originRepository.findByDocuments(List.of(filed.id())).size());
        assertEquals(0, originRepository.findByDocuments(List.of()).size());
    }

    @Test
    @Order(3)
    void theSameBytesArrivingAgainAreADuplicate() {
        var outcome = service.file(
                mailbox,
                plainRule(),
                envelope("Weitergeleitet", attachment("pruefung.pdf", pdf("one"))),
                attachment("pruefung.pdf", pdf("one")),
                "<forwarded@musterstadt.de>");

        assertEquals(MailImportOutcome.DUPLICATE, outcome);
    }

    /** The cheapest guard there is against an executable arriving as a document. */
    @Test
    @Order(4)
    void aFileWhoseNameContradictsItsBytesIsRefused() {
        var outcome = service.file(
                mailbox,
                plainRule(),
                envelope("Rechnung", attachment("rechnung.pdf", png())),
                attachment("rechnung.pdf", png()),
                "<lying@musterstadt.de>");

        assertEquals(MailImportOutcome.TYPE_NOT_ALLOWED, outcome);
    }

    @Test
    @Order(5)
    void bytesOfAKindTheRuleDoesNotWantAreRefused() {
        var outcome = service.file(
                mailbox,
                plainRule(),
                envelope("Foto", attachment("foto.png", png())),
                attachment("foto.png", png()),
                "<photo@musterstadt.de>");

        assertEquals(MailImportOutcome.TYPE_NOT_ALLOWED, outcome);
    }

    @Test
    @Order(6)
    void bytesNothingCanBeMadeOfAreRefused() {
        byte[] nonsense = "just text".getBytes(StandardCharsets.UTF_8);
        var outcome = service.file(
                mailbox,
                plainRule(),
                envelope("Notiz", attachment("notiz.txt", nonsense)),
                attachment("notiz.txt", nonsense),
                "<text@musterstadt.de>");

        assertEquals(MailImportOutcome.TYPE_NOT_ALLOWED, outcome);
    }

    /** What keeps the store from filling with the crest in everybody's signature. */
    @Test
    @Order(7)
    void somethingTooSmallToBeADocumentIsRefused() {
        var outcome = service.file(
                mailbox,
                rule(false, List.of(), 1_000_000L, List.of("application/pdf"), MailTitleSource.SUBJECT),
                envelope("Klein", attachment("klein.pdf", pdf("small"))),
                attachment("klein.pdf", pdf("small")),
                "<small@musterstadt.de>");

        assertEquals(MailImportOutcome.TOO_SMALL, outcome);
    }

    /** A part refused before it was fetched has no bytes to judge, and says so rather than crashing. */
    @Test
    @Order(8)
    void somethingNeverFetchedIsRecordedAsTooLarge() {
        var outcome = service.file(
                mailbox,
                plainRule(),
                envelope("Video", attachment("video.mp4", null)),
                attachment("video.mp4", null),
                "<big@musterstadt.de>");

        assertEquals(MailImportOutcome.TOO_LARGE, outcome);
    }

    @Test
    @Order(9)
    void aRuleThatNamesMembersFilesUnderThem() {
        var outcome = service.file(
                mailbox,
                rule(false, List.of(memberId), 0L, List.of("application/pdf"), MailTitleSource.SUBJECT),
                envelope("Jugendgruppe", attachment("brief.pdf", pdf("named"))),
                attachment("brief.pdf", pdf("named")),
                "<named@musterstadt.de>");

        assertEquals(MailImportOutcome.IMPORTED, outcome);
        var filed =
                memberDocumentRepo.findByStation(station.id(), List.of(memberId), null, true, false, "simple", 10, 0);
        assertTrue(filed.stream().anyMatch(document -> "Jugendgruppe".equals(document.title())));
    }

    @Test
    @Order(10)
    void aSubjectNamingOneMemberFilesUnderThemWhenTheRuleAsks() {
        var outcome = service.file(
                mailbox,
                rule(true, List.of(), 0L, List.of("application/pdf"), MailTitleSource.SUBJECT),
                envelope("Attest Anna Weber", attachment("attest.pdf", pdf("subject"))),
                attachment("attest.pdf", pdf("subject")),
                "<subject@musterstadt.de>");

        assertEquals(MailImportOutcome.IMPORTED, outcome);
        var filed =
                memberDocumentRepo.findByStation(station.id(), List.of(memberId), null, true, false, "simple", 20, 0);
        assertTrue(filed.stream().anyMatch(document -> "Attest Anna Weber".equals(document.title())));
    }

    @Test
    @Order(11)
    void aSubjectNamingNobodyStillFilesTheDocument() {
        var outcome = service.file(
                mailbox,
                rule(true, List.of(), 0L, List.of("application/pdf"), MailTitleSource.SUBJECT),
                envelope("Rechnung 2026", attachment("rechnung2.pdf", pdf("nobody"))),
                attachment("rechnung2.pdf", pdf("nobody")),
                "<nobody@musterstadt.de>");

        assertEquals(MailImportOutcome.IMPORTED, outcome);
    }

    @Test
    @Order(12)
    void theTitleComesFromTheFileNameWhereTheRuleSaysSo() {
        service.file(
                mailbox,
                rule(false, List.of(), 0L, List.of("application/pdf"), MailTitleSource.FILE_NAME),
                envelope("Betreff wird ignoriert", attachment("Geraetepruefung.pdf", pdf("byname"))),
                attachment("Geraetepruefung.pdf", pdf("byname")),
                "<byname@musterstadt.de>");

        var filed = memberDocumentRepo.findByStation(station.id(), List.of(), null, true, false, "simple", 30, 0);
        assertTrue(filed.stream().anyMatch(document -> "Geraetepruefung".equals(document.title())));
    }

    /** An embedded picture from a phone often has no name, and a store of files called nothing is unusable. */
    @Test
    @Order(13)
    void anAttachmentWithNoNameIsStillGivenOne() {
        service.file(
                mailbox,
                rule(false, List.of(), 0L, List.of("application/pdf"), MailTitleSource.FILE_NAME),
                envelope("Ohne Dateiname", new Attachment(null, "application/pdf", 20, true, pdf("noname"))),
                new Attachment(null, "application/pdf", 20, true, pdf("noname")),
                "<noname@musterstadt.de>");

        var filed = memberDocumentRepo.findByStation(station.id(), List.of(), null, true, false, "simple", 40, 0);
        assertTrue(filed.stream().anyMatch(document -> "anhang.pdf".equals(document.fileName())));
    }

    @Test
    @Order(14)
    void aMessageNothingCouldBeTakenFromIsStillWrittenDown() {
        int before = logRepository.countByStation(station.id());

        service.recordRefusal(
                mailbox,
                null,
                envelope("Fremder Absender"),
                "<stranger@example.com>",
                MailImportOutcome.SENDER_NOT_ALLOWED,
                "No rule of this mailbox trusts that address");

        assertEquals(before + 1, logRepository.countByStation(station.id()));
        var newest = logRepository.findByStation(station.id(), 1, 0).getFirst();
        assertEquals(MailImportOutcome.SENDER_NOT_ALLOWED, newest.outcome());
        assertEquals("<stranger@example.com>", newest.messageId());
    }
}
