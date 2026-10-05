/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.KnowledgeBaseRefusal;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseCommentRoutes.CreateKbCommentRequest;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseCommentRoutes.UpdateKbCommentRequest;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes.FileContentResponse;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes.RemoteKbFile;
import dev.chojo.ember.feature.knowledgebase.service.KbFavouriteService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService.FederatedKbBrowse;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.util.SafeContentDisposition;
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

import java.io.IOException;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * User-facing routes over content held by federation partners: browsing and reading their
 * knowledge-base files, copying one into this station, and commenting on them. Whether a partner
 * lives on this instance or on another one is resolved in the service.
 */
@Singleton
public class FederatedKnowledgeBaseRoutes implements Routes {

    private final KnowledgeBaseFederationService federationService;
    private final KbFavouriteService favourites;

    @Inject
    public FederatedKnowledgeBaseRoutes(
            KnowledgeBaseFederationService federationService, KbFavouriteService favourites) {
        this.federationService = federationService;
        this.favourites = favourites;
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) throw KnowledgeBaseRefusal.PARTNER_KB_COMMENT_EMPTY.raise();
        return content;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/federated/kb", this::browseKb, StationPermission.USER);
        routes.get(prefix + "/federated/{stationuid}/kb/folders/{id}", this::browseKbFolder, StationPermission.USER);
        routes.get(prefix + "/federated/{stationuid}/kb/files/{id}", this::getFile, StationPermission.USER);
        routes.get(
                prefix + "/federated/{stationuid}/kb/files/{id}/content", this::getFileContent, StationPermission.USER);
        routes.get(prefix + "/federated/{stationuid}/kb/files/{id}/pdf", this::getFilePdf, StationPermission.USER);
        routes.post(
                prefix + "/federated/{stationuid}/kb/files/{id}/copy",
                this::copyFile,
                StationPermission.KNOWLEDGE_EDIT);

        routes.get(
                prefix + "/federated/{stationuid}/kb/files/{fileId}/comments",
                this::listComments,
                StationPermission.LOGIN);
        routes.post(
                prefix + "/federated/{stationuid}/kb/files/{fileId}/comments",
                this::createComment,
                StationPermission.LOGIN);
        routes.put(
                prefix + "/federated/{stationuid}/kb/comments/{commentId}",
                this::updateComment,
                StationPermission.LOGIN);
        routes.delete(
                prefix + "/federated/{stationuid}/kb/comments/{commentId}",
                this::deleteComment,
                StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/federated/kb",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederatedKbBrowse.class)))
    private void browseKb(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(federationService.browseFederatedKb(session.stationId(), session.userType()));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/kb/folders/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederatedKbBrowse.class)))
    private void browseKbFolder(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(federationService.browseFederatedKbFolder(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "id"), session.userType()));
    }

    /**
     * A partner's file, which is also the moment a favourite of it learns its current name: the
     * partner has just answered for it, and a renamed file should not go on showing its old one.
     */
    @OpenApi(
            path = "/api/v1/federated/{stationuid}/kb/files/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RemoteKbFile.class)))
    private void getFile(Context ctx) {
        var session = StationSession.from(ctx);
        UUID partner = pathUuid(ctx, "stationuid");
        int fileId = pathInt(ctx, "id");
        var file = federationService.getFederatedKbFile(session.stationId(), partner, fileId);
        favourites.refreshPartnerFile(partner, fileId, federationService.describe(session.stationId(), partner, file));
        ctx.json(file);
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/kb/files/{id}/content",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FileContentResponse.class)))
    private void getFileContent(Context ctx) {
        var session = StationSession.from(ctx);
        int fileId = pathInt(ctx, "id");
        var content =
                federationService.getFederatedKbFileContent(session.stationId(), pathUuid(ctx, "stationuid"), fileId);
        ctx.json(new FileContentResponse(fileId, content));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/kb/files/{id}/pdf",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void getFilePdf(Context ctx) {
        var session = StationSession.from(ctx);
        try {
            var rendered = federationService.renderFederatedKbFilePdf(
                    session.stationId(),
                    pathUuid(ctx, "stationuid"),
                    pathInt(ctx, "id"),
                    NameParts.of(session.user().account()).called());
            ctx.contentType("application/pdf");
            ctx.header(
                    "Content-Disposition",
                    SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, rendered.fileName()));
            ctx.result(rendered.data());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw KnowledgeBaseRefusal.PARTNER_KB_PDF_STOPPED.raise();
        } catch (IOException e) {
            throw KnowledgeBaseRefusal.PARTNER_KB_PDF_NOT_MADE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/kb/files/{id}/copy",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = KbFile.class)))
    private void copyFile(Context ctx) {
        var session = StationSession.from(ctx);
        var copied = federationService.copyKbFile(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "id"),
                session.member().id());
        favourites.carryOverToCopy(session.member().id(), copied.id());
        ctx.status(HttpStatus.CREATED).json(copied);
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/kb/files/{fileId}/comments",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void listComments(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(federationService.listFederatedComments(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "fileId")));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/kb/files/{fileId}/comments",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateKbCommentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = CommentResponse.class)))
    private void createComment(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(CreateKbCommentRequest.class);
        var created = federationService.createFederatedComment(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "fileId"),
                session.member().uid(),
                NameParts.of(session.user().account()).called(),
                req.parentId(),
                requireContent(req.content()));
        ctx.status(HttpStatus.CREATED).json(created);
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/kb/comments/{commentId}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateKbCommentRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse.class)))
    private void updateComment(Context ctx) {
        var session = StationSession.from(ctx);
        var req = ctx.bodyAsClass(UpdateKbCommentRequest.class);
        ctx.json(federationService.updateFederatedComment(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "commentId"),
                session.member().uid(),
                requireContent(req.content())));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/kb/comments/{commentId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteComment(Context ctx) {
        var session = StationSession.from(ctx);
        federationService.deleteFederatedComment(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "commentId"),
                session.member().uid());
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
