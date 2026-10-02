/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.ConfigChanges;
import dev.chojo.ember.conf.UnwritableConf;
import dev.chojo.ember.feature.mail.entity.MailFallbackPayload;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.MailFallbackChain;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.MailingConfigRequest;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstanceMailSettingsServiceTest {
    @TempDir
    Path directory;

    private WebhookKeyService webhookKeys;
    private InstanceMailSettingsService service;

    private static MailFallbackPayload smtp(String host, String password) {
        return new MailFallbackPayload(
                MailProviderType.SMTP,
                host,
                587,
                SmtpEncryption.STARTTLS,
                "relay",
                password,
                "",
                "wache@test.com",
                "Wache",
                3,
                100,
                "",
                "",
                null);
    }

    private static MailFallbackPayload none() {
        return new MailFallbackPayload(MailProviderType.NONE, "", 0, null, "", "", "", "", "", 0, 0, "", "", null);
    }

    @BeforeEach
    void setup() {
        webhookKeys = mock(WebhookKeyService.class);
        when(webhookKeys.webhookUrl(any(), isNull(), anyString())).thenReturn("https://ember.test/hook");
        var conf = new Conf(directory);
        service = new InstanceMailSettingsService(conf, new ConfigChanges(conf), webhookKeys);
    }

    private Conf reread() {
        return new Conf(directory);
    }

    @Test
    void aFreshInstanceSendsThroughNobody() {
        var chain = service.providers();

        assertTrue(chain.fallbacks().isEmpty());
        assertEquals(2, chain.attempts());
    }

    @Test
    void theProvidersAreWrittenInOrderWithoutTheEmptyOnes() {
        service.updateProviders(
                new MailFallbackChain(2, List.of(smtp("one.test", "secret"), none(), smtp("two.test", ""))));

        var providers = reread().main().mailing().providers();
        assertEquals(2, providers.size());
        assertEquals("one.test", providers.get(0).host());
        assertEquals("secret", providers.get(0).password());
        assertEquals("two.test", providers.get(1).host());
    }

    @Test
    void aMaskedSecretKeepsTheOneStoredAtItsPlaceAndGoesOutMasked() {
        service.updateProviders(new MailFallbackChain(2, List.of(smtp("one.test", "secret"))));

        var answer = service.updateProviders(
                new MailFallbackChain(2, List.of(smtp("renamed.test", MailFallbackPayload.MASK))));

        assertEquals("secret", reread().main().mailing().providers().getFirst().password());
        assertEquals(MailFallbackPayload.MASK, answer.fallbacks().getFirst().smtpPassword());
        assertEquals("https://ember.test/hook", answer.fallbacks().getFirst().deliveryWebhookUrl());
    }

    @Test
    void anEmptyListIsNotHowSendingIsStopped() {
        service.updateProviders(new MailFallbackChain(2, List.of(smtp("one.test", "secret"))));

        var refused =
                assertThrows(RefusalResponse.class, () -> service.updateProviders(new MailFallbackChain(2, null)));

        assertEquals(SystemRefusal.INSTANCE_MAIL_PROVIDER_LIST_EMPTY, refused.refusal());
        assertEquals(1, reread().main().mailing().providers().size());
    }

    @Test
    void anEmptyListIsFineWhenThereWasNothingToLose() {
        assertTrue(service.updateProviders(new MailFallbackChain(2, List.of(none())))
                .fallbacks()
                .isEmpty());
    }

    @Test
    void clearingStopsTheInstanceSending() {
        service.updateProviders(new MailFallbackChain(2, List.of(smtp("one.test", "secret"))));

        service.clear();

        var mailing = reread().main().mailing();
        assertTrue(mailing.providers().isEmpty());
        assertEquals(MailProviderType.NONE, mailing.provider());
        assertEquals("", mailing.smtp().host());
    }

    @Test
    void theDigestIntervalIsWrittenToTheFile() {
        assertEquals(15, service.updateMailing(new MailingConfigRequest(15)).notificationDigestIntervalMinutes());

        assertEquals(15, reread().main().mailing().notificationDigestIntervalMinutes());
        assertEquals(15, service.mailing().notificationDigestIntervalMinutes());
    }

    @Test
    void aNewWebhookKeyAnswersWithTheNewAddress() {
        assertEquals("https://ember.test/hook", service.regenerateWebhookKey().deliveryWebhookUrl());

        verify(webhookKeys).regenerate(null);
    }

    @Test
    void aChangeTheFileCannotTakeIsTakenBack() {
        var unwritable = UnwritableConf.create();
        var failing = new InstanceMailSettingsService(unwritable, new ConfigChanges(unwritable), webhookKeys);
        var mailing = unwritable.main().mailing();
        var before = mailing.senders();

        assertEquals(
                SystemRefusal.SETTINGS_NOT_SAVED,
                assertThrows(
                                RefusalResponse.class,
                                () -> failing.updateProviders(new MailFallbackChain(2, List.of(smtp("x.test", "s")))))
                        .refusal());
        assertEquals(
                SystemRefusal.SETTINGS_NOT_SAVED,
                assertThrows(RefusalResponse.class, failing::clear).refusal());
        assertEquals(
                SystemRefusal.SETTINGS_NOT_SAVED,
                assertThrows(RefusalResponse.class, () -> failing.updateMailing(new MailingConfigRequest(15)))
                        .refusal());

        assertEquals(before, mailing.senders());
        assertEquals(60, mailing.notificationDigestIntervalMinutes());
    }
}
