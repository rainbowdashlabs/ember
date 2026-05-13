/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.service.MemberImportService;
import dev.chojo.ember.service.MemberImportService.ColumnMapping;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Singleton
public class MemberImportRoutes implements Routes {
    private static final int MAX_CSV_BYTES = 2 * 1024 * 1024; // 2 MB

    private final MemberImportService importService;

    @Inject
    public MemberImportRoutes(MemberImportService importService) {
        this.importService = importService;
    }

    private static void validateCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new BadRequestResponse("csv is required");
        }
        if (csv.getBytes(StandardCharsets.UTF_8).length > MAX_CSV_BYTES) {
            throw new BadRequestResponse("CSV file exceeds maximum size of 2 MB");
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/members/import/parse", this::parse, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/members/import/preview", this::preview, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/members/import", this::importMembers, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/members/import-team/preview", this::previewTeam, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/members/import-team", this::importTeamMembers, Roles.MEMBER_MANAGEMENT);
    }

    @OpenApi(
            path = "/api/v1/members/import/parse",
            methods = HttpMethod.POST,
            summary = "Parse CSV and return headers + rows",
            tags = {"Member Import"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CsvRequest.class)),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        content = @OpenApiContent(from = MemberImportService.ParseResult.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void parse(Context ctx) {
        var request = ctx.bodyAsClass(CsvRequest.class);
        validateCsv(request.csv());
        ctx.json(importService.parseCsv(request.csv(), request.separator()));
    }

    @OpenApi(
            path = "/api/v1/members/import/preview",
            methods = HttpMethod.POST,
            summary = "Preview a CSV member import with column mappings",
            tags = {"Member Import"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ImportRequest.class)),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        content = @OpenApiContent(from = MemberImportService.PreviewResult.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void preview(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ImportRequest.class);
        validateCsv(request.csv());
        ctx.json(importService.preview(session.stationId(), request.csv(), request.separator(), request.mappings()));
    }

    @OpenApi(
            path = "/api/v1/members/import",
            methods = HttpMethod.POST,
            summary = "Import members from CSV with column mappings",
            tags = {"Member Import"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ImportRequest.class)),
            responses = {
                @OpenApiResponse(
                        status = "201",
                        content = @OpenApiContent(from = MemberImportService.ImportResult.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void importMembers(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ImportRequest.class);
        validateCsv(request.csv());
        ctx.status(HttpStatus.CREATED)
                .json(importService.importMembers(
                        session.stationId(), request.csv(), request.separator(), request.mappings()));
    }

    @OpenApi(
            path = "/api/v1/members/import-team/preview",
            methods = HttpMethod.POST,
            summary = "Preview a CSV team member import with column mappings",
            tags = {"Member Import"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ImportRequest.class)),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        content = @OpenApiContent(from = MemberImportService.PreviewResult.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void previewTeam(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ImportRequest.class);
        validateCsv(request.csv());
        ctx.json(importService.preview(session.stationId(), request.csv(), request.separator(), request.mappings()));
    }

    @OpenApi(
            path = "/api/v1/members/import-team",
            methods = HttpMethod.POST,
            summary = "Import team members from CSV with column mappings",
            tags = {"Member Import"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ImportRequest.class)),
            responses = {
                @OpenApiResponse(
                        status = "201",
                        content = @OpenApiContent(from = MemberImportService.TeamImportResult.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void importTeamMembers(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ImportRequest.class);
        validateCsv(request.csv());
        ctx.status(HttpStatus.CREATED)
                .json(importService.importTeamMembers(
                        session.stationId(), request.csv(), request.separator(), request.mappings()));
    }

    public record CsvRequest(String csv, String separator) {}

    public record ImportRequest(String csv, String separator, List<ColumnMapping> mappings) {}
}
