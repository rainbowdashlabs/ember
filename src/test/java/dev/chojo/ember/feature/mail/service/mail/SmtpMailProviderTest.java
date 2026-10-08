/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import dev.chojo.ember.feature.mail.entity.MailAttachment;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session an SMTP relay is reached with: it gives up on a relay that stops answering, it never
 * talks to a STARTTLS relay without encryption, and it only talks in the clear when that was chosen.
 */
class SmtpMailProviderTest {

    private static GreenMail plainRelay;

    @BeforeAll
    static void setup() {
        plainRelay = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        plainRelay.start();
        plainRelay.setUser("sender@relay.test", "sender", "secret");
    }

    @AfterAll
    static void cleanup() {
        if (plainRelay != null) plainRelay.stop();
    }

    @BeforeEach
    void emptyRelay() throws Exception {
        plainRelay.purgeEmailFromAllMailboxes();
    }

    private static SmtpMailProvider toPlainRelay(SmtpEncryption encryption) {
        return new SmtpMailProvider(
                "127.0.0.1",
                plainRelay.getSmtp().getPort(),
                encryption,
                "sender",
                "secret",
                "sender@relay.test",
                "Sender");
    }

    @ParameterizedTest
    @EnumSource(SmtpEncryption.class)
    void everyConnectionHasTimeouts(SmtpEncryption encryption) {
        var props = SmtpMailProvider.sessionProperties("smtp.relay.test", 587, encryption);

        assertEquals("30000", props.getProperty("mail.smtp.connectiontimeout"));
        assertEquals("60000", props.getProperty("mail.smtp.timeout"));
        assertEquals("60000", props.getProperty("mail.smtp.writetimeout"));
    }

    @Test
    void aStartTlsRelayMustEncrypt() {
        var props = SmtpMailProvider.sessionProperties("smtp.relay.test", 587, SmtpEncryption.STARTTLS);

        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", props.getProperty("mail.smtp.starttls.required"));
        assertNull(props.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void anImplicitTlsRelayEncryptsFromTheStart() {
        var props = SmtpMailProvider.sessionProperties("smtp.relay.test", 465, SmtpEncryption.IMPLICIT_TLS);

        assertEquals("true", props.getProperty("mail.smtp.ssl.enable"));
        assertNull(props.getProperty("mail.smtp.starttls.required"));
    }

    @Test
    void anUnencryptedRelayNeverTriesToEncrypt() {
        var props = SmtpMailProvider.sessionProperties("smtp.relay.test", 25, SmtpEncryption.NONE);

        assertEquals("false", props.getProperty("mail.smtp.starttls.enable"));
        assertNull(props.getProperty("mail.smtp.starttls.required"));
        assertNull(props.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void nothingGoesToARelayThatWillNotEncrypt() {
        var result = toPlainRelay(SmtpEncryption.STARTTLS).send("reader@relay.test", "Hello", "<p>body</p>", "1");

        assertNotEquals(MailProvider.SendResult.SENT, result);
        assertEquals(0, plainRelay.getReceivedMessages().length, "the login and the mail stay off the wire");
    }

    @Test
    void aRelayChosenAsUnencryptedReceivesTheMail() {
        var result = toPlainRelay(SmtpEncryption.NONE).send("reader@relay.test", "Hello", "<p>body</p>", "1");

        assertEquals(MailProvider.SendResult.SENT, result);
        assertEquals(1, plainRelay.getReceivedMessages().length);
    }

    @Test
    void aFileGoesAlongBelowTheText() throws Exception {
        byte[] pdf = "%PDF-1.7 sealed".getBytes(StandardCharsets.US_ASCII);
        var result = toPlainRelay(SmtpEncryption.NONE)
                .send(
                        "reader@relay.test",
                        "Copy",
                        "<p>body</p>",
                        "2",
                        List.of(new MailAttachment("copy.pdf", MailAttachment.PDF, pdf)));

        assertEquals(MailProvider.SendResult.SENT, result);
        var received = plainRelay.getReceivedMessages()[0];
        var parts = (MimeMultipart) received.getContent();
        assertEquals(2, parts.getCount());
        assertTrue(parts.getBodyPart(0).isMimeType("text/html"));
        var file = parts.getBodyPart(1);
        assertEquals(Part.ATTACHMENT, file.getDisposition());
        assertEquals("copy.pdf", file.getFileName());
        assertTrue(file.isMimeType("application/pdf"));
        assertArrayEquals(pdf, file.getInputStream().readAllBytes());
    }
}
