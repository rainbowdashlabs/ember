/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.generator.service.BulkGenerationService;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.BulkPreviewResponse;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.GenerationJobResponse;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.GenerationJobSummary;
import dev.chojo.ember.feature.generator.service.BulkGenerationService.MemberSelection;
import dev.chojo.ember.feature.generator.service.DocumentIssuerService.IssuerChoice;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
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

import java.util.List;

/**
 * Generating one template for many members in a background run, as a manager: a look before the run,
 * starting it, and following it until every member is done. The right is the one for generating a
 * document for a single member.
 */
@Singleton
public class GenerationJobRoutes implements Routes {
    private final BulkGenerationService bulk;
    private final DocumentTemplateService templates;

    @Inject
    public GenerationJobRoutes(BulkGenerationService bulk, DocumentTemplateService templates) {
        this.bulk = bulk;
        this.templates = templates;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(
                prefix + "/document-generation/templates/{templateId}/jobs/preview",
                this::preview,
                StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(
                prefix + "/document-generation/templates/{templateId}/jobs",
                this::start,
                StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.get(prefix + "/document-generation/jobs", this::recent, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.get(prefix + "/document-generation/jobs/{jobId}", this::job, StationPermission.DOCUMENT_EDIT_MEMBER);
    }

    /**
     * A run to start.
     *
     * @param memberIds     the members one by one, in the order they are generated, or null to choose by
     *                      audience
     * @param audience      the audience whose current members the run generates for, or null for every
     *                      current member, read only where no members are named
     * @param acceptMissing whether a member with incomplete data still gets a document, its gaps left to
     *                      fill in by hand
     * @param issuer        the member to issue every document instead of the template's issuer, or null to
     *                      keep the template's
     */
    public record JobStartRequest(
            @Nullable List<Integer> memberIds,
            @Nullable RestrictionAudience audience,
            boolean acceptMissing,
            @Nullable IssuerChoice issuer) {}

    /**
     * A run to look at before it starts.
     *
     * @param memberIds the members one by one, or null to choose by audience
     * @param audience  the audience whose current members the run would generate for, or null for every
     *                  current member, read only where no members are named
     * @param issuer    the member to issue every document instead of the template's issuer, or null to keep
     *                  the template's
     */
    public record JobPreviewRequest(
            @Nullable List<Integer> memberIds,
            @Nullable RestrictionAudience audience,
            @Nullable IssuerChoice issuer) {}

    @OpenApi(
            path = "/api/v1/document-generation/templates/{templateId}/jobs/preview",
            methods = HttpMethod.POST,
            summary = "Draw a template for the first of many members and list what every member lacks",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = JobPreviewRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BulkPreviewResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void preview(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(JobPreviewRequest.class);
        var selection = new MemberSelection(request.memberIds(), request.audience());
        ctx.json(bulk.preview(session, requireOwnedTemplate(ctx), selection, request.issuer()));
    }

    @OpenApi(
            path = "/api/v1/document-generation/templates/{templateId}/jobs",
            methods = HttpMethod.POST,
            summary = "Start generating a template for many members in the background",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "templateId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = JobStartRequest.class)),
            responses = {
                @OpenApiResponse(status = "202", content = @OpenApiContent(from = GenerationJobResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void start(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(JobStartRequest.class);
        var selection = new MemberSelection(request.memberIds(), request.audience());
        ctx.status(HttpStatus.ACCEPTED)
                .json(bulk.start(
                        session, requireOwnedTemplate(ctx), selection, request.acceptMissing(), request.issuer()));
    }

    @OpenApi(
            path = "/api/v1/document-generation/jobs",
            methods = HttpMethod.GET,
            summary = "The latest runs that generate a template for many members, the newest first",
            tags = {"Documents"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = GenerationJobSummary[].class)))
    private void recent(Context ctx) {
        ctx.json(bulk.recent(StationSession.from(ctx)));
    }

    @OpenApi(
            path = "/api/v1/document-generation/jobs/{jobId}",
            methods = HttpMethod.GET,
            summary = "A run that generates a template for many members, with how it went for each",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "jobId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = GenerationJobResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void job(Context ctx) {
        ctx.json(bulk.job(StationSession.from(ctx).stationId(), RouteSupport.pathInt(ctx, "jobId")));
    }

    /** The template behind the path, which has to be one of the reader's station. */
    private int requireOwnedTemplate(Context ctx) {
        int templateId = RouteSupport.pathInt(ctx, "templateId");
        return templates
                .requireOwned(StationSession.from(ctx).owner(), templateId)
                .id();
    }
}
