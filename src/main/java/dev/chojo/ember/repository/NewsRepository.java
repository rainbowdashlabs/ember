/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.News;
import dev.chojo.ember.entity.NewsComment;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class NewsRepository {

    public News create(int stationId, String title, String contentMarkdown, String contentHtml, int authorId) {
        return Query.query("""
                        INSERT INTO news(station_id, title, content_markdown, content_html, author_id, published_at)
                        VALUES(:station_id, :title, :content_markdown, :content_html, :author_id, :published_at)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("title", title)
                        .bind("content_markdown", contentMarkdown)
                        .bind("content_html", contentHtml)
                        .bind("author_id", authorId)
                        .bind("published_at", Instant.now(), INSTANT_TIMESTAMP))
                .map(News.map())
                .first()
                .orElseThrow();
    }

    public Optional<News> findById(int id) {
        return Query.query("SELECT * FROM news WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(News.map())
                .first();
    }

    public List<News> findByStation(int stationId, int offset, int limit) {
        return Query.query(
                        "SELECT * FROM news WHERE station_id = :station_id ORDER BY published_at DESC LIMIT :limit OFFSET :offset;")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(News.map())
                .all();
    }

    public List<News> findVisibleForMember(int stationId, int memberId, int offset, int limit) {
        return Query.query("""
                        SELECT DISTINCT n.*
                        FROM news n
                        LEFT JOIN news_group_restriction ngr ON n.id = ngr.news_id
                        WHERE n.station_id = :station_id
                          AND n.published_at IS NOT NULL
                          AND (
                            NOT EXISTS (SELECT 1 FROM news_group_restriction r WHERE r.news_id = n.id)
                            OR ngr.group_id IN (SELECT mge.group_id FROM member_group_entry mge WHERE mge.member_id = :member_id)
                          )
                        ORDER BY n.published_at DESC
                        LIMIT :limit OFFSET :offset;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(News.map())
                .all();
    }

    public boolean update(int id, String title, String contentMarkdown, String contentHtml) {
        return Query.query("""
                        UPDATE news SET title = :title, content_markdown = :content_markdown, content_html = :content_html
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("title", title)
                        .bind("content_markdown", contentMarkdown)
                        .bind("content_html", contentHtml))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM news WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Group Restrictions --

    public List<Integer> findGroupRestrictions(int newsId) {
        return Query.query("SELECT group_id FROM news_group_restriction WHERE news_id = :news_id;")
                .single(Call.of().bind("news_id", newsId))
                .map(row -> row.getInt("group_id"))
                .all();
    }

    // -- Comments --

    public NewsComment createComment(int newsId, Integer parentId, int authorId, String content) {
        return Query.query("""
                        INSERT INTO news_comment(news_id, parent_id, author_id, content)
                        VALUES(:news_id, :parent_id, :author_id, :content)
                        RETURNING *;""")
                .single(Call.of()
                        .bind("news_id", newsId)
                        .bind("parent_id", parentId)
                        .bind("author_id", authorId)
                        .bind("content", content))
                .map(NewsComment.map())
                .first()
                .orElseThrow();
    }

    public List<NewsComment> findCommentsByNews(int newsId) {
        return Query.query("SELECT * FROM news_comment WHERE news_id = :news_id ORDER BY created_at ASC;")
                .single(Call.of().bind("news_id", newsId))
                .map(NewsComment.map())
                .all();
    }

    public int countComments(int newsId) {
        return Query.query("SELECT COUNT(*) AS cnt FROM news_comment WHERE news_id = :news_id;")
                .single(Call.of().bind("news_id", newsId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    public boolean deleteComment(int id) {
        return Query.query("DELETE FROM news_comment WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Group Restrictions --

    public void setGroupRestrictions(int newsId, List<Integer> groupIds) {
        Query.query("DELETE FROM news_group_restriction WHERE news_id = :news_id;")
                .single(Call.of().bind("news_id", newsId))
                .delete();
        for (int groupId : groupIds) {
            Query.query("INSERT INTO news_group_restriction(news_id, group_id) VALUES(:news_id, :group_id);")
                    .single(Call.of().bind("news_id", newsId).bind("group_id", groupId))
                    .insert();
        }
    }

    // -- Acknowledgements --

    public void acknowledge(int newsId, int memberId) {
        Query.query(
                        "INSERT INTO news_acknowledgement(news_id, member_id) VALUES(:news_id, :member_id) ON CONFLICT DO NOTHING;")
                .single(Call.of().bind("news_id", newsId).bind("member_id", memberId))
                .insert();
    }

    public boolean isAcknowledged(int newsId, int memberId) {
        return Query.query("SELECT 1 FROM news_acknowledgement WHERE news_id = :news_id AND member_id = :member_id;")
                .single(Call.of().bind("news_id", newsId).bind("member_id", memberId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    public int countUnacknowledged(int stationId, int memberId) {
        return Query.query("""
                        SELECT COUNT(*) AS cnt FROM news n
                        WHERE n.station_id = :station_id
                          AND n.published_at IS NOT NULL
                          AND NOT EXISTS (SELECT 1 FROM news_acknowledgement na WHERE na.news_id = n.id AND na.member_id = :member_id)
                          AND (
                            NOT EXISTS (SELECT 1 FROM news_group_restriction r WHERE r.news_id = n.id)
                            OR EXISTS (SELECT 1 FROM news_group_restriction r JOIN member_group_entry mge ON r.group_id = mge.group_id
                                       WHERE r.news_id = n.id AND mge.member_id = :member_id)
                          );""")
                .single(Call.of().bind("station_id", stationId).bind("member_id", memberId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }
}
