/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.roles.InstancePermission;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.MailSettings;
import dev.chojo.ember.conf.file.elements.Mailing;
import dev.chojo.ember.conf.file.elements.Theming;
import dev.chojo.ember.feature.legal.service.ConsentService;
import dev.chojo.ember.feature.legal.service.LegalDocumentService;
import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Singleton
public class AdminSettingsRoutes implements Routes {
    private static final String STATION_REGISTRATION_ENABLED = "station_registration_enabled";
    private static final String FORCE_PRIDE_FLAG = "force_pride_flag";

    private static final Logger log = LoggerFactory.getLogger(AdminSettingsRoutes.class);

    private final ApplicationSettingRepository settingRepository;
    private final ImageService imageService;
    private final Conf conf;
    private final LegalDocumentService documentService;

    @Inject
    public AdminSettingsRoutes(
            ApplicationSettingRepository settingRepository,
            ImageService imageService,
            Conf conf,
            ConsentService consentService) {
        this.settingRepository = settingRepository;
        this.imageService = imageService;
        this.conf = conf;
        this.documentService = new LegalDocumentService();
        initializeAppLogos();
        initializeLogoFragments();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/public/settings/station-registration", this::isRegistrationEnabled);
        routes.get(prefix + "/public/settings/theme", this::getPublicTheme);
        routes.get(prefix + "/public/logo/{name}", this::serveAppLogo);
        routes.get(prefix + "/public/logo-fragment/{name}", this::serveLogoFragment);
        routes.get(prefix + "/admin/settings", this::getSettings, InstancePermission.ADMINISTRATOR);
        routes.put(prefix + "/admin/settings", this::updateSettings, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/config/auth", this::getAuthConfig, InstancePermission.ADMINISTRATOR);
        routes.put(prefix + "/admin/config/auth", this::updateAuthConfig, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/config/mailing", this::getMailingConfig, InstancePermission.ADMINISTRATOR);
        routes.put(prefix + "/admin/config/mailing", this::updateMailingConfig, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/legal/{type}", this::getLegalDocument, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/legal/{type}/locales", this::getLegalLocales, InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/legal/{type}/{locale}",
                this::getLegalDocumentLocale,
                InstancePermission.ADMINISTRATOR);
        routes.get(
                prefix + "/admin/legal/{type}/{locale}/files", this::getLegalFiles, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/legal/{type}/{locale}/files", this::saveLegalFiles, InstancePermission.ADMINISTRATOR);
        routes.put(prefix + "/admin/legal/{type}", this::updateLegalDocument, InstancePermission.ADMINISTRATOR);
        routes.put(
                prefix + "/admin/legal/{type}/{locale}",
                this::updateLegalDocumentLocale,
                InstancePermission.ADMINISTRATOR);
    }

    private void initializeAppLogos() {
        String[] logos = {
            "IconBG",
            "IconBG_Blink",
            "IconBG_FAQ",
            "IconBG_FAQ_Blink",
            "IconBG_NoBlush",
            "IconBG_NoBlush_Blink",
            "NoBG_OrangeGlow",
            "NoBG_OrangeGlow_Blink",
            "NoBG_OrangeGlow_FAQ",
            "NoBG_OrangeGlow_FAQ_Blink",
            "NoBG_NoGlow",
            "NoBG_NoGlow_Blink",
            "NoBG_NoGlow_FAQ",
            "NoBG_NoGlow_FAQ_Blink"
        };
        for (String logo : logos) {
            storeIfChanged(ImageCategory.APP_LOGOS, logo, "logo/" + logo + ".png");
        }
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
            storeIfChanged(
                    ImageCategory.LOGO_FRAGMENTS, fragment.getKey(), "logo_fragments/" + fragment.getValue() + ".png");
        }
    }

    private void storeIfChanged(ImageCategory category, String id, String resourcePath) {
        try (var is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) return;
            byte[] data = is.readAllBytes();
            String hash = sha256(data);
            Path hashFile = Path.of("data", "images", category.directory(), id, ".hash");
            if (Files.exists(hashFile) && Files.readString(hashFile).trim().equals(hash)) return;
            imageService.store(category, id, data, "image/png");
            Files.createDirectories(hashFile.getParent());
            Files.writeString(hashFile, hash);
        } catch (Exception e) {
            log.warn("Failed to initialize image {}/{}: {}", category, id, e.getMessage());
        }
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        }
    }

