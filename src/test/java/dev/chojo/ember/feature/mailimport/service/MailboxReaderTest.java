/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import dev.chojo.ember.feature.mailimport.entity.MailSecurity;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a mailbox, against a server that really speaks IMAP.
 *
 * <p>What the whole-cycle stories cannot reach from outside is the two-stage fetch: a part refused on what
 * the mail declares about it is never downloaded, and a part that survives that is downloaded and then
 * judged on its bytes. That distinction is the difference between refusing a 40 MB video and transferring
 * it in order to refuse it, so it is worth driving directly.
 */
class MailboxReaderTest {

    private static final String USER = "archive@musterstadt.de";
    private static final String PASSWORD = "not-a-real-password";
    private static final List<String> PDF_ONLY = List.of("application/pdf");

    private static GreenMail greenMail;

    @BeforeAll
    static void setup() {
        greenMail = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_IMAP));
        greenMail.start();
        greenMail.setUser(USER, USER, PASSWORD);
    }

    @AfterAll
    static void cleanup() {
        if (greenMail != null) greenMail.stop();
    }

    @BeforeEach
    void emptyInbox() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
    }

    private static MailboxReader reader() throws MessagingException {
        return new MailboxReader("127.0.0.1", greenMail.getImap().getPort(), MailSecurity.NONE, USER, PASSWORD, 10);
    }

    private static byte[] pdf() {
        return "%PDF-1.7\nreal enough".getBytes(StandardCharsets.US_ASCII);
    }

    private void deliver(String fileName, byte[] data, String declaredType) throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("post@musterstadt.de"));
        message.setRecipients(Message.RecipientType.TO, USER);
        message.setSubject("Unterlagen");

        var body = new MimeBodyPart();
        body.setText("Anbei.");
        var part = new MimeBodyPart();
        part.setDataHandler(new jakarta.activation.DataHandler(new ByteArrayDataSource(data, declaredType)));
        part.setFileName(fileName);
        part.setDisposition(MimeBodyPart.ATTACHMENT);

        var multipart = new MimeMultipart();
        multipart.addBodyPart(body);
        multipart.addBodyPart(part);
        message.setContent(multipart);
        greenMail
                .getManagers()
                .getImapHostManager()
                .getInbox(greenMail.getUserManager().getUser(USER))
                .store(message);
    }

    /** The commonest mistake in setting one of these up is a folder spelled the provider's way. */
    @Test
    void theFoldersTheAccountCanSeeAreListed() throws Exception {
        try (var reader = reader()) {
            assertTrue(reader.folders().contains("INBOX"));
        }
    }

    @Test
    void aFolderThatIsNotThereIsRefusedByName() throws Exception {
        try (var reader = reader()) {
            var thrown = assertThrows(MessagingException.class, () -> reader.open("Gibtesnicht", false));
            assertTrue(thrown.getMessage().contains("Gibtesnicht"));
        }
    }

    @Test
    void theEnvelopeCarriesTheSenderTheSubjectAndTheAttachment() throws Exception {
        deliver("pruefung.pdf", pdf(), "application/pdf");

        try (var reader = reader()) {
            reader.open("INBOX", false);
            var messages = reader.since(Instant.now().minusSeconds(600), 10);
            assertEquals(1, messages.size());

            var envelope = reader.read(messages.getFirst(), false, PDF_ONLY, 1_000_000);
            assertEquals("post@musterstadt.de", envelope.sender());
            assertEquals("Unterlagen", envelope.subject());
            assertNotNull(envelope.messageId());
            assertEquals(1, envelope.attachments().size());
            assertNotNull(envelope.attachments().getFirst().data(), "small enough to be worth fetching");
            assertNull(envelope.authResult(), "nothing can be concluded where the server wrote no header");
        }
    }

    /**
     * The point of reading the structure first. A part the mail already declares as too big is never
     * transferred, so a 40 MB video attached to a mail nobody wants does not cross the network.
     */
    @Test
    void aPartTheMailDeclaresAsTooLargeIsNeverFetched() throws Exception {
        deliver("pruefung.pdf", pdf(), "application/pdf");

        try (var reader = reader()) {
            reader.open("INBOX", false);
            var envelope = reader.read(
                    reader.since(Instant.now().minusSeconds(600), 10).getFirst(), false, PDF_ONLY, 4);

            assertEquals(1, envelope.attachments().size());
            assertNull(envelope.attachments().getFirst().data(), "refused before it was fetched");
        }
    }

    /**
     * A type nobody wants and a name that does not look like anything we take is refused before the
     * download too.
     */
    @Test
    void aPartOfAKindNobodyWantsIsNeverFetched() throws Exception {
        deliver("daten.bin", "just bytes".getBytes(StandardCharsets.UTF_8), "application/x-custom");

        try (var reader = reader()) {
            reader.open("INBOX", false);
            var envelope = reader.read(
                    reader.since(Instant.now().minusSeconds(600), 10).getFirst(), false, PDF_ONLY, 1_000_000);

            assertEquals(1, envelope.attachments().size());
            assertNull(envelope.attachments().getFirst().data());
        }
    }

    /**
     * Servers and scanners declare PDFs as a generic stream often enough that refusing on the declared
     * type alone would drop the very files this exists to collect, so a name that looks right buys a
     * download and then a sniff.
     */
    @Test
    void aPdfDeclaredAsAGenericStreamIsStillFetched() throws Exception {
        deliver("pruefung.pdf", pdf(), "application/octet-stream");

        try (var reader = reader()) {
            reader.open("INBOX", false);
            var envelope = reader.read(
                    reader.since(Instant.now().minusSeconds(600), 10).getFirst(), false, PDF_ONLY, 1_000_000);

            assertNotNull(envelope.attachments().getFirst().data(), "the name earned it a look");
        }
    }

    @Test
    void mailOlderThanTheMomentAskedForIsNotReturned() throws Exception {
        deliver("pruefung.pdf", pdf(), "application/pdf");

        try (var reader = reader()) {
            reader.open("INBOX", false);
            assertTrue(reader.since(Instant.now().plusSeconds(3600), 10).isEmpty());
        }
    }

    @Test
    void onlyAsManyMessagesAsAskedForAreReturned() throws Exception {
        deliver("eins.pdf", pdf(), "application/pdf");
        deliver("zwei.pdf", pdf(), "application/pdf");

        try (var reader = reader()) {
            reader.open("INBOX", false);
            assertEquals(1, reader.since(Instant.now().minusSeconds(600), 1).size());
        }
    }

    @Test
    void aMessageCanBeMarkedFlaggedAndMoved() throws Exception {
        deliver("pruefung.pdf", pdf(), "application/pdf");

        try (var reader = reader()) {
            reader.open("INBOX", true);
            var message = reader.since(Instant.now().minusSeconds(600), 10).getFirst();

            reader.markSeen(message);
            reader.flag(message);
            reader.moveTo(message, "Erledigt");
        }

        var moved = greenMail
                .getManagers()
                .getImapHostManager()
                .getFolder(greenMail.getUserManager().getUser(USER), "Erledigt");
        assertEquals(1, moved.getMessageCount());
    }

    /**
     * The encryption a station chooses reaches the connection. Both of these are pointed at a server that
     * speaks neither, so what is being checked is that the attempt is made and refused rather than
     * silently downgraded to plain.
     */
    @Test
    void anEncryptedConnectionToAServerThatSpeaksPlainIsRefusedRatherThanDowngraded() {
        int port = greenMail.getImap().getPort();

        assertThrows(
                MessagingException.class,
                () -> new MailboxReader("127.0.0.1", port, MailSecurity.SSL, USER, PASSWORD, 5).close());
        assertThrows(
                MessagingException.class,
                () -> new MailboxReader("127.0.0.1", port, MailSecurity.STARTTLS, USER, PASSWORD, 5).close());
    }

    @Test
    void aHostThatIsNotThereIsRefused() {
        assertThrows(
                MessagingException.class,
                () -> new MailboxReader("127.0.0.1", 1, MailSecurity.NONE, USER, PASSWORD, 2).close());
    }

    @Test
    void aMessageWithNothingAttachedCarriesNoAttachments() throws Exception {
        var message = new MimeMessage(Session.getInstance(new Properties()));
        message.setFrom(new InternetAddress("post@musterstadt.de"));
        message.setRecipients(Message.RecipientType.TO, USER);
        message.setSubject("Nur Text");
        message.setText("Kein Anhang.");
        greenMail
                .getManagers()
                .getImapHostManager()
                .getInbox(greenMail.getUserManager().getUser(USER))
                .store(message);

        try (var reader = reader()) {
            reader.open("INBOX", false);
            var envelope = reader.read(
                    reader.since(Instant.now().minusSeconds(600), 10).getFirst(), true, PDF_ONLY, 1_000_000);

            assertTrue(envelope.attachments().isEmpty());
            assertFalse(envelope.subject().isBlank());
        }
    }
}
