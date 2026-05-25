/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentCreated;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.event.events.NewsCreated;
import dev.chojo.ember.event.events.NewsDeleted;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.entity.NewsComment;
import dev.chojo.ember.feature.news.repository.NewsRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for managing news articles and comments.
 * Handles creation with group restrictions, updates, deletions, and comment operations.
 */
@Singleton
public class NewsService {
    private final NewsRepository newsRepository;
    private final RestrictionRepository restrictionRepository;
    private final DomainEventBus eventBus;

    @Inject
    public NewsService(
            NewsRepository newsRepository, RestrictionRepository restrictionRepository, DomainEventBus eventBus) {
        this.newsRepository = newsRepository;
        this.restrictionRepository = restrictionRepository;
        this.eventBus = eventBus;
    }

    /**
     * Creates a news article and optionally applies group restrictions.
     *
     * @param stationId       the station to publish in
     * @param title           article title
     * @param contentMarkdown article body in Markdown
     * @param contentHtml     article body as HTML
     * @param authorId        member ID of the author
     * @param groupIds        group IDs to restrict visibility to (empty for unrestricted)
     * @return the newly created news entry
     */
    public News create(
            int stationId,
            String title,
            String contentMarkdown,
            String contentHtml,
            int authorId,
            List<Integer> roleIds,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        var news = newsRepository.create(stationId, title, contentMarkdown, contentHtml, authorId);
        setRestrictions(news.id(), roleIds, groupIds, tagIds, memberIds);
        eventBus.publish(new NewsCreated(stationId, news.id(), title));
        return news;
    }

    /**
     * Finds a news article by its ID.
     *
     * @param id the news article ID
     * @return the news article, or empty if not found
     */
    public Optional<News> findById(int id) {
        return newsRepository.findById(id);
    }

    /**
     * Retrieves news articles for a station with pagination.
     *
     * @param stationId the station ID
     * @param offset    pagination offset
     * @param limit     maximum number of results
     * @return list of news articles
     */
    public List<News> findByStation(int stationId, int offset, int limit) {
        return newsRepository.findByStation(stationId, offset, limit);
    }

    /**
     * Retrieves published news visible to a specific member, respecting group restrictions.
     *
     * @param stationId the station ID
     * @param memberId  the member ID
     * @param offset    pagination offset
     * @param limit     maximum number of results
     * @return list of visible news articles
     */
    public List<News> findVisibleForMember(int stationId, int memberId, int offset, int limit) {
        return newsRepository.findVisibleForMember(stationId, memberId, offset, limit);
    }

    /**
     * Updates a news article's content and group restrictions.
     *
     * @param id              the news article ID
     * @param title           new title
     * @param contentMarkdown new Markdown content
     * @param contentHtml     new HTML content
     * @param groupIds        new group restriction IDs
     * @return the updated news article, or empty if the article was not found
     */
    public Optional<News> update(
            int id,
            String title,
            String contentMarkdown,
            String contentHtml,
            List<Integer> roleIds,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds) {
        if (newsRepository.update(id, title, contentMarkdown, contentHtml)) {
            setRestrictions(id, roleIds, groupIds, tagIds, memberIds);
            return newsRepository.findById(id);
        }
        return Optional.empty();
    }

    /**
     * Deletes a news article by its ID.
     *
     * @param id the news article ID
     * @return {@code true} if the article was deleted
     */
    public boolean delete(int id) {
        var news = newsRepository.findById(id).orElse(null);
        if (news == null) return false;
        if (newsRepository.delete(id)) {
            eventBus.publish(new NewsDeleted(news.stationId(), id, news.title()));
            return true;
        }
        return false;
    }

    /**
     * Retrieves the restriction set for a news article.
     */
    public RestrictionSet findRestrictions(int newsId) {
        var news = newsRepository.findById(newsId).orElse(null);
        RestrictionMode mode = news != null ? news.restrictionMode() : RestrictionMode.AND;
        return restrictionRepository.findRestrictionSet(
                RestrictionType.NEWS.table(), RestrictionType.NEWS.fkColumn(), newsId, mode);
    }

    /**
     * Sets all restrictions for a news article.
     */
    public void setRestrictions(
            int newsId, List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {
        restrictionRepository.setRestrictions(
                RestrictionType.NEWS.table(),
                RestrictionType.NEWS.fkColumn(),
                newsId,
                roleIds != null ? roleIds : List.of(),
                groupIds != null ? groupIds : List.of(),
                tagIds != null ? tagIds : List.of(),
                memberIds != null ? memberIds : List.of());
    }

    // -- Comments --

    /**
     * Counts the total number of comments on a news article.
     *
     * @param newsId the news article ID
     * @return comment count
     */
    public int countComments(int newsId) {
        return newsRepository.countComments(newsId);
    }

    /**
     * Creates a comment on a news article.
     *
     * @param newsId   the news article ID
     * @param parentId parent comment ID for replies, or {@code null} for top-level comments
     * @param authorId member ID of the comment author
     * @param content  comment text
     * @return the newly created comment
     */
    public NewsComment createComment(
            int stationId, int newsId, Integer parentId, int authorId, String authorName, String content) {
        var comment = newsRepository.createComment(newsId, parentId, authorId, content);
        var news = newsRepository.findById(newsId).orElse(null);
        if (news != null) {
            String preview = content.length() > 100 ? content.substring(0, 100) + "..." : content;
            Integer parentAuthorId = null;
            if (parentId != null) {
                parentAuthorId = newsRepository
                        .findCommentById(parentId)
                        .map(NewsComment::authorId)
                        .orElse(null);
            }
            eventBus.publish(new CommentCreated(
                    stationId,
                    newsId,
                    news.title(),
                    news.authorId(),
                    comment.id(),
                    parentId,
                    parentAuthorId,
                    authorId,
                    authorName,
                    preview));
        }
        return comment;
    }

    /**
     * Retrieves all comments for a news article.
     *
     * @param newsId the news article ID
     * @return list of comments
     */
    public List<NewsComment> findComments(int newsId) {
        return newsRepository.findCommentsByNews(newsId);
    }

    /**
     * Finds a comment by its ID.
     *
     * @param id the comment ID
     * @return the comment, or empty if not found
     */
    public Optional<NewsComment> findCommentById(int id) {
        return newsRepository.findCommentById(id);
    }

    /**
     * Updates the content of a comment.
     *
     * @param id      the comment ID
     * @param content new comment text
     * @return {@code true} if the comment was updated
     */
    public boolean updateComment(int id, String content) {
        return newsRepository.updateComment(id, content);
    }

    /**
     * Deletes a comment by its ID.
     *
     * @param id the comment ID
     * @return {@code true} if the comment was deleted
     */
    public boolean deleteComment(int stationId, int id) {
        var comment = newsRepository.findCommentById(id).orElse(null);
        if (comment == null) return false;
        if (newsRepository.deleteComment(id)) {
            String preview =
                    comment.content().length() > 100 ? comment.content().substring(0, 100) + "..." : comment.content();
            eventBus.publish(new CommentDeleted(stationId, id, preview));
            return true;
        }
        return false;
    }
}
