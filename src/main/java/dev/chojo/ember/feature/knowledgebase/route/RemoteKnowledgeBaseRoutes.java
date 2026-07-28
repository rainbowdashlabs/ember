/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Server-to-server knowledge-base routes. A federated partner reads what this station shares with
 * it and writes comments on those files; the caller is the partner verified from the request
 * signature, never a logged-in user.
 */
@Singleton
public class RemoteKnowledgeBaseRoutes implements Routes {

    private final KnowledgeBaseService service;
    private final KnowledgeBaseFederationService federationService;

    @Inject
    public RemoteKnowledgeBaseRoutes(KnowledgeBaseService service, KnowledgeBaseFederationService federationService) {
        this.service = service;
        this.federationService = federationService;
    }

    /**
     * Reads the partner verified from the request signature, answering {@code 403} when the request
     * carried none.
     */
    private static FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) throw new BadRequestResponse("content is required");
        return content;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/remote/kb/browse", this::browseKb);
        routes.get(prefix + "/remote/kb/search", this::searchKb);
        routes.get(prefix + "/remote/kb/files/{id}", this::getFile);
        routes.get(prefix + "/remote/kb/files/{id}/content", this::getFileContent);
        routes.get(prefix + "/remote/kb/files/{fileId}/comments", this::listComments);
        routes.post(prefix + "/remote/kb/files/{fileId}/comments", this::createComment);
        routes.put(prefix + "/remote/kb/comments/{commentId}", this::updateComment);
        routes.delete(prefix + "/remote/kb/comments/{commentId}", this::deleteComment);
    }

    private void browseKb(Context ctx) {
        ctx.json(federationService.browseForPartner(requireFederationPartner(ctx)));
    }

    private void searchKb(Context ctx) {
        var partner = requireFederationPartner(ctx);
        ctx.json(federationService.searchForPartner(partner, ctx.queryParam("q")));
    }

    private void getFile(Context ctx) {
        var partner = requireFederationPartner(ctx);
        ctx.json(federationService.fileForPartner(partner, pathInt(ctx, "id")));
    }

    private void getFileContent(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int fileId = pathInt(ctx, "id");
        ctx.json(new FileContentResponse(fileId, federationService.fileContentForPartner(partner, fileId)));
    }

    private void listComments(Context ctx) {
        requireFederationPartner(ctx);
        ctx.json(federationService.listComments(pathInt(ctx, "fileId")));
    }

    private void createComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int fileId = pathInt(ctx, "fileId");
        var req = ctx.bodyAsClass(RemoteKbCommentRequest.class);
        var comment = federationService.createRemoteComment(
                fileId,
                partner.id(),
                req.remoteMemberUid(),
                req.displayName(),
                req.parentId(),
                requireContent(req.content()));
        ctx.status(HttpStatus.CREATED).json(federationService.toCommentResponse(comment));
    }

    private void updateComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteKbCommentUpdateRequest.class);
        var updated = federationService.updateRemoteComment(
                partner, commentId, req.remoteMemberUid(), requireContent(req.content()));
        ctx.json(federationService.toCommentResponse(updated));
    }

    private void deleteComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteKbCommentDeleteRequest.class);
        federationService.requireRemoteCommentAuthor(partner, commentId, req.remoteMemberUid(), "delete");
        if (!service.deleteComment(partner.stationId(), commentId)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record FileContentResponse(int fileId, String content) {}

    public record RemoteKbCommentRequest(UUID remoteMemberUid, String displayName, Integer parentId, String content) {}

    public record RemoteKbCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    public record RemoteKbCommentDeleteRequest(UUID remoteMemberUid) {}
}
