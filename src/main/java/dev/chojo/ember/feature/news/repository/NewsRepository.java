/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.repository;

import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.entity.NewsViewer;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for persisting and querying news articles, comments, group restrictions, and acknowledgements.
 */
@Singleton
public class NewsRepository {

    private static final String NEWS_COLUMNS =
            "n.id, n.station_id, n.title, n.content_markdown, n.content_html, n.author_station_uid, n.author_member_uid, n.published_at, n.created_at, n.restriction_mode, EXISTS(SELECT 1 FROM news_restriction r WHERE r.news_id = n.id) AS restricted, n.public_blog";
    private static final String NEWS_COLUMNS_BARE =
            "id, station_id, title, content_markdown, content_html, author_station_uid, author_member_uid, published_at, created_at, restriction_mode, EXISTS(SELECT 1 FROM news_restriction r WHERE r.news_id = id) AS restricted, public_blog";

    /**
     * Creates a new news article and returns the persisted entity.
     *
     * @param stationId       the station to publish in
     * @param title           article title
     * @param contentMarkdown article body in Markdown
     * @param contentHtml     article body as HTML
     * @param author          identity of the author
     * @return the newly created news entry
     */
    public News create(int stationId, String title, String contentMarkdown, String contentHtml, MemberIdentity author) {
        return query("""
                            INSERT INTO news(station_id, title, content_markdown, content_html, author_station_uid, author_member_uid, published_at)
                            VALUES(:station_id, :title, :content_markdown, :content_html, :author_station_uid::uuid, :author_member_uid::uuid, :published_at)
                            RETURNING\s""" + NEWS_COLUMNS_BARE + ";")
                .single(call().bind("station_id", stationId)
                        .bind("title", title)
                        .bind("content_markdown", contentMarkdown)
                        .bind("content_html", contentHtml)
                        .bind(
                                "author_station_uid",
                                author != null ? author.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "author_member_uid",
                                author != null ? author.memberUid() : null,
                                StandardValueConverter.UUID_STRING)
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
        return query("SELECT " + NEWS_COLUMNS + " FROM news n WHERE n.id = :id;")
                .single(call().bind("id", id))
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
        return query(
                        "SELECT " + NEWS_COLUMNS
                                + " FROM news n WHERE n.station_id = :station_id ORDER BY n.published_at DESC LIMIT :limit OFFSET :offset;")
                .single(call().bind("station_id", stationId)
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
        return query("SELECT " + NEWS_COLUMNS + " FROM news n"
                        + " WHERE n.station_id = :station_id"
                        + " AND n.published_at IS NOT NULL"
                        + " AND check_restriction('news_restriction', 'news_id', 'news', 'id', n.id, :member_id, 'NEWS_MANAGER')"
                        + " ORDER BY n.published_at DESC"
                        + " LIMIT :limit OFFSET :offset;")
                .single(call().bind("station_id", stationId)
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
        return query("""
                            UPDATE news SET title = :title, content_markdown = :content_markdown, content_html = :content_html
                            WHERE id = :id;""")
                .single(call().bind("id", id)
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
        return query("DELETE FROM news WHERE id = :id;")
                .single(call().bind("id", id))
                .delete()
                .changed();
    }

    public void updatePublicBlog(int id, boolean publicBlog) {
        query("UPDATE news SET public_blog = :public_blog WHERE id = :id;")
                .single(call().bind("id", id).bind("public_blog", publicBlog))
                .update();
    }

    public List<News> findPublicBlogEntries(int stationId, int offset, int limit) {
        return query("SELECT " + NEWS_COLUMNS
                        + " FROM news n WHERE n.station_id = :station_id"
                        + " AND n.public_blog = TRUE AND n.published_at IS NOT NULL"
                        + " AND NOT EXISTS(SELECT 1 FROM news_restriction r WHERE r.news_id = n.id)"
                        + " ORDER BY n.published_at DESC LIMIT :limit OFFSET :offset;")
                .single(call().bind("station_id", stationId)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(News.map())
                .all();
    }

    public boolean hasPublicBlogEntries(int stationId) {
        return query("""
                        SELECT EXISTS(
                            SELECT 1 FROM news n
                            WHERE n.station_id = :station_id AND n.public_blog = TRUE
                            AND n.published_at IS NOT NULL
                            AND NOT EXISTS(SELECT 1 FROM news_restriction r WHERE r.news_id = n.id)
                        ) AS exists;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getBoolean("exists"))
                .first()
                .orElse(false);
    }

    // -- Comments --

    /**
     * Creates a comment on a news article.
     *
     * @param newsId   the news article ID
     * @param parentId parent comment ID for replies, or {@code null} for top-level comments
     * @param author   identity of the comment author, or {@code null} for federated/system comments
     * @param content  comment text
     * @return the newly created comment
     */
    public NewsComment createComment(int newsId, Integer parentId, MemberIdentity author, String content) {
        return query("""
                            INSERT INTO news_comment(news_id, parent_id, author_station_uid, author_member_uid, content)
                            VALUES(:news_id, :parent_id, :author_station_uid::uuid, :author_member_uid::uuid, :content)
                            RETURNING *;""")
                .single(call().bind("news_id", newsId)
                        .bind("parent_id", parentId)
                        .bind(
                                "author_station_uid",
                                author != null ? author.stationUid() : null,
                                StandardValueConverter.UUID_STRING)
                        .bind(
                                "author_member_uid",
                                author != null ? author.memberUid() : null,
                                StandardValueConverter.UUID_STRING)
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
        return query(
                        "SELECT id, news_id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at FROM news_comment WHERE news_id = :news_id ORDER BY created_at ASC;")
                .single(call().bind("news_id", newsId))
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
        return query("SELECT count(*) AS cnt FROM news_comment WHERE news_id = :news_id;")
                .single(call().bind("news_id", newsId))
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
        return query(
                        "SELECT id, news_id, parent_id, author_station_uid, author_member_uid, content, deleted, created_at FROM news_comment WHERE id = :id;")
                .single(call().bind("id", id))
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
        return query("UPDATE news_comment SET content = :content WHERE id = :id;")
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
    public boolean deleteComment(int id) {
        boolean hasChildren = hasCommentChildren(id);
        if (hasChildren) {
            return query("UPDATE news_comment SET deleted = TRUE, content = '' WHERE id = :id;")
                    .single(call().bind("id", id))
                    .update()
                    .changed();
        }
        return query("DELETE FROM news_comment WHERE id = :id;")
                .single(call().bind("id", id))
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
        return query("SELECT EXISTS(SELECT 1 FROM news_comment WHERE parent_id = :id);")
                .single(call().bind("id", id))
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
        query(
                        "INSERT INTO news_acknowledgement(news_id, member_id) VALUES(:news_id, :member_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("news_id", newsId).bind("member_id", memberId))
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
        return query("SELECT 1 FROM news_acknowledgement WHERE news_id = :news_id AND member_id = :member_id;")
                .single(call().bind("news_id", newsId).bind("member_id", memberId))
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
        return query("""
                            SELECT count(*) AS cnt FROM news n
                            WHERE n.station_id = :station_id
                              AND n.published_at IS NOT NULL
                              AND NOT exists (SELECT 1 FROM news_acknowledgement na WHERE na.news_id = n.id AND na.member_id = :member_id)
                              AND check_restriction('news_restriction', 'news_id', 'news', 'id', n.id, :member_id, 'NEWS_MANAGER');""")
                .single(call().bind("station_id", stationId).bind("member_id", memberId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    // -- Views --

    /**
     * Records that a member fully saw a news article. Idempotent — (news_id, member_id)
     * is the table's PK so a repeated view from the same member is silently ignored.
     */
    public void recordView(int newsId, int memberId) {
        query("INSERT INTO news_view(news_id, member_id) VALUES(:news_id, :member_id) ON CONFLICT DO NOTHING;")
                .single(call().bind("news_id", newsId).bind("member_id", memberId))
                .insert();
    }

    /**
     * Returns every member who has seen the news (most-recent first), with the timestamp
     * of their first view.
     */
    public List<NewsViewer> findSeenViewers(int newsId) {
        return query("""
                            SELECT st.uid AS station_uid, sm.uid AS member_uid, nv.seen_at
                            FROM news_view nv
                            JOIN station_member sm ON sm.id = nv.member_id
                            JOIN station st ON st.id = sm.station_id
                            WHERE nv.news_id = :news_id
                            ORDER BY nv.seen_at DESC;""")
                .single(call().bind("news_id", newsId))
                .map(NewsViewer.map())
                .all();
    }

    /**
     * Counts how many distinct members have viewed a news entry.
     *
     * @param newsId the news article ID
     * @return view count
     */
    public int countViews(int newsId) {
        return query("SELECT count(*) AS cnt FROM news_view WHERE news_id = :news_id;")
                .single(call().bind("news_id", newsId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
    }

    /**
     * Checks whether a specific member has viewed a news entry.
     *
     * @param newsId   the news article ID
     * @param memberId the member ID
     * @return {@code true} if the member has viewed the article
     */
    public boolean hasViewed(int newsId, int memberId) {
        return query("SELECT 1 FROM news_view WHERE news_id = :news_id AND member_id = :member_id;")
                .single(call().bind("news_id", newsId).bind("member_id", memberId))
                .map(row -> true)
                .first()
                .isPresent();
    }

    /**
     * Returns every active station member who is allowed to see the news (per restrictions)
     * but has not yet been observed viewing it. {@code seenAt} on the returned rows is always
     * {@code null}.
     */
    public List<NewsViewer> findUnseenViewers(int newsId, int stationId) {
        return query("""
                            SELECT st.uid AS station_uid, sm.uid AS member_uid, NULL::timestamptz AS seen_at
                            FROM station_member sm
                            JOIN station st ON st.id = sm.station_id
                            WHERE sm.station_id = :station_id
                              AND sm.former = FALSE
                              AND check_restriction('news_restriction', 'news_id', 'news', 'id', :news_id, sm.id, 'NEWS_MANAGER')
                              AND NOT EXISTS (
                                  SELECT 1 FROM news_view nv
                                  WHERE nv.news_id = :news_id AND nv.member_id = sm.id)
                            ORDER BY sm.id;""")
                .single(call().bind("news_id", newsId).bind("station_id", stationId))
                .map(NewsViewer.map())
                .all();
    }
}
