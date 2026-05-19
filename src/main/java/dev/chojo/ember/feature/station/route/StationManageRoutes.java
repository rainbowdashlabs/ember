/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ember.feature.station.entity.StationMailConfig;
import dev.chojo.ember.feature.station.repository.StationMailConfigRepository;
import dev.chojo.ember.feature.station.repository.StationRepository.StationLogo;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
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

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.zone.ZoneRulesException;
import java.util.Optional;
import java.util.Set;

@Singleton
public class StationManageRoutes implements Routes {
    private static final long MAX_LOGO_SIZE = 2 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/svg+xml");

    private final StationService stationService;
    private final StationMailConfigRepository mailConfigRepository;
    private final EmailService emailService;

    @Inject
    public StationManageRoutes(
            StationService stationService,
            StationMailConfigRepository mailConfigRepository,
            EmailService emailService) {
        this.stationService = stationService;
        this.mailConfigRepository = mailConfigRepository;
        this.emailService = emailService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/station/manage", this::getStation, Roles.MANAGER);
        routes.put(prefix + "/station/manage", this::updateStation, Roles.MANAGER);
        routes.post(prefix + "/station/manage/logo", this::uploadLogo, Roles.MANAGER);
        routes.get(prefix + "/station/manage/logo", this::getLogo, Roles.LOGIN);
        routes.get(prefix + "/stations/{stationId}/logo", this::getLogoByStation, Roles.LOGIN);
        routes.delete(prefix + "/station/manage/logo", this::deleteLogo, Roles.MANAGER);
        routes.get(prefix + "/station/manage/mail", this::getMailConfig, Roles.MANAGER);
        routes.put(prefix + "/station/manage/mail", this::updateMailConfig, Roles.MANAGER);
        routes.post(prefix + "/station/manage/mail/test", this::testMailConfig, Roles.MANAGER);
        routes.get(prefix + "/station/manage/modules", this::getDisabledModules, Roles.MANAGER);
        routes.put(prefix + "/station/manage/modules", this::setDisabledModules, Roles.MANAGER);
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
                .ifPresentOrElse(
                        station -> {
                            boolean hasLogo =
                                    stationService.getLogo(station.id()).isPresent();
                            ctx.json(new StationInfo(
                                    station.id(), station.name(), station.timezone(), station.locale(), hasLogo));
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
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
                throw new BadRequestResponse("Invalid timezone: " + request.timezone());
            }
            stationService.updateTimezone(session.stationId(), request.timezone());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            stationService.updateLocale(session.stationId(), request.locale());
        }
        stationService
                .update(session.stationId(), request.name())
                .ifPresentOrElse(
                        station -> {
                            boolean hasLogo =
                                    stationService.getLogo(station.id()).isPresent();
                            ctx.json(new StationInfo(
                                    station.id(), station.name(), station.timezone(), station.locale(), hasLogo));
                        },
                        () -> {
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
        UploadedFile file = ctx.uploadedFile("logo");
        if (file == null) {
            throw new BadRequestResponse("No file uploaded");
        }
        if (file.size() > MAX_LOGO_SIZE) {
            throw new BadRequestResponse("Logo exceeds maximum size of 2 MB");
        }
        String contentType = file.contentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP, SVG");
        }
        try {
            byte[] data = file.content().readAllBytes();
            stationService.setLogo(session.stationId(), data, contentType);
            ctx.json(new MessageResponse("Logo uploaded"));
        } catch (IOException e) {
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
        Optional<StationLogo> logoOpt = stationService.getLogo(session.stationId());
        if (logoOpt.isEmpty()) {
            throw new NotFoundResponse("No logo set");
        }
        StationLogo logo = logoOpt.get();
        ctx.contentType(logo.contentType());
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
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getLogoByStation(Context ctx) {
        int stationId = ctx.pathParamAsClass("stationId", Integer.class).get();
        Optional<StationLogo> logoOpt = stationService.getLogo(stationId);
        if (logoOpt.isEmpty()) {
            throw new NotFoundResponse("No logo set");
        }
        StationLogo logo = logoOpt.get();
        ctx.contentType(logo.contentType());
        ctx.result(logo.data());
    }

    @OpenApi(
            path = "/api/v1/station/manage/logo",
            methods = HttpMethod.DELETE,
            summary = "Delete station logo",
            tags = {"Station Manage"},
            responses = {@OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class))})
    private void deleteLogo(Context ctx) {
        UserSession session = UserSession.from(ctx);
        stationService.deleteLogo(session.stationId());
        ctx.json(new MessageResponse("Logo deleted"));
    }

    public record UpdateStationRequest(String name, String timezone, String locale) {}

    public record StationInfo(int id, String name, String timezone, String locale, boolean hasLogo) {}

    // -- Mail config --

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

    public record MailTestResponse(boolean success, String error) {}

    @OpenApi(
            path = "/api/v1/station/manage/mail",
            methods = HttpMethod.GET,
            summary = "Get station mail configuration",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailConfigResponse.class)))
    private void getMailConfig(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(buildMailConfigResponse(session.stationId()));
    }

    private MailConfigResponse buildMailConfigResponse(int stationId) {
        var config = mailConfigRepository.findByStation(stationId);
        if (config.isPresent()) {
            var c = config.get();
            return new MailConfigResponse(
                    c.provider().name(),
                    c.smtpHost(),
                    c.smtpPort(),
                    c.smtpSsl(),
                    c.smtpUser(),
                    c.senderAddress(),
                    c.senderName(),
                    !c.apiKey().isBlank(),
                    c.providerName(),
                    c.providerUrl(),
                    c.dailyLimit(),
                    c.monthlyLimit(),
                    mailConfigRepository.getDailyCount(stationId, LocalDate.now()),
                    mailConfigRepository.getMonthlyCount(stationId, LocalDate.now()));
        }
        return new MailConfigResponse("NONE", "", 587, false, "", "", "", false, "", "", 100, 2000, 0, 0);
    }

    @OpenApi(
            path = "/api/v1/station/manage/mail",
            methods = HttpMethod.PUT,
            summary = "Update station mail configuration",
            tags = {"Station Manage"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MailConfigRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailConfigResponse.class)))
    private void updateMailConfig(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(MailConfigRequest.class);

        MailProviderType provider;
        try {
            provider = body.provider() != null ? MailProviderType.valueOf(body.provider()) : MailProviderType.NONE;
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid provider: " + body.provider());
        }

        // Preserve existing password/apiKey if not provided (empty string = keep existing)
        var existing = mailConfigRepository.findByStation(session.stationId()).orElse(null);
        String smtpPassword = body.smtpPassword();
        String apiKey = body.apiKey();
        if (existing != null) {
            if (smtpPassword == null || smtpPassword.isEmpty()) smtpPassword = existing.smtpPassword();
            if (apiKey == null || apiKey.isEmpty()) apiKey = existing.apiKey();
        }
        if (smtpPassword == null) smtpPassword = "";
        if (apiKey == null) apiKey = "";

        var config = new StationMailConfig(
                session.stationId(),
                provider,
                body.smtpHost() != null ? body.smtpHost() : "",
                body.smtpPort() != null ? body.smtpPort() : 587,
                body.smtpSsl() != null ? body.smtpSsl() : false,
                body.smtpUser() != null ? body.smtpUser() : "",
                smtpPassword,
                body.senderAddress() != null ? body.senderAddress() : "",
                body.senderName() != null ? body.senderName() : "",
                apiKey,
                body.providerName() != null ? body.providerName() : "",
                body.providerUrl() != null ? body.providerUrl() : "",
                body.dailyLimit() != null ? body.dailyLimit() : 100,
                body.monthlyLimit() != null ? body.monthlyLimit() : 2000);

        mailConfigRepository.upsert(config);
        ctx.json(buildMailConfigResponse(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/station/manage/mail/test",
            methods = HttpMethod.POST,
            summary = "Test station mail configuration",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MailTestResponse.class)))
    private void testMailConfig(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var provider = emailService.resolveStationProvider(session.stationId());
        if (provider.isEmpty()) {
            ctx.json(new MailTestResponse(false, "No mail provider configured"));
            return;
        }
        String error = provider.get().testConnection();
        ctx.json(new MailTestResponse(error == null, error));
    }

    // -- Module settings --

    @OpenApi(
            path = "/api/v1/station/manage/modules",
            methods = HttpMethod.GET,
            summary = "Get disabled modules",
            tags = {"Station Manage"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ModulesResponse.class)))
    private void getDisabledModules(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(new ModulesResponse(stationService.findDisabledModules(session.stationId())));
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
        ctx.json(new ModulesResponse(stationService.findDisabledModules(session.stationId())));
    }

    public record ModulesResponse(Set<String> disabledModules) {}
}
