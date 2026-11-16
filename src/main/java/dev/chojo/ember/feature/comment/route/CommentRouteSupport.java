/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.CommentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes.CreateCommentRequest;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes.UpdateCommentRequest;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.function.Function;

/**
 * The comment routes of one kind of thing a station member comments on: listing, writing, changing and
 * removing, the same for an appointment and an event group.
 *
 * <p>Each kind keeps its own routes class, whose handlers carry the route's description and decide how the
 * thing and a comment on it are found; the handlers then hand the request to this helper. Every comment
 * route asks for the right to log in on the station and for the module the thing belongs to. A comment is
 * changed by its author only and removed by its author or whoever moderates the thing.
 */
public final class CommentRouteSupport {
    private final CommentService comments;
    private final MemberIdentityFactory identities;
    private final CommentEntityType type;
    private final Function<Comment, CommentResponse> toResponse;

    /**
     * @param comments   the comment service
     * @param identities builds the identity a local member writes under
     * @param type       the kind of thing commented on
     * @param toResponse how a comment of that kind is answered
     */
    public CommentRouteSupport(
            CommentService comments,
            MemberIdentityFactory identities,
            CommentEntityType type,
            Function<Comment, CommentResponse> toResponse) {
        this.comments = comments;
        this.identities = identities;
        this.type = type;
        this.toResponse = toResponse;
    }

    /**
     * Registers the four comment routes of the kind.
     *
     * @param routes   the router
     * @param module   the module the thing belongs to
     * @param listPath the path naming the thing, for listing and writing
     * @param itemPath the path naming one comment, for changing and removing
     * @param list     answers the listing
     * @param create   answers a new comment
     * @param update   answers a change
     * @param delete   answers a removal
     */
    public void register(
            JavalinDefaultRoutingApi routes,
            StationModule module,
            String listPath,
            String itemPath,
            Handler list,
            Handler create,
            Handler update,
            Handler delete) {
        routes.get(listPath, list, StationPermission.LOGIN, module);
        routes.post(listPath, create, StationPermission.LOGIN, module);
        routes.put(itemPath, update, StationPermission.LOGIN, module);
        routes.delete(itemPath, delete, StationPermission.LOGIN, module);
    }

    /**
     * Answers with the comments on a thing the reader may read.
     *
     * @param ctx      the request
     * @param targetId the thing
     * @param filter   which of its comments are asked for
     */
    public void list(Context ctx, int targetId, CommentFilter filter) {
        comments.requireReadable(StationSession.from(ctx), type, targetId);
        ctx.json(comments.list(type, targetId, filter).stream().map(toResponse).toList());
    }

    /**
     * Writes the comment the request body carries on a thing.
     *
     * @param ctx        the request
     * @param targetId   the thing
     * @param occurrence the day of the thing the comment is about, read from the request, or null for the
     *                   thing as a whole
     */
    public void create(Context ctx, int targetId, Function<CreateCommentRequest, @Nullable LocalDate> occurrence) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CreateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw CommentRefusal.COMMENT_NEEDS_TEXT.raise();
        }
        var comment = comments.create(
                session,
                type,
                targetId,
                writer(session),
                new NewComment(request.parentId(), occurrence.apply(request), request.content()));
        ctx.status(HttpStatus.CREATED).json(toResponse.apply(comment));
    }

    /**
     * A comment of the kind by its id.
     *
     * @param commentId the comment
     * @param missing   the refusal where there is none
     * @return the comment
     */
    public Comment comment(int commentId, Refusal missing) {
        return comments.findById(type, commentId).orElseThrow(missing::raise);
    }

    /**
     * Changes a comment to the text the request body carries, where the reader wrote it.
     *
     * @param ctx     the request
     * @param comment the comment, already found
     */
    public void update(Context ctx, Comment comment) {
        StationSession session = StationSession.from(ctx);
        if (!comments.mayModify(session, actor(session), comment, Moderation.EDIT)) {
            throw CommentRefusal.COMMENT_NOT_YOURS_TO_CHANGE.raise();
        }
        var request = ctx.bodyAsClass(UpdateCommentRequest.class);
        if (request.content() == null || request.content().isBlank()) {
            throw CommentRefusal.COMMENT_CHANGE_NEEDS_TEXT.raise();
        }
        var updated = comments.update(comment, writer(session), request.content())
                .orElseThrow(CommentRefusal.COMMENT_NOT_HERE_AFTER_CHANGE::raise);
        ctx.json(toResponse.apply(updated));
    }

    /**
     * Removes a comment of the reader's station, where the reader wrote it or moderates the thing.
     *
     * @param ctx     the request
     * @param comment the comment, already found
     */
    public void delete(Context ctx, Comment comment) {
        StationSession session = StationSession.from(ctx);
        comments.requireSameStation(session, comment);
        if (!comments.mayModify(session, actor(session), comment, Moderation.DELETE)) {
            throw CommentRefusal.COMMENT_NOT_YOURS_TO_DELETE.raise();
        }
        if (!comments.delete(comment)) {
            throw CommentRefusal.COMMENT_NOT_DELETED.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private MemberIdentity actor(StationSession session) {
        return identities.local(session.stationId(), session.member().id());
    }

    private CommentWriter writer(StationSession session) {
        return CommentWriter.local(
                actor(session), NameParts.of(session.user().account()).called());
    }
}
