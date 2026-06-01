/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.entity.Comment;
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
     * Sets inline identity columns on an event comment.
     */
    public void setInlineIdentity(int commentId, UUID stationUid, UUID memberUid) {
        Query.query(
                        "UPDATE event_comment SET author_station_uid = :station_uid::uuid, author_member_uid = :member_uid::uuid WHERE id = :id;")
                .single(Call.of()
                        .bind("id", commentId)
                        .bind("station_uid", stationUid, StandardValueConverter.UUID_STRING)
                        .bind("member_uid", memberUid, StandardValueConverter.UUID_STRING))
                .update();
    }

    /**
     * Finds the inline MemberIdentity for a comment.
     *
     * @param commentId the comment ID
     * @return the MemberIdentity, or null if not set
     */
    public MemberIdentity findInlineIdentity(int commentId) {
        return Query.query("SELECT author_station_uid, author_member_uid FROM event_comment WHERE id = :id;")
                .single(Call.of().bind("id", commentId))
                .map(row -> {
                    UUID stationUid = row.get("author_station_uid", StandardValueConverter.UUID_STRING);
                    UUID memberUid = row.get("author_member_uid", StandardValueConverter.UUID_STRING);
                    if (stationUid != null && memberUid != null) {
                        return new MemberIdentity(stationUid, memberUid);
                    }
                    return null;
                })
                .first()
                .orElse(null);
    }
}
