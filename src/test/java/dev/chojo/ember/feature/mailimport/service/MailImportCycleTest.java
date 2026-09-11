/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.MailImport;
import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.mailimport.entity.MailImportOutcome;
import dev.chojo.ember.feature.mailimport.entity.MailMailbox;
import dev.chojo.ember.feature.mailimport.entity.MailRuleAction;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import dev.chojo.ember.feature.mailimport.entity.MailTitleSource;
import dev.chojo.ember.feature.mailimport.repository.MailImportLogRepository;
import dev.chojo.ember.feature.mailimport.repository.MailMailboxRepository;
import dev.chojo.ember.feature.mailimport.repository.MailOriginRepository;
import dev.chojo.ember.feature.mailimport.repository.MailRuleRepository;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import org.apache.james.jdkim.DKIMVerifier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * A whole visit to a mailbox, against a mail server that really speaks IMAP.
 *
 * <p>The reader, the rule selection and the filing are all worth testing against something that answers
 * the way a provider answers, because the parts of this that break are the parts that involve somebody
 * else's server: a multipart nested one level deeper than expected, a part with no disposition, a name
 * that arrives encoded. A fake reader would agree with whatever this code believes.
 */
class MailImportCycleTest extends RepositoryTestBase {

    private static final String USER = "archive@musterstadt.de";
    private static final String PASSWORD = "not-a-real-password";
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    private static GreenMail greenMail;
    private static Path storageRoot;
    private static MailImportService importService;
    private static MailMailboxRepository mailboxRepository;
    private static MailRuleRepository ruleRepository;
    private static MailImportLogRepository logRepository;
    private static CredentialCipher cipher;
    private static NotificationService notifications;
    private static Station station;
    private static Account account;
    private static MailMailbox mailbox;

