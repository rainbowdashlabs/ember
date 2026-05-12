/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.News;
import dev.chojo.ember.entity.NewsComment;
import dev.chojo.ember.repository.NewsRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class NewsService {
    private final NewsRepository newsRepository;

    @Inject
    public NewsService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    public News create(
            int stationId,
            String title,
            String contentMarkdown,
            String contentHtml,
            int authorId,
            List<Integer> groupIds) {
        var news = newsRepository.create(stationId, title, contentMarkdown, contentHtml, authorId);
        if (!groupIds.isEmpty()) {
            newsRepository.setGroupRestrictions(news.id(), groupIds);
        }
        return news;
    }

    public Optional<News> findById(int id) {
        return newsRepository.findById(id);
    }

    public List<News> findByStation(int stationId, int offset, int limit) {
        return newsRepository.findByStation(stationId, offset, limit);
    }

    public List<News> findVisibleForMember(int stationId, int memberId, int offset, int limit) {
        return newsRepository.findVisibleForMember(stationId, memberId, offset, limit);
    }

    public Optional<News> update(
            int id, String title, String contentMarkdown, String contentHtml, List<Integer> groupIds) {
        if (newsRepository.update(id, title, contentMarkdown, contentHtml)) {
            newsRepository.setGroupRestrictions(id, groupIds);
            return newsRepository.findById(id);
        }
        return Optional.empty();
    }

    public boolean delete(int id) {
        return newsRepository.delete(id);
    }

    public List<Integer> findGroupRestrictions(int newsId) {
        return newsRepository.findGroupRestrictions(newsId);
    }

    // -- Comments --

    public int countComments(int newsId) {
        return newsRepository.countComments(newsId);
    }

    public NewsComment createComment(int newsId, Integer parentId, int authorId, String content) {
        return newsRepository.createComment(newsId, parentId, authorId, content);
    }

    public List<NewsComment> findComments(int newsId) {
        return newsRepository.findCommentsByNews(newsId);
    }

    public boolean deleteComment(int id) {
        return newsRepository.deleteComment(id);
    }
}
