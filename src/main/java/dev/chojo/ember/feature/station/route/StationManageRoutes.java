/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.mail.entity.MailFallbackPayload;
import dev.chojo.ember.feature.mail.entity.SmtpEncryption;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailDashboardService;
import dev.chojo.ember.feature.mail.service.MailDashboardService.MailDashboard;
import dev.chojo.ember.feature.mail.service.MailDashboardService.RequeuedMails;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.StationMailSettingsService;
import dev.chojo.ember.feature.mail.service.StationMailSettingsService.WebhookUrl;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.service.StationExportService;
import dev.chojo.ember.feature.station.service.StationImportService;
import dev.chojo.ember.feature.station.service.StationLocationService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.feature.station.service.StationNotificationTimesService;
import dev.chojo.ember.feature.station.service.StationNotificationTimesService.NotificationSchedulePayload;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.feature.station.service.StationSettingsService;
import dev.chojo.ember.feature.station.service.StationSettingsService.UpdateStationRequest;
import dev.chojo.ember.feature.station.transfer.ImportProgress;
import dev.chojo.ember.util.MailAddress;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NoContentResponse;
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
    private final StationSettingsService settingsService;
    private final MailLocaleService mailLocaleService;
    private final StationMailSettingsService mailSettings;
    private final EmailService emailService;
    private final MailDashboardService dashboardService;
    private final AuthService authService;
    private final StationImportService importService;
    private final StationLocationService locationService;
    private final StationNotificationTimesService notificationTimes;
    private final StationLogoService logoService;
    private final ClusterService clusterService;

    @Inject
    public StationManageRoutes(
            StationService stationService,
            StationSettingsService settingsService,
            MailLocaleService mailLocaleService,
            StationMailSettingsService mailSettings,
            EmailService emailService,
            MailDashboardService dashboardService,
            AuthService authService,
            StationImportService importService,
            StationLocationService locationService,
            StationLogoService logoService,
            ClusterService clusterService,
            StationNotificationTimesService notificationTimes) {
        this.stationService = stationService;
        this.settingsService = settingsService;
        this.notificationTimes = notificationTimes;
        this.mailLocaleService = mailLocaleService;
        this.mailSettings = mailSettings;
        this.emailService = emailService;
        this.dashboardService = dashboardService;
        this.authService = authService;
        this.importService = importService;
        this.locationService = locationService;
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
        routes.get(
                prefix + "/station/manage/notifications",
                this::getNotificationSchedule,
                StationPermission.STATION_MAIL);
        routes.put(
                prefix + "/station/manage/notifications",
                this::updateNotificationSchedule,
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
                    throw Refusal.STATION_NOT_HERE_ON_MANAGE.raise();
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
                station.pdfHidesInstanceUrl(),
                station.nicknamesEnabled(),
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
        var station = settingsService.update(session.stationId(), ctx.bodyAsClass(UpdateStationRequest.class));
        ctx.json(buildStationInfo(station, session));
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
            throw Refusal.LOGO_SET_BY_CLUSTER.raise();
        }
        UploadedFile file = ctx.uploadedFile("logo");
        if (file == null) {
            throw Refusal.LOGO_UPLOAD_MISSING_FILE.raise();
        }
        if (file.size() > MAX_LOGO_SIZE) {
            throw Refusal.LOGO_TOO_LARGE.raise();
        }
        String contentType = file.contentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw Refusal.LOGO_KIND_NOT_TAKEN.raise();
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            logoService.store(session.stationId(), data, contentType);
            ctx.json(new MessageResponse("Logo uploaded"));
        } catch (IOException e) {
            log.warn("Failed to read uploaded logo file", e);
            throw Refusal.LOGO_NOT_READ.raise();
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
        var station = stationService
                .findByUid(UUID.fromString(uidParam))
                .orElseThrow(Refusal.STATION_NOT_HERE_FOR_LOGO::raise);
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

    /**
     * The address this station's own mail provider reports delivery events to.
     *
     * <p>A station gets an address of its own rather than the instance's, so what it hands to its
     * provider can only ever touch its own post.
     */
    @OpenApi(
            path = "/api/v1/station/manage/mail/webhook",
            methods = HttpMethod.GET,
            summary = "Get the address the station's mail provider reports delivery events to",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WebhookUrl.class)))
    private void getMailWebhook(Context ctx) {
        ctx.json(mailSettings.webhook(UserSession.from(ctx).stationId()));
    }

    private void updateSigningSecret(Context ctx) {
        var request = ctx.bodyAsClass(SigningSecretRequest.class);
        ctx.json(mailSettings.updateSigningSecret(UserSession.from(ctx).stationId(), request.secret()));
    }

    /**
     * @param secret the secret as the provider issued it, or empty to stop checking signatures
     */
    public record SigningSecretRequest(String secret) {}

    /**
     * Replaces this station's webhook key, which takes its old address out of service at once.
     */
    private void regenerateMailWebhook(Context ctx) {
        ctx.json(mailSettings.regenerateWebhook(UserSession.from(ctx).stationId()));
    }

    private void getNotificationSchedule(Context ctx) {
        ctx.json(notificationTimes.times(UserSession.from(ctx).stationId()));
    }

    private void updateNotificationSchedule(Context ctx) {
        var request = ctx.bodyAsClass(NotificationSchedulePayload.class);
        notificationTimes.update(UserSession.from(ctx).stationId(), request.sendTimes());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void getMailFallbacks(Context ctx) {
        ctx.json(mailSettings.providers(UserSession.from(ctx).stationId()));
    }

    private void updateMailFallbacks(Context ctx) {
        var incoming = List.of(ctx.bodyAsClass(MailFallbackPayload[].class));
        ctx.json(mailSettings.updateProviders(UserSession.from(ctx).stationId(), incoming));
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
            throw Refusal.MAIL_PROVIDER_POSITION_NOT_A_NUMBER.raise();
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
        mailSettings.clear(UserSession.from(ctx).stationId());
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
        mailSettings.requireProvider(session.stationId());
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
        stationService.deleteMoved(UserSession.from(ctx).stationId());
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
        if (session.member() == null) throw Refusal.NOT_A_MEMBER_ON_HANDOVER.raise();
        if (!stationService.isOwner(session.stationId(), session.member().id())) {
            throw Refusal.ONLY_THE_OWNER_HANDS_OVER.raise();
        }
        var req = ctx.bodyAsClass(TransferOwnershipRequest.class);
        if (!stationService.transferOwnership(
                session.stationId(), session.member().id(), req.newOwnerMemberId())) {
            throw Refusal.NEW_OWNER_NOT_A_MANAGER.raise();
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
            throw Refusal.IMPORT_NEEDS_A_TRANSFER_CODE.raise();
        }
        var parsed = StationExportService.parseToken(req.token())
                .orElseThrow(Refusal.TRANSFER_CODE_NOT_GOOD_ON_IMPORT::raise);
        String sourceUrl = (req.sourceUrl() != null && !req.sourceUrl().isBlank()) ? req.sourceUrl() : parsed.host();
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw Refusal.IMPORT_NEEDS_A_SOURCE.raise();
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
            throw Refusal.NO_IMPORT_RUNNING.raise();
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
            throw Refusal.STATION_DELETE_LINK_CARRIES_NOTHING.raise();
        }
        var stationIdOpt = authService.confirmStationDeletion(token);
        if (stationIdOpt.isEmpty()) {
            throw Refusal.STATION_DELETE_LINK_UNKNOWN.raise();
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
            boolean pdfHidesInstanceUrl,
            boolean nicknamesEnabled,
            boolean themeLocked,
            boolean colorsLocked,
            boolean feelLocked,
            boolean logoLocked,
            String clusterName) {}

    /**
     * Request body for updating the station's mail configuration.
     */
    public record MailConfigRequest(
            String provider,
            String smtpHost,
            Integer smtpPort,
            SmtpEncryption smtpEncryption,
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
                .orElseThrow(Refusal.MAIL_PROVIDER_NOT_KNOWN::raise);
        dashboardService.liftBlock(UserSession.from(ctx).stationId(), provider, ctx.queryParam("domain"));
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
