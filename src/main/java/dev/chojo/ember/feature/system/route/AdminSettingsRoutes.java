/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.feature.legal.entity.DocumentPlaceholder;
import dev.chojo.ember.feature.legal.entity.LegalDocumentType;
import dev.chojo.ember.feature.legal.service.BrowserStorageService;
import dev.chojo.ember.feature.legal.service.LegalDocumentService;
import dev.chojo.ember.feature.legal.service.LegalImportService;
import dev.chojo.ember.feature.mail.entity.MailTestResponse;
import dev.chojo.ember.feature.mail.entity.ProviderTestRequest;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.MailFallbackChain;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.MailingConfigRequest;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.MailingConfigResponse;
import dev.chojo.ember.feature.mail.service.InstanceMailSettingsService.WebhookUrlResponse;
import dev.chojo.ember.feature.mail.service.MailDashboardService;
import dev.chojo.ember.feature.mail.service.MailDashboardService.MailDashboard;
import dev.chojo.ember.feature.mail.service.MailDashboardService.RequeuedMails;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.media.service.LogoFragmentService;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.system.entity.LogFacet;
import dev.chojo.ember.feature.system.service.ApplicationLogService;
import dev.chojo.ember.feature.system.service.ApplicationLogService.ApplicationLogPage;
import dev.chojo.ember.feature.system.service.ApplicationLogService.LogFilter;
import dev.chojo.ember.feature.system.service.ApplicationLogService.LoggingConfig;
import dev.chojo.ember.feature.system.service.ApplicationLogService.LoggingConfigRequest;
import dev.chojo.ember.feature.system.service.DataInitializer;
import dev.chojo.ember.feature.system.service.DataInitializer.TemplateSection;
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
import dev.chojo.ember.util.FilePaths;
import dev.chojo.ember.util.MailAddress;
import dev.chojo.ember.util.PandocConverter;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Singleton
public class AdminSettingsRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(AdminSettingsRoutes.class);
    /**
     * Pattern allowed for the {@code {name}} path segment on the public logo routes.
     * Restricting to {@code [A-Za-z0-9_-]+} forbids slashes, dots, and {@code ..} so
     * the value can never escape the configured image directory regardless of how
     * the underlying filesystem resolver normalises the path.
     */
    private static final Pattern SAFE_LOGO_NAME = Pattern.compile("^[A-Za-z0-9_-]+$");
    /**
     * Pattern allowed for the {@code {locale}} path segment on the admin legal
     * routes. Restricting to two lowercase letters with an optional uppercase
     * region tag rejects {@code ..} segments, slashes, and any value that would
     * otherwise let an admin write or list files outside the configured legal
     * directory.
     */
    private static final Pattern SAFE_LOCALE = Pattern.compile("^[a-z]{2}(-[A-Z]{2})?$");

    private final InstanceSettingsService instanceSettings;
    private final SecuritySettingsService securitySettings;
    private final InstanceMailSettingsService mailSettings;
    private final ApplicationLogService applicationLog;
    private final LogoFragmentService logoFragmentService;
    private final Conf conf;
    private final EmailService emailService;
    private final MailLocaleService mailLocaleService;
    private final MailDashboardService dashboardService;
    private final LegalDocumentService documentService;

    @Inject
    public AdminSettingsRoutes(
            InstanceSettingsService instanceSettings,
            SecuritySettingsService securitySettings,
            InstanceMailSettingsService mailSettings,
            ApplicationLogService applicationLog,
            LogoFragmentService logoFragmentService,
            Conf conf,
            EmailService emailService,
            MailLocaleService mailLocaleService,
            MailDashboardService dashboardService) {
        this.instanceSettings = instanceSettings;
        this.securitySettings = securitySettings;
        this.mailSettings = mailSettings;
        this.applicationLog = applicationLog;
        this.dashboardService = dashboardService;
        this.logoFragmentService = logoFragmentService;
        this.conf = conf;
        this.emailService = emailService;
        this.mailLocaleService = mailLocaleService;
        this.documentService = new LegalDocumentService(conf.main().api().placeholderFile());
        initializeLogoFragments();
    }

    /**
     * Parses the {@code locale} path parameter, validates it against
     * {@link #SAFE_LOCALE}, and checks that the resolved directory stays inside
     * {@code base}. Refuses on any mismatch so the route returns 400 with a
     * static, user-safe message.
     */
    private static String safeLocale(Context ctx, Path base) {
        String locale = ctx.pathParam("locale");
        if (locale == null || !SAFE_LOCALE.matcher(locale).matches()) {
            throw Refusal.SETTINGS_LOCALE_NOT_A_LANGUAGE.raise();
        }
        Path resolved = base.resolve(locale).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw Refusal.SETTINGS_LOCALE_OUT_OF_PLACE.raise();
        }
        return locale;
    }

    /**
     * The folder of one language under the legal directory. The path is checked again even though
     * {@link #safeLocale} validated it, so a caller that skips that gate still cannot escape.
     */
    private static Path resolveLocaleDir(Path base, String locale) {
        Path resolved = base.resolve(locale).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw Refusal.SETTINGS_LOCALE_FOLDER_OUT_OF_PLACE.raise();
        }
        return resolved;
    }

    private static @Nullable String safeLogoName(Context ctx) {
        String name = ctx.pathParam("name");
        if (name == null) return null;
        if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
        return SAFE_LOGO_NAME.matcher(name).matches() ? name : null;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/settings/station-registration", this::isRegistrationEnabled);
        routes.get(prefix + "/public/settings/theme", this::getPublicTheme);
        routes.get(prefix + "/public/logo-fragment/{name}", this::serveLogoFragment);
        routes.get(prefix + "/admin/settings", this::getSettings, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/settings",
                this::updateSettings,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/config/auth/tokens", this::getTokensConfig, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/auth/tokens",
                this::updateTokensConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.post(
                prefix + "/admin/config/auth/tokens/generate-pepper",
                this::generateTokenPepper,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/config/auth/hibp", this::getHibpConfig, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/auth/hibp",
                this::updateHibpConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(
                prefix + "/admin/config/auth/two-factor",
                this::getTwoFactorCoreConfig,
                InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/auth/two-factor",
                this::updateTwoFactorCoreConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.post(
                prefix + "/admin/config/auth/two-factor/generate-secret-key",
                this::generateTwoFactorSecretKey,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(
                prefix + "/admin/config/auth/two-factor/totp", this::getTotpConfig, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/auth/two-factor/totp",
                this::updateTotpConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(
                prefix + "/admin/config/auth/two-factor/backup-codes",
                this::getBackupCodesConfig,
                InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/auth/two-factor/backup-codes",
                this::updateBackupCodesConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/config/auth/webauthn", this::getWebAuthnConfig, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/auth/webauthn",
                this::updateWebAuthnConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/config/mailing", this::getMailingConfig, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/config/mailing/providers", this::getMailFallbacks, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/mailing/providers",
                this::updateMailFallbacks,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.post(
                prefix + "/admin/config/mailing/webhook-key",
                this::regenerateWebhookKey,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/config/mailing/dashboard", this::mailDashboard, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/config/mailing/stuck/requeue",
                this::requeueStuckMails,
                InstancePermission.ADMINISTRATOR);
        routes.delete(prefix + "/admin/config/mailing/blocks", this::liftMailBlock, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/monitoring/log", this::applicationLog, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/monitoring/log/facets", this::applicationLogFacets, InstancePermission.ADMINISTRATOR);
        routes.delete(prefix + "/admin/monitoring/log", this::clearApplicationLog, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/config/logging", this::getLoggingConfig, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/logging",
                this::updateLoggingConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.post(prefix + "/admin/config/mailing/test-mail", this::sendTestMail, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/config/mailing/providers/{position}/test",
                this::testMailProvider,
                InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/mailing",
                this::updateMailingConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.delete(
                prefix + "/admin/config/mailing",
                this::clearMailingConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/legal/placeholders", this::getLegalPlaceholders, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/legal/placeholders", this::updateLegalPlaceholders, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/legal/{type}", this::getLegalDocument, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/legal/{type}/locales", this::getLegalLocales, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/legal/{type}/{locale}",
                this::getLegalDocumentLocale,
                InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/legal/{type}/{locale}/files", this::getLegalFiles, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/legal/{type}/{locale}/templates",
                this::getLegalTemplates,
                InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/legal/{type}/{locale}/import",
                this::importLegalDocument,
                InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/legal/{type}/{locale}/files",
                this::saveLegalFiles,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.put(
                prefix + "/admin/legal/{type}",
                this::updateLegalDocument,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.put(
                prefix + "/admin/legal/{type}/{locale}",
                this::updateLegalDocumentLocale,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
    }

    private void initializeLogoFragments() {
        Map.Entry<String, String>[] resourceByApiName = new Map.Entry[] {
            Map.entry("fire_blank", "fire_blank"),
            Map.entry("fire_blink", "fire_blink_mid"),
            Map.entry("fire_blink_left", "fire_blink_left"),
            Map.entry("fire_blink_right", "fire_blink_right"),
            Map.entry("fire_blush", "fire_blush"),
            Map.entry("fire_eyes_left", "fire_eyes_left"),
            Map.entry("fire_eyes_left_half", "fire_eyes_left_half"),
            Map.entry("fire_eyes_mid", "fire_eyes_mid"),
            Map.entry("fire_eyes_mid_half", "fire_eyes_mid_half"),
            Map.entry("fire_eyes_right", "fire_eyes_right"),
            Map.entry("fire_eyes_right_half", "fire_eyes_right_half"),
            Map.entry("fire_faq", "fire_faq"),
            Map.entry("fire_glow", "fire_glow"),
            Map.entry("fire_woah_one", "fire_woah_one"),
            Map.entry("fire_woah_two", "fire_woah_two"),
        };
        for (var fragment : resourceByApiName) {
            storeLogoFragmentIfChanged(fragment.getKey(), "logo_fragments/" + fragment.getValue() + ".png");
        }
    }

    private void storeLogoFragmentIfChanged(String id, String resourcePath) {
        byte[] data = readResource(resourcePath);
        if (data == null) return;
        try {
            logoFragmentService.storeIfChanged(id, data, "image/png");
        } catch (Exception e) {
            log.warn("Failed to initialize logo fragment {}: {}", id, e.getMessage());
        }
    }

    private byte @Nullable [] readResource(String resourcePath) {
        try (var is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            return is.readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to read resource {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    @OpenApi(
            path = "/api/v1/public/logo-fragment/{name}",
            methods = HttpMethod.GET,
            summary = "One fragment of the animated logo as an image",
            tags = {"Settings"},
            pathParams = @OpenApiParam(name = "name", type = String.class, required = true),
            queryParams = @OpenApiParam(name = "size", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(type = "image/png")),
                @OpenApiResponse(status = "404")
            })
    @StationFree("the logo belongs to the instance and is served to anyone, station or not")
    private void serveLogoFragment(Context ctx) {
        String name = safeLogoName(ctx);
        if (name == null) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        logoFragmentService
                .read(name, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "public, max-age=86400");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    @OpenApi(
            path = "/api/v1/public/settings/station-registration",
            methods = HttpMethod.GET,
            summary = "Check if station registration is enabled (public)",
            tags = {"Settings"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationRegistrationStatus.class)))
    private void isRegistrationEnabled(Context ctx) {
        ctx.json(new StationRegistrationStatus(instanceSettings.stationRegistrationEnabled()));
    }

    @OpenApi(
            path = "/api/v1/public/settings/theme",
            methods = HttpMethod.GET,
            summary = "The instance's default look (public)",
            tags = {"Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicTheme.class)))
    private void getPublicTheme(Context ctx) {
        ctx.json(instanceSettings.publicTheme());
    }

    @OpenApi(
            path = "/api/v1/admin/settings",
            methods = HttpMethod.GET,
            summary = "Get application settings",
            tags = {"Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ApplicationSettings.class)))
    private void getSettings(Context ctx) {
        ctx.json(instanceSettings.settings());
    }

    @OpenApi(
            path = "/api/v1/admin/settings",
            methods = HttpMethod.PUT,
            summary = "Update application settings",
            tags = {"Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ApplicationSettings.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ApplicationSettings.class)))
    private void updateSettings(Context ctx) {
        ctx.json(instanceSettings.update(ctx.bodyAsClass(ApplicationSettings.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/tokens",
            methods = HttpMethod.GET,
            summary = "Get the token and session lifetimes",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TokensConfigResponse.class)))
    private void getTokensConfig(Context ctx) {
        ctx.json(securitySettings.tokens());
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/tokens",
            methods = HttpMethod.PUT,
            summary = "Update the token and session lifetimes",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TokensConfigRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TokensConfigResponse.class)))
    private void updateTokensConfig(Context ctx) {
        ctx.json(securitySettings.updateTokens(ctx.bodyAsClass(TokensConfigRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/tokens/generate-pepper",
            methods = HttpMethod.POST,
            summary = "Generate a new token pepper",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TokensConfigResponse.class)))
    private void generateTokenPepper(Context ctx) {
        ctx.json(securitySettings.generateTokenPepper());
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/hibp",
            methods = HttpMethod.GET,
            summary = "Get the breached password check settings",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = HibpConfigResponse.class)))
    private void getHibpConfig(Context ctx) {
        ctx.json(securitySettings.hibp());
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/hibp",
            methods = HttpMethod.PUT,
            summary = "Update the breached password check settings",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = HibpConfigRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = HibpConfigResponse.class)))
    private void updateHibpConfig(Context ctx) {
        ctx.json(securitySettings.updateHibp(ctx.bodyAsClass(HibpConfigRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/two-factor",
            methods = HttpMethod.GET,
            summary = "Get the two-factor settings",
            tags = {"Admin Settings"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = TwoFactorCoreConfigResponse.class)))
    private void getTwoFactorCoreConfig(Context ctx) {
        ctx.json(securitySettings.twoFactorCore());
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/two-factor",
            methods = HttpMethod.PUT,
            summary = "Update the two-factor settings",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TwoFactorCoreConfigRequest.class)),
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = TwoFactorCoreConfigResponse.class)))
    private void updateTwoFactorCoreConfig(Context ctx) {
        ctx.json(securitySettings.updateTwoFactorCore(ctx.bodyAsClass(TwoFactorCoreConfigRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/two-factor/generate-secret-key",
            methods = HttpMethod.POST,
            summary = "Generate a new key for the two-factor secrets",
            tags = {"Admin Settings"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = TwoFactorCoreConfigResponse.class)))
    private void generateTwoFactorSecretKey(Context ctx) {
        ctx.json(securitySettings.generateTwoFactorSecretKey());
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/two-factor/totp",
            methods = HttpMethod.GET,
            summary = "Get the authenticator app settings",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TotpConfig.class)))
    private void getTotpConfig(Context ctx) {
        ctx.json(securitySettings.totp());
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/two-factor/totp",
            methods = HttpMethod.PUT,
            summary = "Update the authenticator app settings",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TotpConfig.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TotpConfig.class)))
    private void updateTotpConfig(Context ctx) {
        ctx.json(securitySettings.updateTotp(ctx.bodyAsClass(TotpConfig.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/two-factor/backup-codes",
            methods = HttpMethod.GET,
            summary = "Get the backup code settings",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BackupCodesConfig.class)))
    private void getBackupCodesConfig(Context ctx) {
        ctx.json(securitySettings.backupCodes());
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/two-factor/backup-codes",
            methods = HttpMethod.PUT,
            summary = "Update the backup code settings",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BackupCodesConfig.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BackupCodesConfig.class)))
    private void updateBackupCodesConfig(Context ctx) {
        ctx.json(securitySettings.updateBackupCodes(ctx.bodyAsClass(BackupCodesConfig.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/webauthn",
            methods = HttpMethod.GET,
            summary = "Get the security key settings",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WebAuthnConfig.class)))
    private void getWebAuthnConfig(Context ctx) {
        ctx.json(securitySettings.webAuthn());
    }

    @OpenApi(
            path = "/api/v1/admin/config/auth/webauthn",
            methods = HttpMethod.PUT,
            summary = "Update the security key settings",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WebAuthnConfig.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WebAuthnConfig.class)))
    private void updateWebAuthnConfig(Context ctx) {
        ctx.json(securitySettings.updateWebAuthn(ctx.bodyAsClass(WebAuthnConfig.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/test-mail",
            methods = HttpMethod.POST,
            summary = "Send a test email to the signed-in account via the instance mail relay",
            tags = {"Admin Settings"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400")
            })
    private void sendTestMail(Context ctx) {
        if (!emailService.isGlobalMailConfigured()) {
            throw Refusal.INSTANCE_HAS_NO_MAIL_PROVIDER.raise();
        }
        UserSession session = UserSession.from(ctx);
        var account = session.account();
        emailService.sendTestEmail(
                account.email(), account.firstName(), mailLocaleService.forAccount(account.id()), null);
        ctx.json(new MessageResponse("Test email queued"));
    }

    /**
     * Tries one provider of the instance list, and sends a test mail through it when an address is
     * given. The address need not be the administrator's own: whether a relay delivers is often a
     * question about somebody else's mailbox.
     */
    @OpenApi(
            path = "/api/v1/admin/config/mailing/providers/{position}/test",
            methods = HttpMethod.POST,
            summary = "Send a test mail through one provider of the instance list",
            tags = {"Admin Settings"},
            pathParams = @OpenApiParam(name = "position", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ProviderTestRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailTestResponse.class)))
    private void testMailProvider(Context ctx) {
        int position;
        try {
            position = Integer.parseInt(ctx.pathParam("position"));
        } catch (NumberFormatException e) {
            throw Refusal.INSTANCE_MAIL_PROVIDER_POSITION_NOT_A_NUMBER.raise(ctx.pathParam("position"));
        }
        var body = ctx.body().isBlank() ? null : ctx.bodyAsClass(ProviderTestRequest.class);
        String recipient = body == null ? null : body.recipient();
        if (recipient == null || recipient.isBlank()) {
            ctx.json(new MailTestResponse(false, "No recipient given"));
            return;
        }
        var account = UserSession.from(ctx).account();
        String error = emailService.sendTestMailThrough(
                null,
                position,
                MailAddress.require(recipient),
                account.firstName(),
                mailLocaleService.forAccount(account.id()));
        ctx.json(new MailTestResponse(error == null, error));
    }

    /**
     * What has become of the instance's post: the queue, how each provider stands today, and what
     * the providers reported back about the mails they took.
     */
    @OpenApi(
            path = "/api/v1/admin/config/mailing/dashboard",
            methods = HttpMethod.GET,
            summary = "The state of the instance mail queue and its providers",
            tags = {"Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailDashboard.class)))
    private void mailDashboard(Context ctx) {
        ctx.json(dashboardService.forOwner(null));
    }

    /**
     * Puts mails a dead worker left in sending back into the queue, either one named mail or all
     * of them.
     */
    @OpenApi(
            path = "/api/v1/admin/config/mailing/stuck/requeue",
            methods = HttpMethod.POST,
            summary = "Queue left-behind instance mails for another attempt",
            tags = {"Settings"},
            queryParams =
                    @OpenApiParam(name = "id", type = Integer.class, description = "One mail, or all when absent"),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequeuedMails.class)))
    private void requeueStuckMails(Context ctx) {
        Integer id = ctx.queryParam("id") == null
                ? null
                : ctx.queryParamAsClass("id", Integer.class).get();
        ctx.json(dashboardService.requeueStuck(null, id));
    }

    /**
     * Lifts a block by hand, for when an operator knows the relay has been taken off the list and
     * does not want to wait out the week.
     */
    @OpenApi(
            path = "/api/v1/admin/config/mailing/blocks",
            methods = HttpMethod.DELETE,
            summary = "Lift a provider's block for a recipient domain",
            tags = {"Settings"},
            queryParams = {
                @OpenApiParam(name = "provider", type = String.class, required = true),
                @OpenApiParam(name = "domain", type = String.class)
            },
            responses = @OpenApiResponse(status = "204"))
    private void liftMailBlock(Context ctx) {
        var provider = MailProviderType.fromName(ctx.queryParam("provider"))
                .orElseThrow(Refusal.MAIL_PROVIDER_KIND_UNKNOWN::raise);
        dashboardService.liftBlock(null, provider, ctx.queryParam("domain"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The application log, newest first, narrowed by whatever the reader asked for.
     */
    @OpenApi(
            path = "/api/v1/admin/monitoring/log",
            methods = HttpMethod.GET,
            summary = "Read and search the application log",
            tags = {"Monitoring"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ApplicationLogPage.class)))
    private void applicationLog(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(200);
        ctx.json(applicationLog.page(logFilter(ctx), ctx.queryParam("before"), limit));
    }

    private static LogFilter logFilter(Context ctx) {
        return new LogFilter(
                ctx.queryParam("level"), ctx.queryParam("search"), ctx.queryParam("logger"), ctx.queryParam("thread"));
    }

    /**
     * The loggers or threads the current filter matches, searched by name.
     *
     * <p>Separate from the log itself so that typing in the list of loggers does not fetch the log
     * again, and so that one below the top of the list can still be reached.
     */
    @OpenApi(
            path = "/api/v1/admin/monitoring/log/facets",
            methods = HttpMethod.GET,
            summary = "Search the loggers or threads present in the log",
            tags = {"Monitoring"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LogFacet[].class)))
    private void applicationLogFacets(Context ctx) {
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(applicationLog.defaultFacetLimit());
        boolean threads = "thread".equalsIgnoreCase(ctx.queryParam("kind"));
        ctx.json(applicationLog.facets(logFilter(ctx), threads, ctx.queryParam("name"), limit));
    }

    /**
     * Empties the stored log, for when it holds something that should not be kept.
     */
    @OpenApi(
            path = "/api/v1/admin/monitoring/log",
            methods = HttpMethod.DELETE,
            summary = "Empty the stored application log",
            tags = {"Monitoring"},
            responses = @OpenApiResponse(status = "204"))
    private void clearApplicationLog(Context ctx) {
        applicationLog.clear();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/config/logging",
            methods = HttpMethod.GET,
            summary = "Get how much of the application log is stored",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoggingConfig.class)))
    private void getLoggingConfig(Context ctx) {
        ctx.json(applicationLog.config());
    }

    @OpenApi(
            path = "/api/v1/admin/config/logging",
            methods = HttpMethod.PUT,
            summary = "Update how much of the application log is stored",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LoggingConfigRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoggingConfig.class)))
    private void updateLoggingConfig(Context ctx) {
        ctx.json(applicationLog.updateConfig(ctx.bodyAsClass(LoggingConfigRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing",
            methods = HttpMethod.GET,
            summary = "Get the instance mail settings that belong to no one provider",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailingConfigResponse.class)))
    private void getMailingConfig(Context ctx) {
        ctx.json(mailSettings.mailing());
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/providers",
            methods = HttpMethod.GET,
            summary = "Get the instance's mail providers in the order they are tried",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailFallbackChain.class)))
    private void getMailFallbacks(Context ctx) {
        ctx.json(mailSettings.providers());
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/providers",
            methods = HttpMethod.PUT,
            summary = "Replace the instance's mail providers",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MailFallbackChain.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailFallbackChain.class)))
    private void updateMailFallbacks(Context ctx) {
        ctx.json(mailSettings.updateProviders(ctx.bodyAsClass(MailFallbackChain.class)));
    }

    /**
     * Replaces the instance webhook key, which takes the old address out of service at once. An
     * operator does this when the address has been seen by somebody it should not have been.
     */
    @OpenApi(
            path = "/api/v1/admin/config/mailing/webhook-key",
            methods = HttpMethod.POST,
            summary = "Replace the instance's delivery webhook key",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WebhookUrlResponse.class)))
    private void regenerateWebhookKey(Context ctx) {
        ctx.json(mailSettings.regenerateWebhookKey());
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing",
            methods = HttpMethod.PUT,
            summary = "Update the instance mail settings that belong to no one provider",
            tags = {"Admin Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MailingConfigRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailingConfigResponse.class)))
    private void updateMailingConfig(Context ctx) {
        ctx.json(mailSettings.updateMailing(ctx.bodyAsClass(MailingConfigRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing",
            methods = HttpMethod.DELETE,
            summary = "Reset the instance mail settings",
            tags = {"Admin Settings"},
            responses = @OpenApiResponse(status = "204"))
    private void clearMailingConfig(Context ctx) {
        mailSettings.clear();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/legal/{type}",
            methods = HttpMethod.GET,
            summary = "Get a legal document in the default language",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "type", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LegalDocumentResponse.class)))
    private void getLegalDocument(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        var doc = documentService.getDocument(dir, "de");
        ctx.json(new LegalDocumentResponse(type, doc.markdown(), doc.version()));
    }

    @OpenApi(
            path = "/api/v1/admin/legal/{type}/{locale}",
            methods = HttpMethod.GET,
            summary = "Get a legal document in one language",
            tags = {"Admin"},
            pathParams = {
                @OpenApiParam(name = "type", type = String.class, required = true),
                @OpenApiParam(name = "locale", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LegalDocumentResponse.class)))
    private void getLegalDocumentLocale(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        String locale = safeLocale(ctx, dir);
        var doc = documentService.getDocument(dir, locale);
        ctx.json(new LegalDocumentResponse(type, doc.markdown(), doc.version()));
    }

    @OpenApi(
            path = "/api/v1/admin/legal/{type}/locales",
            methods = HttpMethod.GET,
            summary = "List the languages a legal document is written in",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "type", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void getLegalLocales(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        List<String> locales = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && !FilePaths.nameOf(entry).equals("history")) {
                    locales.add(FilePaths.nameOf(entry));
                }
            }
        } catch (IOException e) {
            log.error("Failed to list locales for {}", type, e);
        }
        Collections.sort(locales);
        ctx.json(locales);
    }

    @OpenApi(
            path = "/api/v1/admin/legal/{type}",
            methods = HttpMethod.PUT,
            summary = "Replace a legal document in the default language",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "type", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LegalDocumentRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LegalDocumentResponse.class)))
    private void updateLegalDocument(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        updateLegalDocumentForLocale(ctx, type, legalDir(type), "de");
    }

    @OpenApi(
            path = "/api/v1/admin/legal/{type}/{locale}",
            methods = HttpMethod.PUT,
            summary = "Replace a legal document in one language",
            tags = {"Admin"},
            pathParams = {
                @OpenApiParam(name = "type", type = String.class, required = true),
                @OpenApiParam(name = "locale", type = String.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LegalDocumentRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LegalDocumentResponse.class)))
    private void updateLegalDocumentLocale(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        String locale = safeLocale(ctx, dir);
        updateLegalDocumentForLocale(ctx, type, dir, locale);
    }

    private void updateLegalDocumentForLocale(Context ctx, LegalDocumentType type, Path dir, String locale) {
        var request = ctx.bodyAsClass(LegalDocumentRequest.class);
        Path localeDir = resolveLocaleDir(dir, locale);
        try {
            Files.createDirectories(localeDir);
            Path file = localeDir.resolve("01-content.md");
            Files.writeString(file, request.content(), StandardCharsets.UTF_8);
            documentService.initialize(dir);
            var doc = documentService.getDocument(dir, locale);
            ctx.json(new LegalDocumentResponse(type, doc.markdown(), doc.version()));
        } catch (IOException e) {
            log.error("Failed to write legal document: {}/{}", type, locale, e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @OpenApi(
            path = "/api/v1/admin/legal/{type}/{locale}/files",
            methods = HttpMethod.GET,
            summary = "List the sections of a legal document in one language",
            tags = {"Admin"},
            pathParams = {
                @OpenApiParam(name = "type", type = String.class, required = true),
                @OpenApiParam(name = "locale", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LegalFileEntry[].class)))
    private void getLegalFiles(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        String locale = safeLocale(ctx, dir);
        ctx.json(readLegalFiles(resolveLocaleDir(dir, locale), locale));
    }

    /** A section's name without the disabled marker and order prefix: {@code _01-name.md} reads {@code name}. */
    private static String displayNameOf(String fileName) {
        return fileName.replaceFirst("^_?\\d+-", "").replaceFirst("\\.md$", "");
    }

    private List<LegalFileEntry> readLegalFiles(Path localeDir, String locale) {
        List<LegalFileEntry> files = new ArrayList<>();
        if (!Files.isDirectory(localeDir)) return files;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localeDir, "*.md")) {
            List<Path> sorted = new ArrayList<>();
            stream.forEach(sorted::add);
            Collections.sort(sorted);
            for (Path file : sorted) {
                String rawName = FilePaths.nameOf(file);
                boolean enabled = !rawName.startsWith("_");
                boolean generated = BrowserStorageService.isGeneratedSection(rawName);
                String content = generated
                        ? documentService.browserStorage().toMarkdown(locale)
                        : Files.readString(file, StandardCharsets.UTF_8);
                files.add(new LegalFileEntry(rawName, displayNameOf(rawName), content, enabled, generated));
            }
        } catch (IOException e) {
            log.error("Failed to list legal files in {}", localeDir, e);
        }
        return files;
    }

    @OpenApi(
            path = "/api/v1/admin/legal/placeholders",
            methods = HttpMethod.GET,
            summary = "List the placeholders used across the legal documents with their values",
            tags = {"Admin"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentPlaceholder[].class)))
    private void getLegalPlaceholders(Context ctx) {
        ctx.json(collectPlaceholders());
    }

    @OpenApi(
            path = "/api/v1/admin/legal/placeholders",
            methods = HttpMethod.PUT,
            summary = "Set the values of the placeholders used across the legal documents",
            tags = {"Admin"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PlaceholderValues.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentPlaceholder[].class)))
    private void updateLegalPlaceholders(Context ctx) {
        var request = ctx.bodyAsClass(PlaceholderValues.class);
        documentService.placeholders().save(request.values() == null ? Map.of() : request.values());
        for (LegalDocumentType type : LegalDocumentType.values()) {
            documentService.initialize(legalDir(type));
        }
        ctx.json(collectPlaceholders());
    }

    /**
     * Gathers every placeholder written into any legal document, merged across types and locales,
     * and pairs it with the value configured for it. A value whose placeholder has since been
     * removed from every document is listed too, without usages, so it can still be cleared.
     */
    private List<DocumentPlaceholder> collectPlaceholders() {
        var placeholders = documentService.placeholders();
        Map<String, List<DocumentPlaceholder.Usage>> usages = new TreeMap<>();
        for (LegalDocumentType type : LegalDocumentType.values()) {
            placeholders.scan(legalDir(type), type.slug()).forEach((name, found) -> usages.computeIfAbsent(
                            name, _ -> new ArrayList<>())
                    .addAll(found));
        }

        Map<String, String> values = placeholders.values();
        List<DocumentPlaceholder> result = new ArrayList<>();
        usages.forEach(
                (name, found) -> result.add(new DocumentPlaceholder(name, values.getOrDefault(name, ""), found)));
        values.forEach((name, value) -> {
            if (!usages.containsKey(name)) result.add(new DocumentPlaceholder(name, value, List.of()));
        });
        return result;
    }

    @OpenApi(
            path = "/api/v1/admin/legal/{type}/{locale}/templates",
            methods = HttpMethod.GET,
            summary = "List the sections Ember ships for a legal document",
            tags = {"Admin"},
            pathParams = {
                @OpenApiParam(name = "type", type = String.class, required = true),
                @OpenApiParam(name = "locale", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TemplateSection[].class)))
    private void getLegalTemplates(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        String locale = safeLocale(ctx, legalDir(type));
        ctx.json(DataInitializer.documentTemplates(type.slug(), locale));
    }

    /**
     * Reads an uploaded document and returns it as markdown, converting a word processor file the
     * same way the knowledge base does. Returns {@code null} when the request carries no file, so
     * the caller can fall back to markdown in the body.
     */
    private static @Nullable String uploadedMarkdown(Context ctx) {
        var file = ctx.uploadedFile("file");
        if (file == null) return null;
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            String format = importFormat(file.filename());
            if (format == null) return new String(data, StandardCharsets.UTF_8);
            return PandocConverter.toMarkdown(data, format);
        } catch (Exception e) {
            log.warn("Legal document conversion failed", e);
            throw Refusal.LEGAL_DOCUMENT_NOT_READ.raise();
        }
    }

    /**
     * The pandoc format of an uploaded file, or {@code null} when it is markdown or plain text
     * already and needs no conversion.
     */
    private static @Nullable String importFormat(String filename) {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "docx";
        if (lower.endsWith(".odt")) return "odt";
        if (lower.endsWith(".rtf")) return "rtf";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
        if (lower.endsWith(".epub")) return "epub";
        if (lower.endsWith(".tex") || lower.endsWith(".latex")) return "latex";
        return null;
    }

    @OpenApi(
            path = "/api/v1/admin/legal/{type}/{locale}/import",
            pathParams = {
                @OpenApiParam(name = "type", type = String.class, required = true),
                @OpenApiParam(name = "locale", type = String.class, required = true)
            },
            methods = HttpMethod.POST,
            summary = "Turn an externally written document into sections",
            description = "Splits a document into sections, takes the numbering out of its headings and rewrites the "
                    + "cross-references onto anchors. Nothing is written: the sections come back for the editor to "
                    + "review and save.",
            tags = {"Admin"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LegalImportRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LegalImportResponse.class)))
    private void importLegalDocument(Context ctx) {
        parseLegalType(ctx);
        String markdown = uploadedMarkdown(ctx);
        if (markdown == null) {
            var request = ctx.bodyAsClass(LegalImportRequest.class);
            markdown = request.markdown();
        }
        if (markdown == null || markdown.isBlank()) {
            throw Refusal.LEGAL_DOCUMENT_NEEDS_TEXT.raise();
        }
        var imported = LegalImportService.normalise(markdown);
        var files = imported.sections().stream()
                .map(section ->
                        new LegalFileEntry(section.fileName(), section.displayName(), section.content(), true, false))
                .toList();
        ctx.json(new LegalImportResponse(
                imported.title(), files, imported.references(), List.copyOf(imported.unmatched())));
    }

    @OpenApi(
            path = "/api/v1/admin/legal/{type}/{locale}/files",
            methods = HttpMethod.PUT,
            summary = "Replace the sections of a legal document in one language",
            tags = {"Admin"},
            pathParams = {
                @OpenApiParam(name = "type", type = String.class, required = true),
                @OpenApiParam(name = "locale", type = String.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LegalFileEntry[].class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LegalFileEntry[].class)))
    private void saveLegalFiles(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        String locale = safeLocale(ctx, dir);
        Path localeDir = resolveLocaleDir(dir, locale);
        var request = ctx.bodyAsClass(LegalFileEntry[].class);
        try {
            Files.createDirectories(localeDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(localeDir, "*.md")) {
                for (Path old : stream) {
                    Files.delete(old);
                }
            }
            for (int i = 0; i < request.length; i++) {
                var entry = request[i];
                String prefix = String.format("%02d", i + 1);
                boolean generated = entry.generated() || BrowserStorageService.SECTION_NAME.equals(entry.displayName());
                String safeName = generated
                        ? BrowserStorageService.SECTION_NAME
                        : entry.displayName().replaceAll("[^a-zA-Z0-9_-]", "-");
                String filename = (entry.enabled() ? "" : "_") + prefix + "-" + safeName + ".md";
                Files.writeString(
                        localeDir.resolve(filename), generated ? "" : entry.content(), StandardCharsets.UTF_8);
            }
            if (type == LegalDocumentType.PRIVACY || type == LegalDocumentType.CONSENT) {
                documentService.ensureGeneratedSection(dir);
            }
            documentService.initialize(dir);
            ctx.json(readLegalFiles(localeDir, locale));
        } catch (IOException e) {
            log.error("Failed to save legal files for {}/{}", type, locale, e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Path legalDir(LegalDocumentType type) {
        var api = conf.main().api();
        return switch (type) {
            case PRIVACY -> Path.of(api.privacyPolicyDir());
            case TOS -> Path.of(api.tosDir());
            case CONSENT -> Path.of(api.consentDir());
            case IMPRINT -> Path.of(api.imprintDir());
        };
    }

    private LegalDocumentType parseLegalType(Context ctx) {
        try {
            return LegalDocumentType.fromSlug(ctx.pathParam("type"));
        } catch (IllegalArgumentException e) {
            throw Refusal.LEGAL_DOCUMENT_KIND_UNKNOWN.raise(ctx.pathParam("type"));
        }
    }

    public record StationRegistrationStatus(boolean enabled) {}

    public record LegalDocumentResponse(LegalDocumentType type, String content, String version) {}

    public record LegalDocumentRequest(String content) {}

    /**
     * One section of a legal document. A {@code generated} section is rendered by the
     * application rather than written by an administrator: its content is read-only and
     * only its position and its enabled state can be changed.
     */
    public record LegalFileEntry(
            String filename, String displayName, String content, boolean enabled, boolean generated) {}

    /**
     * A document written elsewhere, as markdown. A word processor file is converted to markdown
     * before it gets here, the same way the knowledge base takes one.
     *
     * @param markdown the document to normalise
     */
    public record LegalImportRequest(String markdown) {}

    /**
     * What an import made of the document.
     *
     * @param title      the document title, if it carried one
     * @param files      the sections, ready to be reviewed and saved
     * @param references how many numbers became references
     * @param unmatched  numbers that look like a reference but point at no section of this document
     */
    public record LegalImportResponse(
            @Nullable String title, List<LegalFileEntry> files, int references, List<String> unmatched) {}

    /**
     * The values an administrator gives the placeholders used across the legal documents.
     *
     * @param values placeholder name to replacement; an entry left empty clears the value
     */
    public record PlaceholderValues(Map<String, String> values) {}
}
