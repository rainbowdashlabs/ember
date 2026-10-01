/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Single source of truth for turning a comment of any commentable surface into a
 * {@link CommentResponse}. Centralises the two rules every surface repeated: a soft-deleted
 * comment is stripped down to an empty body without author information, and a live comment
 * carries an author identity enriched with display metadata.
 *
 * <p>The resolver is passed in rather than injected so route classes and services can call these
 * methods with the {@link MemberNameResolver} they already hold, mirroring
 * {@link dev.chojo.ember.feature.federation.service.FederationDisplayNames}.
 */
public final class CommentResponseMapper {

    private CommentResponseMapper() {}

    /**
     * Maps an event comment, carrying the occurrence date of date-scoped comments.
     */
    public static CommentResponse fromEvent(MemberNameResolver resolver, Comment comment) {
        return withResolvedName(resolver, new Scope(null, null, null, comment.eventDate()), comment);
    }

    /**
     * Maps a news comment.
     */
    public static CommentResponse fromNews(MemberNameResolver resolver, Comment comment) {
        return withResolvedName(resolver, new Scope(comment.targetId(), null, null, null), comment);
    }

    /**
     * Maps a knowledge base file comment.
     */
    public static CommentResponse fromKb(MemberNameResolver resolver, Comment comment) {
        return withResolvedName(resolver, new Scope(null, comment.targetId(), null, null), comment);
    }

    /**
     * Maps a board ticket comment. The board surface labels authors from the enriched identity
     * alone, so {@code authorName} stays {@code null} and no separate name lookup is issued.
     */
    public static CommentResponse fromBoard(MemberNameResolver resolver, Comment comment) {
        var scope = new Scope(null, null, comment.targetId(), null);
        if (comment.deleted()) {
            return removed(scope, comment);
        }
        return live(scope, comment, resolver.enrichDisplay(comment.author()), null);
    }

    private static CommentResponse withResolvedName(MemberNameResolver resolver, Scope scope, Comment comment) {
        if (comment.deleted()) {
            return removed(scope, comment);
        }
        var resolved = resolver.resolveDisplay(comment.author());
        return live(scope, comment, resolved.identity(), resolved.name() != null ? resolved.name() : "");
    }

    private static CommentResponse live(
            Scope scope, Comment comment, @Nullable MemberIdentity author, @Nullable String authorName) {
        return new CommentResponse(
                comment.id(),
                scope.newsId(),
                scope.fileId(),
                scope.ticketId(),
                comment.parentId(),
                author,
                authorName,
                comment.content(),
                false,
                comment.createdAt(),
                comment.updatedAt(),
                scope.eventDate());
    }

    private static CommentResponse removed(Scope scope, Comment comment) {
        return new CommentResponse(
                comment.id(),
                scope.newsId(),
                scope.fileId(),
                scope.ticketId(),
                comment.parentId(),
                null,
                null,
                "",
                true,
                comment.createdAt(),
                null,
                scope.eventDate());
    }

    private record Scope(
            @Nullable Integer newsId,
            @Nullable Integer fileId,
            @Nullable Integer ticketId,
            @Nullable LocalDate eventDate) {}
}