    @BeforeAll
    static void setup() throws IOException {
        greenMail = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP));
        greenMail.start();
        greenMail.setUser(USER, USER, PASSWORD);

        storageRoot = Files.createTempDirectory("mail-import-cycle");
        var backend = new LocalStorageBackend(storageRoot);
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var documentService =
                new DocumentService(memberDocumentRepo, storage, new ImageVariantService(storage), stationRepo);
        var quotaService = new StorageQuotaService(storageUsageRepo, new Storage(), new DomainEventBus(Set.of()));

        mailboxRepository = new MailMailboxRepository();
        ruleRepository = new MailRuleRepository();
        logRepository = new MailImportLogRepository();
        var originRepository = new MailOriginRepository();
        cipher = new CredentialCipher(KEY);
        notifications = mock(NotificationService.class);

        station = stationRepo.create("Cycle Station");
        account = accountRepo.create("cycle@test.com", "Anna", "Weber");
        int memberId = stationMemberRepo.create(station.id(), account.id()).id();

        var filingService = new MailFilingService(
                documentService,
                logRepository,
                originRepository,
                quotaService,
                stationId -> List.of(new SubjectMemberMatch.Candidate(memberId, "Anna Weber")));

        importService = new MailImportService(
                mailboxRepository,
                ruleRepository,
                logRepository,
                filingService,
                quotaService,
                cipher,
                MailHostPolicies.allowingTheLocalNetwork(),
                new DkimVerification(() -> new DKIMVerifier(SignedMail.publishedKeys())),
                new MailImport(),
                notifications);
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (greenMail != null) greenMail.stop();
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

    /** A mailbox and its inbox, fresh for every story, so one story's mail is not another's. */
    @BeforeEach
    void freshMailbox() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
        mailbox = mailboxRepository.create(
                station.id(),
                "Archiv",
                "127.0.0.1",
                greenMail.getImap().getPort(),
                MailSecurity.NONE,
                USER,
                cipher.encrypt(PASSWORD.getBytes(StandardCharsets.UTF_8)),
                "INBOX",
                false,
                15,
                Instant.now().minusSeconds(3600));
    }

    /** The same mailbox, told to file nothing that is not signed by the domain it comes from. */
    private MailMailbox askingForASignature() {
        mailboxRepository.update(
                mailbox.id(),
                "Archiv",
                "127.0.0.1",
                greenMail.getImap().getPort(),
                MailSecurity.NONE,
                USER,
                "INBOX",
                true,
                true,
                15,
                Instant.now().minusSeconds(3600));
        return mailboxRepository.findById(mailbox.id()).orElseThrow();
    }

    private void rule(List<String> senders, List<String> types, long minSize, boolean inline, MailRuleAction action) {
        ruleRepository.create(
                mailbox.id(),
                "Bescheinigungen",
                0,
                null,
                null,
                types,
                minSize,
                inline,
                MailTitleSource.SUBJECT,
                false,
                false,
                false,
                action,
                null,
                senders,
                List.of("Post"));
    }

    private static byte[] pdf(String marker) {
        return ("%PDF-1.7\n" + marker).getBytes(StandardCharsets.US_ASCII);
    }

    /** Delivers a mail with one attachment, the way a scanner or an office would send it. */
    private void deliver(String from, String subject, String fileName, byte[] data, boolean inline) throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, USER);
        message.setSubject(subject);

        var body = new MimeBodyPart();
        body.setText("Anbei die Unterlagen.");

        var multipart = new MimeMultipart();
        multipart.addBodyPart(body);
        if (fileName != null) {
            var part = new MimeBodyPart();
            part.setDataHandler(
                    new jakarta.activation.DataHandler(new ByteArrayDataSource(data, "application/octet-stream")));
            part.setFileName(fileName);
            part.setDisposition(inline ? MimeBodyPart.INLINE : MimeBodyPart.ATTACHMENT);
            multipart.addBodyPart(part);
        }
        message.setContent(multipart);
        store(message);
    }

    private static void store(MimeMessage message) throws Exception {
        greenMail
                .getManagers()
                .getImapHostManager()
                .getInbox(greenMail.getUserManager().getUser(USER))
                .store(message);
    }

    private static MailboxReader reader() throws Exception {
        return new MailboxReader(
                MailHostPolicies.allowingTheLocalNetwork(),
                "127.0.0.1",
                greenMail.getImap().getPort(),
                MailSecurity.NONE,
                USER,
                PASSWORD,
                10);
    }

    /**
     * Delivers a message signed by that domain, over the bytes this server really hands back.
     *
     * <p>A signature covers what was sent, to the byte. Signing a message before any server has written
     * it down would sign something nobody ever receives, so it is stored once, read back the way the
     * import reads it, and stored again with a signature over exactly those bytes.
     */
    private void deliverSignedBy(String domain, String from, String subject, String fileName, byte[] data)
            throws Exception {
        deliver(from, subject, fileName, data, false);
        byte[] source;
        try (var reader = reader()) {
            reader.open("INBOX", false);
            source = reader.rawSource(
                    reader.since(Instant.now().minusSeconds(600), 1).getFirst());
        }
        greenMail.purgeEmailFromAllMailboxes();
        store(new MimeMessage(
                Session.getInstance(new Properties()), new ByteArrayInputStream(SignedMail.signedBy(domain, source))));
    }

    /** The same delivery, with a header the receiving server would have written. */
    private void deliverWithHeader(
            String from, String subject, String fileName, byte[] data, String header, String value) throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, USER);
        message.setSubject(subject);
        message.setHeader(header, value);

        var body = new MimeBodyPart();
        body.setText("Anbei die Unterlagen.");
        var part = new MimeBodyPart();
        part.setDataHandler(
                new jakarta.activation.DataHandler(new ByteArrayDataSource(data, "application/octet-stream")));
        part.setFileName(fileName);
        part.setDisposition(MimeBodyPart.ATTACHMENT);

        var multipart = new MimeMultipart();
        multipart.addBodyPart(body);
        multipart.addBodyPart(part);
        message.setContent(multipart);
        store(message);
    }

    @Test
    void anAttachmentFromATrustedSenderBecomesADocument() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.MARK_SEEN);
        deliver("post@musterstadt.de", "Pruefbescheinigung", "pruefung.pdf", pdf("real"), false);

        var cycle = importService.run(mailbox, Instant.now());

        assertEquals(1, cycle.looked());
        assertEquals(1, cycle.imported(), "the attachment was filed");
        assertEquals(0, cycle.refused());
        assertTrue(logRepository.findByStation(station.id(), 50, 0).stream()
                .anyMatch(entry -> entry.outcome() == MailImportOutcome.IMPORTED
                        && "pruefung.pdf".equals(entry.attachmentName())));
    }

    /** The boundary that actually holds. A stranger's mail is looked at and refused out loud. */
    @Test
    void mailFromAnAddressNoRuleTrustsIsRefused() throws Exception {
        rule(List.of("archive@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliver("fremder@woanders.de", "Angebot", "angebot.pdf", pdf("stranger"), false);

        var cycle = importService.run(mailbox, Instant.now());

        assertEquals(0, cycle.imported());
        assertEquals(1, cycle.refused());
        assertTrue(logRepository.findByStation(station.id(), 50, 0).stream()
                .anyMatch(entry -> entry.outcome() == MailImportOutcome.SENDER_NOT_ALLOWED));
    }

    /**
     * The failure that would otherwise import everything twice: the message is remembered as handled, so a
     * second visit finds nothing to do.
     */
    @Test
    void asecondVisitDoesNotFileTheSameMailAgain() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.MARK_SEEN);
        deliver("post@musterstadt.de", "Nur einmal", "einmal.pdf", pdf("once"), false);

        var first = importService.run(mailbox, Instant.now());
        var second = importService.run(mailbox, Instant.now());

        assertEquals(1, first.imported());
        assertEquals(0, second.imported(), "nothing was filed a second time");
        assertEquals(0, second.refused(), "and nothing was refused either: it was simply already done");
    }

    /** Off by default, because every mail with a signature embeds a crest. */
    @Test
    void anEmbeddedPictureIsPassedOverUnlessTheRuleAsksForIt() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliver("post@musterstadt.de", "Mit Logo", "logo.pdf", pdf("inline"), true);

        var cycle = importService.run(mailbox, Instant.now());

        assertEquals(0, cycle.imported());
        assertTrue(logRepository.findByStation(station.id(), 50, 0).stream()
                .anyMatch(entry -> entry.outcome() == MailImportOutcome.NO_ATTACHMENT));
    }

    @Test
    void anEmbeddedPictureIsTakenWhereTheRuleAsksForIt() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, true, MailRuleAction.NOTHING);
        deliver("post@musterstadt.de", "Mit Logo", "logo.pdf", pdf("inline-wanted"), true);

        assertEquals(1, importService.run(mailbox, Instant.now()).imported());
    }

    @Test
    void aMessageWithNothingHangingOffItIsWrittenDownAsSuch() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliver("post@musterstadt.de", "Nur Text", null, null, false);

        var cycle = importService.run(mailbox, Instant.now());

        assertEquals(0, cycle.imported());
        assertTrue(logRepository.findByStation(station.id(), 50, 0).stream()
                .anyMatch(entry -> entry.outcome() == MailImportOutcome.NO_ATTACHMENT));
    }

    /** A mailbox with no rules is visited and does nothing, rather than failing. */
    @Test
    void aMailboxWithNoRulesTakesNothingAndDoesNotFail() throws Exception {
        deliver("post@musterstadt.de", "Ohne Regel", "egal.pdf", pdf("norule"), false);

        var cycle = importService.run(mailbox, Instant.now());

        assertEquals(0, cycle.looked());
        assertEquals(0, cycle.imported());
        assertNotNull(mailboxRepository.findById(mailbox.id()).orElseThrow().lastCheckAt());
    }

    @Test
    void theRuleMarksTheMessageAsItSaysItWill() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.MARK_SEEN);
        deliver("post@musterstadt.de", "Gelesen", "gelesen.pdf", pdf("seen"), false);

        importService.run(mailbox, Instant.now());

        var stored = greenMail
                .getManagers()
                .getImapHostManager()
                .getInbox(greenMail.getUserManager().getUser(USER));
        assertEquals(0, stored.getUnseenCount(), "the message was marked read, which is what stops it coming back");
    }

    /**
     * A host that is not there is a failure written onto the mailbox rather than an exception thrown at
     * whoever asked, because the caller is a daemon thread nobody is watching.
     */
    @Test
    void aMailboxThatCannotBeReachedRecordsWhyAndCarriesOn() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        var unreachable = mailboxRepository.create(
                station.id(),
                "Kaputt",
                "127.0.0.1",
                1,
                MailSecurity.NONE,
                USER,
                cipher.encrypt(PASSWORD.getBytes(StandardCharsets.UTF_8)),
                "INBOX",
                false,
                15,
                Instant.now().minusSeconds(3600));
        ruleRepository.create(
                unreachable.id(),
                "Egal",
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
                MailRuleAction.NOTHING,
                null,
                List.of("*@musterstadt.de"),
                List.of());

        var cycle = importService.run(unreachable, Instant.now());

        assertEquals(0, cycle.looked());
        var after = mailboxRepository.findById(unreachable.id()).orElseThrow();
        assertEquals(1, after.failureCount());
        assertNotNull(after.lastError(), "and the reason is on the mailbox, where the page can read it");
        assertFalse(after.suspended(), "one failure is not enough to stop trying");
    }

    /** The folder somebody typed wrong, which is the commonest mistake in setting one of these up. */
    @Test
    void aFolderThatIsNotThereIsAFailureAndNotACrash() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        mailboxRepository.update(
                mailbox.id(),
                "Archiv",
                "127.0.0.1",
                greenMail.getImap().getPort(),
                MailSecurity.NONE,
                USER,
                "Gibtesnicht",
                false,
                true,
                15,
                Instant.now().minusSeconds(3600));

        var cycle = importService.run(mailboxRepository.findById(mailbox.id()).orElseThrow(), Instant.now());

        assertEquals(0, cycle.looked());
        assertNotNull(mailboxRepository.findById(mailbox.id()).orElseThrow().lastError());
    }

    /** Mail older than the mailbox is what stops a first cycle importing a decade. */
    @Test
    void mailOlderThanTheMailboxIsLeftAlone() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliver("post@musterstadt.de", "Alt", "alt.pdf", pdf("old"), false);
        mailboxRepository.update(
                mailbox.id(),
                "Archiv",
                "127.0.0.1",
                greenMail.getImap().getPort(),
                MailSecurity.NONE,
                USER,
                "INBOX",
                false,
                true,
                15,
                Instant.now().plusSeconds(3600));

        var cycle = importService.run(mailboxRepository.findById(mailbox.id()).orElseThrow(), Instant.now());

        assertEquals(0, cycle.looked(), "nothing arrived after the moment the mailbox starts from");
    }

    /**
     * Moving is offered where deleting is not: it does everything deleting does and whoever finds out can
     * undo it.
     */
    @Test
    void aRuleThatMovesTheMessagePutsItInTheOtherFolder() throws Exception {
        ruleRepository.create(
                mailbox.id(),
                "Verschieben",
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
                MailRuleAction.MOVE,
                "Erledigt",
                List.of("*@musterstadt.de"),
                List.of());
        deliver("post@musterstadt.de", "Verschoben", "verschoben.pdf", pdf("moved"), false);

        assertEquals(1, importService.run(mailbox, Instant.now()).imported());

        var moved = greenMail
                .getManagers()
                .getImapHostManager()
                .getFolder(greenMail.getUserManager().getUser(USER), "Erledigt");
        assertNotNull(moved, "the folder was made and the message put in it");
        assertEquals(1, moved.getMessageCount());
    }

    @Test
    void aRuleThatFlagsTheMessageFlagsIt() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.FLAG);
        deliver("post@musterstadt.de", "Markiert", "markiert.pdf", pdf("flagged"), false);

        assertEquals(1, importService.run(mailbox, Instant.now()).imported());
    }

    /**
     * A rule separates one sender's PDFs from their photographs by attachment name, which is why the name
     * filter is part of whether the rule takes the message at all.
     */
    @Test
    void anAttachmentNameFilterDecidesWhetherTheRuleTakesTheMessage() throws Exception {
        ruleRepository.create(
                mailbox.id(),
                "Nur Scans",
                0,
                null,
                "scan",
                List.of("application/pdf"),
                0L,
                false,
                MailTitleSource.SUBJECT,
                false,
                false,
                false,
                MailRuleAction.NOTHING,
                null,
                List.of("*@musterstadt.de"),
                List.of());
        deliver("post@musterstadt.de", "Kein Scan", "rechnung.pdf", pdf("notascan"), false);

        var cycle = importService.run(mailbox, Instant.now());

        assertEquals(0, cycle.imported());
        assertTrue(logRepository.findByStation(station.id(), 50, 0).stream()
                .anyMatch(entry -> entry.outcome() == MailImportOutcome.NO_RULE_MATCHED));
    }

    /**
     * A guard against carelessness rather than a security boundary: plenty of providers write no such
     * header at all, so a verdict cannot be demanded without refusing ordinary mail on those servers.
     */
    @Test
    void mailTheReceivingServerSaidFailedItsOwnChecksIsRefused() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliverWithHeader(
                "post@musterstadt.de",
                "Gefaelscht",
                "gefaelscht.pdf",
                pdf("spoofed"),
                "Authentication-Results",
                "mx.example.com; dmarc=fail header.from=musterstadt.de");

        var cycle = importService.run(mailbox, Instant.now());

        assertEquals(0, cycle.imported());
        assertTrue(logRepository.findByStation(station.id(), 50, 0).stream()
                .anyMatch(entry -> entry.outcome() == MailImportOutcome.AUTHENTICATION_FAILED));
    }

    /** Where the header is absent nothing can be concluded, so the mail is judged on its address alone. */
    @Test
    void mailWhoseDomainPassedItsOwnChecksIsTakenAsNormal() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliverWithHeader(
                "post@musterstadt.de",
                "Echt",
                "echt.pdf",
                pdf("passed"),
                "Authentication-Results",
                "mx.example.com; dmarc=pass header.from=musterstadt.de");

        assertEquals(1, importService.run(mailbox, Instant.now()).imported());
    }

    /**
     * Somebody has to be told. The error is on the mailbox either way, but nobody goes looking at a page
     * for an import that silently stopped a month ago.
     */
    @Test
    void aMailboxThatKeepsFailingIsSuspendedAndSomebodyIsTold() {
        var unreachable = mailboxRepository.create(
                station.id(),
                "Hoffnungslos",
                "127.0.0.1",
                1,
                MailSecurity.NONE,
                USER,
                cipher.encrypt(PASSWORD.getBytes(StandardCharsets.UTF_8)),
                "INBOX",
                false,
                15,
                Instant.now().minusSeconds(3600));
        ruleRepository.create(
                unreachable.id(),
                "Egal",
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
                MailRuleAction.NOTHING,
                null,
                List.of("*@musterstadt.de"),
                List.of());
        for (int i = 0; i < MailImportSchedule.FAILURES_BEFORE_SUSPENSION - 1; i++) {
            mailboxRepository.recordFailure(unreachable.id(), Instant.now(), "still refused");
        }

        importService.run(mailboxRepository.findById(unreachable.id()).orElseThrow(), Instant.now());

        assertTrue(
                mailboxRepository.findById(unreachable.id()).orElseThrow().suspended(),
                "enough failures in a row take it out of the rotation");
        verify(notifications, atLeastOnce())
                .notifyMembersWithRole(
                        eq(station.id()),
                        eq(StationPermission.STATION_MAIL.name()),
                        eq(NotificationType.MAILBOX_SUSPENDED),
                        any());
    }

    /**
     * Filing under nobody is the ordinary outcome, so this is not a failure report: what nobody notices is
     * the store quietly filling with paperwork waiting to be sorted.
     */
    @Test
    void whatArrivedWithNobodyOnItIsReported() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliver("post@musterstadt.de", "Unsortiert", "unsortiert.pdf", pdf("unbound-digest"), false);

        assertEquals(1, importService.run(mailbox, Instant.now()).imported());

        verify(notifications, atLeastOnce())
                .notifyMembersWithRole(
                        eq(station.id()),
                        eq(StationPermission.DOCUMENT_READ.name()),
                        eq(NotificationType.MAIL_IMPORT_UNBOUND),
                        any());
    }

    /** What the setting is: the same message, refused where a signature is asked for and filed where it is not. */
    @Test
    void aMessageNobodySignedIsRefusedOnlyWhereTheMailboxAsksForASignature() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliver("post@musterstadt.de", "Ohne Signatur", "ohne.pdf", pdf("unsigned"), false);

        var refusing = importService.run(askingForASignature(), Instant.now());

        assertEquals(0, refusing.imported());
        assertEquals(1, refusing.refused());
        assertTrue(logRepository.findByStation(station.id(), 50, 0).stream()
                .anyMatch(entry -> entry.outcome() == MailImportOutcome.NO_SIGNATURE));
    }

    @Test
    void theSameUnsignedMessageIsFiledWhereTheMailboxDoesNotAskForASignature() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliver("post@musterstadt.de", "Ohne Signatur", "ohne.pdf", pdf("unsigned-but-wanted"), false);

        assertEquals(1, importService.run(mailbox, Instant.now()).imported());
    }

    /**
     * The bytes are what a signature covers, so this is also the story about fetching the message as it
     * arrived rather than as this code would write it out again.
     */
    @Test
    void aMessageSignedByTheDomainItComesFromIsFiled() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliverSignedBy("musterstadt.de", "post@musterstadt.de", "Signiert", "signiert.pdf", pdf("signed"));

        assertEquals(1, importService.run(askingForASignature(), Instant.now()).imported());
    }

    /** It verifies, and it is somebody else's signature on somebody else's name. */
    @Test
    void aMessageSignedByAnotherDomainIsRefusedThoughItsSignatureHolds() throws Exception {
        rule(List.of("*@musterstadt.de"), List.of("application/pdf"), 0L, false, MailRuleAction.NOTHING);
        deliverSignedBy("fremder.de", "post@musterstadt.de", "Gefaelscht", "gefaelscht.pdf", pdf("misaligned"));

        var cycle = importService.run(askingForASignature(), Instant.now());

        assertEquals(0, cycle.imported());
        assertEquals(1, cycle.refused());
        assertTrue(logRepository.findByStation(station.id(), 50, 0).stream()
                .anyMatch(entry -> entry.outcome() == MailImportOutcome.SIGNATURE_NOT_ALIGNED));
    }
}
