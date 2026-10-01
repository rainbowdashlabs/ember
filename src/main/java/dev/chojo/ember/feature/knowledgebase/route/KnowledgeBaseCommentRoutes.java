/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Comments members of this station write on their own knowledge-base files.
 */
@Singleton
public class KnowledgeBaseCommentRoutes implements Routes {

    private final CommentService commentService;
    private final KbAuthorNameService authorNameService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public KnowledgeBaseCommentRoutes(
            CommentService commentService,
            KbAuthorNameService authorNameService,
            MemberIdentityFactory memberIdentityFactory,
            MemberNameResolver memberNameResolver) {
        this.commentService = commentService;
        this.authorNameService = authorNameService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.memberNameResolver = memberNameResolver;
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

    @OpenApi(
            path = "/api/v1/kb/files/{fileId}/comments",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void listComments(Context ctx) {
        int fileId = pathInt(ctx, "fileId");
        commentService.requireReadable(UserSession.from(ctx), CommentEntityType.KB, fileId);
        ctx.json(commentService.list(CommentEntityType.KB, fileId, CommentFilter.ALL).stream()
                .map(this::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/kb/files/{fileId}/comments",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateKbCommentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = CommentResponse.class)))
    private void createComment(Context ctx) {
        int fileId = pathInt(ctx, "fileId");
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateKbCommentRequest.class);
        var comment = commentService.create(
                session,
                CommentEntityType.KB,
                fileId,
                writer(session),
                new NewComment(req.parentId(), null, requireContent(req.content())));
        ctx.status(HttpStatus.CREATED).json(toResponse(comment));
    }

    @OpenApi(
            path = "/api/v1/kb/comments/{commentId}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateKbCommentRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse.class)))
    private void updateComment(Context ctx) {
        int commentId = pathInt(ctx, "commentId");
        var session = UserSession.from(ctx);
        var comment = requireOwnedComment(session, commentId);
        if (!commentService.mayModify(session, actor(session), comment, Moderation.EDIT)) {
            throw Refusal.KB_COMMENT_NOT_YOURS_TO_CHANGE.raise();
        }
        var req = ctx.bodyAsClass(UpdateKbCommentRequest.class);
        var updated = commentService
                .update(comment, writer(session), requireContent(req.content()))
                .orElseThrow(Refusal.KB_COMMENT_NOT_HERE_AFTER_CHANGE::raise);
        ctx.json(toResponse(updated));
    }

    @OpenApi(
            path = "/api/v1/kb/comments/{commentId}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void deleteComment(Context ctx) {
        int commentId = pathInt(ctx, "commentId");
        var session = UserSession.from(ctx);
        var comment = requireOwnedComment(session, commentId);
        if (!commentService.mayModify(session, actor(session), comment, Moderation.DELETE)) {
            throw Refusal.KB_COMMENT_NOT_YOURS_TO_DELETE.raise();
        }
        if (!commentService.delete(comment)) {
            throw Refusal.KB_COMMENT_NOT_DELETED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Loads a comment and asserts the caller's station owns the file it belongs to, returning the
     * comment. Answers 404 when the comment is absent or the file belongs to another station.
     */
    private Comment requireOwnedComment(UserSession session, int commentId) {
        var comment = commentService
                .findById(CommentEntityType.KB, commentId)
                .orElseThrow(Refusal.KB_COMMENT_NOT_HERE::raise);
        commentService.requireReadable(session, CommentEntityType.KB, comment.targetId());
        return comment;
    }

    private MemberIdentity actor(UserSession session) {
        return memberIdentityFactory.local(session.stationId(), session.member().id());
    }

    private CommentWriter writer(UserSession session) {
        return CommentWriter.local(
                actor(session),
                authorNameService.resolveMemberName(session.member().id()));
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponseMapper.fromKb(memberNameResolver, comment);
    }

    public record CreateKbCommentRequest(Integer parentId, String content) {}

    public record UpdateKbCommentRequest(String content) {}
}
