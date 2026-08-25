/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.knowledgebase.entity.ConversionStatus;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.service.KbCommentService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService.RemoteKbBrowse;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService.RemoteKbSearchResultItem;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Server-to-server knowledge-base routes. A federated partner reads what this station shares with
 * it and writes comments on those files; the caller is the partner verified from the request
 * signature, never a logged-in user.
 */
@Singleton
public class RemoteKnowledgeBaseRoutes implements Routes {

    public static final FederationEndpoint BROWSE_KB =
            FederationEndpoint.get(FederationSurface.KB_SHARE, "/remote/kb/browse", RemoteKbBrowse.class);
    public static final FederationEndpoint BROWSE_KB_FOLDER =
            FederationEndpoint.get(FederationSurface.KB_SHARE, "/remote/kb/folders/{id}/browse", RemoteKbBrowse.class);
    public static final FederationEndpoint SEARCH_KB =
            FederationEndpoint.getList(FederationSurface.KB_SHARE, "/remote/kb/search", RemoteKbSearchResultItem.class);
    public static final FederationEndpoint GET_FILE =
            FederationEndpoint.get(FederationSurface.KB_SHARE, "/remote/kb/files/{id}", RemoteKbFile.class);
    public static final FederationEndpoint GET_FILE_CONTENT = FederationEndpoint.get(
            FederationSurface.KB_SHARE, "/remote/kb/files/{id}/content", FileContentResponse.class);
    public static final FederationEndpoint LIST_COMMENTS = FederationEndpoint.getList(
            FederationSurface.KB_SHARE, "/remote/kb/files/{fileId}/comments", CommentResponse.class);
    public static final FederationEndpoint CREATE_COMMENT = FederationEndpoint.post(
            FederationSurface.KB_SHARE,
            "/remote/kb/files/{fileId}/comments",
            RemoteKbCommentRequest.class,
            CommentResponse.class);
    public static final FederationEndpoint UPDATE_COMMENT = FederationEndpoint.put(
            FederationSurface.KB_SHARE,
            "/remote/kb/comments/{commentId}",
            RemoteKbCommentUpdateRequest.class,
            CommentResponse.class);
    public static final FederationEndpoint DELETE_COMMENT = FederationEndpoint.delete(
            FederationSurface.KB_SHARE,
            "/remote/kb/comments/{commentId}",
            RemoteKbCommentDeleteRequest.class,
            Void.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            BROWSE_KB,
            BROWSE_KB_FOLDER,
            SEARCH_KB,
            GET_FILE,
            GET_FILE_CONTENT,
            LIST_COMMENTS,
            CREATE_COMMENT,
            UPDATE_COMMENT,
            DELETE_COMMENT);

    private final KbCommentService commentService;
    private final KnowledgeBaseFederationService federationService;

    @Inject
    public RemoteKnowledgeBaseRoutes(
            KbCommentService commentService, KnowledgeBaseFederationService federationService) {
        this.commentService = commentService;
        this.federationService = federationService;
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) throw new BadRequestResponse("content is required");
        return content;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, binder -> binder.handle(BROWSE_KB, this::browseKb)
                .handle(BROWSE_KB_FOLDER, this::browseKbFolder)
                .handle(SEARCH_KB, this::searchKb)
                .handle(GET_FILE, this::getFile)
                .handle(GET_FILE_CONTENT, this::getFileContent)
                .handle(LIST_COMMENTS, this::listComments)
                .handle(CREATE_COMMENT, this::createComment)
                .handle(UPDATE_COMMENT, this::updateComment)
                .handle(DELETE_COMMENT, this::deleteComment));
    }

    private void browseKb(Context ctx) {
        ctx.json(federationService.browseForPartner(FederationSession.requirePartner(ctx)));
    }

    private void browseKbFolder(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        ctx.json(federationService.folderForPartner(partner, pathInt(ctx, "id")));
    }

    private void searchKb(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        ctx.json(federationService.searchForPartner(partner, ctx.queryParam("q")));
    }

    private void getFile(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        ctx.json(federationService.remoteFileForPartner(partner, pathInt(ctx, "id")));
    }

    private void getFileContent(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int fileId = pathInt(ctx, "id");
        ctx.json(new FileContentResponse(fileId, federationService.fileContentForPartner(partner, fileId)));
    }

    private void listComments(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int fileId = pathInt(ctx, "fileId");
        federationService.fileForPartner(partner, fileId);
        ctx.json(federationService.listComments(fileId));
    }

    private void createComment(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int fileId = pathInt(ctx, "fileId");
        federationService.fileForPartner(partner, fileId);
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
        var partner = FederationSession.requirePartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteKbCommentUpdateRequest.class);
        var updated = federationService.updateRemoteComment(
                partner, commentId, req.remoteMemberUid(), requireContent(req.content()));
        ctx.json(federationService.toCommentResponse(updated));
    }

    private void deleteComment(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteKbCommentDeleteRequest.class);
        federationService.requireRemoteCommentAuthor(partner, commentId, req.remoteMemberUid(), "delete");
        if (!commentService.deleteComment(partner.stationId(), commentId)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * A knowledge-base file as served to a requesting partner. It deliberately does not reuse the
     * {@link KbFile} entity: that record carries this instance's internal numeric station id, which
     * the API layer rewrites into a UUID on the way out and cannot map back on the way in, and the
     * folder, position and restriction fields mean nothing to the partner reading it.
     */
    public record RemoteKbFile(
            int id,
            UUID stationUid,
            String name,
            String description,
            KbFileType fileType,
            String mimeType,
            long fileSize,
            String youtubeUrl,
            String linkUrl,
            Instant createdAt,
            Instant updatedAt,
            ConversionStatus conversionStatus) {

        public static RemoteKbFile of(KbFile file, UUID stationUid) {
            return new RemoteKbFile(
                    file.id(),
                    stationUid,
                    file.name(),
                    file.description(),
                    file.fileType(),
                    file.mimeType(),
                    file.fileSize(),
                    file.youtubeUrl(),
                    file.linkUrl(),
                    file.createdAt(),
                    file.updatedAt(),
                    file.conversionStatus());
        }
    }

    public record FileContentResponse(int fileId, String content) {}

    public record RemoteKbCommentRequest(UUID remoteMemberUid, String displayName, Integer parentId, String content) {}

    public record RemoteKbCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    public record RemoteKbCommentDeleteRequest(UUID remoteMemberUid) {}
}
