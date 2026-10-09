/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.MailFallbackChain;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.MailingConfigRequest;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.MailingConfigResponse;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.WebhookUrlResponse;
import dev.chojo.ember.feature.mail.service.MailDashboardService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.media.service.LogoFragmentService;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.system.service.ApplicationLogService;
import dev.chojo.ember.feature.system.service.ApplicationLogService.ApplicationLogPage;
import dev.chojo.ember.feature.system.service.ApplicationLogService.LogFilter;
import dev.chojo.ember.feature.system.service.ApplicationLogService.LoggingConfig;
import dev.chojo.ember.feature.system.service.ApplicationLogService.LoggingConfigRequest;
import dev.chojo.ember.feature.system.service.InstanceSettingsService;
import dev.chojo.ember.feature.system.service.InstanceSettingsService.ApplicationSettings;
import dev.chojo.ember.feature.system.service.InstanceSettingsService.PublicTheme;
import dev.chojo.ember.feature.system.service.SecuritySettingsService;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.BackupCodesConfig;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.HibpConfigRequest;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.HibpConfigResponse;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.TokensConfigRequest;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.TokensConfigResponse;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.TotpConfig;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.TwoFactorCoreConfigRequest;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.TwoFactorCoreConfigResponse;
import dev.chojo.ember.feature.system.service.SecuritySettingsService.WebAuthnConfig;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Request;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The instance settings screens, over HTTP: each handler reads the request, asks its service and
 * answers with what the service returned.
 */
class AdminSettingsRoutesTest {
    @TempDir
    Path directory;