    private void serveLogoFragment(Context ctx) {
        String name = ctx.pathParam("name");
        if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        imageService
                .read(ImageCategory.LOGO_FRAGMENTS, name, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "public, max-age=86400");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    private void serveAppLogo(Context ctx) {
        String name = ctx.pathParam("name");
        // Strip .png extension if present
        if (name.endsWith(".png")) name = name.substring(0, name.length() - 4);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        imageService
                .read(ImageCategory.APP_LOGOS, name, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "public, max-age=86400");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NO_CONTENT));
    }

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

    // -- Auth config --

    private void getAuthConfig(Context ctx) {
        var auth = conf.main().auth();
        ctx.json(new AuthConfigResponse(
                auth.tokenBytes(), auth.verifyTokenHours(), auth.passwordTokenHours(), auth.sessionMinutes()));
    }

    private void updateAuthConfig(Context ctx) {
        var request = ctx.bodyAsClass(AuthConfigRequest.class);
        var auth = conf.main().auth();
        try {
            setField(Auth.class, auth, "tokenBytes", request.tokenBytes());
            setField(Auth.class, auth, "verifyTokenHours", request.verifyTokenHours());
            setField(Auth.class, auth, "passwordTokenHours", request.passwordTokenHours());
            setField(Auth.class, auth, "sessionMinutes", request.sessionMinutes());
            conf.save();
            ctx.json(new AuthConfigResponse(
                    auth.tokenBytes(), auth.verifyTokenHours(), auth.passwordTokenHours(), auth.sessionMinutes()));
        } catch (Exception e) {
            log.error("Failed to update auth config", e);
            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // -- Mailing config --

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

    // -- Legal documents --

    private void getLegalDocument(Context ctx) {
        String type = ctx.pathParam("type");
        Path dir = legalDir(type);
        if (dir == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(new ErrorResponse("Invalid legal document type: " + type));
            return;
        }
        var doc = documentService.getDocument(dir, "de");
        ctx.json(new LegalDocumentResponse(type, doc.markdown(), doc.version()));
    }

    private void getLegalDocumentLocale(Context ctx) {
        String type = ctx.pathParam("type");
        String locale = ctx.pathParam("locale");
        Path dir = legalDir(type);
        if (dir == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(new ErrorResponse("Invalid legal document type: " + type));
            return;
        }
        var doc = documentService.getDocument(dir, locale);
        ctx.json(new LegalDocumentResponse(type, doc.markdown(), doc.version()));
    }

    private void getLegalLocales(Context ctx) {
        String type = ctx.pathParam("type");
        Path dir = legalDir(type);
        if (dir == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(new ErrorResponse("Invalid legal document type: " + type));
            return;
        }
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
        updateLegalDocumentForLocale(ctx, "de");
    }

    private void updateLegalDocumentLocale(Context ctx) {
        updateLegalDocumentForLocale(ctx, ctx.pathParam("locale"));
    }

    private void updateLegalDocumentForLocale(Context ctx, String locale) {
        String type = ctx.pathParam("type");
        Path dir = legalDir(type);
        if (dir == null) {
            ctx.status(HttpStatus.BAD_REQUEST).json(new ErrorResponse("Invalid legal document type: " + type));
            return;
        }
        var request = ctx.bodyAsClass(LegalDocumentRequest.class);
        Path localeDir = dir.resolve(locale);
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
        String type = ctx.pathParam("type");
        String locale = ctx.pathParam("locale");
        Path dir = legalDir(type);
        if (dir == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }
        ctx.json(readLegalFiles(dir.resolve(locale)));
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
        String type = ctx.pathParam("type");
        String locale = ctx.pathParam("locale");
        Path dir = legalDir(type);
        if (dir == null) {
            ctx.status(HttpStatus.BAD_REQUEST);
            return;
        }
        Path localeDir = dir.resolve(locale);
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

    private Path legalDir(String type) {
        var api = conf.main().api();
        return switch (type) {
            case "privacy" -> Path.of(api.privacyPolicyDir());
            case "tos" -> Path.of(api.tosDir());
            case "consent" -> Path.of(api.consentDir());
            case "imprint" -> Path.of(api.imprintDir());
            default -> null;
        };
    }

    private static void setField(Class<?> clazz, Object target, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @OpenApiName("StationRegistrationStatus")
    public record RegistrationStatus(boolean enabled) {}

    public record ApplicationSettings(
            boolean stationRegistrationEnabled,
            String instanceDefaultTheme,
            String instanceDefaultFeel,
            boolean instanceLockFeel,
            boolean forcePrideFlag) {}

    public record AuthConfigResponse(
            int tokenBytes, int verifyTokenHours, int passwordTokenHours, int sessionMinutes) {}

    public record AuthConfigRequest(int tokenBytes, int verifyTokenHours, int passwordTokenHours, int sessionMinutes) {}

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

    public record LegalDocumentResponse(String type, String content, String version) {}

    public record LegalDocumentRequest(String content) {}

    public record LegalFileEntry(String filename, String displayName, String content, boolean enabled) {}

    public record PublicThemeResponse(
            String defaultTheme, String defaultFeel, boolean lockFeel, boolean forcePrideFlag) {}

    private record ErrorResponse(String error) {}
}
