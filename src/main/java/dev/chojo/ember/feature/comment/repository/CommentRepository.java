/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The comments on every kind of target, threaded through their parents.
 *
 * <p>Every read and write by id names the kind of target it expects, so a comment of one kind is
 * never reached through the paths of another. Listings run oldest first, with the id breaking ties
 * between comments written in the same instant.
 */
@Singleton
public class CommentRepository {

    private static final String COLUMNS =
            "id, station_id, event_id, event_date, news_id, kb_file_id, board_ticket_id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at, updated_at";

    private static String stationOf(CommentEntityType type) {
        return switch (type) {
            case EVENT -> "(SELECT station_id FROM station_event WHERE id = :target_id)";
            case NEWS -> "(SELECT station_id FROM news WHERE id = :target_id)";
            case KB -> "(SELECT station_id FROM kb_file WHERE id = :target_id)";
            case BOARD_TICKET ->
                "(SELECT b.station_id FROM board_ticket t JOIN board b ON b.id = t.board_id WHERE t.id = :target_id)";
        };
    }

    /**
     * Every comment on one target, deleted placeholders included so the thread keeps its shape.
     *
     * @param type     the kind of target
     * @param targetId the target
     * @return the comments, oldest first
     */
    public List<Comment> findByTarget(CommentEntityType type, int targetId) {
        return query("""
                SELECT %s
                FROM comment
                WHERE %s = :target_id
                ORDER BY created_at, id;""", COLUMNS, type.column())
                .single(call().bind("target_id", targetId))
                .map(Comment.map())
                .all();
    }

    /**
     * The comments on one occurrence of an appointment, or on the appointment as a whole.
     *
     * @param eventId   the appointment
     * @param eventDate the occurrence, or {@code null} for the comments on the whole appointment
     * @return the comments, oldest first
     */
    public List<Comment> findByEventOccurrence(int eventId, @Nullable LocalDate eventDate) {
        return query("""
                SELECT %s
                FROM comment
                WHERE event_id = :target_id
                  AND event_date IS NOT DISTINCT FROM :event_date
                ORDER BY created_at, id;""", COLUMNS)
                .single(call().bind("target_id", eventId).bind("event_date", eventDate))
                .map(Comment.map())
                .all();
    }

    /**
     * The comments on one target written from one station.
     *
     * @param type       the kind of target
     * @param targetId   the target
     * @param stationUid the station the authors belong to
     * @return those comments, oldest first
     */
    public List<Comment> findByTargetFrom(CommentEntityType type, int targetId, UUID stationUid) {
        return query("""
                SELECT %s
                FROM comment
                WHERE %s = :target_id
                  AND author_station_uid = :station_uid::UUID
                ORDER BY created_at, id;""", COLUMNS, type.column())
                .single(call().bind("target_id", targetId)
                        .bind("station_uid", stationUid, StandardValueConverter.UUID_STRING))
                .map(Comment.map())
                .all();
    }

    /**
     * How many comments one target carries, deleted placeholders included.
     *
     * @param type     the kind of target
     * @param targetId the target
     * @return the number of comments
     */
    public int count(CommentEntityType type, int targetId) {
        return SqlSupport.count(
                "SELECT count(*) AS cnt FROM comment WHERE %s = :target_id;",
                call().bind("target_id", targetId), type.column());
    }

    /**
     * Finds a comment of the given kind by its id.
     *
     * @param type the kind of target the comment must hang under
     * @param id   the comment
     * @return the comment, empty when there is none of that kind by that id
     */
    public Optional<Comment> findById(CommentEntityType type, int id) {
        return query("""
                SELECT %s
                FROM comment
                WHERE id = :id
                  AND %s IS NOT NULL;""", COLUMNS, type.column())
                .single(call().bind("id", id))
                .map(Comment.map())
                .first();
    }

    /**
     * Writes a new comment. The station is copied from the target.
     *
     * @param type      the kind of target
     * @param targetId  the target
     * @param eventDate the occurrence of a recurring appointment, {@code null} otherwise
     * @param parentId  the comment answered, or {@code null} for a top-level comment
     * @param author    the identity of the author, or {@code null}
     * @param content   the comment text
     * @return the stored comment
     */
    public Comment create(
            CommentEntityType type,
            int targetId,
            @Nullable LocalDate eventDate,
            @Nullable Integer parentId,
            @Nullable MemberIdentity author,
            String content) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    comment
                    (station_id, %s, event_date, parent_id, author_station_uid, author_member_uid, content)
                VALUES
                    (%s, :target_id, :event_date, :parent_id, :author_station_uid::UUID, :author_member_uid::UUID, :content)
                RETURNING %s;""",
                call().bind("target_id", targetId)
                        .bind("event_date", eventDate)
                        .bind("parent_id", parentId)
                        .bind(
                                "author_station_uid",
                                author != null ? author.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "author_member_uid",
                                author != null ? author.memberUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind("content", content),
                Comment.map(),
                type.column(),
                stationOf(type),
                COLUMNS);
    }

    /**
     * Rewrites a comment and records when it was edited.
     *
     * @param type    the kind of target the comment must hang under
     * @param id      the comment
     * @param content the new text
     * @return {@code true} if the comment was updated
     */
    public boolean update(CommentEntityType type, int id, String content) {
        return query("""
                UPDATE comment
                SET content = :content, updated_at = now()
                WHERE id = :id
                  AND %s IS NOT NULL;""", type.column())
                .single(call().bind("id", id).bind("content", content))
                .update()
                .changed();
    }

    /**
     * Removes a comment: a comment with replies stays as an empty placeholder so the thread keeps
     * its shape, one without is deleted outright.
     *
     * @param type the kind of target the comment must hang under
     * @param id   the comment
     * @return {@code true} if the comment was deleted or marked as deleted
     */
    public boolean delete(CommentEntityType type, int id) {
        if (hasChildren(id)) {
            return query("""
                    UPDATE comment
                    SET deleted = TRUE, content = ''
                    WHERE id = :id
                      AND %s IS NOT NULL;""", type.column())
                    .single(call().bind("id", id))
                    .update()
                    .changed();
        }
        return query("DELETE FROM comment WHERE id = :id AND %s IS NOT NULL;", type.column())
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Checks whether a comment has any replies.
     *
     * @param id the comment
     * @return {@code true} if the comment has replies
     */
    public boolean hasChildren(int id) {
        return SqlSupport.exists("SELECT 1 FROM comment WHERE parent_id = :id LIMIT 1;", call().bind("id", id));
    }

    /**
     * The appointment a comment hangs under, by id and name.
     *
     * @param id   the appointment
     * @param name what it is called
     */
    public record CommentedEvent(int id, String name) {}

    /**
     * Finds the appointment an appointment comment was written under.
     *
     * @param commentId the comment
     * @return the appointment, empty when there is no such appointment comment
     */
    public Optional<CommentedEvent> findCommentedEvent(int commentId) {
        return query("""
                SELECT e.id, e.name
                FROM
                    comment c
                    JOIN station_event e ON e.id = c.event_id
                WHERE c.id = :id;""")
                .single(call().bind("id", commentId))
                .map(row -> new CommentedEvent(row.getInt("id"), row.getString("name")))
                .first();
    }
}
