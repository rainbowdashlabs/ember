/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import dev.chojo.ember.feature.mail.repository.MailProviderBlockRepository;
import dev.chojo.ember.feature.mail.repository.ProviderSecretRepository;
import dev.chojo.ember.feature.mail.repository.StationMailProviderRepository;
import dev.chojo.ember.feature.mail.route.MailFallbackPayload;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailDashboardService;
import dev.chojo.ember.feature.mail.service.MailDashboardService.MailDashboard;
import dev.chojo.ember.feature.mail.service.MailDashboardService.RequeuedMails;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.station.service.StationImportService;
import dev.chojo.ember.feature.station.service.StationLocationService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.station.transfer.ImportProgress;
import dev.chojo.ember.feature.webhook.service.WebhookKeyService;
import dev.chojo.ember.util.MailAddress;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NoContentResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UploadedFile;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Routes for station self-management by managers, including settings, logo, mail configuration,
 * module toggles, data import, ownership transfer, and station deletion.
 */
@Singleton
public class StationManageRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(StationManageRoutes.class);
    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final StationService stationService;
    private final AccountRepository accountRepository;
    private final MailLocaleService mailLocaleService;
    private final StationMailProviderRepository mailProviderRepository;
    private final WebhookKeyService webhookKeyService;
    private final ProviderSecretRepository providerSecretRepository;
    private final Api apiConfig;
    private final EmailService emailService;
    private final MailDashboardService dashboardService;
    private final MailProviderBlockRepository blockRepository;
    private final AuthService authService;
    private final StationImportService importService;
    private final StationLocationService locationService;
    private final StationRepository stationRepository;
    private final StationLogoService logoService;
    private final ClusterService clusterService;

    @Inject
    public StationManageRoutes(
            StationService stationService,
            AccountRepository accountRepository,
            MailLocaleService mailLocaleService,
            StationMailProviderRepository mailProviderRepository,
            WebhookKeyService webhookKeyService,
            ProviderSecretRepository providerSecretRepository,
            Api apiConfig,
            EmailService emailService,
            MailDashboardService dashboardService,
            MailProviderBlockRepository blockRepository,
            AuthService authService,
            StationImportService importService,
            StationLocationService locationService,
            StationRepository stationRepository,
            StationLogoService logoService,
            ClusterService clusterService) {
        this.stationService = stationService;
        this.accountRepository = accountRepository;
        this.mailLocaleService = mailLocaleService;
        this.mailProviderRepository = mailProviderRepository;
        this.webhookKeyService = webhookKeyService;
        this.providerSecretRepository = providerSecretRepository;
        this.apiConfig = apiConfig;
        this.emailService = emailService;
        this.dashboardService = dashboardService;
        this.blockRepository = blockRepository;
        this.authService = authService;
        this.importService = importService;
        this.locationService = locationService;
        this.stationRepository = stationRepository;
        this.logoService = logoService;
        this.clusterService = clusterService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/station/manage",
                this::getStation,
                StationPermission.STATION_GENERAL,
                StationPermission.STATION_LOOK_AND_FEEL,
                StationPermission.STATION_FEDERATION);
        routes.put(prefix + "/station/manage", this::updateStation, StationPermission.STATION_GENERAL);
        routes.post(prefix + "/station/manage/logo", this::uploadLogo, StationPermission.STATION_LOOK_AND_FEEL);
        routes.get(prefix + "/station/manage/logo", this::getLogo, StationPermission.LOGIN);
        routes.get(prefix + "/stations/{stationId}/logo", this::getLogoByStation, StationPermission.LOGIN);
        routes.get(prefix + "/public/stations/{stationId}/logo", this::getLogoByStation);
        routes.delete(prefix + "/station/manage/logo", this::deleteLogo, StationPermission.STATION_LOOK_AND_FEEL);
        routes.delete(prefix + "/station/manage/mail", this::clearMailConfig, StationPermission.STATION_MAIL);
        routes.get(prefix + "/station/manage/mail/webhook", this::getMailWebhook, StationPermission.STATION_MAIL);
        routes.post(
                prefix + "/station/manage/mail/webhook", this::regenerateMailWebhook, StationPermission.STATION_MAIL);
        routes.put(
                prefix + "/station/manage/mail/signing-secret",
                this::updateSigningSecret,
                StationPermission.STATION_MAIL);
        routes.get(prefix + "/station/manage/mail/providers", this::getMailFallbacks, StationPermission.STATION_MAIL);
        routes.put(
                prefix + "/station/manage/mail/providers", this::updateMailFallbacks, StationPermission.STATION_MAIL);
        routes.post(prefix + "/station/manage/mail/test", this::testMailConfig, StationPermission.STATION_MAIL);
        routes.post(
                prefix + "/station/manage/mail/providers/{position}/test",
                this::testMailProvider,
                StationPermission.STATION_MAIL);
        routes.get(prefix + "/station/manage/mail/dashboard", this::mailDashboard, StationPermission.STATION_MAIL);
        routes.post(
                prefix + "/station/manage/mail/stuck/requeue", this::requeueStuckMails, StationPermission.STATION_MAIL);
        routes.delete(prefix + "/station/manage/mail/blocks", this::liftMailBlock, StationPermission.STATION_MAIL);
        routes.post(prefix + "/station/manage/mail/test-mail", this::sendTestMail, StationPermission.STATION_MAIL);
        routes.get(prefix + "/station/manage/modules", this::getDisabledModules, StationPermission.STATION_MODULES);
        routes.put(prefix + "/station/manage/modules", this::setDisabledModules, StationPermission.STATION_MODULES);
        routes.post(prefix + "/station/manage/import", this::importInto, StationPermission.STATION_IMPORT_EXPORT);
        routes.get(
                prefix + "/station/manage/import/progress",
                this::importProgress,
                StationPermission.STATION_IMPORT_EXPORT);
        routes.post(
                prefix + "/station/manage/request-delete",
                this::requestDelete,
                StationPermission.STATION_ADMINISTRATOR);
        routes.post(
                prefix + "/station/manage/delete-moved", this::deleteMoved, StationPermission.STATION_ADMINISTRATOR);
        routes.post(
                prefix + "/station/manage/transfer-ownership",
                this::transferOwnership,
                StationPermission.STATION_ADMINISTRATOR);
        routes.get(prefix + "/public/confirm-station-delete", this::confirmDelete);
        routes.get(prefix + "/station/location", this::getLocation, StationPermission.STATION_GENERAL);
        routes.put(prefix + "/station/location", this::updateLocation, StationPermission.STATION_GENERAL);
        routes.delete(prefix + "/station/location", this::clearLocation, StationPermission.STATION_GENERAL);
    }

    private void getLocation(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(locationService.find(session.stationId()));
    }

    private void updateLocation(Context ctx) {
        var session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(StationLocationService.LocationUpdate.class);
        locationService.update(session.stationId(), body);
        ctx.json(locationService.find(session.stationId()));
    }

    private void clearLocation(Context ctx) {
        var session = UserSession.from(ctx);
        locationService.clear(session.stationId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/station/manage",
            methods = HttpMethod.GET,
            summary = "Get the current station info for management",
            tags = {"Station Manage"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationInfo.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getStation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        stationService
                .findById(session.stationId())
                .ifPresentOrElse(station -> ctx.json(buildStationInfo(station, session)), () -> {
                    throw new NotFoundResponse();
                });
    }

    private StationInfo buildStationInfo(Station station, UserSession session) {
        boolean hasLogo = logoService.exists(station.id());
        var locks = stationService.lookAndFeelLocks(station.id());
        boolean isOwner = session.member() != null
                && station.ownerMemberId() != null
                && station.ownerMemberId() == session.member().id();
        return new StationInfo(
                station.uid().toString(),
                station.name(),
                station.timezone(),
                station.locale(),
                hasLogo,
                station.ownerMemberId(),
                isOwner,
                station.defaultTheme(),
                station.allowUserTheme(),
                station.customThemeColors(),
                station.defaultFeel(),
                station.allowUserFeel(),
                station.publicKbMode(),
                station.discoveryVisibility(),
                station.discoveryDescription(),
                station.discoveryShowKb(),
                station.publicCalendarEnabled(),
                station.publicPagesEnabled(),
                station.publicSlug(),
                station.publicWaitlistEnabled(),
                station.publicBlogEnabled(),
                locks.theme(),
                locks.colors(),
                locks.feel(),
                locks.logo(),
                stationService.clusterNameOf(station.id()).orElse(null));
    }

    @OpenApi(
            path = "/api/v1/station/manage",
            methods = HttpMethod.PUT,
            summary = "Update the current station name",
            tags = {"Station Manage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateStationRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationInfo.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateStation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(UpdateStationRequest.class);
        if (request.name() == null || request.name().isBlank()) {
            throw new BadRequestResponse("name is required");
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            try {
                ZoneId.of(request.timezone());
            } catch (ZoneRulesException e) {
                log.warn("Invalid timezone: {}", request.timezone(), e);
                throw new BadRequestResponse("Invalid timezone: " + request.timezone());
            }
            stationService.updateTimezone(session.stationId(), request.timezone());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            stationService.updateLocale(session.stationId(), request.locale());
        }
        if (request.defaultTheme() != null) {
            stationService.updateThemeSettings(
                    session.stationId(),
                    request.defaultTheme(),
                    request.allowUserTheme() != null ? request.allowUserTheme() : true,
                    request.customThemeColors(),
                    request.defaultFeel() != null ? request.defaultFeel() : ThemeFeel.ROUNDED,
                    request.allowUserFeel() != null ? request.allowUserFeel() : true);
        }
        if (request.publicKbMode() != null) {
            stationService.updatePublicKbMode(session.stationId(), PublicKbMode.valueOf(request.publicKbMode()));
        }
        if (request.discoveryVisibility() != null) {
            stationService.updateDiscoverySettings(
                    session.stationId(),
                    request.discoveryVisibility(),
                    request.discoveryDescription(),
                    request.discoveryShowKb() != null ? request.discoveryShowKb() : false);
        }
        if (request.publicCalendarEnabled() != null) {
            stationService.updatePublicCalendarEnabled(session.stationId(), request.publicCalendarEnabled());
        }
        if (request.publicPagesEnabled() != null) {
            stationService.updatePublicPagesEnabled(session.stationId(), request.publicPagesEnabled());
        }
        if (request.publicWaitlistEnabled() != null) {
            stationService.updatePublicWaitlistEnabled(session.stationId(), request.publicWaitlistEnabled());
        }
        if (request.publicBlogEnabled() != null) {
            stationService.updatePublicBlogEnabled(session.stationId(), request.publicBlogEnabled());
        }
        if (request.publicSlug() != null) {
            try {
                stationService.updatePublicSlug(
                        session.stationId(), request.publicSlug().isBlank() ? null : request.publicSlug());
            } catch (IllegalArgumentException e) {
                throw new BadRequestResponse(e.getMessage());
            }
        }
        stationService
                .update(session.stationId(), request.name())
                .ifPresentOrElse(station -> ctx.json(buildStationInfo(station, session)), () -> {
                    throw new NotFoundResponse();
                });
    }

    @OpenApi(
            path = "/api/v1/station/manage/logo",
            methods = HttpMethod.POST,
            summary = "Upload station logo (max 2MB, image only)",
            tags = {"Station Manage"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void uploadLogo(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (stationService.lookAndFeelLocks(session.stationId()).logo()) {
            throw new BadRequestResponse("The logo is set by the cluster this station belongs to");
        }
        UploadedFile file = ctx.uploadedFile("logo");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (file.size() > MAX_LOGO_SIZE) {
            throw new BadRequestResponse("Logo exceeds maximum size of 2 MB");
        }
        String contentType = file.contentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP, GIF");
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            logoService.store(session.stationId(), data, contentType);
            ctx.json(new MessageResponse("Logo uploaded"));
        } catch (IOException e) {
            log.warn("Failed to read uploaded logo file", e);
            throw new BadRequestResponse("Failed to read uploaded file");
        }
    }

    @OpenApi(
            path = "/api/v1/station/manage/logo",
            methods = HttpMethod.GET,
            summary = "Get station logo",
            tags = {"Station Manage"},
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getLogo(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        serveLogo(ctx, session.stationId(), size);
    }

    private void serveLogo(Context ctx, int stationId, int size) {
        var logoOpt = logoService.read(stationId, size);
        if (logoOpt.isEmpty()) {
            throw new NoContentResponse("No logo set");
        }
        var logo = logoOpt.get();
        ctx.contentType(logo.contentType());
        ctx.header("Cache-Control", "public, max-age=86400");
        ctx.result(logo.data());
    }

    @OpenApi(
            path = "/api/v1/stations/{stationId}/logo",
            methods = HttpMethod.GET,
            summary = "Get a station's logo by ID",
            tags = {"Station Manage"},
            pathParams = @OpenApiParam(name = "stationId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "204", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getLogoByStation(Context ctx) {
        String uidParam = ctx.pathParam("stationId");
        var station = stationService.findByUid(UUID.fromString(uidParam)).orElseThrow(NotFoundResponse::new);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(0);
        serveLogo(ctx, station.id(), size);
    }

    @OpenApi(
            path = "/api/v1/station/manage/logo",
            methods = HttpMethod.DELETE,
            summary = "Delete station logo",
            tags = {"Station Manage"},
            responses = {@OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class))})
    private void deleteLogo(Context ctx) {
        UserSession session = UserSession.from(ctx);
        logoService.delete(session.stationId());
        ctx.json(new MessageResponse("Logo deleted"));
    }

    @OpenApi(
            path = "/api/v1/station/manage/mail",
            methods = HttpMethod.GET,
            summary = "Get station mail configuration",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailConfigResponse.class)))
    /**
     * The address this station's own mail provider reports delivery events to.
     *
     * <p>A station gets an address of its own rather than the instance's, so what it hands to its
     * provider can only ever touch its own post.
     */
    private void getMailWebhook(Context ctx) {
        var session = UserSession.from(ctx);
        var provider = mailProviderRepository.findByStation(session.stationId()).stream()
                .findFirst()
                .map(MailChainEntry::provider)
                .orElse(MailProviderType.NONE);
        ctx.json(new WebhookUrl(
                webhookKeyService.webhookUrl(apiConfig.baseUrl(), session.stationId(), provider.webhookPath()),
                providerSecretRepository.find(session.stationId(), provider).isPresent()));
    }

    /**
     * Stores the signing secret a provider issued, so its reports can be checked against it rather
     * than trusted for knowing the address. An empty value switches the check back off.
     */
    private void updateSigningSecret(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SigningSecretRequest.class);
        var provider = mailProviderRepository.findByStation(session.stationId()).stream()
                .findFirst()
                .map(MailChainEntry::provider)
                .orElse(MailProviderType.NONE);
        providerSecretRepository.store(session.stationId(), provider, request.secret());
        log.info("Station {} set the signing secret of {}", session.stationId(), provider);
        getMailWebhook(ctx);
    }

    /**
     * @param secret the secret as the provider issued it, or empty to stop checking signatures
     */
    public record SigningSecretRequest(String secret) {}

    /**
     * Replaces this station's webhook key, which takes its old address out of service at once.
     */
    private void regenerateMailWebhook(Context ctx) {
        var session = UserSession.from(ctx);
        webhookKeyService.regenerate(session.stationId());
        log.info("Station {} replaced its webhook key", session.stationId());
        getMailWebhook(ctx);
    }

    /**
     * @param deliveryWebhookUrl the address to paste into the provider's settings
     * @param signingSecretSet   whether a signing secret is stored, without revealing it
     */
    public record WebhookUrl(String deliveryWebhookUrl, boolean signingSecretSet) {}

    /**
     * The providers this station falls back to, after the one in its own mail configuration.
     */
    private void getMailFallbacks(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(mailProviderRepository.findByStation(session.stationId()).stream()
                .map(entry -> new MailFallbackPayload(
                                entry.provider(),
                                entry.smtpHost(),
                                entry.smtpPort(),
                                entry.smtpSsl(),
                                entry.smtpUser(),
                                entry.smtpPassword(),
                                entry.apiKey(),
                                entry.senderAddress(),
                                entry.senderName(),
                                entry.attempts(),
                                entry.dailySendLimit(),
                                entry.providerName(),
                                entry.providerUrl(),
                                null)
                        .masked()
                        .withWebhookUrl(webhookKeyService.webhookUrl(
                                apiConfig.baseUrl(),
                                session.stationId(),
                                entry.provider().webhookPath())))
                .toList());
    }

    /**
     * Replaces the order this station falls back through.
     *
     * <p>A station's chain is its own and never runs into the instance's: a station that has taken
     * its outgoing mail into its own hands keeps it there, rather than having its post leave under
     * a sender it did not choose.
     */
    private void updateMailFallbacks(Context ctx) {
        var session = UserSession.from(ctx);
        var incoming = List.of(ctx.bodyAsClass(MailFallbackPayload[].class));
        var stored = mailProviderRepository.findByStation(session.stationId());
        List<MailChainEntry> next = new ArrayList<>();
        for (int i = 0; i < incoming.size(); i++) {
            var entry = incoming.get(i);
            if (entry.provider() == null || entry.provider() == MailProviderType.NONE) continue;
            var previous = i < stored.size() ? stored.get(i) : null;
            next.add(new MailChainEntry(
                    next.size() + 1,
                    entry.provider(),
                    entry.smtpHost(),
                    entry.smtpPort(),
                    entry.smtpSsl(),
                    entry.smtpUser(),
                    MailFallbackPayload.keepOrReplace(
                            entry.smtpPassword(), previous == null ? "" : previous.smtpPassword()),
                    MailFallbackPayload.keepOrReplace(entry.apiKey(), previous == null ? "" : previous.apiKey()),
                    entry.senderAddress(),
                    entry.senderName(),
                    Math.max(1, entry.attempts()),
                    Math.max(0, entry.dailySendLimit()),
                    entry.providerName(),
                    entry.providerUrl()));
        }
        // Emptying the list is what the delete route is for. A save that arrives empty is far more
        // often a client that failed to load it than a station meaning to stop sending.
        if (next.isEmpty() && !stored.isEmpty()) {
            throw new BadRequestResponse("Refusing to replace the provider list with an empty one");
        }
        mailProviderRepository.replace(session.stationId(), next);
        log.info("Station {} set {} mail fallback(s)", session.stationId(), next.size());
        getMailFallbacks(ctx);
    }

    /**
     * Tries one provider of this station's list against its relay, without sending anything.
     */
    private void testMailProvider(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int position;
        try {
            position = Integer.parseInt(ctx.pathParam("position"));
        } catch (NumberFormatException e) {
            throw new BadRequestResponse("Invalid provider position: " + ctx.pathParam("position"));
        }
        var body = ctx.body().isBlank() ? null : ctx.bodyAsClass(ProviderTestRequest.class);
        String recipient = body == null ? null : body.recipient();
        if (recipient == null || recipient.isBlank()) {
            String error = emailService.testStationMailConnection(session.stationId(), position);
            ctx.json(new MailTestResponse(error == null, error));
            return;
        }
        var account = session.account();
        String error = emailService.sendTestMailThrough(
                session.stationId(),
                position,
                MailAddress.require(recipient),
                account.firstName(),
                mailLocaleService.forAccount(account.id()));
        ctx.json(new MailTestResponse(error == null, error));
    }

    @OpenApi(
            path = "/api/v1/station/manage/mail",
            methods = HttpMethod.DELETE,
            summary = "Clear station mail configuration",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "204"))
    private void clearMailConfig(Context ctx) {
        UserSession session = UserSession.from(ctx);
        mailProviderRepository.replace(session.stationId(), List.of());
        throw new NoContentResponse();
    }

    @OpenApi(
            path = "/api/v1/station/manage/mail/test",
            methods = HttpMethod.POST,
            summary = "Test station mail configuration",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailTestResponse.class)))
    private void testMailConfig(Context ctx) {
        UserSession session = UserSession.from(ctx);
        String error = emailService.testStationMailConnection(session.stationId());
        ctx.json(new MailTestResponse(error == null, error));
    }

    @OpenApi(
            path = "/api/v1/station/manage/mail/test-mail",
            methods = HttpMethod.POST,
            summary = "Send a test email to the signed-in account via the station mail configuration",
            tags = {"Station Manage"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400")
            })
    private void sendTestMail(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (mailProviderRepository.findByStation(session.stationId()).isEmpty()) {
            throw new BadRequestResponse("No mail provider configured");
        }
        var account = session.account();
        emailService.sendTestEmail(
                account.email(), account.firstName(), mailLocaleService.forAccount(account.id()), session.stationId());
        ctx.json(new MessageResponse("Test email queued"));
    }

    @OpenApi(
            path = "/api/v1/station/manage/modules",
            methods = HttpMethod.GET,
            summary = "Get disabled modules",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ModulesResponse.class)))
    private void getDisabledModules(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(new ModulesResponse(
                stationService.findDisabledModules(session.stationId()),
                stationService.findClusterDeniedModules(session.stationId()),
                clusterService
                        .findByStation(session.stationId())
                        .map(Cluster::name)
                        .orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/station/manage/modules",
            methods = HttpMethod.PUT,
            summary = "Set disabled modules",
            tags = {"Station Manage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ModulesResponse.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ModulesResponse.class)))
    private void setDisabledModules(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(ModulesResponse.class);
        stationService.setDisabledModules(session.stationId(), body.disabledModules());
        ctx.json(new ModulesResponse(
                stationService.findDisabledModules(session.stationId()),
                stationService.findClusterDeniedModules(session.stationId()),
                clusterService
                        .findByStation(session.stationId())
                        .map(Cluster::name)
                        .orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/station/manage/request-delete",
            methods = HttpMethod.POST,
            summary = "Request station deletion (sends confirmation email)",
            description = "Sends a confirmation link and waits for it. On an instance that cannot send at all "
                    + "there is nobody to ask, so the station is deleted straight away and the answer says so.",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DeleteRequestResponse.class)))
    private void requestDelete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var deleteNow = authService.requestStationDeletion(session.accountId(), session.stationId());
        if (deleteNow.isPresent()) {
            stationService.delete(deleteNow.get());
            ctx.json(new DeleteRequestResponse("Station deleted", true));
            return;
        }
        ctx.json(new DeleteRequestResponse("Confirmation email sent. Check your inbox.", false));
    }

    @OpenApi(
            path = "/api/v1/station/manage/delete-moved",
            methods = HttpMethod.POST,
            summary = "Delete a station's local copy after it has been moved to another instance",
            description = "Bypass the email-confirmation flow used by request-delete. Allowed only when the "
                    + "station is in the read-only-after-transfer state - the data lives on the destination "
                    + "instance, the local copy is a stale shadow.",
            tags = {"Station Manage"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "409")
            })
    private void deleteMoved(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int stationId = session.stationId();
        if (!stationRepository.isReadOnlyForTransfer(stationId)) {
            throw new BadRequestResponse("Station is not in a moved state. Use request-delete for active stations.");
        }
        stationService.delete(stationId);
        ctx.json(new MessageResponse("Station deleted"));
    }

    @OpenApi(
            path = "/api/v1/station/manage/transfer-ownership",
            methods = HttpMethod.POST,
            summary = "Transfer station ownership to another manager",
            tags = {"Station Manage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TransferOwnershipRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400"),
                @OpenApiResponse(status = "403")
            })
    private void transferOwnership(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        if (!stationService.isOwner(session.stationId(), session.member().id())) {
            throw new ForbiddenResponse("Only the station owner can transfer ownership");
        }
        var req = ctx.bodyAsClass(TransferOwnershipRequest.class);
        if (!stationService.transferOwnership(
                session.stationId(), session.member().id(), req.newOwnerMemberId())) {
            throw new BadRequestResponse("Target member must have the MANAGER role");
        }
        ctx.json(new MessageResponse("Ownership transferred"));
    }

    @OpenApi(
            path = "/api/v1/station/manage/import",
            methods = HttpMethod.POST,
            summary = "Import data from a remote instance into this station",
            description =
                    "Imports members, groups, roles, etc. from a remote station into the current station. Accounts are linked by email when possible.",
            tags = {"Station Manage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationImportRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400")
            })
    private void importInto(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(StationImportRequest.class);
        if (req.token() == null || req.token().isBlank()) {
            throw new BadRequestResponse("token is required");
        }
        var parsed = StationExportService.parseToken(req.token())
                .orElseThrow(() -> new BadRequestResponse("Invalid transfer token"));
        String sourceUrl = (req.sourceUrl() != null && !req.sourceUrl().isBlank()) ? req.sourceUrl() : parsed.host();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new BadRequestResponse("token does not contain a source URL; sourceUrl is required");
        }
        importService.startRemoteImportInto(session.stationId(), sourceUrl.replaceAll("/+$", ""), parsed.token());
        ctx.status(HttpStatus.CREATED).json(new MessageResponse("Import started"));
    }

    // -- Module settings --

    @OpenApi(
            path = "/api/v1/station/manage/import/progress",
            methods = HttpMethod.GET,
            summary = "Get import progress for the current station",
            tags = {"Station Manage"},
            responses = {@OpenApiResponse(status = "200"), @OpenApiResponse(status = "404")})
    private void importProgress(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var progress = importService.getProgress(session.stationId());
        if (progress == null) {
            throw new NotFoundResponse("No active import");
        }
        ctx.json(new ImportProgressResponse(
                progress.stationId(),
                progress.stationName(),
                progress.status(),
                progress.phases(),
                progress.completedPhases(),
                progress.currentPhase(),
                progress.subTotal(),
                progress.subCompleted(),
                progress.error()));
    }

    @OpenApi(
            path = "/api/v1/public/confirm-station-delete",
            methods = HttpMethod.GET,
            summary = "Confirm and execute station deletion",
            tags = {"Station Manage"},
            queryParams = @OpenApiParam(name = "token", required = true),
            responses = {@OpenApiResponse(status = "200"), @OpenApiResponse(status = "400")})
    private void confirmDelete(Context ctx) {
        String token = ctx.queryParam("token");
        if (token == null || token.isBlank()) {
            throw new BadRequestResponse("token is required");
        }
        var stationIdOpt = authService.confirmStationDeletion(token);
        if (stationIdOpt.isEmpty()) {
            throw new BadRequestResponse("Invalid or expired token");
        }
        stationService.delete(stationIdOpt.get());
        ctx.json(new MessageResponse("Station deleted"));
    }

    /**
     * The answer to a deletion request.
     *
     * @param deleted whether the station is already gone, rather than waiting for a link to be
     *                clicked in the owner's mail
     */
    public record DeleteRequestResponse(String message, boolean deleted) {}

    /**
     * Request body for updating station settings.
     *
     * @param name     the station name
     * @param timezone the IANA timezone identifier
     * @param locale   the locale string (e.g., "de-DE")
     */
    public record UpdateStationRequest(
            String name,
            String timezone,
            String locale,
            String defaultTheme,
            Boolean allowUserTheme,
            String customThemeColors,
            ThemeFeel defaultFeel,
            Boolean allowUserFeel,
            String publicKbMode,
            DiscoveryVisibility discoveryVisibility,
            String discoveryDescription,
            Boolean discoveryShowKb,
            Boolean publicCalendarEnabled,
            Boolean publicPagesEnabled,
            String publicSlug,
            Boolean publicWaitlistEnabled,
            Boolean publicBlogEnabled) {}

    // -- Station deletion --

    /**
     * Response containing station management information.
     *
     * @param id            the station ID
     * @param name          the station name
     * @param timezone      the station timezone
     * @param locale        the station locale
     * @param hasLogo       whether the station has a logo uploaded
     * @param ownerMemberId the member ID of the owner, or {@code null}
     * @param isOwner       whether the current user is the station owner
     */
    public record StationInfo(
            String id,
            String name,
            String timezone,
            String locale,
            boolean hasLogo,
            Integer ownerMemberId,
            boolean isOwner,
            String defaultTheme,
            boolean allowUserTheme,
            String customThemeColors,
            ThemeFeel defaultFeel,
            boolean allowUserFeel,
            PublicKbMode publicKbMode,
            DiscoveryVisibility discoveryVisibility,
            String discoveryDescription,
            boolean discoveryShowKb,
            boolean publicCalendarEnabled,
            boolean publicPagesEnabled,
            String publicSlug,
            boolean publicWaitlistEnabled,
            boolean publicBlogEnabled,
            boolean themeLocked,
            boolean colorsLocked,
            boolean feelLocked,
            boolean logoLocked,
            String clusterName) {}

    /**
     * Response containing the station's mail configuration and current usage statistics.
     */
    public record MailConfigResponse(
            String provider,
            String smtpHost,
            int smtpPort,
            boolean smtpSsl,
            String smtpUser,
            String senderAddress,
            String senderName,
            boolean hasApiKey,
            String providerName,
            String providerUrl,
            int dailyLimit,
            int monthlyLimit,
            int sentToday,
            int sentThisMonth) {}

    /**
     * Request body for updating the station's mail configuration.
     */
    public record MailConfigRequest(
            String provider,
            String smtpHost,
            Integer smtpPort,
            Boolean smtpSsl,
            String smtpUser,
            String smtpPassword,
            String senderAddress,
            String senderName,
            String apiKey,
            String providerName,
            String providerUrl,
            Integer dailyLimit,
            Integer monthlyLimit) {}

    // -- Station import into existing station --

    /**
     * Response from a mail configuration test.
     *
     * @param success whether the test connection succeeded
     * @param error   the error message if the test failed, or {@code null}
     */
    public record MailTestResponse(boolean success, String error) {}

    /**
     * Where a test mail should go. Empty means only the connection is tried and nothing is sent.
     *
     * @param recipient the address to send to, which need not be the one asking: whether a relay
     *                  delivers is often a question about somebody else's mailbox
     */
    public record ProviderTestRequest(String recipient) {}

    /**
     * What has become of this station's post: the queue, how each of its providers stands today,
     * and what those providers reported back about the mails they took.
     */
    @OpenApi(
            path = "/api/v1/station/manage/mail/dashboard",
            methods = HttpMethod.GET,
            summary = "The state of the station mail queue and its providers",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailDashboard.class)))
    private void mailDashboard(Context ctx) {
        ctx.json(dashboardService.forOwner(UserSession.from(ctx).stationId()));
    }

    /**
     * Puts mails a dead worker left in sending back into the queue, either one named mail or all
     * of this station's.
     */
    @OpenApi(
            path = "/api/v1/station/manage/mail/stuck/requeue",
            methods = HttpMethod.POST,
            summary = "Queue left-behind station mails for another attempt",
            tags = {"Station Manage"},
            queryParams =
                    @OpenApiParam(name = "id", type = Integer.class, description = "One mail, or all when absent"),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RequeuedMails.class)))
    private void requeueStuckMails(Context ctx) {
        Integer id = ctx.queryParam("id") == null
                ? null
                : ctx.queryParamAsClass("id", Integer.class).get();
        ctx.json(dashboardService.requeueStuck(UserSession.from(ctx).stationId(), id));
    }

    /**
     * Lifts a block by hand, for when the relay has been taken off the list and nobody wants to
     * wait out the week.
     */
    private void liftMailBlock(Context ctx) {
        var provider = MailProviderType.fromName(ctx.queryParam("provider"))
                .orElseThrow(() -> new BadRequestResponse("Unknown mail provider: " + ctx.queryParam("provider")));
        blockRepository.lift(UserSession.from(ctx).stationId(), provider, ctx.queryParam("domain"));
        throw new NoContentResponse();
    }

    /**
     * Response and request body for the set of disabled modules.
     *
     * @param disabledModules      the modules the station switched off itself
     * @param clusterDeniedModules the modules its cluster switched off, which it cannot turn back on
     * @param clusterName          the cluster doing the denying, or {@code null} when it answers to nobody
     */
    public record ModulesResponse(
            Set<StationModule> disabledModules, Set<StationModule> clusterDeniedModules, String clusterName) {
        /** The shape a caller sends: only its own list matters on the way in. */
        public ModulesResponse(Set<StationModule> disabledModules) {
            this(disabledModules, Set.of(), null);
        }
    }

    /**
     * Request body for transferring station ownership.
     *
     * @param newOwnerMemberId the member ID of the new owner
     */
    public record TransferOwnershipRequest(int newOwnerMemberId) {}

    /**
     * Request body for importing data from a remote Ember instance.
     *
     * @param sourceUrl the base URL of the remote instance
     * @param token     the transfer token for authentication
     */
    public record StationImportRequest(String sourceUrl, String token) {}

    /**
     * Response containing the progress of an ongoing import operation.
     *
     * @param stationId       the target station ID
     * @param stationName     the target station name
     * @param status          the import status (IN_PROGRESS, COMPLETED, FAILED)
     * @param phases          the ordered list of phase ids the import walks (tables, storage
     *                        backend, per-category file copies, avatar carry-over)
     * @param completedPhases the number of phases finished so far
     * @param currentPhase    the phase id currently being processed, or {@code null} if completed
     * @param error           the error message if the import failed, or {@code null}
     */
    public record ImportProgressResponse(
            int stationId,
            String stationName,
            ImportProgress.Status status,
            List<String> phases,
            int completedPhases,
            String currentPhase,
            int subTotal,
            int subCompleted,
            String error) {}
}
