/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.HibpSettings;
import dev.chojo.ember.conf.file.elements.Logging;
import dev.chojo.ember.conf.file.elements.MailProviderEntry;
import dev.chojo.ember.conf.file.elements.MailSettings;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.conf.file.elements.Theming;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.legal.entity.DocumentPlaceholder;
import dev.chojo.ember.feature.legal.entity.LegalDocumentType;
import dev.chojo.ember.feature.legal.service.BrowserStorageService;
import dev.chojo.ember.feature.legal.service.LegalDocumentService;
import dev.chojo.ember.feature.legal.service.LegalImportService;
import dev.chojo.ember.feature.mail.route.MailFallbackPayload;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailDashboardService;
import dev.chojo.ember.feature.mail.service.MailDashboardService.MailDashboard;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailTemplateRenderer;
import dev.chojo.ember.feature.media.service.LogoFragmentService;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.feature.system.service.DataInitializer;
import dev.chojo.ember.feature.system.service.DatabaseLogAppender;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService;
import dev.chojo.ember.util.MailAddress;
import dev.chojo.ember.util.PandocConverter;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Singleton
public class AdminSettingsRoutes implements Routes {
    private static final String STATION_REGISTRATION_ENABLED = "station_registration_enabled";
    private static final String FORCE_PRIDE_FLAG = "force_pride_flag";

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

    private static final Set<String> TOTP_ALGORITHMS = Set.of("SHA1", "SHA256", "SHA512");
    private static final Set<String> WEBAUTHN_ATTESTATIONS = Set.of("none", "indirect", "direct");
    private final ApplicationSettingRepository settingRepository;
    private final LogoFragmentService logoFragmentService;
    private final Conf conf;
    private final EmailService emailService;
    private final AccountRepository accountRepository;
    private final MailLocaleService mailLocaleService;
    private final WebhookKeyService webhookKeyService;
    private final MailDashboardService dashboardService;
    private final ApplicationLogRepository logRepository;
    private final LegalDocumentService documentService;

    @Inject
    public AdminSettingsRoutes(
            ApplicationSettingRepository settingRepository,
            LogoFragmentService logoFragmentService,
            Conf conf,
            EmailService emailService,
            AccountRepository accountRepository,
            MailLocaleService mailLocaleService,
            WebhookKeyService webhookKeyService,
            MailDashboardService dashboardService,
            ApplicationLogRepository logRepository) {
        this.dashboardService = dashboardService;
        this.logRepository = logRepository;
        this.settingRepository = settingRepository;
        this.logoFragmentService = logoFragmentService;
        this.conf = conf;
        this.emailService = emailService;
        this.accountRepository = accountRepository;
        this.mailLocaleService = mailLocaleService;
        this.webhookKeyService = webhookKeyService;
        this.documentService = new LegalDocumentService(conf.main().api().placeholderFile());
        initializeLogoFragments();
    }

