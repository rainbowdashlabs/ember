/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for persisting and querying news articles, comments, group restrictions, and acknowledgements.
 */
@Singleton
public class NewsRepository {

    private static final String NEWS_COLUMNS =
            "n.id, n.station_id, n.title, n.content_markdown, n.content_html, n.author_id, n.published_at, n.created_at, n.restriction_mode, EXISTS(SELECT 1 FROM news_restriction r WHERE r.news_id = n.id) AS restricted";
    private static final String NEWS_COLUMNS_BARE =
            "id, station_id, title, content_markdown, content_html, author_id, published_at, created_at, restriction_mode, EXISTS(SELECT 1 FROM news_restriction r WHERE r.news_id = id) AS restricted";

    /**
     * Creates a new news article and returns the persisted entity.
     *
     * @param stationId       the station to publish in
     * @param title           article title
     * @param contentMarkdown article body in Markdown
     * @param contentHtml     article body as HTML
     * @param authorId        member ID of the author
     * @return the newly created news entry
     */
    public News create(int stationId, String title, String contentMarkdown, String contentHtml, int authorId) {
        return Query.query("""
                            INSERT INTO news(station_id, title, content_markdown, content_html, author_id, published_at)
                            VALUES(:station_id, :title, :content_markdown, :content_html, :author_id, :published_at)
                            RETURNING\s""" + NEWS_COLUMNS_BARE + ";")
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

    /**
     * Finds a news article by its ID.
     *
     * @param id the news article ID
     * @return the news article, or empty if not found
     */
    public Optional<News> findById(int id) {
        return Query.query("SELECT " + NEWS_COLUMNS + " FROM news n WHERE n.id = :id;")
                .single(Call.of().bind("id", id))
                .map(News.map())
                .first();
    }

    /**
     * Retrieves news articles for a station, ordered by publication date descending.
     *
     * @param stationId the station ID
     * @param offset    pagination offset
     * @param limit     maximum number of results
     * @return list of news articles
     */
    public List<News> findByStation(int stationId, int offset, int limit) {
        return Query.query(
                        "SELECT " + NEWS_COLUMNS
                                + " FROM news n WHERE n.station_id = :station_id ORDER BY n.published_at DESC LIMIT :limit OFFSET :offset;")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(News.map())
                .all();
    }

    /**
     * Retrieves published news visible to a specific member, using the DB restriction check function.
     * The function resolves role inheritance, restriction mode, and manager bypass.
     *
     * @param stationId the station ID
     * @param memberId  the member ID
     * @param offset    pagination offset
     * @param limit     maximum number of results
     * @return list of visible news articles
     */
    public List<News> findVisibleForMember(int stationId, int memberId, int offset, int limit) {
        return Query.query("SELECT " + NEWS_COLUMNS + " FROM news n"
                        + " WHERE n.station_id = :station_id"
                        + " AND n.published_at IS NOT NULL"
                        + " AND check_restriction('news_restriction', 'news_id', 'news', 'id', n.id, :member_id, 'NEWS_MANAGER')"
                        + " ORDER BY n.published_at DESC"
                        + " LIMIT :limit OFFSET :offset;")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("member_id", memberId)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(News.map())
                .all();
    }

    /**
     * Updates the title and content of a news article.
     *
     * @param id              the news article ID
     * @param title           new title
     * @param contentMarkdown new Markdown content
     * @param contentHtml     new HTML content
     * @return {@code true} if a row was updated
     */
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

    /**
     * Deletes a news article by its ID.
     *
     * @param id the news article ID
     * @return {@code true} if a row was deleted
     */
    public boolean delete(int id) {
        return Query.query("DELETE FROM news WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    // -- Comments --

    /**
     * Creates a comment on a news article.
     *
     * @param newsId   the news article ID
     * @param parentId parent comment ID for replies, or {@code null} for top-level comments
     * @param authorId member ID of the comment author
     * @param content  comment text
     * @return the newly created comment
     */
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

    /**
     * Retrieves all comments for a news article, ordered by creation time ascending.
     *
     * @param newsId the news article ID
     * @return list of comments
     */
    public List<NewsComment> findCommentsByNews(int newsId) {
        return Query.query("SELECT * FROM news_comment WHERE news_id = :news_id ORDER BY created_at ASC;")
                .single(Call.of().bind("news_id", newsId))
                .map(NewsComment.map())
                .all();
    }

    /**
     * Counts the total number of comments on a news article.
     *
     * @param newsId the news article ID
     * @return comment count
     */
    public int countComments(int newsId) {
        return Query.query("SELECT count(*) AS cnt FROM news_comment WHERE news_id = :news_id;")
                .single(Call.of().bind("news_id", newsId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    /**
     * Finds a comment by its ID.
     *
     * @param id the comment ID
     * @return the comment, or empty if not found
     */
    public Optional<NewsComment> findCommentById(int id) {
        return Query.query("SELECT * FROM news_comment WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(NewsComment.map())
                .first();
    }

    /**
     * Updates the content of a comment.
     *
     * @param id      the comment ID
     * @param content new comment text
     * @return {@code true} if a row was updated
     */
    public boolean updateComment(int id, String content) {
        return Query.query("UPDATE news_comment SET content = :content WHERE id = :id;")
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
    public boolean deleteComment(int id) {
        boolean hasChildren = hasCommentChildren(id);
        if (hasChildren) {
            return Query.query("UPDATE news_comment SET deleted = TRUE, content = '' WHERE id = :id;")
                    .single(Call.of().bind("id", id))
                    .update()
                    .changed();
        }
        return Query.query("DELETE FROM news_comment WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Checks whether a comment has any child replies.
     *
     * @param id the comment ID
     * @return {@code true} if the comment has children
     */
    public boolean hasCommentChildren(int id) {
        return Query.query("SELECT EXISTS(SELECT 1 FROM news_comment WHERE parent_id = :id);")
                .single(Call.of().bind("id", id))
                .map(row -> row.getBoolean(1))
                .first()
                .orElse(false);
    }

    // -- Acknowledgements --

    /**
     * Records that a member has acknowledged (read) a news article. Idempotent.
     *
     * @param newsId   the news article ID
     * @param memberId the member ID
     */
    // Not yet exposed via routes — acknowledgement UI not implemented
    public void acknowledge(int newsId, int memberId) {
        Query.query(
                        "INSERT INTO news_acknowledgement(news_id, member_id) VALUES(:news_id, :member_id) ON CONFLICT DO NOTHING;")
                .single(Call.of().bind("news_id", newsId).bind("member_id", memberId))
                .insert();
    }

    /**
     * Checks whether a member has acknowledged a news article.
     *
     * @param newsId   the news article ID
     * @param memberId the member ID
     * @return {@code true} if the member has acknowledged the article
     */
    public boolean isAcknowledged(int newsId, int memberId) {
        return Query.query("SELECT 1 FROM news_acknowledgement WHERE news_id = :news_id AND member_id = :member_id;")
                .single(Call.of().bind("news_id", newsId).bind("member_id", memberId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    /**
     * Counts how many published and visible news articles a member has not yet acknowledged.
     *
     * @param stationId the station ID
     * @param memberId  the member ID
     * @return number of unacknowledged news articles
     */
    public int countUnacknowledged(int stationId, int memberId) {
        return Query.query("""
                            SELECT count(*) AS cnt FROM news n
                            WHERE n.station_id = :station_id
                              AND n.published_at IS NOT NULL
                              AND NOT exists (SELECT 1 FROM news_acknowledgement na WHERE na.news_id = n.id AND na.member_id = :member_id)
                              AND check_restriction('news_restriction', 'news_id', 'news', 'id', n.id, :member_id, 'NEWS_MANAGER');""")
                .single(Call.of().bind("station_id", stationId).bind("member_id", memberId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }
}
