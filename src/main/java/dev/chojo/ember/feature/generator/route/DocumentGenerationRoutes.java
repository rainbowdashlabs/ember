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
import dev.chojo.ember.feature.generator.service.DocumentGenerationService;
import dev.chojo.ember.feature.generator.service.DocumentGenerationService.GeneratedDocumentResponse;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.PreviewResponse;
import dev.chojo.ember.feature.generator.service.DocumentIssuerService.IssuerChoice;
import dev.chojo.ember.feature.generator.service.DocumentTemplateRequest;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateSummary;
import dev.chojo.ember.feature.generator.service.SelfServiceDocumentService;
import dev.chojo.ember.feature.generator.service.SelfServiceDocumentService.SelfServiceOffer;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
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

import java.util.Objects;

/**
 * Generating documents from templates: by a manager for any member, and by a member for themselves
 * and the members in their care.
 *
 * <p>A manager needs the right to file documents for members, the same right as uploading one for
 * them. Self service needs no right beyond being signed in; whom the reader may act for, and which
 * templates are offered, is the service's to decide.
 */
@Singleton
public class DocumentGenerationRoutes implements Routes {
    private final DocumentGenerationService generation;
    private final SelfServiceDocumentService selfService;
    private final DocumentTemplateService templates;
    private final StationMemberService members;

