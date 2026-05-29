/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbCommentFederatedAuthor;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for managing knowledge base file comments with threaded reply support.
 */
@Singleton
public class KbCommentRepository {

    /**
     * Finds all comments for a KB file, ordered by creation time.
     * Includes soft-deleted comments so the thread structure is preserved.
     *
     * @param fileId the KB file ID
     * @return the list of comments
     */
    public List<KbComment> findByFile(int fileId) {
        return Query.query("SELECT id, file_id, parent_id, author_id, content, deleted, created_at, updated_at"
                        + " FROM kb_comment WHERE file_id = :file_id ORDER BY created_at;")
                .single(Call.of().bind("file_id", fileId))
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
        return Query.query("SELECT id, file_id, parent_id, author_id, content, deleted, created_at, updated_at"
                        + " FROM kb_comment WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(KbComment.map())
                .first();
    }

    /**
     * Creates a new comment on a KB file.
     *
     * @param fileId   the KB file ID
     * @param parentId the parent comment ID for replies, or {@code null} for top-level comments
     * @param authorId the member ID of the author
     * @param content  the comment text
     * @return the created comment
     */
    public KbComment create(int fileId, Integer parentId, int authorId, String content) {
        return Query.query("INSERT INTO kb_comment (file_id, parent_id, author_id, content)"
                        + " VALUES (:file_id, :parent_id, :author_id, :content)"
                        + " RETURNING id, file_id, parent_id, author_id, content, deleted, created_at, updated_at;")
                .single(Call.of()
                        .bind("file_id", fileId)
                        .bind("parent_id", parentId)
                        .bind("author_id", authorId)
                        .bind("content", content))
                .map(KbComment.map())
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
        return Query.query("UPDATE kb_comment SET content = :content, updated_at = now() WHERE id = :id;")
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
        if (hasChildren(id)) {
            return Query.query("UPDATE kb_comment SET deleted = TRUE, content = '' WHERE id = :id;")
                    .single(Call.of().bind("id", id))
                    .update()
                    .changed();
        }
        return Query.query("DELETE FROM kb_comment WHERE id = :id;")
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
        return Query.query("SELECT EXISTS(SELECT 1 FROM kb_comment WHERE parent_id = :id);")
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
                        INSERT INTO kb_comment_federated_author(comment_id, partner_id, remote_member_id)
                        VALUES (:comment_id, :partner_id, :remote_member_id);""")
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
    public Optional<KbCommentFederatedAuthor> findFederatedAuthor(int commentId) {
        return Query.query("SELECT * FROM kb_comment_federated_author WHERE comment_id = :comment_id;")
                .single(Call.of().bind("comment_id", commentId))
                .map(KbCommentFederatedAuthor.map())
                .first();
    }
}
