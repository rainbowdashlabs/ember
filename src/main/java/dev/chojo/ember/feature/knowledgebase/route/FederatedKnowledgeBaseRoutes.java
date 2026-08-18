/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseCommentRoutes.CreateKbCommentRequest;
import dev.chojo.ember.feature.knowledgebase.route.KnowledgeBaseCommentRoutes.UpdateKbCommentRequest;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes.FileContentResponse;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.util.SafeContentDisposition;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;

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

    @Inject
    public FederatedKnowledgeBaseRoutes(KnowledgeBaseFederationService federationService) {
        this.federationService = federationService;
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) throw new BadRequestResponse("content is required");
        return content;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/federated/kb", this::browseKb, StationPermission.USER);
        routes.get(prefix + "/federated/{stationuid}/kb/files/{id}", this::getFile, StationPermission.USER);
        routes.get(
                prefix + "/federated/{stationuid}/kb/files/{id}/content", this::getFileContent, StationPermission.USER);
        routes.get(prefix + "/federated/{stationuid}/kb/files/{id}/pdf", this::getFilePdf, StationPermission.USER);
        routes.post(prefix + "/federated/kb/files/{id}/copy", this::copyFile, StationPermission.KNOWLEDGE_EDIT);
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

    private void browseKb(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(federationService.browseFederatedKb(session.stationId()));
    }

    private void getFile(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(federationService.getFederatedKbFile(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "id")));
    }

    private void getFileContent(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = pathInt(ctx, "id");
        var content =
                federationService.getFederatedKbFileContent(session.stationId(), pathUuid(ctx, "stationuid"), fileId);
        ctx.json(new FileContentResponse(fileId, content));
    }

    private void getFilePdf(Context ctx) {
        var session = UserSession.from(ctx);
        try {
            var rendered = federationService.renderFederatedKbFilePdf(
                    session.stationId(),
                    pathUuid(ctx, "stationuid"),
                    pathInt(ctx, "id"),
                    session.account().fullName().trim());
            ctx.contentType("application/pdf");
            ctx.header(
                    "Content-Disposition",
                    SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, rendered.fileName()));
            ctx.result(rendered.data());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServerErrorResponse("Failed to render PDF");
        } catch (IOException e) {
            throw new InternalServerErrorResponse("Failed to render PDF");
        }
    }

    private void copyFile(Context ctx) {
        var session = UserSession.from(ctx);
        var copied = federationService.copyKbFile(
                pathInt(ctx, "id"), session.stationId(), session.member().id());
        ctx.status(HttpStatus.CREATED).json(copied);
    }

    private void listComments(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(federationService.listFederatedComments(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "fileId")));
    }

    private void createComment(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateKbCommentRequest.class);
        var created = federationService.createFederatedComment(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "fileId"),
                session.member().uid(),
                session.account().fullName().trim(),
                req.parentId(),
                requireContent(req.content()));
        ctx.status(HttpStatus.CREATED).json(created);
    }

    private void updateComment(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(UpdateKbCommentRequest.class);
        ctx.json(federationService.updateFederatedComment(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "commentId"),
                session.member().uid(),
                requireContent(req.content())));
    }

    private void deleteComment(Context ctx) {
        var session = UserSession.from(ctx);
        boolean deleted = federationService.deleteFederatedComment(
                session.stationId(),
                pathUuid(ctx, "stationuid"),
                pathInt(ctx, "commentId"),
                session.member().uid());
        if (!deleted) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
