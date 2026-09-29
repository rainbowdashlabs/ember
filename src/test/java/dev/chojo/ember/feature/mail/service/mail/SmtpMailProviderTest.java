/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service.mail;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The session an SMTP relay is reached with: it gives up on a relay that stops answering, and it
 * never talks to a STARTTLS relay without encryption.
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

    @Test
    void everyConnectionHasTimeouts() {
        for (boolean ssl : new boolean[] {true, false}) {
            var props = SmtpMailProvider.sessionProperties("smtp.relay.test", 587, ssl);

            assertEquals("30000", props.getProperty("mail.smtp.connectiontimeout"));
            assertEquals("60000", props.getProperty("mail.smtp.timeout"));
            assertEquals("60000", props.getProperty("mail.smtp.writetimeout"));
        }
    }

    @Test
    void aStartTlsRelayMustEncrypt() {
        var props = SmtpMailProvider.sessionProperties("smtp.relay.test", 587, false);

        assertEquals("true", props.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", props.getProperty("mail.smtp.starttls.required"));
        assertNull(props.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void anImplicitTlsRelayEncryptsFromTheStart() {
        var props = SmtpMailProvider.sessionProperties("smtp.relay.test", 465, true);

        assertEquals("true", props.getProperty("mail.smtp.ssl.enable"));
        assertNull(props.getProperty("mail.smtp.starttls.required"));
    }

    @Test
    void nothingGoesToARelayThatWillNotEncrypt() {
        var provider = new SmtpMailProvider(
                "127.0.0.1", plainRelay.getSmtp().getPort(), false, "sender", "secret", "sender@relay.test", "Sender");

        var result = provider.send("reader@relay.test", "Hello", "<p>body</p>", "1");

        assertNotEquals(MailProvider.SendResult.SENT, result);
        assertEquals(0, plainRelay.getReceivedMessages().length, "the login and the mail stay off the wire");
    }
}
