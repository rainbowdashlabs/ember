/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing event comments with threaded reply support.
 */
@Singleton
public class EventCommentRepository {

    private static final String COMMENT_COLUMNS =
            "id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at, updated_at, event_date";

    /**
     * Finds all comments for an event, ordered by creation time.
     * Includes soft-deleted comments so the thread structure is preserved.
     *
     * @param eventId the event ID
     * @return the list of comments
     */
    public List<Comment> findByEvent(int eventId) {
        return query("""
                SELECT %s
                FROM event_comment
                WHERE event_id = :event_id
                ORDER BY created_at;""", COMMENT_COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(Comment.map())
                .all();
    }

    /**
     * Finds comments scoped to a specific occurrence of a recurring event. When
     * {@code eventDate} is {@code null}, returns only whole-event comments (event_date IS
     * NULL); when set, returns comments matching that occurrence date.
     */
    public List<Comment> findByEventAndDate(int eventId, LocalDate eventDate) {
        if (eventDate == null) {
            return query("""
                    SELECT %s
                    FROM event_comment
                    WHERE event_id = :event_id
                      AND event_date IS NULL
                    ORDER BY created_at;""", COMMENT_COLUMNS)
                    .single(call().bind("event_id", eventId))
                    .map(Comment.map())
                    .all();
        }
        return query("""
                SELECT %s
                FROM event_comment
                WHERE event_id = :event_id
                  AND event_date = :event_date
                ORDER BY created_at;""", COMMENT_COLUMNS)
                .single(call().bind("event_id", eventId).bind("event_date", eventDate))
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
        return SqlSupport.findById("event_comment", COMMENT_COLUMNS, id, Comment.map());
    }

    /**
     * Creates a new comment on an event.
     *
     * @param eventId  the event ID
     * @param parentId the parent comment ID for replies, or {@code null} for top-level comments
     * @param author   the identity of the author, or {@code null}
     * @param content  the comment text
     * @return the created comment
     */
    public Comment create(int eventId, Integer parentId, MemberIdentity author, String content, LocalDate eventDate) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    event_comment
                    (event_id, parent_id, author_station_uid, author_member_uid, content, event_date)
                VALUES
                    (:event_id, :parent_id, :author_station_uid::UUID, :author_member_uid::UUID, :content, :event_date)
                RETURNING %s;""",
                call().bind("event_id", eventId)
                        .bind("parent_id", parentId)
                        .bind(
                                "author_station_uid",
                                author != null ? author.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "author_member_uid",
                                author != null ? author.memberUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind("content", content)
                        .bind("event_date", eventDate),
                Comment.map(),
                COMMENT_COLUMNS);
    }

    /**
     * Updates the content of a comment and sets the updated_at timestamp.
     *
     * @param id      the comment ID
     * @param content the new content
     * @return {@code true} if the comment was updated
     */
    public boolean update(int id, String content) {
        return query("UPDATE event_comment SET content = :content, updated_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("content", content))
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
            return query("UPDATE event_comment SET deleted = TRUE, content = '' WHERE id = :id;")
                    .single(call().bind("id", id))
                    .update()
                    .changed();
        }
        return SqlSupport.deleteById("event_comment", id);
    }

    /**
     * The station owning the event a comment hangs under. A comment carries no station of its own,
     * so this is what an ownership check on a comment endpoint compares against.
     *
     * @param commentId the comment ID
     * @return the owning station, empty when there is no such comment
     */
    public Optional<Integer> findCommentStation(int commentId) {
        return query("""
                SELECT e.station_id
                FROM
                    event_comment c
                    JOIN station_event e ON e.id = c.event_id
                WHERE c.id = :id;""")
                .single(call().bind("id", commentId))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    /**
     * Checks whether a comment has any child replies.
     *
     * @param id the comment ID
     * @return {@code true} if the comment has children
     */
    public boolean hasChildren(int id) {
        return SqlSupport.exists("SELECT 1 FROM event_comment WHERE parent_id = :id LIMIT 1;", call().bind("id", id));
    }
}
