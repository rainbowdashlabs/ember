/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.station.service.StationApplicationService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
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

/**
 * Routes for station application submission (public) and management (admin).
 * Handles the full application lifecycle: submit, verify, list, accept, and deny.
 */
@Singleton
public class StationApplicationRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(StationApplicationRoutes.class);

    private final StationApplicationService applicationService;
    private final ApplicationSettingRepository settingRepository;

    @Inject
    public StationApplicationRoutes(
            StationApplicationService applicationService, ApplicationSettingRepository settingRepository) {
        this.applicationService = applicationService;
        this.settingRepository = settingRepository;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Public endpoints (no auth required)
        routes.post(prefix + "/station-applications", this::submit);
        routes.post(prefix + "/station-applications/verify", this::verify);

        // Admin endpoints
        routes.get(prefix + "/admin/station-applications", this::list, Roles.ADMIN);
        routes.get(prefix + "/admin/station-applications/{id}", this::get, Roles.ADMIN);
        routes.post(prefix + "/admin/station-applications/{id}/accept", this::accept, Roles.ADMIN);
        routes.post(prefix + "/admin/station-applications/{id}/deny", this::deny, Roles.ADMIN);
    }

    @OpenApi(
            path = "/api/v1/station-applications",
            methods = HttpMethod.POST,
            summary = "Submit a station application",
            tags = {"Station Applications"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ApplicationRequest.class)),
            responses = {@OpenApiResponse(status = "201"), @OpenApiResponse(status = "400")})
    private void submit(Context ctx) {
        if (!settingRepository.getBoolean("station_registration_enabled", true)) {
            throw new ForbiddenResponse("Station registration is currently disabled");
        }
        var request = ctx.bodyAsClass(ApplicationRequest.class);
        if (isBlank(request.firstName())
                || isBlank(request.lastName())
                || isBlank(request.email())
                || isBlank(request.stationName())) {
            throw new BadRequestResponse("All fields are required");
        }
        var application = applicationService.submit(
                request.firstName(),
                request.lastName(),
                request.email(),
                request.stationName(),
                request.introduction());
        ctx.status(HttpStatus.CREATED).json(application);
    }

    @OpenApi(
            path = "/api/v1/station-applications/verify",
            methods = HttpMethod.POST,
            summary = "Verify a station application email",
            tags = {"Station Applications"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = VerifyRequest.class)),
            responses = {@OpenApiResponse(status = "204"), @OpenApiResponse(status = "404")})
    private void verify(Context ctx) {
        var request = ctx.bodyAsClass(VerifyRequest.class);
        if (isBlank(request.token())) {
            throw new BadRequestResponse("Token is required");
        }
        if (applicationService.verify(request.token())) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/admin/station-applications",
            methods = HttpMethod.GET,
            summary = "List all station applications",
            tags = {"Station Applications"},
            responses = @OpenApiResponse(status = "200"))
    private void list(Context ctx) {
        ctx.json(applicationService.findAll());
    }

    @OpenApi(
            path = "/api/v1/admin/station-applications/{id}",
            methods = HttpMethod.GET,
            summary = "Get a station application by ID",
            tags = {"Station Applications"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {@OpenApiResponse(status = "200"), @OpenApiResponse(status = "404")})
    private void get(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        applicationService.findById(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/admin/station-applications/{id}/accept",
            methods = HttpMethod.POST,
            summary = "Accept a station application",
            tags = {"Station Applications"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400"),
                @OpenApiResponse(status = "404")
            })
    private void accept(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        try {
            var result = applicationService.accept(id);
            ctx.json(result);
        } catch (IllegalArgumentException e) {
            log.warn("Station application not found for accept, id={}", id, e);
            throw new NotFoundResponse();
        } catch (IllegalStateException e) {
            log.warn("Invalid state when accepting station application id={}", id, e);
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/admin/station-applications/{id}/deny",
            methods = HttpMethod.POST,
            summary = "Deny a station application",
            tags = {"Station Applications"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DenyRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400"),
                @OpenApiResponse(status = "404")
            })
    private void deny(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(DenyRequest.class);
        try {
            var result = applicationService.deny(id, request.reason());
            ctx.json(result);
        } catch (IllegalArgumentException e) {
            log.warn("Station application not found for deny, id={}", id, e);
            throw new NotFoundResponse();
        } catch (IllegalStateException e) {
            log.warn("Invalid state when denying station application id={}", id, e);
            throw new BadRequestResponse(e.getMessage());
        }
    }

    /**
     * Request body for submitting a new station application.
     *
     * @param firstName    the applicant's first name
     * @param lastName     the applicant's last name
     * @param email        the applicant's email address
     * @param stationName  the desired station name
     * @param introduction optional introduction text
     */
    public record ApplicationRequest(
            String firstName, String lastName, String email, String stationName, String introduction) {}

    /**
     * Request body for verifying an application via email token.
     *
     * @param token the verification token
     */
    public record VerifyRequest(String token) {}

    /**
     * Request body for denying a station application.
     *
     * @param reason the reason for denial
     */
    public record DenyRequest(String reason) {}
}
