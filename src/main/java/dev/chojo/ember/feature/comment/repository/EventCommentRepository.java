/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.EventCommentFederatedAuthor;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing event comments with threaded reply support.
 */
@Singleton
public class EventCommentRepository {

    /**
     * Finds all comments for an event, ordered by creation time.
     * Includes soft-deleted comments so the thread structure is preserved.
     *
     * @param eventId the event ID
     * @return the list of comments
     */
    public List<Comment> findByEvent(int eventId) {
        return Query.query("SELECT id, parent_id, author_id, content, deleted, created_at, updated_at"
                        + " FROM event_comment WHERE event_id = :event_id ORDER BY created_at;")
                .single(Call.of().bind("event_id", eventId))
                .map(Comment.map())
                .all();
    }

    /**
     * Finds a comment by its ID.
     *
     * @param id the comment ID
     * @return the comment, if found
     */
    public Optional<Comment> findById(int id) {
        return Query.query(
                        "SELECT id, parent_id, author_id, content, deleted, created_at, updated_at FROM event_comment WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(Comment.map())
                .first();
    }

    /**
     * Creates a new comment on an event.
     *
     * @param eventId  the event ID
     * @param parentId the parent comment ID for replies, or {@code null} for top-level comments
     * @param authorId the member ID of the author
     * @param content  the comment text
     * @return the created comment
     */
    public Comment create(int eventId, Integer parentId, Integer authorId, String content) {
        return Query.query("INSERT INTO event_comment (event_id, parent_id, author_id, content)"
                        + " VALUES (:event_id, :parent_id, :author_id, :content)"
                        + " RETURNING id, parent_id, author_id, content, deleted, created_at, updated_at;")
                .single(Call.of()
                        .bind("event_id", eventId)
                        .bind("parent_id", parentId)
                        .bind("author_id", authorId)
                        .bind("content", content))
                .map(Comment.map())
                .first()
                .orElseThrow();
    }

    /**
     * Updates the content of a comment and sets the updated_at timestamp.
     *
     * @param id      the comment ID
     * @param content the new content
     * @return {@code true} if the comment was updated
     */
    public boolean update(int id, String content) {
        return Query.query("UPDATE event_comment SET content = :content, updated_at = now() WHERE id = :id;")
                .single(Call.of().bind("id", id).bind("content", content))
                .update()
                .changed();
    }

    /**
     * Soft-deletes a comment if it has children, or hard-deletes it if it has none.
     *
     * @param id the comment ID
     * @return {@code true} if the comment was deleted or marked as deleted
     */
    public boolean delete(int id) {
        boolean hasChildren = hasChildren(id);
        if (hasChildren) {
            return Query.query("UPDATE event_comment SET deleted = TRUE, content = '' WHERE id = :id;")
                    .single(Call.of().bind("id", id))
                    .update()
                    .changed();
        }
        return Query.query("DELETE FROM event_comment WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .update()
                .changed();
    }

    /**
     * Checks whether a comment has any child replies.
     *
     * @param id the comment ID
     * @return {@code true} if the comment has children
     */
    public boolean hasChildren(int id) {
        return Query.query("SELECT EXISTS(SELECT 1 FROM event_comment WHERE parent_id = :id);")
                .single(Call.of().bind("id", id))
                .map(row -> row.getBoolean(1))
                .first()
                .orElse(false);
    }

    /**
     * Records the federated author identity for a comment.
     *
     * @param commentId      the local comment ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the member UUID on the remote station
     */
    public void setFederatedAuthor(int commentId, int partnerId, UUID remoteMemberId) {
        Query.query("""
                        INSERT INTO event_comment_federated_author(comment_id, partner_id, remote_member_id)
                        VALUES (:comment_id, :partner_id, :remote_member_id::uuid);""")
                .single(Call.of()
                        .bind("comment_id", commentId)
                        .bind("partner_id", partnerId)
                        .bind("remote_member_id", remoteMemberId, StandardValueConverter.UUID_STRING))
                .insert();
    }

    /**
     * Finds the federated author record for a comment.
     *
     * @param commentId the comment ID
     * @return the federated author, if present
     */
    public Optional<EventCommentFederatedAuthor> findFederatedAuthor(int commentId) {
        return Query.query("SELECT * FROM event_comment_federated_author WHERE comment_id = :comment_id;")
                .single(Call.of().bind("comment_id", commentId))
                .map(EventCommentFederatedAuthor.map())
                .first();
    }
}
