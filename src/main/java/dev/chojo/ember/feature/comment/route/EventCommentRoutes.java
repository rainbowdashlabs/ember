/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * HTTP route definitions for event comments.
 * Provides endpoints for CRUD operations on comments attached to events.
 */
@Singleton
public class EventCommentRoutes implements Routes {
    private final CommentService commentService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public EventCommentRoutes(
            CommentService commentService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.commentService = commentService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/{eventId}/comments", this::list, Roles.LOGIN);
        routes.post(prefix + "/events/{eventId}/comments", this::create, Roles.LOGIN);
        routes.put(prefix + "/events/comments/{commentId}", this::update, Roles.LOGIN);
        routes.delete(prefix + "/events/comments/{commentId}", this::delete, Roles.LOGIN);
    }

    private void list(Context ctx) {
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        var comments = commentService.findByEvent(eventId);
        ctx.json(comments.stream().map(this::toResponse).toList());
    }

    private void create(Context ctx) {
        int eventId = ctx.pathParamAsClass("eventId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var comment = commentService.create(
                session.stationId(),
                eventId,
                request.parentId(),
                session.member().id(),
                session.account().fullName().trim(),
                request.content());
        ctx.status(HttpStatus.CREATED).json(toResponse(comment));
    }

    private void update(Context ctx) {
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        if (comment.authorId() != session.member().id()) {
            throw new ForbiddenResponse("You can only edit your own comments");
        }
        var request = ctx.bodyAsClass(UpdateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        commentService.update(commentId, request.content());
        var updated = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        ctx.json(toResponse(updated));
    }

    private void delete(Context ctx) {
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        boolean isAuthor = comment.authorId() == session.member().id();
        boolean canModerate = session.hasRole(Roles.EVENT_MANAGER);
        if (!isAuthor && !canModerate) {
            throw new ForbiddenResponse("You can only delete your own comments");
        }
        if (commentService.delete(commentId)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private CommentResponse toResponse(Comment comment) {
        var memberOpt = stationMemberRepository.findById(comment.authorId());
        Integer authorAccountId = memberOpt.map(StationMember::accountId).orElse(null);
        String authorName = memberOpt
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
        return new CommentResponse(
                comment.id(),
                comment.parentId(),
                comment.authorId(),
                authorAccountId,
                authorName,
                comment.content(),
                comment.createdAt(),
                comment.updatedAt());
    }

    /**
     * Request body for creating a comment.
     */
    public record CreateCommentRequest(Integer parentId, String content) {}

    /**
     * Request body for updating a comment.
     */
    public record UpdateCommentRequest(String content) {}

    /**
     * API response representing a comment with resolved author information.
     */
    public record CommentResponse(
            int id,
            Integer parentId,
            int authorId,
            Integer authorAccountId,
            String authorName,
            String content,
            Instant createdAt,
            Instant updatedAt) {}
}
