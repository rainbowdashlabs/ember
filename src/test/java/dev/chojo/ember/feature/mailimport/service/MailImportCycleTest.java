/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
                new MailImport());
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
                15,
                Instant.now().minusSeconds(3600));
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
                List.of("Post"),
                List.of());
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
        greenMail
                .getManagers()
                .getImapHostManager()
                .getInbox(greenMail.getUserManager().getUser(USER))
                .store(message);
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
                List.of(),
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
                true,
                15,
                Instant.now().plusSeconds(3600));

        var cycle = importService.run(mailboxRepository.findById(mailbox.id()).orElseThrow(), Instant.now());

        assertEquals(0, cycle.looked(), "nothing arrived after the moment the mailbox starts from");
    }
}
