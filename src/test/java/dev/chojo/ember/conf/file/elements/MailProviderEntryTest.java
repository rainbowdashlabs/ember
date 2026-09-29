/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A configuration file written before the encryption had three values keeps meaning what it can
 * still safely mean: the old flag set gives implicit TLS, unset gives required STARTTLS, and an
 * unencrypted relay is never chosen for an operator who did not choose it.
 */
class MailProviderEntryTest {

    private static MailProviderEntry legacy(boolean ssl) {
        var entry = new MailProviderEntry();
        try {
            var field = MailProviderEntry.class.getDeclaredField("ssl");
            field.setAccessible(true);
            field.set(entry, ssl);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return entry;
    }

    @Test
    void theOldFlagSetMeansImplicitTls() {
        assertEquals(SmtpEncryption.IMPLICIT_TLS, legacy(true).encryption());
    }

    @Test
    void theOldFlagUnsetMeansRequiredStartTls() {
        assertEquals(SmtpEncryption.STARTTLS, legacy(false).encryption());
    }

    @Test
    void anExplicitChoiceWinsOverTheOldFlag() {
        var entry = new MailProviderEntry(
                MailProviderType.SMTP, "relay.local", 25, SmtpEncryption.NONE, "", "", "", "a@b.test", "", 2, 0);

        assertEquals(SmtpEncryption.NONE, entry.encryption());
    }
}
