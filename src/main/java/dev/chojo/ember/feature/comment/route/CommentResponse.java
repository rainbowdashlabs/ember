/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.route;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.MemberIdentity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * API response representing a comment with resolved author information.
 *
 * <p>Shared by every commentable surface (events, news, knowledge base files, board tickets).
 * The owning entity is identified by exactly one of {@code newsId}, {@code fileId} or
 * {@code ticketId}; the remaining scope fields stay {@code null} for that surface. Null fields
 * are omitted from the response so a surface does not carry the scope fields it never had.
 * Consumers must treat an absent field as {@code null}.
 *
 * @param id         unique identifier of the comment
 * @param newsId     owning news article, or {@code null} outside the news surface
 * @param fileId     owning knowledge base file, or {@code null} outside the knowledge base surface
 * @param ticketId   owning board ticket, or {@code null} outside the board surface
 * @param parentId   parent comment for threaded replies, or {@code null} for top-level comments
 * @param author     enriched identity of the author; {@code null} for deleted comments
 * @param authorName resolved display name of the author; {@code null} for deleted comments and on
 *                   surfaces that render the name from {@code author} alone
 * @param content    text content, or an empty string for deleted comments
 * @param deleted    whether the comment has been soft-deleted
 * @param createdAt  timestamp when the comment was created
 * @param updatedAt  timestamp of the last edit, or {@code null} when never edited
 * @param eventDate  occurrence date for date-scoped comments on recurring events, {@code null}
 *                   otherwise. Serialised as ISO {@code yyyy-MM-dd}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CommentResponse(
        int id,
        Integer newsId,
        Integer fileId,
        Integer ticketId,
        Integer parentId,
        MemberIdentity author,
        String authorName,
        String content,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        LocalDate eventDate) {}
