/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing knowledge base file comments with threaded reply support.
 */
@Singleton
public class KbCommentRepository {

    private static final String KB_COMMENT_COLUMNS =
            "id, file_id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at, updated_at";

    /**
     * Finds all comments for a KB file, ordered by creation time.
     * Includes soft-deleted comments so the thread structure is preserved.
     *
     * @param fileId the KB file ID
     * @return the list of comments
     */
    public List<KbComment> findByFile(int fileId) {
        return query("""
                SELECT %s
                FROM kb_comment
                WHERE file_id = :file_id
                ORDER BY created_at;""", KB_COMMENT_COLUMNS)
                .single(call().bind("file_id", fileId))
                .map(KbComment.map())
                .all();
    }

    /**
     * Finds a comment by its ID.
     *
     * @param id the comment ID
     * @return the comment, if found
     */
    public Optional<KbComment> findById(int id) {
        return SqlSupport.findById("kb_comment", KB_COMMENT_COLUMNS, id, KbComment.map());
    }

    /**
     * Creates a new comment on a KB file.
     *
     * @param fileId   the KB file ID
     * @param parentId the parent comment ID for replies, or {@code null} for top-level comments
     * @param author   the identity of the comment author (may be null for anonymous)
     * @param content  the comment text
     * @return the created comment
     */
    public KbComment create(int fileId, Integer parentId, MemberIdentity author, String content) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO kb_comment (file_id, parent_id, author_station_uid, author_member_uid, content)
                VALUES (:file_id, :parent_id, :author_station_uid::UUID, :author_member_uid::UUID, :content)
                RETURNING %s;""".formatted(KB_COMMENT_COLUMNS),
                call().bind("file_id", fileId)
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
                KbComment.map());
    }

    /**
     * Updates the content of a comment and sets the updated_at timestamp.
     *
     * @param id      the comment ID
     * @param content the new content
     * @return {@code true} if the comment was updated
     */
    public boolean update(int id, String content) {
        return query("UPDATE kb_comment SET content = :content, updated_at = now() WHERE id = :id;")
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
        if (hasChildren(id)) {
            return query("UPDATE kb_comment SET deleted = TRUE, content = '' WHERE id = :id;")
                    .single(call().bind("id", id))
                    .update()
                    .changed();
        }
        return SqlSupport.deleteById("kb_comment", id);
    }

    /**
     * Checks whether a comment has any child replies.
     *
     * @param id the comment ID
     * @return {@code true} if the comment has children
     */
    public boolean hasChildren(int id) {
        return SqlSupport.exists("SELECT 1 FROM kb_comment WHERE parent_id = :id LIMIT 1;", call().bind("id", id));
    }
}
