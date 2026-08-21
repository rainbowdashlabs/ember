/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.news.entity.NewsAttachment;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The files news entries hand over. Every read joins the media library, because an attachment
 * without its file name and hash is not something a reader can be handed.
 */
@Singleton
public class NewsAttachmentRepository {

    private static final String SELECT_COLUMNS = """
            a.id, a.news_id, a.file_id, a.label, a.sort_order, a.created_at,
            f.file_name, f.mime_type, f.file_size, f.content_hash""";

    /**
     * Attaches a file to an entry, placing it last.
     */
    public NewsAttachment attach(int newsId, int fileId, String label) {
        int id = SqlSupport.insertReturning(
                """
                INSERT INTO news_attachment(news_id, file_id, label, sort_order)
                VALUES (
                    :news_id,
                    :file_id,
                    :label,
                    COALESCE((SELECT MAX(sort_order) + 1 FROM news_attachment WHERE news_id = :news_id), 0))
                RETURNING id;""",
                call().bind("news_id", newsId).bind("file_id", fileId).bind("label", label),
                row -> row.getInt("id"));
        return findById(id).orElseThrow();
    }

    public Optional<NewsAttachment> findById(int attachmentId) {
        return query("""
                SELECT %s
                FROM news_attachment a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.id = :id;""", SELECT_COLUMNS)
                .single(call().bind("id", attachmentId))
                .map(NewsAttachment.map())
                .first();
    }

    public List<NewsAttachment> findByNews(int newsId) {
        return query("""
                SELECT %s
                FROM news_attachment a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.news_id = :news_id
                ORDER BY a.sort_order, a.id;""", SELECT_COLUMNS)
                .single(call().bind("news_id", newsId))
                .map(NewsAttachment.map())
                .all();
    }

    /**
     * The attachments of several entries at once, so a listing does not ask once per row.
     */
    public Map<Integer, List<NewsAttachment>> findByNewsIds(List<Integer> newsIds) {
        Map<Integer, List<NewsAttachment>> out = new HashMap<>();
        if (newsIds == null || newsIds.isEmpty()) return out;
        var rows = query("""
                SELECT %s
                FROM news_attachment a
                JOIN station_file f ON f.id = a.file_id
                WHERE a.news_id = ANY(:news_ids)
                ORDER BY a.news_id, a.sort_order, a.id;""", SELECT_COLUMNS)
                .single(call().bind("news_ids", newsIds, PostgreSqlTypes.INTEGER))
                .map(NewsAttachment.map())
                .all();
        for (var row : rows) {
            out.computeIfAbsent(row.newsId(), _ -> new ArrayList<>()).add(row);
        }
        return out;
    }

    public boolean updateLabel(int attachmentId, String label) {
        return query("UPDATE news_attachment SET label = :label WHERE id = :id;")
                .single(call().bind("id", attachmentId).bind("label", label))
                .update()
                .changed();
    }

    /**
     * Writes the order the author put the attachments in. Ids that do not belong to the entry are
     * ignored rather than moved, so a stale client cannot reorder somebody else's entry.
     */
    public void reorder(int newsId, List<Integer> attachmentIds) {
        for (int i = 0; i < attachmentIds.size(); i++) {
            query("UPDATE news_attachment SET sort_order = :sort_order WHERE id = :id AND news_id = :news_id;")
                    .single(call().bind("id", attachmentIds.get(i))
                            .bind("news_id", newsId)
                            .bind("sort_order", i))
                    .update();
        }
    }

    public boolean detach(int attachmentId) {
        return SqlSupport.deleteById("news_attachment", attachmentId);
    }
}