    private InstanceSettingsService instanceSettings;
    private SecuritySettingsService securitySettings;
    private InstanceMailSettingsService mailSettings;
    private ApplicationLogService applicationLog;
    private MailDashboardService dashboard;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        instanceSettings = mock(InstanceSettingsService.class);
        securitySettings = mock(SecuritySettingsService.class);
        mailSettings = mock(InstanceMailSettingsService.class);
        applicationLog = mock(ApplicationLogService.class);
        dashboard = mock(MailDashboardService.class);
        harness = RouteHarness.serving(new AdminSettingsRoutes(
                instanceSettings,
                securitySettings,
                mailSettings,
                applicationLog,
                mock(LogoFragmentService.class),
                new Conf(directory),
                mock(EmailService.class),
                mock(MailLocaleService.class),
                dashboard));
    }

    private Response get(HttpClient client, String path) {
        return client.get(PREFIX + path, harness.as(TestSessions.administrator()));
    }

    private Response put(HttpClient client, String path, String json) {
        return client.put(PREFIX + path, body(json), harness.as(TestSessions.administrator()));
    }

    private Response post(HttpClient client, String path) {
        return client.post(PREFIX + path, null, harness.as(TestSessions.administrator()));
    }

    private Response delete(HttpClient client, String path) {
        return client.delete(PREFIX + path, null, harness.as(TestSessions.administrator()));
    }

    @Test
    void thePublicSettingsAnswerAnybody() {
        when(instanceSettings.stationRegistrationEnabled()).thenReturn(true);
        when(instanceSettings.publicTheme()).thenReturn(new PublicTheme("ember", "ROUNDED", false, true));

        harness.run((server, client) -> {
            var registration = client.get(PREFIX + "/public/settings/station-registration");
            assertEquals(200, registration.code());
            assertTrue(json(registration).path("enabled").asBoolean());

            var theme = client.get(PREFIX + "/public/settings/theme");
            assertTrue(json(theme).path("forcePrideFlag").asBoolean());
        });
    }

    @Test
    void theGeneralSettingsAreReadAndWrittenByAnAdministratorOnly() {
        var settings = new ApplicationSettings(true, "forest", "CORNERS", true, false, "de", List.of("de", "en"));
        when(instanceSettings.settings()).thenReturn(settings);
        when(instanceSettings.update(any())).thenReturn(settings);

        harness.run((server, client) -> {
            assertEquals(
                    "forest",
                    json(get(client, "/admin/settings"))
                            .path("instanceDefaultTheme")
                            .asString());

            var saved = put(client, "/admin/settings", """
                    {"stationRegistrationEnabled": true, "instanceDefaultTheme": "forest",
                     "instanceDefaultFeel": "CORNERS", "instanceLockFeel": true, "forcePrideFlag": false,
                     "defaultMailLocale": "de", "availableMailLocales": []}""");
            assertEquals(200, saved.code());

            var member = client.get(PREFIX + "/admin/settings", harness.as(TestSessions.member(3)));
            assertEquals(403, member.code());
        });

        verify(instanceSettings)
                .update(new ApplicationSettings(true, "forest", "CORNERS", true, false, "de", List.of()));
    }

    @Test
    void tokensAndThePepperGoThroughTheSecuritySettings() {
        var tokens = new TokensConfigResponse(32, 24, 72, 30, 43200, 60, true);
        when(securitySettings.tokens()).thenReturn(tokens);
        when(securitySettings.updateTokens(any())).thenReturn(tokens);
        when(securitySettings.generateTokenPepper()).thenReturn(tokens);

        harness.run((server, client) -> {
            assertEquals(
                    43200,
                    json(get(client, "/admin/config/auth/tokens"))
                            .path("sessionMinutes")
                            .asInt());
            assertEquals(200, put(client, "/admin/config/auth/tokens", """
                            {"tokenBytes": 48, "verifyTokenHours": 12, "passwordTokenHours": 24,
                             "setupTokenDays": 14, "sessionMinutes": 600, "untrustedSessionMinutes": 30,
                             "tokenPepperConfigured": true}""").code());
            assertTrue(json(post(client, "/admin/config/auth/tokens/generate-pepper"))
                    .path("tokenPepperConfigured")
                    .asBoolean());
        });

        verify(securitySettings).updateTokens(new TokensConfigRequest(48, 12, 24, 14, 600, 30));
    }

    @Test
    void theLeakCheckAndTwoFactorGoThroughTheSecuritySettings() {
        var hibp = new HibpConfigResponse(true, "https://x/", 30, 5);
        var twoFactor = new TwoFactorCoreConfigResponse(true, 300, 30, 7, false);
        when(securitySettings.hibp()).thenReturn(hibp);
        when(securitySettings.updateHibp(any())).thenReturn(hibp);
        when(securitySettings.twoFactorCore()).thenReturn(twoFactor);
        when(securitySettings.updateTwoFactorCore(any())).thenReturn(twoFactor);
        when(securitySettings.generateTwoFactorSecretKey()).thenReturn(twoFactor);

        harness.run((server, client) -> {
            assertEquals(
                    30,
                    json(get(client, "/admin/config/auth/hibp"))
                            .path("staleAfterDays")
                            .asInt());
            put(client, "/admin/config/auth/hibp", """
                    {"enabled": false, "endpoint": "https://y/", "staleAfterDays": 3, "timeoutSeconds": 2}""");
            assertEquals(
                    300,
                    json(get(client, "/admin/config/auth/two-factor"))
                            .path("stepUpFreshnessSeconds")
                            .asInt());
            put(client, "/admin/config/auth/two-factor", """
                    {"enabled": false, "stepUpFreshnessSeconds": 600, "trustedDeviceMaxDays": 3,
                     "enrollmentGraceDays": 2}""");
            assertEquals(
                    200,
                    post(client, "/admin/config/auth/two-factor/generate-secret-key")
                            .code());
        });

        verify(securitySettings).updateHibp(new HibpConfigRequest(false, "https://y/", 3, 2));
        verify(securitySettings).updateTwoFactorCore(new TwoFactorCoreConfigRequest(false, 600, 3, 2));
        verify(securitySettings).generateTwoFactorSecretKey();
    }

    @Test
    void authenticatorsBackupCodesAndSecurityKeysGoThroughTheSecuritySettings() {
        var totp = new TotpConfig(6, 30, "SHA1", 1, "Ember");
        var webauthn = new WebAuthnConfig("ember.test", "Ember", "none", 60);
        when(securitySettings.totp()).thenReturn(totp);
        when(securitySettings.updateTotp(any())).thenReturn(totp);
        when(securitySettings.backupCodes()).thenReturn(new BackupCodesConfig(10));
        when(securitySettings.updateBackupCodes(any())).thenReturn(new BackupCodesConfig(12));
        when(securitySettings.webAuthn()).thenReturn(webauthn);
        when(securitySettings.updateWebAuthn(any())).thenReturn(webauthn);

        harness.run((server, client) -> {
            assertEquals(
                    "Ember",
                    json(get(client, "/admin/config/auth/two-factor/totp"))
                            .path("issuer")
                            .asString());
            put(client, "/admin/config/auth/two-factor/totp", """
                    {"digits": 8, "periodSeconds": 60, "algorithm": "sha256", "driftWindow": 2, "issuer": "Wache"}""");
            assertEquals(
                    10,
                    json(get(client, "/admin/config/auth/two-factor/backup-codes"))
                            .path("count")
                            .asInt());
            assertEquals(
                    12,
                    json(put(client, "/admin/config/auth/two-factor/backup-codes", "{\"count\": 12}"))
                            .path("count")
                            .asInt());
            assertEquals(
                    "ember.test",
                    json(get(client, "/admin/config/auth/webauthn"))
                            .path("rpId")
                            .asString());
            put(client, "/admin/config/auth/webauthn", """
                    {"rpId": "a.test", "rpName": "A", "attestation": "direct", "timeoutSeconds": 90}""");
        });

        verify(securitySettings).updateTotp(new TotpConfig(8, 60, "sha256", 2, "Wache"));
        verify(securitySettings).updateBackupCodes(new BackupCodesConfig(12));
        verify(securitySettings).updateWebAuthn(new WebAuthnConfig("a.test", "A", "direct", 90));
    }

    @Test
    void theInstanceMailGoesThroughTheMailSettings() {
        when(mailSettings.mailing()).thenReturn(new MailingConfigResponse(60, 50));
        when(mailSettings.updateMailing(any())).thenReturn(new MailingConfigResponse(15, 40));
        when(mailSettings.providers()).thenReturn(new MailFallbackChain(2, List.of()));
        when(mailSettings.updateProviders(any())).thenReturn(new MailFallbackChain(2, List.of()));
        when(mailSettings.regenerateWebhookKey()).thenReturn(new WebhookUrlResponse("https://ember.test/hook"));

        harness.run((server, client) -> {
            assertEquals(
                    60,
                    json(get(client, "/admin/config/mailing"))
                            .path("notificationDigestIntervalMinutes")
                            .asInt());
            assertEquals(
                    15,
                    json(put(client, "/admin/config/mailing", """
                                    {"notificationDigestIntervalMinutes": 15, "stationShare": 40,
                                     "deliveryWebhookUrl": "ignored"}"""))
                            .path("notificationDigestIntervalMinutes")
                            .asInt());
            assertEquals(
                    2,
                    json(get(client, "/admin/config/mailing/providers"))
                            .path("attempts")
                            .asInt());
            assertEquals(
                    200, put(client, "/admin/config/mailing/providers", """
                            {"attempts": 2, "fallbacks": []}""").code());
            assertEquals(
                    "https://ember.test/hook",
                    json(post(client, "/admin/config/mailing/webhook-key"))
                            .path("deliveryWebhookUrl")
                            .asString());
            assertEquals(204, delete(client, "/admin/config/mailing").code());
        });

        verify(mailSettings).updateMailing(new MailingConfigRequest(15, 40));
        verify(mailSettings).updateProviders(new MailFallbackChain(2, List.of()));
        verify(mailSettings).clear();
    }

    @Test
    void aMailBlockIsLiftedForTheInstanceAndAnUnknownProviderIsRefused() {
        harness.run((server, client) -> {
            assertEquals(
                    204,
                    delete(client, "/admin/config/mailing/blocks?provider=BREVO&domain=example.org")
                            .code());
            assertEquals(
                    SystemRefusal.MAIL_PROVIDER_KIND_UNKNOWN,
                    refusalOf(delete(client, "/admin/config/mailing/blocks?provider=PIGEON")));
        });

        verify(dashboard).liftBlock(null, MailProviderType.BREVO, "example.org");
    }

    @Test
    void theLogIsReadSearchedClearedAndConfiguredThroughTheLogService() {
        var page = new ApplicationLogPage(List.of(), List.of(), List.of(), true, "INFO", 14, 0);
        when(applicationLog.page(any(), any(), anyInt())).thenReturn(page);
        when(applicationLog.defaultFacetLimit()).thenReturn(20);
        when(applicationLog.config()).thenReturn(new LoggingConfig(true, "INFO", 14, 3));
        when(applicationLog.updateConfig(any())).thenReturn(new LoggingConfig(true, "WARN", 30, 3));

        harness.run((server, client) -> {
            assertEquals(
                    "INFO",
                    json(get(client, "/admin/monitoring/log?level=info&search=boom&logger=a&thread=t&before=9"))
                            .path("databaseLevel")
                            .asString());
            assertEquals(
                    200,
                    get(client, "/admin/monitoring/log/facets?kind=thread&name=work")
                            .code());
            assertEquals(
                    200, get(client, "/admin/monitoring/log/facets?limit=5").code());
            assertEquals(204, delete(client, "/admin/monitoring/log").code());
            assertEquals(
                    3,
                    json(get(client, "/admin/config/logging"))
                            .path("storedLines")
                            .asInt());
            assertEquals(
                    "WARN",
                    json(put(client, "/admin/config/logging", """
                                    {"databaseEnabled": true, "databaseLevel": "warn", "retentionDays": 30}"""))
                            .path("databaseLevel")
                            .asString());
        });

        verify(applicationLog).page(new LogFilter("info", "boom", "a", "t"), "9", 200);
        verify(applicationLog).facets(new LogFilter(null, null, null, null), true, "work", 20);
        verify(applicationLog).facets(new LogFilter(null, null, null, null), false, null, 5);
        verify(applicationLog).clear();
        verify(applicationLog).updateConfig(new LoggingConfigRequest(true, "warn", 30));
    }

    /**
     * A legal document is read for what its bytes are: a Word document, even renamed, becomes sections,
     * and an old binary Word file is refused rather than read as text.
     */
    @Test
    void aLegalImportReadsTheFileForWhatItIsAndRefusesTheOldWordFormat() throws IOException {
        byte[] word;
        try (var in = getClass().getResourceAsStream("/generator/certificate.docx")) {
            word = Objects.requireNonNull(in, "the fixture").readAllBytes();
        }
        byte[] oldWord = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0};

        harness.run((server, client) -> {
            var read = client.request(PREFIX + "/admin/legal/privacy/de/import", upload("datenschutz.odt", word));
            assertEquals(200, read.code(), () -> read.body().string());
            assertTrue(json(read).toString().contains("Bescheinigung"));
            assertEquals(
                    SystemRefusal.LEGAL_DOCUMENT_NOT_READ,
                    refusalOf(client.request(PREFIX + "/admin/legal/privacy/de/import", upload("alt.doc", oldWord))));
        });
    }

    private Consumer<Request.Builder> upload(String fileName, byte[] data) {
        return harness.as(TestSessions.administrator()).andThen(TestUploads.multipart(fileName, data));
    }
}
