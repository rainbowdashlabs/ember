/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.entity.MailFallbackPayload;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationMailSettingsServiceTest {
    private StationMailProviderRepository providers;
    private ProviderSecretRepository secrets;
    private WebhookKeyService webhookKeys;
    private StationMailSettingsService service;

    private static MailChainEntry stored(String password) {
        return new MailChainEntry(
                1,
                MailProviderType.BREVO,
                "h",
                587,
                SmtpEncryption.STARTTLS,
                "u",
                password,
                "key",
                "a@b.test",
                "Nord",
                2,
                0,
                "Brevo",
                "https://brevo.test");
    }

    private static MailFallbackPayload incoming(MailProviderType provider, String password) {
        return new MailFallbackPayload(
                provider,
                "h",
                587,
                SmtpEncryption.STARTTLS,
                "u",
                password,
                MailFallbackPayload.MASK,
                "a@b.test",
                "Nord",
                0,
                -3,
                "Brevo",
                "https://brevo.test",
                null);
    }

    @BeforeEach
    void setup() {
        providers = mock(StationMailProviderRepository.class);
        secrets = mock(ProviderSecretRepository.class);
        webhookKeys = mock(WebhookKeyService.class);
        var api = mock(Api.class);
        when(api.baseUrl()).thenReturn("https://ember.test");
        when(webhookKeys.webhookUrl(eq("https://ember.test"), eq(3), anyString()))
                .thenReturn("https://ember.test/hook");
        service = new StationMailSettingsService(providers, secrets, webhookKeys, api);
    }

    @Test
    void theWebhookBelongsToTheFirstProviderAndSaysWhetherItIsSigned() {
        when(providers.findByStation(3)).thenReturn(List.of(stored("pw")));
        when(secrets.find(3, MailProviderType.BREVO)).thenReturn(Optional.of("s"));

        assertTrue(service.webhook(3).signingSecretSet());
        service.updateSigningSecret(3, "fresh");
        service.regenerateWebhook(3);

        verify(secrets).store(3, MailProviderType.BREVO, "fresh");
        verify(webhookKeys).regenerate(3);
        assertEquals("Brevo", service.firstEntry(3).orElseThrow().providerName());
    }

    @Test
    void aStationWithoutProvidersSignsNothingAndCannotSendATestMail() {
        assertFalse(service.webhook(3).signingSecretSet());
        assertTrue(service.firstEntry(3).isEmpty());
        assertEquals(
                StationRefusal.NO_MAIL_PROVIDER_SET,
                assertThrows(RefusalResponse.class, () -> service.requireProvider(3))
                        .refusal());
    }

    @Test
    void theProvidersGoOutMaskedAndComeBackKeepingTheStoredSecrets() {
        when(providers.findByStation(3)).thenReturn(List.of(stored("pw")));
        service.requireProvider(3);

        var listed = service.providers(3);
        service.updateProviders(
                3, List.of(incoming(MailProviderType.NONE, "x"), incoming(MailProviderType.BREVO, "new")));

        assertEquals(MailFallbackPayload.MASK, listed.getFirst().smtpPassword());
        assertEquals("https://ember.test/hook", listed.getFirst().deliveryWebhookUrl());
        verify(providers)
                .replace(
                        3,
                        List.of(new MailChainEntry(
                                1,
                                MailProviderType.BREVO,
                                "h",
                                587,
                                SmtpEncryption.STARTTLS,
                                "u",
                                "new",
                                "",
                                "a@b.test",
                                "Nord",
                                1,
                                0,
                                "Brevo",
                                "https://brevo.test")));
    }

    @Test
    void anEmptyListIsRefusedWhereThereWasOneAndClearingIsTheWayToStop() {
        when(providers.findByStation(3)).thenReturn(List.of(stored("pw")));

        var refused =
                assertThrows(RefusalResponse.class, () -> service.updateProviders(3, List.of(incoming(null, "x"))));
        service.clear(3);

        assertEquals(StationRefusal.MAIL_PROVIDER_LIST_EMPTY, refused.refusal());
        verify(providers).replace(3, List.of());
    }
}