    /**
     * Parses the {@code locale} path parameter, validates it against
     * {@link #SAFE_LOCALE}, and checks that the resolved directory stays inside
     * {@code base}. Throws {@link BadRequestResponse} on any
     * mismatch so the route returns 400 with a static, user-safe message.
     */
    private static String safeLocale(Context ctx, Path base) {
        String locale = ctx.pathParam("locale");
        if (locale == null || !SAFE_LOCALE.matcher(locale).matches()) {
            throw new BadRequestResponse("Invalid locale");
        }
        Path resolved = base.resolve(locale).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw new BadRequestResponse("Invalid locale");
        }
        return locale;
    }

    private static Path resolveLocaleDir(Path base, String locale) {
        // safeLocale has already validated the value, but re-check the resolved
        // path so a future caller that forgets the validation gate is still
        // caught here rather than escaping the legal directory.
        Path resolved = base.resolve(locale).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw new BadRequestResponse("Invalid locale");
        }
        return resolved;
    }

    private static String safeLogoName(Context ctx) {
        String name = ctx.pathParam("name");
        if (name == null) return null;
        if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
        return SAFE_LOGO_NAME.matcher(name).matches() ? name : null;
    }

    private static void requireRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new BadRequestResponse(field + " must be between " + min + " and " + max);
        }
    }

    private static void setField(Class<?> clazz, Object target, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
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
        routes.get(
                prefix + "/admin/config/auth/two-factor/webauthn",
                this::getWebAuthnConfig,
                InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/config/auth/two-factor/webauthn",
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
        routes.get(prefix + "/admin/monitoring/log", this::applicationLog, InstancePermission.ADMINISTRATOR);
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
        // Map from API name (used by frontend) to resource filename
        Map.Entry<String, String>[] fragments = new Map.Entry[] {
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
        for (var fragment : fragments) {
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

    private byte[] readResource(String resourcePath) {
        try (var is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) return null;
            return is.readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to read resource {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

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

    // -- Security config: tokens & sessions --

    @OpenApi(
            path = "/api/v1/public/settings/station-registration",
            methods = HttpMethod.GET,
            summary = "Check if station registration is enabled (public)",
            tags = {"Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RegistrationStatus.class)))
    private void isRegistrationEnabled(Context ctx) {
        boolean enabled = settingRepository.getBoolean(STATION_REGISTRATION_ENABLED, true);
        ctx.json(new RegistrationStatus(enabled));
    }

    @OpenApi(
            path = "/api/v1/admin/settings",
            methods = HttpMethod.GET,
            summary = "Get application settings",
            tags = {"Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ApplicationSettings.class)))
    private void getPublicTheme(Context ctx) {
        var theming = conf.main().theming();
        boolean forcePride = settingRepository.getBoolean(FORCE_PRIDE_FLAG, false);
        ctx.json(new PublicThemeResponse(
                theming.defaultTheme(), theming.defaultFeel().name(), theming.lockFeel(), forcePride));
    }

    private void getSettings(Context ctx) {
        boolean registrationEnabled = settingRepository.getBoolean(STATION_REGISTRATION_ENABLED, true);
        boolean forcePride = settingRepository.getBoolean(FORCE_PRIDE_FLAG, false);
        var theming = conf.main().theming();
        ctx.json(new ApplicationSettings(
                registrationEnabled,
                theming.defaultTheme(),
                theming.defaultFeel().name(),
                theming.lockFeel(),
                forcePride,
                settingRepository.defaultMailLocale(),
                availableMailLocales()));
    }

    /**
     * The languages this instance can actually write a mail in - one per directory of mail
     * templates. Offering anything else would let an administrator pick a language that silently
     * falls back to English on the first mail sent.
     */
    private static List<String> availableMailLocales() {
        File[] directories =
                Path.of(MailTemplateRenderer.TEMPLATE_ROOT).toFile().listFiles(File::isDirectory);
        if (directories == null) return List.of("en");
        return Arrays.stream(directories).map(File::getName).sorted().toList();
    }

    @OpenApi(
            path = "/api/v1/admin/settings",
            methods = HttpMethod.PUT,
            summary = "Update application settings",
            tags = {"Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ApplicationSettings.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ApplicationSettings.class)))
    private void updateSettings(Context ctx) {
        var request = ctx.bodyAsClass(ApplicationSettings.class);
        settingRepository.setBoolean(STATION_REGISTRATION_ENABLED, request.stationRegistrationEnabled());
        settingRepository.setBoolean(FORCE_PRIDE_FLAG, request.forcePrideFlag());
        if (request.defaultMailLocale() != null && !request.defaultMailLocale().isBlank()) {
            if (!availableMailLocales().contains(request.defaultMailLocale())) {
                throw new BadRequestResponse("No mail templates exist for " + request.defaultMailLocale());
            }
            settingRepository.set(ApplicationSettingRepository.DEFAULT_MAIL_LOCALE, request.defaultMailLocale());
        }
        try {
            var theming = conf.main().theming();
            if (request.instanceDefaultTheme() != null) {
                setField(Theming.class, theming, "defaultTheme", request.instanceDefaultTheme());
            }
            if (request.instanceDefaultFeel() != null) {
                setField(Theming.class, theming, "defaultFeel", ThemeFeel.valueOf(request.instanceDefaultFeel()));
            }
            setField(Theming.class, theming, "lockFeel", request.instanceLockFeel());
            conf.save();
        } catch (Exception e) {
            log.error("Failed to update instance settings", e);
        }
        var theming = conf.main().theming();
        ctx.json(new ApplicationSettings(
                request.stationRegistrationEnabled(),
                theming.defaultTheme(),
                theming.defaultFeel().name(),
                theming.lockFeel(),
                request.forcePrideFlag(),
                settingRepository.defaultMailLocale(),
                availableMailLocales()));
    }

    // -- Security config: HIBP --

    private void getTokensConfig(Context ctx) {
        var auth = conf.main().auth();
        ctx.json(buildTokensResponse(auth));
    }

    private void updateTokensConfig(Context ctx) {
        var request = ctx.bodyAsClass(TokensConfigRequest.class);
        requireRange(request.tokenBytes(), 16, 256, "tokenBytes");
        requireRange(request.verifyTokenHours(), 1, 720, "verifyTokenHours");
        requireRange(request.passwordTokenHours(), 1, 720, "passwordTokenHours");
        requireRange(request.sessionMinutes(), 5, 43200, "sessionMinutes");
        var auth = conf.main().auth();
        try {
            setField(Auth.class, auth, "tokenBytes", request.tokenBytes());
            setField(Auth.class, auth, "verifyTokenHours", request.verifyTokenHours());
            setField(Auth.class, auth, "passwordTokenHours", request.passwordTokenHours());
            setField(Auth.class, auth, "sessionMinutes", request.sessionMinutes());
            conf.save();
            ctx.json(buildTokensResponse(auth));
        } catch (Exception e) {
            log.error("Failed to update tokens config", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void generateTokenPepper(Context ctx) {
        var auth = conf.main().auth();
        if (auth.tokenPepper() != null && !auth.tokenPepper().isBlank()) {
            throw new BadRequestResponse("tokenPepper is already configured");
        }
        byte[] random = new byte[48];
        new SecureRandom().nextBytes(random);
        String pepper = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        try {
            setField(Auth.class, auth, "tokenPepper", pepper);
            conf.save();
            ctx.json(buildTokensResponse(auth));
        } catch (Exception e) {
            log.error("Failed to generate token pepper", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // -- Security config: 2FA core --

    private TokensConfigResponse buildTokensResponse(Auth auth) {
        return new TokensConfigResponse(
                auth.tokenBytes(),
                auth.verifyTokenHours(),
                auth.passwordTokenHours(),
                auth.sessionMinutes(),
                auth.tokenPepper() != null && !auth.tokenPepper().isBlank());
    }

    private void getHibpConfig(Context ctx) {
        var hibp = conf.main().auth().hibp();
        ctx.json(buildHibpResponse(hibp));
    }

    private void updateHibpConfig(Context ctx) {
        var request = ctx.bodyAsClass(HibpConfigRequest.class);
        requireRange(request.staleAfterDays(), 1, 365, "staleAfterDays");
        requireRange(request.timeoutSeconds(), 1, 30, "timeoutSeconds");
        if (request.endpoint() == null || request.endpoint().isBlank()) {
            throw new BadRequestResponse("endpoint is required");
        }
        var hibp = conf.main().auth().hibp();
        try {
            setField(HibpSettings.class, hibp, "enabled", request.enabled());
            setField(HibpSettings.class, hibp, "endpoint", request.endpoint());
            setField(HibpSettings.class, hibp, "staleAfterDays", request.staleAfterDays());
            setField(HibpSettings.class, hibp, "timeoutSeconds", request.timeoutSeconds());
            conf.save();
            ctx.json(buildHibpResponse(hibp));
        } catch (Exception e) {
            log.error("Failed to update HIBP config", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private HibpConfigResponse buildHibpResponse(HibpSettings hibp) {
        return new HibpConfigResponse(hibp.enabled(), hibp.endpoint(), hibp.staleAfterDays(), hibp.timeoutSeconds());
    }

    // -- Security config: TOTP --

    private void getTwoFactorCoreConfig(Context ctx) {
        var twoFactor = conf.main().auth().twoFactor();
        ctx.json(buildTwoFactorCoreResponse(twoFactor));
    }

    private void updateTwoFactorCoreConfig(Context ctx) {
        var request = ctx.bodyAsClass(TwoFactorCoreConfigRequest.class);
        requireRange(request.stepUpFreshnessSeconds(), 60, 3600, "stepUpFreshnessSeconds");
        requireRange(request.trustedDeviceMaxDays(), 1, 30, "trustedDeviceMaxDays");
        requireRange(request.enrollmentGraceDays(), 1, 7, "enrollmentGraceDays");
        var twoFactor = conf.main().auth().twoFactor();
        try {
            setField(TwoFactorSettings.class, twoFactor, "enabled", request.enabled());
            setField(TwoFactorSettings.class, twoFactor, "stepUpFreshnessSeconds", request.stepUpFreshnessSeconds());
            setField(TwoFactorSettings.class, twoFactor, "trustedDeviceMaxDays", request.trustedDeviceMaxDays());
            setField(TwoFactorSettings.class, twoFactor, "enrollmentGraceDays", request.enrollmentGraceDays());
            conf.save();
            ctx.json(buildTwoFactorCoreResponse(twoFactor));
        } catch (Exception e) {
            log.error("Failed to update 2FA core config", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void generateTwoFactorSecretKey(Context ctx) {
        var twoFactor = conf.main().auth().twoFactor();
        if (twoFactor.secretKey() != null && !twoFactor.secretKey().isBlank()) {
            throw new BadRequestResponse("twoFactor.secretKey is already configured");
        }
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String key = Base64.getEncoder().encodeToString(random);
        try {
            setField(TwoFactorSettings.class, twoFactor, "secretKey", key);
            conf.save();
            ctx.json(buildTwoFactorCoreResponse(twoFactor));
        } catch (Exception e) {
            log.error("Failed to generate 2FA secret key", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // -- Security config: backup codes --

    private TwoFactorCoreConfigResponse buildTwoFactorCoreResponse(TwoFactorSettings twoFactor) {
        return new TwoFactorCoreConfigResponse(
                twoFactor.enabled(),
                twoFactor.stepUpFreshnessSeconds(),
                twoFactor.trustedDeviceMaxDays(),
                twoFactor.enrollmentGraceDays(),
                twoFactor.secretKey() != null && !twoFactor.secretKey().isBlank());
    }

    private void getTotpConfig(Context ctx) {
        var totp = conf.main().auth().twoFactor().totp();
        ctx.json(buildTotpResponse(totp));
    }

    // -- Security config: WebAuthn --

    private void updateTotpConfig(Context ctx) {
        var request = ctx.bodyAsClass(TotpConfigRequest.class);
        requireRange(request.digits(), 4, 8, "digits");
        requireRange(request.periodSeconds(), 15, 60, "periodSeconds");
        requireRange(request.driftWindow(), 0, 3, "driftWindow");
        if (request.issuer() == null || request.issuer().isBlank()) {
            throw new BadRequestResponse("issuer is required");
        }
        String algorithm =
                request.algorithm() == null ? "" : request.algorithm().toUpperCase(Locale.ROOT);
        if (!TOTP_ALGORITHMS.contains(algorithm)) {
            throw new BadRequestResponse("algorithm must be one of SHA1, SHA256, SHA512");
        }
        var totp = conf.main().auth().twoFactor().totp();
        try {
            setField(TwoFactorSettings.TotpConfig.class, totp, "digits", request.digits());
            setField(TwoFactorSettings.TotpConfig.class, totp, "periodSeconds", request.periodSeconds());
            setField(TwoFactorSettings.TotpConfig.class, totp, "algorithm", algorithm);
            setField(TwoFactorSettings.TotpConfig.class, totp, "driftWindow", request.driftWindow());
            setField(TwoFactorSettings.TotpConfig.class, totp, "issuer", request.issuer());
            conf.save();
            ctx.json(buildTotpResponse(totp));
        } catch (Exception e) {
            log.error("Failed to update TOTP config", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private TotpConfigResponse buildTotpResponse(TwoFactorSettings.TotpConfig totp) {
        return new TotpConfigResponse(
                totp.digits(), totp.periodSeconds(), totp.algorithm(), totp.driftWindow(), totp.issuer());
    }

    private void getBackupCodesConfig(Context ctx) {
        var backup = conf.main().auth().twoFactor().backupCodes();
        ctx.json(new BackupCodesConfigResponse(backup.count()));
    }

    private void updateBackupCodesConfig(Context ctx) {
        var request = ctx.bodyAsClass(BackupCodesConfigRequest.class);
        requireRange(request.count(), 5, 20, "count");
        var backup = conf.main().auth().twoFactor().backupCodes();
        try {
            setField(TwoFactorSettings.BackupCodesConfig.class, backup, "count", request.count());
            conf.save();
            ctx.json(new BackupCodesConfigResponse(backup.count()));
        } catch (Exception e) {
            log.error("Failed to update backup codes config", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void getWebAuthnConfig(Context ctx) {
        var webauthn = conf.main().auth().twoFactor().webauthn();
        ctx.json(buildWebAuthnResponse(webauthn));
    }

    private void updateWebAuthnConfig(Context ctx) {
        var request = ctx.bodyAsClass(WebAuthnConfigRequest.class);
        requireRange(request.timeoutSeconds(), 10, 300, "timeoutSeconds");
        String attestation =
                request.attestation() == null ? "" : request.attestation().toLowerCase(Locale.ROOT);
        if (!WEBAUTHN_ATTESTATIONS.contains(attestation)) {
            throw new BadRequestResponse("attestation must be one of none, indirect, direct");
        }
        var webauthn = conf.main().auth().twoFactor().webauthn();
        try {
            setField(
                    TwoFactorSettings.WebAuthnConfig.class,
                    webauthn,
                    "rpId",
                    request.rpId() == null ? "" : request.rpId());
            setField(
                    TwoFactorSettings.WebAuthnConfig.class,
                    webauthn,
                    "rpName",
                    request.rpName() == null ? "" : request.rpName());
            setField(TwoFactorSettings.WebAuthnConfig.class, webauthn, "attestation", attestation);
            setField(TwoFactorSettings.WebAuthnConfig.class, webauthn, "timeoutSeconds", request.timeoutSeconds());
            setField(
                    TwoFactorSettings.WebAuthnConfig.class,
                    webauthn,
                    "requireResidentKey",
                    request.requireResidentKey());
            conf.save();
            ctx.json(buildWebAuthnResponse(webauthn));
        } catch (Exception e) {
            log.error("Failed to update WebAuthn config", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // -- Mailing config --

    private WebAuthnConfigResponse buildWebAuthnResponse(TwoFactorSettings.WebAuthnConfig webauthn) {
        return new WebAuthnConfigResponse(
                webauthn.rpId(),
                webauthn.rpName(),
                webauthn.attestation(),
                webauthn.timeoutSeconds(),
                webauthn.requireResidentKey());
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
            throw new BadRequestResponse("No mail provider configured");
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
    private void testMailProvider(Context ctx) {
        int position;
        try {
            position = Integer.parseInt(ctx.pathParam("position"));
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("Invalid provider position: " + ctx.pathParam("position"));
        }
        var body = ctx.body().isBlank() ? null : ctx.bodyAsClass(ProviderTestRequest.class);
        String recipient = body == null ? null : body.recipient();
        if (recipient == null || recipient.isBlank()) {
            ctx.json(new MailTestResult(false, "No recipient given"));
            return;
        }
        var account = UserSession.from(ctx).account();
        String error = emailService.sendTestMailThrough(
                null,
                position,
                MailAddress.require(recipient),
                account.firstName(),
                mailLocaleService.forAccount(account.id()));
        ctx.json(new MailTestResult(error == null, error));
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
     * The application log, newest first, narrowed by whatever the reader asked for.
     *
     * <p>Only what the operator chose to keep in the database is here. The console and the file
     * always hold everything, which is what makes this safe to switch off.
     */
    @OpenApi(
            path = "/api/v1/admin/monitoring/log",
            methods = HttpMethod.GET,
            summary = "Read and search the application log",
            tags = {"Monitoring"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ApplicationLogPage.class)))
    private void applicationLog(Context ctx) {
        List<String> levels = Arrays.stream(ctx.queryParamAsClass("level", String.class)
                        .getOrDefault("")
                        .split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(LOG_LEVELS::contains)
                .toList();
        String search = ctx.queryParam("search");
        Long before = parseLongOrNull(ctx.queryParam("before"));
        int limit = Math.clamp(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(200), 1, 500);
        var entries = logRepository.search(levels, search, before, limit);
        ctx.json(new ApplicationLogPage(
                entries,
                conf.main().logging().databaseEnabled(),
                conf.main().logging().databaseLevel(),
                conf.main().logging().retentionDays(),
                DatabaseLogAppender.dropped()));
    }

    /**
     * Empties the stored log, for when it holds something that should not be kept.
     */
    private void clearApplicationLog(Context ctx) {
        logRepository.clear();
        log.info("The stored application log was cleared by an administrator");
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void getLoggingConfig(Context ctx) {
        var logging = conf.main().logging();
        ctx.json(new LoggingConfig(
                logging.databaseEnabled(), logging.databaseLevel(), logging.retentionDays(), logRepository.size()));
    }

    private void updateLoggingConfig(Context ctx) {
        var request = ctx.bodyAsClass(LoggingConfigRequest.class);
        String level = request.databaseLevel() == null
                ? "DEBUG"
                : request.databaseLevel().toUpperCase(Locale.ROOT);
        if (!LOG_LEVELS.contains(level)) {
            throw new BadRequestResponse("Unknown log level: " + request.databaseLevel());
        }
        requireRange(request.retentionDays(), 1, 3650, "retentionDays");
        var logging = conf.main().logging();
        try {
            setField(Logging.class, logging, "databaseEnabled", request.databaseEnabled());
            setField(Logging.class, logging, "databaseLevel", level);
            setField(Logging.class, logging, "retentionDays", request.retentionDays());
            conf.save();
        } catch (Exception e) {
            log.error("Failed to update the logging configuration", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }
        getLoggingConfig(ctx);
    }

    /** The severities a client may ask for, so an unknown one is refused rather than ignored. */
    private static final Set<String> LOG_LEVELS = Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR");

    /**
     * Reads the paging cursor. An unreadable one starts at the top rather than refusing: the cursor
     * is an optimisation for reading further back, not something worth an error.
     */
    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A page of the log, with what the reader needs to make sense of a short one.
     *
     * @param entries       the lines, newest first
     * @param databaseEnabled whether anything is being stored at all
     * @param databaseLevel the lowest severity being stored
     * @param retentionDays how long lines are kept
     * @param dropped       how many lines were dropped since start because the queue was full,
     *                      which is what says the log is incomplete rather than quiet
     */
    public record ApplicationLogPage(
            List<ApplicationLogRepository.LogEntry> entries,
            boolean databaseEnabled,
            String databaseLevel,
            int retentionDays,
            long dropped) {}

    /**
     * @param storedLines how many lines are stored, so an operator can see what a retention change
     *                    would act on
     */
    public record LoggingConfig(boolean databaseEnabled, String databaseLevel, int retentionDays, int storedLines) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoggingConfigRequest(boolean databaseEnabled, String databaseLevel, int retentionDays) {}

    /** Where a test mail should go. */
    public record ProviderTestRequest(String recipient) {}

    /** Whether the provider took the message, and what it said when it did not. */
    public record MailTestResult(boolean success, String error) {}

    private void getMailingConfig(Context ctx) {
        ctx.json(new MailingConfigResponse(conf.main().mailing().notificationDigestIntervalMinutes()));
    }

    /**
     * The providers the instance falls back to, after the one configured on the mailing page.
     */
    private void getMailFallbacks(Context ctx) {
        var mailing = conf.main().mailing();
        ctx.json(new MailFallbackChain(
                Math.max(1, mailing.attempts()),
                mailing.providers().stream()
                        .map(fallback -> new MailFallbackPayload(
                                        fallback.provider(),
                                        fallback.host(),
                                        fallback.port(),
                                        fallback.ssl(),
                                        fallback.user(),
                                        fallback.password(),
                                        fallback.apiKey(),
                                        fallback.senderAddress(),
                                        fallback.senderName(),
                                        fallback.attempts(),
                                        fallback.dailySendLimit(),
                                        "",
                                        "",
                                        null)
                                .masked()
                                .withWebhookUrl(webhookKeyService.webhookUrl(
                                        conf.main().api().baseUrl(),
                                        null,
                                        fallback.provider().webhookPath())))
                        .toList()));
    }

    /**
     * Replaces the order the instance falls back through.
     *
     * <p>Written as a whole rather than entry by entry, because the order is the point: a
     * half-applied chain would send mail through a route nobody asked for.
     */
    private void updateMailFallbacks(Context ctx) {
        var request = ctx.bodyAsClass(MailFallbackChain.class);
        var mailing = conf.main().mailing();
        var stored = mailing.providers();
        List<MailProviderEntry> next = new ArrayList<>();
        var entries = request.fallbacks() == null ? List.<MailFallbackPayload>of() : request.fallbacks();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            if (entry.provider() == null || entry.provider() == MailProviderType.NONE) continue;
            var previous = i < stored.size() ? stored.get(i) : null;
            next.add(new MailProviderEntry(
                    entry.provider(),
                    entry.smtpHost(),
                    entry.smtpPort(),
                    entry.smtpSsl(),
                    entry.smtpUser(),
                    MailFallbackPayload.keepOrReplace(
                            entry.smtpPassword(), previous == null ? "" : previous.password()),
                    MailFallbackPayload.keepOrReplace(entry.apiKey(), previous == null ? "" : previous.apiKey()),
                    entry.senderAddress(),
                    entry.senderName(),
                    Math.max(1, entry.attempts()),
                    Math.max(0, entry.dailySendLimit())));
        }
        // Emptying the list is what the delete route is for. A save that arrives empty is far more
        // often a client that failed to load it than an operator meaning to stop sending, and the
        // difference is not recoverable: the fields the first provider used to live in go with it.
        if (next.isEmpty() && !stored.isEmpty()) {
            throw new BadRequestResponse("Refusing to replace the provider list with an empty one");
        }
        try {
            setField(Mailing.class, mailing, "providers", next);
            setField(Mailing.class, mailing, "fallbacks", List.<MailProviderEntry>of());
            conf.save();
        } catch (Exception e) {
            log.error("Failed to update the mail provider list", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            return;
        }
        getMailFallbacks(ctx);
    }

    /**
     * @param attempts  how many attempts the first provider gets before the chain moves on
     * @param fallbacks the providers after it, in the order they are tried
     */
    public record MailFallbackChain(int attempts, List<MailFallbackPayload> fallbacks) {}

    /**
     * Replaces the instance webhook key, which takes the old address out of service at once. An
     * operator does this when the address has been seen by somebody it should not have been.
     */
    private void regenerateWebhookKey(Context ctx) {
        webhookKeyService.regenerate(null);
        ctx.json(new WebhookUrlResponse(
                webhookKeyService.webhookUrl(conf.main().api().baseUrl(), null, "mail/brevo")));
    }

    /**
     * @param deliveryWebhookUrl the freshly minted address
     */
    public record WebhookUrlResponse(String deliveryWebhookUrl) {}

    // -- Legal documents --

    private void updateMailingConfig(Context ctx) {
        var request = ctx.bodyAsClass(MailingConfigRequest.class);
        var mailing = conf.main().mailing();
        try {
            setField(
                    Mailing.class,
                    mailing,
                    "notificationDigestIntervalMinutes",
                    request.notificationDigestIntervalMinutes());
            conf.save();
            ctx.json(new MailingConfigResponse(mailing.notificationDigestIntervalMinutes()));
        } catch (Exception e) {
            log.error("Failed to update mailing config", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Empties the instance list, which is what stopping to send means now that the providers are
     * one list. The fields the first provider used to live in are cleared with it, so an instance
     * that has never been saved since does not fall back to them.
     */
    private void clearMailingConfig(Context ctx) {
        var mailing = conf.main().mailing();
        var smtp = mailing.smtp();
        try {
            setField(Mailing.class, mailing, "providers", List.<MailProviderEntry>of());
            setField(Mailing.class, mailing, "fallbacks", List.<MailProviderEntry>of());
            setField(Mailing.class, mailing, "provider", MailProviderType.NONE);
            setField(Mailing.class, mailing, "senderAddress", "");
            setField(Mailing.class, mailing, "user", "");
            setField(Mailing.class, mailing, "password", "");
            setField(Mailing.class, mailing, "apiKey", "");
            setField(MailSettings.class, smtp, "host", "");
            conf.save();
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            log.error("Failed to clear mailing config", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void getLegalDocument(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        var doc = documentService.getDocument(dir, "de");
        ctx.json(new LegalDocumentResponse(type, doc.markdown(), doc.version()));
    }

    private void getLegalDocumentLocale(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        String locale = safeLocale(ctx, dir);
        var doc = documentService.getDocument(dir, locale);
        ctx.json(new LegalDocumentResponse(type, doc.markdown(), doc.version()));
    }

    private void getLegalLocales(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        List<String> locales = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry) && !entry.getFileName().toString().equals("history")) {
                    locales.add(entry.getFileName().toString());
                }
            }
        } catch (IOException e) {
            log.error("Failed to list locales for {}", type, e);
        }
        Collections.sort(locales);
        ctx.json(locales);
    }

    private void updateLegalDocument(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        updateLegalDocumentForLocale(ctx, type, legalDir(type), "de");
    }

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

    private void getLegalFiles(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        String locale = safeLocale(ctx, dir);
        ctx.json(readLegalFiles(resolveLocaleDir(dir, locale), locale));
    }

    private List<LegalFileEntry> readLegalFiles(Path localeDir, String locale) {
        List<LegalFileEntry> files = new ArrayList<>();
        if (!Files.isDirectory(localeDir)) return files;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localeDir, "*.md")) {
            List<Path> sorted = new ArrayList<>();
            stream.forEach(sorted::add);
            Collections.sort(sorted);
            for (Path file : sorted) {
                String rawName = file.getFileName().toString();
                boolean enabled = !rawName.startsWith("_");
                boolean generated = BrowserStorageService.isGeneratedSection(rawName);
                String content = generated
                        ? documentService.browserStorage().toMarkdown(locale)
                        : Files.readString(file, StandardCharsets.UTF_8);
                // Strip the leading _ and numeric prefix for display: _01-name.md or 01-name.md -> name
                String displayName = rawName.replaceFirst("^_?\\d+-", "").replaceFirst("\\.md$", "");
                files.add(new LegalFileEntry(rawName, displayName, content, enabled, generated));
            }
        } catch (IOException e) {
            log.error("Failed to list legal files in {}", localeDir, e);
        }
        return files;
    }

    private void getLegalPlaceholders(Context ctx) {
        ctx.json(collectPlaceholders());
    }

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
    private static String uploadedMarkdown(Context ctx) {
        var file = ctx.uploadedFile("file");
        if (file == null) return null;
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            String format = importFormat(file.filename());
            if (format == null) return new String(data, StandardCharsets.UTF_8);
            return PandocConverter.toMarkdown(data, format);
        } catch (Exception e) {
            log.warn("Legal document conversion failed", e);
            throw new BadRequestResponse("Document conversion failed");
        }
    }

    /**
     * The pandoc format of an uploaded file, or {@code null} when it is markdown or plain text
     * already and needs no conversion.
     */
    private static String importFormat(String filename) {
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
            throw new BadRequestResponse("markdown is required");
        }
        var imported = LegalImportService.normalise(markdown);
        var files = imported.sections().stream()
                .map(section ->
                        new LegalFileEntry(section.fileName(), section.displayName(), section.content(), true, false))
                .toList();
        ctx.json(new LegalImportResponse(
                imported.title(), files, imported.references(), List.copyOf(imported.unmatched())));
    }

    private void saveLegalFiles(Context ctx) {
        LegalDocumentType type = parseLegalType(ctx);
        Path dir = legalDir(type);
        String locale = safeLocale(ctx, dir);
        Path localeDir = resolveLocaleDir(dir, locale);
        var request = ctx.bodyAsClass(LegalFileEntry[].class);
        try {
            Files.createDirectories(localeDir);
            // Delete all existing .md files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(localeDir, "*.md")) {
                for (Path old : stream) {
                    Files.delete(old);
                }
            }
            // Write files in order with numeric prefix
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
            throw new BadRequestResponse("Invalid legal document type: " + ctx.pathParam("type"));
        }
    }

    @OpenApiName("StationRegistrationStatus")
    public record RegistrationStatus(boolean enabled) {}

    /**
     * @param defaultMailLocale    the language system mails use for accounts with no station to
     *                             take one from
     * @param availableMailLocales the languages this instance holds mail templates for; read-only,
     *                             so the client can offer exactly what will work
     */
    public record ApplicationSettings(
            boolean stationRegistrationEnabled,
            String instanceDefaultTheme,
            String instanceDefaultFeel,
            boolean instanceLockFeel,
            boolean forcePrideFlag,
            String defaultMailLocale,
            List<String> availableMailLocales) {}

    public record TokensConfigResponse(
            int tokenBytes,
            int verifyTokenHours,
            int passwordTokenHours,
            int sessionMinutes,
            boolean tokenPepperConfigured) {}

    public record TokensConfigRequest(
            int tokenBytes, int verifyTokenHours, int passwordTokenHours, int sessionMinutes) {}

    public record HibpConfigResponse(boolean enabled, String endpoint, int staleAfterDays, int timeoutSeconds) {}

    public record HibpConfigRequest(boolean enabled, String endpoint, int staleAfterDays, int timeoutSeconds) {}

    public record TwoFactorCoreConfigResponse(
            boolean enabled,
            int stepUpFreshnessSeconds,
            int trustedDeviceMaxDays,
            int enrollmentGraceDays,
            boolean secretKeyConfigured) {}

    public record TwoFactorCoreConfigRequest(
            boolean enabled, int stepUpFreshnessSeconds, int trustedDeviceMaxDays, int enrollmentGraceDays) {}

    public record TotpConfigResponse(int digits, int periodSeconds, String algorithm, int driftWindow, String issuer) {}

    public record TotpConfigRequest(int digits, int periodSeconds, String algorithm, int driftWindow, String issuer) {}

    public record BackupCodesConfigResponse(int count) {}

    public record BackupCodesConfigRequest(int count) {}

    public record WebAuthnConfigResponse(
            String rpId, String rpName, String attestation, int timeoutSeconds, boolean requireResidentKey) {}

    public record WebAuthnConfigRequest(
            String rpId, String rpName, String attestation, int timeoutSeconds, boolean requireResidentKey) {}

    /**
     * @param deliveryWebhookUrl the address a mail provider reports delivery events to. It carries
     *                           the instance webhook key, so it is a secret in itself and is only
     *                           ever handed to an administrator.
     */
    /**
     * What is left of the mailing page once the providers became a list of their own: the settings
     * that belong to the instance rather than to any one provider.
     */
    public record MailingConfigResponse(int notificationDigestIntervalMinutes) {}

    /**
     * The fields the client may set. Read-only ones the response carries, the webhook address
     * among them, are accepted and dropped rather than refused, so a client holding an older
     * response does not fail on them.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MailingConfigRequest(int notificationDigestIntervalMinutes) {}

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
            String title, List<LegalFileEntry> files, int references, List<String> unmatched) {}

    /**
     * The values an administrator gives the placeholders used across the legal documents.
     *
     * @param values placeholder name to replacement; an entry left empty clears the value
     */
    public record PlaceholderValues(Map<String, String> values) {}

    public record PublicThemeResponse(
            String defaultTheme, String defaultFeel, boolean lockFeel, boolean forcePrideFlag) {}
}