    @Inject
    public DocumentGenerationRoutes(
            DocumentGenerationService generation,
            SelfServiceDocumentService selfService,
            DocumentTemplateService templates,
            StationMemberService members) {
        this.generation = generation;
        this.selfService = selfService;
        this.templates = templates;
        this.members = members;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(
                prefix + "/document-template-preview", this::previewDraft, StationPermission.DOCUMENT_TEMPLATE_EDIT);
        routes.get(prefix + "/document-generation/templates", this::usable, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(
                prefix + "/document-generation/templates/{templateId}/members/{memberId}/preview",
                this::preview,
                StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.post(
                prefix + "/document-generation/templates/{templateId}/members/{memberId}",
                this::generate,
                StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.get(prefix + "/self-service/documents/{memberId}", this::offers, StationPermission.LOGIN);
        routes.post(
                prefix + "/self-service/documents/{memberId}/templates/{templateId}",
                this::generateForSelf,
                StationPermission.LOGIN);
    }

    /**
     * A template the editor holds, to be drawn before it is saved.
     *
     * @param template   the template as the editor holds it
     * @param templateId the saved template it changes, whose PDF a PDF template fills, or null for a new one
     * @param memberId   the member to draw it for, or null to show the placeholders by their labels
     */
    public record DraftPreviewRequest(
            @Nullable DocumentTemplateRequest template,
            @Nullable Integer templateId,
            @Nullable Integer memberId) {}

    @OpenApi(
            path = "/api/v1/document-template-preview",
            methods = HttpMethod.POST,
            summary = "Draw a document template as the editor holds it, for a member or with its placeholders",
            tags = {"Documents"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DraftPreviewRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PreviewResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void previewDraft(Context ctx) {
        var session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(DraftPreviewRequest.class);
        Integer memberId = request.memberId();
        if (memberId != null) requireOwnedMember(ctx, memberId);
        var template = Objects.requireNonNullElseGet(request.template(), DocumentGenerationRoutes::emptyTemplate);
        ctx.json(generation.previewDraft(session, template, request.templateId(), memberId));
    }

    /** @return a template with nothing written in it, what a preview without a template draws */
    static DocumentTemplateRequest emptyTemplate() {
        return new DocumentTemplateRequest(
                null, null, null, null, null, false, null, false, false, false, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    @OpenApi(
            path = "/api/v1/document-generation/templates",
            methods = HttpMethod.GET,
            summary = "The document templates a manager can generate documents from",
            tags = {"Documents"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentTemplateSummary[].class)))
    private void usable(Context ctx) {
        ctx.json(generation.usable(StationSession.from(ctx).owner()));
    }

    /**
     * How a manager wants a document generated, beyond the template and the member.
     *
     * @param issuer the member to issue it instead of the template's issuer, or null to keep the template's
     */
    public record ManagerGenerationRequest(@Nullable IssuerChoice issuer) {

        /** Nothing asked beyond the template's own settings. */
        static final ManagerGenerationRequest AS_THE_TEMPLATE_SAYS = new ManagerGenerationRequest(null);

        static ManagerGenerationRequest of(Context ctx) {
            return ctx.body().isBlank() ? AS_THE_TEMPLATE_SAYS : ctx.bodyAsClass(ManagerGenerationRequest.class);
        }
    }

    @OpenApi(
            path = "/api/v1/document-generation/templates/{templateId}/members/{memberId}/preview",
            methods = HttpMethod.POST,
            summary = "Draw a document for a member without filing it, listing the values that are missing",
            tags = {"Documents"},
            pathParams = {
                @OpenApiParam(name = "templateId", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ManagerGenerationRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PreviewResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void preview(Context ctx) {
        var session = StationSession.from(ctx);
        int memberId = requireOwnedMember(ctx, RouteSupport.pathInt(ctx, "memberId"));
        var request = ManagerGenerationRequest.of(ctx);
        ctx.json(generation.preview(session, requireOwnedTemplate(ctx), memberId, request.issuer()));
    }

    @OpenApi(
            path = "/api/v1/document-generation/templates/{templateId}/members/{memberId}",
            methods = HttpMethod.POST,
            summary = "Generate a document for a member and file it in their documents",
            tags = {"Documents"},
            pathParams = {
                @OpenApiParam(name = "templateId", type = Integer.class, required = true),
                @OpenApiParam(name = "memberId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ManagerGenerationRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = GeneratedDocumentResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void generate(Context ctx) {
        var session = StationSession.from(ctx);
        int memberId = requireOwnedMember(ctx, RouteSupport.pathInt(ctx, "memberId"));
        var request = ManagerGenerationRequest.of(ctx);
        ctx.status(HttpStatus.CREATED)
                .json(generation.generate(session, requireOwnedTemplate(ctx), memberId, request.issuer()));
    }

    @OpenApi(
            path = "/api/v1/self-service/documents/{memberId}",
            methods = HttpMethod.GET,
            summary = "The documents the reader may generate for themselves or a member in their care",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SelfServiceOffer[].class)))
    private void offers(Context ctx) {
        var session = StationSession.from(ctx);
        int memberId = requireOwnedMember(ctx, RouteSupport.pathInt(ctx, "memberId"));
        ctx.json(selfService.offers(session, memberId));
    }

    @OpenApi(
            path = "/api/v1/self-service/documents/{memberId}/templates/{templateId}",
            methods = HttpMethod.POST,
            summary = "Generate a document for oneself or a member in one's care and file it with them",
            tags = {"Documents"},
            pathParams = {
                @OpenApiParam(name = "memberId", type = Integer.class, required = true),
                @OpenApiParam(name = "templateId", type = Integer.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = GeneratedDocumentResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void generateForSelf(Context ctx) {
        var session = StationSession.from(ctx);
        int memberId = requireOwnedMember(ctx, RouteSupport.pathInt(ctx, "memberId"));
        ctx.status(HttpStatus.CREATED).json(selfService.generate(session, requireOwnedTemplate(ctx), memberId));
    }

    /** The member behind an id, which has to be of the reader's station. */
    private int requireOwnedMember(Context ctx, int memberId) {
        return RouteSupport.requireOwnedOrNotFound(ctx, memberId, members::findById, StationMember::stationId)
                .id();
    }

    /** The template behind the path, which has to be one the reader's station uses: its own or its association's. */
    private int requireOwnedTemplate(Context ctx) {
        int templateId = RouteSupport.pathInt(ctx, "templateId");
        return templates
                .requireUsable(StationSession.from(ctx).stationId(), templateId)
                .id();
    }
}
