/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.NewsComment;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Single source of truth for turning the comment entity of any commentable surface into a
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
        return withResolvedName(
                resolver,
                new Scope(null, null, null, comment.eventDate()),
                comment.id(),
                comment.parentId(),
                comment.author(),
                comment.content(),
                comment.deleted(),
                comment.createdAt(),
                comment.updatedAt());
    }

    /**
     * Maps a news comment. News comments cannot be edited, so {@code updatedAt} stays {@code null}.
     */
    public static CommentResponse fromNews(MemberNameResolver resolver, NewsComment comment) {
        return withResolvedName(
                resolver,
                new Scope(comment.newsId(), null, null, null),
                comment.id(),
                comment.parentId(),
                comment.author(),
                comment.content(),
                comment.deleted(),
                comment.createdAt(),
                null);
    }

    /**
     * Maps a knowledge base file comment.
     */
    public static CommentResponse fromKb(MemberNameResolver resolver, KbComment comment) {
        return withResolvedName(
                resolver,
                new Scope(null, comment.fileId(), null, null),
                comment.id(),
                comment.parentId(),
                comment.author(),
                comment.content(),
                comment.deleted(),
                comment.createdAt(),
                comment.updatedAt());
    }

    /**
     * Maps a board ticket comment. The board surface labels authors from the enriched identity
     * alone, so {@code authorName} stays {@code null} and no separate name lookup is issued.
     */
    public static CommentResponse fromBoard(MemberNameResolver resolver, BoardComment comment) {
        var scope = new Scope(null, null, comment.ticketId(), null);
        if (comment.deleted()) {
            return removed(scope, comment.id(), comment.parentId(), comment.createdAt());
        }
        return live(
                scope,
                comment.id(),
                comment.parentId(),
                resolver.enrichDisplay(comment.author()),
                null,
                comment.content(),
                comment.createdAt(),
                comment.updatedAt());
    }

    private static CommentResponse withResolvedName(
            MemberNameResolver resolver,
            Scope scope,
            int id,
            Integer parentId,
            MemberIdentity author,
            String content,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt) {
        if (deleted) {
            return removed(scope, id, parentId, createdAt);
        }
        var resolved = resolver.resolveDisplay(author);
        return live(
                scope,
                id,
                parentId,
                resolved.identity(),
                resolved.name() != null ? resolved.name() : "",
                content,
                createdAt,
                updatedAt);
    }

    private static CommentResponse live(
            Scope scope,
            int id,
            Integer parentId,
            MemberIdentity author,
            String authorName,
            String content,
            Instant createdAt,
            Instant updatedAt) {
        return new CommentResponse(
                id,
                scope.newsId(),
                scope.fileId(),
                scope.ticketId(),
                parentId,
                author,
                authorName,
                content,
                false,
                createdAt,
                updatedAt,
                scope.eventDate());
    }

    private static CommentResponse removed(Scope scope, int id, Integer parentId, Instant createdAt) {
        return new CommentResponse(
                id,
                scope.newsId(),
                scope.fileId(),
                scope.ticketId(),
                parentId,
                null,
                null,
                "",
                true,
                createdAt,
                null,
                scope.eventDate());
    }

    private record Scope(Integer newsId, Integer fileId, Integer ticketId, LocalDate eventDate) {}
}
