/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NewsServiceTest extends RepositoryTestBase {
    private static NewsService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int newsId;
    private static int commentId;

    @BeforeAll
    static void setup() {
        service = new NewsService(
                newsRepo,
                new dev.chojo.ember.feature.restriction.RestrictionRepository(),
                new dev.chojo.ember.event.DomainEventBus(java.util.Set.of()));
        station = stationRepo.create("NewsStation");
        account = accountRepo.create("news-svc@test.com", "News", "Author");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        var news = service.create(
                station.id(),
                "Test News",
                "Content of the news article",
                "<p>Content of the news article</p>",
                member.id(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        assertNotNull(news);
        assertEquals("Test News", news.title());
        newsId = news.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(service.findById(newsId).isPresent());
    }

    @Test
    @Order(3)
    void findByStation() {
        var list = service.findByStation(station.id(), 0, 100);
        assertTrue(list.stream().anyMatch(n -> n.id() == newsId));
    }

    @Test
    @Order(10)
    void update() {
        var result = service.update(
                newsId,
                "Updated News",
                "Updated content",
                "<p>Updated content</p>",
                List.of(),
                List.of(),
                List.of(),
                List.of());
        assertTrue(result.isPresent());
        assertEquals("Updated News", result.get().title());
    }

    @Test
    @Order(20)
    void createComment() {
        var comment = service.createComment(station.id(), newsId, null, member.id(), "News Author", "Great article!");
        assertNotNull(comment);
        commentId = comment.id();
    }

    @Test
    @Order(21)
    void findComments() {
        var comments = service.findComments(newsId);
        assertTrue(comments.stream().anyMatch(c -> c.id() == commentId));
    }

    @Test
    @Order(22)
    void deleteComment() {
        assertTrue(service.deleteComment(station.id(), commentId));
        var comments = service.findComments(newsId);
        assertFalse(comments.stream().anyMatch(c -> c.id() == commentId));
    }

    @Test
    @Order(30)
    void delete() {
        assertTrue(service.delete(newsId));
        assertTrue(service.findById(newsId).isEmpty());
    }
}
