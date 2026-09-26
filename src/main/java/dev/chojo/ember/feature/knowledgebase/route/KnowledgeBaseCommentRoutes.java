/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbCommentService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.requireOwnedFile;

/**
 * Comments members of this station write on their own knowledge-base files.
 */
@Singleton
public class KnowledgeBaseCommentRoutes implements Routes {

    private final KnowledgeBaseService service;
    private final KbCommentService commentService;
    private final KbAuthorNameService authorNameService;
    private final KnowledgeBaseFederationService federationService;
    private final KbCommentRepository commentRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public KnowledgeBaseCommentRoutes(
            KnowledgeBaseService service,
            KbCommentService commentService,
            KbAuthorNameService authorNameService,
            KnowledgeBaseFederationService federationService,
            KbCommentRepository commentRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.service = service;
        this.commentService = commentService;
        this.authorNameService = authorNameService;
        this.federationService = federationService;
        this.commentRepository = commentRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) throw Refusal.KB_COMMENT_EMPTY.raise();
        return content;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/kb/files/{fileId}/comments", this::listComments, StationPermission.LOGIN);
        routes.post(prefix + "/kb/files/{fileId}/comments", this::createComment, StationPermission.LOGIN);
        routes.put(prefix + "/kb/comments/{commentId}", this::updateComment, StationPermission.LOGIN);
        routes.delete(prefix + "/kb/comments/{commentId}", this::deleteComment, StationPermission.LOGIN);
    }

    private void listComments(Context ctx) {
        int fileId = pathInt(ctx, "fileId");
        requireOwnedFile(ctx, service, fileId);
        ctx.json(federationService.listComments(fileId));
    }

    private void createComment(Context ctx) {
        int fileId = pathInt(ctx, "fileId");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, fileId);
        var req = ctx.bodyAsClass(CreateKbCommentRequest.class);
        String content = requireContent(req.content());
        String authorName = authorNameService.resolveMemberName(session.member().id());
        var comment = commentService.createComment(
                session.stationId(), fileId, req.parentId(), session.member().id(), authorName, content);
        ctx.status(HttpStatus.CREATED).json(federationService.toCommentResponse(comment));
    }

    private void updateComment(Context ctx) {
        int commentId = pathInt(ctx, "commentId");
        var session = UserSession.from(ctx);
        var comment = requireOwnedComment(ctx, commentId);
        var memberIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        if (comment.author() == null || !comment.author().sameMember(memberIdentity)) {
            throw Refusal.KB_COMMENT_NOT_YOURS_TO_CHANGE.raise();
        }
        var req = ctx.bodyAsClass(UpdateKbCommentRequest.class);
        commentRepository.update(commentId, requireContent(req.content()));
        var updated =
                commentRepository.findById(commentId).orElseThrow(Refusal.KB_COMMENT_NOT_HERE_AFTER_CHANGE::raise);
        ctx.json(federationService.toCommentResponse(updated));
    }

    private void deleteComment(Context ctx) {
        int commentId = pathInt(ctx, "commentId");
        var session = UserSession.from(ctx);
        var comment = requireOwnedComment(ctx, commentId);
        var authorIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        boolean isAuthor = comment.author() != null && comment.author().sameMember(authorIdentity);
        if (!isAuthor && !session.hasPermission(StationPermission.KNOWLEDGE_MANAGER)) {
            throw Refusal.KB_COMMENT_NOT_YOURS_TO_DELETE.raise();
        }
        if (!commentService.deleteComment(session.stationId(), commentId)) {
            throw Refusal.KB_COMMENT_NOT_DELETED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Loads a comment and asserts the caller's station owns the file it belongs to, returning the
     * comment. Answers 404 when the comment is absent or the file belongs to another station.
     */
    private KbComment requireOwnedComment(Context ctx, int commentId) {
        var comment = commentRepository.findById(commentId).orElseThrow(Refusal.KB_COMMENT_NOT_HERE::raise);
        requireOwnedFile(ctx, service, comment.fileId());
        return comment;
    }

    public record CreateKbCommentRequest(Integer parentId, String content) {}

    public record UpdateKbCommentRequest(String content) {}
}
