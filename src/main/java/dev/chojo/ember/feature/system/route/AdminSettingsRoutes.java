/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.HibpSettings;
import dev.chojo.ember.conf.file.elements.MailSettings;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.conf.file.elements.Theming;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.feature.legal.entity.LegalDocumentType;
import dev.chojo.ember.feature.legal.service.LegalDocumentService;
import dev.chojo.ember.feature.media.service.LogoFragmentService;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
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

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    private final LegalDocumentService documentService;

    @Inject
    public AdminSettingsRoutes(
            ApplicationSettingRepository settingRepository, LogoFragmentService logoFragmentService, Conf conf) {
        this.settingRepository = settingRepository;
        this.logoFragmentService = logoFragmentService;
        this.conf = conf;
        this.documentService = new LegalDocumentService();
        initializeLogoFragments();
    }

    /**
     * Parses the {@code locale} path parameter, validates it against
     * {@link #SAFE_LOCALE}, and checks that the resolved directory stays inside
     * {@code base}. Throws {@link io.javalin.http.BadRequestResponse} on any
     * mismatch so the route returns 400 with a static, user-safe message.
     */
    private static String safeLocale(Context ctx, Path base) {
        String locale = ctx.pathParam("locale");
        if (locale == null || !SAFE_LOCALE.matcher(locale).matches()) {
            throw new io.javalin.http.BadRequestResponse("Invalid locale");
        }
        Path resolved = base.resolve(locale).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw new io.javalin.http.BadRequestResponse("Invalid locale");
        }
        return locale;
    }

    private static Path resolveLocaleDir(Path base, String locale) {
        // safeLocale has already validated the value, but re-check the resolved
        // path so a future caller that forgets the validation gate is still
        // caught here rather than escaping the legal directory.
        Path resolved = base.resolve(locale).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw new io.javalin.http.BadRequestResponse("Invalid locale");
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
        routes.put(
                prefix + "/admin/config/mailing",
                this::updateMailingConfig,
                InstancePermission.ADMINISTRATOR,
                StepUpCategory.INSTANCE_CONFIG);
        routes.get(prefix + "/admin/legal/{type}", this::getLegalDocument, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/legal/{type}/locales", this::getLegalLocales, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/legal/{type}/{locale}",
                this::getLegalDocumentLocale,
                InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/legal/{type}/{locale}/files", this::getLegalFiles, InstancePermission.ADMINISTRATOR);
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
                forcePride));
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
                request.forcePrideFlag()));
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
        requireRange(request.sessionMinutes(), 5, 1440, "sessionMinutes");
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

    private void getMailingConfig(Context ctx) {
        var mailing = conf.main().mailing();
        var smtp = mailing.smtp();
        ctx.json(new MailingConfigResponse(
                mailing.provider(),
                mailing.senderAddress(),
                mailing.senderName(),
                mailing.user(),
                mailing.password().isEmpty() ? "" : "********",
                mailing.apiKey(),
                smtp.host(),
                smtp.port(),
                smtp.ssl(),
                mailing.dailySendLimit(),
                mailing.notificationDigestIntervalMinutes()));
    }

    // -- Legal documents --

    private void updateMailingConfig(Context ctx) {
        var request = ctx.bodyAsClass(MailingConfigRequest.class);
        var mailing = conf.main().mailing();
        var smtp = mailing.smtp();
        try {
            setField(Mailing.class, mailing, "provider", request.provider());
            setField(Mailing.class, mailing, "senderAddress", request.senderAddress());
            setField(Mailing.class, mailing, "senderName", request.senderName());
            setField(Mailing.class, mailing, "user", request.user());
            // Only update password if not the masked placeholder
            if (request.password() != null && !"********".equals(request.password())) {
                setField(Mailing.class, mailing, "password", request.password());
            }
            setField(Mailing.class, mailing, "apiKey", request.apiKey());
            setField(MailSettings.class, smtp, "host", request.smtpHost());
            setField(MailSettings.class, smtp, "port", request.smtpPort());
            setField(MailSettings.class, smtp, "ssl", request.smtpSsl());
            setField(Mailing.class, mailing, "dailySendLimit", request.dailySendLimit());
            setField(
                    Mailing.class,
                    mailing,
                    "notificationDigestIntervalMinutes",
                    request.notificationDigestIntervalMinutes());
            conf.save();

            // Return response with masked password
            ctx.json(new MailingConfigResponse(
                    mailing.provider(),
                    mailing.senderAddress(),
                    mailing.senderName(),
                    mailing.user(),
                    mailing.password().isEmpty() ? "" : "********",
                    mailing.apiKey(),
                    smtp.host(),
                    smtp.port(),
                    smtp.ssl(),
                    mailing.dailySendLimit(),
                    mailing.notificationDigestIntervalMinutes()));
        } catch (Exception e) {
            log.error("Failed to update mailing config", e);
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
        ctx.json(readLegalFiles(resolveLocaleDir(dir, locale)));
    }

    private List<LegalFileEntry> readLegalFiles(Path localeDir) {
        List<LegalFileEntry> files = new ArrayList<>();
        if (!Files.isDirectory(localeDir)) return files;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localeDir, "*.md")) {
            List<Path> sorted = new ArrayList<>();
            stream.forEach(sorted::add);
            Collections.sort(sorted);
            for (Path file : sorted) {
                String rawName = file.getFileName().toString();
                boolean enabled = !rawName.startsWith("_");
                String content = Files.readString(file, StandardCharsets.UTF_8);
                // Strip the leading _ and numeric prefix for display: _01-name.md or 01-name.md -> name
                String displayName = rawName.replaceFirst("^_?\\d+-", "").replaceFirst("\\.md$", "");
                files.add(new LegalFileEntry(rawName, displayName, content, enabled));
            }
        } catch (IOException e) {
            log.error("Failed to list legal files in {}", localeDir, e);
        }
        return files;
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
            List<LegalFileEntry> result = new ArrayList<>();
            for (int i = 0; i < request.length; i++) {
                var entry = request[i];
                String prefix = String.format("%02d", i + 1);
                String safeName = entry.displayName().replaceAll("[^a-zA-Z0-9_-]", "-");
                String filename = (entry.enabled() ? "" : "_") + prefix + "-" + safeName + ".md";
                Path file = localeDir.resolve(filename);
                Files.writeString(file, entry.content(), StandardCharsets.UTF_8);
                result.add(new LegalFileEntry(filename, entry.displayName(), entry.content(), entry.enabled()));
            }
            documentService.initialize(dir);
            ctx.json(result);
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

    public record ApplicationSettings(
            boolean stationRegistrationEnabled,
            String instanceDefaultTheme,
            String instanceDefaultFeel,
            boolean instanceLockFeel,
            boolean forcePrideFlag) {}

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

    public record MailingConfigResponse(
            MailProviderType provider,
            String senderAddress,
            String senderName,
            String user,
            String password,
            String apiKey,
            String smtpHost,
            int smtpPort,
            boolean smtpSsl,
            int dailySendLimit,
            int notificationDigestIntervalMinutes) {}

    public record MailingConfigRequest(
            MailProviderType provider,
            String senderAddress,
            String senderName,
            String user,
            String password,
            String apiKey,
            String smtpHost,
            int smtpPort,
            boolean smtpSsl,
            int dailySendLimit,
            int notificationDigestIntervalMinutes) {}

    public record LegalDocumentResponse(LegalDocumentType type, String content, String version) {}

    public record LegalDocumentRequest(String content) {}

    public record LegalFileEntry(String filename, String displayName, String content, boolean enabled) {}

    public record PublicThemeResponse(
            String defaultTheme, String defaultFeel, boolean lockFeel, boolean forcePrideFlag) {}

    private record ErrorResponse(String error) {}
}
