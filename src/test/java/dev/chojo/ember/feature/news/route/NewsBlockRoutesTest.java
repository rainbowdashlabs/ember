/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.LocalRouteServer;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.comment.service.CommentMentions;
import dev.chojo.ember.feature.content.BlockReferenceTestBase;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.service.NewsAttachmentService;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.news.service.PublicBlogService;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A news block reads the entry it names, its picker searches for one, and an article saves one, over
 * HTTP. On a public page only what the public blog shows; in a news or wiki article every entry every
 * member may read, internal ones included. Everything else is the same 404 when drawn and a named
 * refusal when saved, and says nothing about a draft or an entry kept to part of the station.
 */
class NewsBlockRoutesTest extends BlockReferenceTestBase {
    private static Station blogless;
    private static News bloglessEntry;
    private static NewsService newsService;
    private static LocalRouteServer server;

    @BeforeAll
    static void setupClass() {
        blogless = stationRepo.create("News Block Without Blog");
        bloglessEntry = newsRepo.create(blogless.id(), "Drehleiter ohne Blog", "Kein Blog", "<p>Kein Blog</p>", null);
        newsRepo.updatePublicBlog(bloglessEntry.id(), true);

        newsService = new NewsService(
                newsRepo,
                contentBlocks(),
                noCellDescriptions(),
                stationRepo,
                restrictionService,
                new DomainEventBus(Set.of()),
                stationMemberRepo,
                mock(MemberLookupService.class),
                mock(MemberNameResolver.class),
                mock(CommentMentions.class));
        var routes = new NewsRoutes(
                newsService,
                mock(NewsAttachmentService.class),
                mock(NewsFederationService.class),
                new PublicBlogService(stationRepo),
                mock(MemberNameResolver.class),
                memberIdentityFactory,
                mock(EmailService.class));
        var session = new UserSession(
                account,
                1,
                station.id(),
                station.uid(),
                member,
                Set.of(StationPermission.PAGE_EDIT, StationPermission.NEWS_EDIT),
                Set.of(),
                null);
        server = LocalRouteServer.serving(stationRepo, clusterRepo, session, routes);
    }

    @AfterAll
    static void cleanupClass() {
        server.close();
        stationRepo.delete(blogless.id());
    }

    private static String publicPath(Station owner, Object newsUid) {
        return "/public/station/%s/news-teaser/%s".formatted(owner.uid(), newsUid);
    }

    private static String memberPath(Object newsUid) {
        return "/news/embed/%s".formatted(newsUid);
    }

    private static void notFound(String path, String why) throws Exception {
        var answer = server.get(path);
        assertEquals(404, answer.statusCode(), why);
        for (var withheld : newsNobodyMayName().values()) {
            assertFalse(answer.body().contains(withheld.title()), why);
        }
    }

    @Test
    void aPublicPageDrawsTheEntryOnThePublicBlog() throws Exception {
        var answer = server.get(publicPath(station, publicNews.publicUid()));

        assertEquals(200, answer.statusCode(), answer.body());
        var teaser = LocalRouteServer.json(answer);
        assertEquals(publicNews.id(), teaser.get("id").asInt());
        assertEquals(publicNews.publicUid().toString(), teaser.get("publicUid").asString());
        assertEquals(publicNews.title(), teaser.get("title").asString());
        assertEquals("Mehr im Text", teaser.get("summary").asString());
        assertTrue(teaser.hasNonNull("publishedAt"));
    }

    @Test
    void aPublicPageDrawsNothingElse() throws Exception {
        notFound(publicPath(station, internalNews.publicUid()), "internal");
        for (var withheld : newsNobodyMayName().entrySet()) {
            notFound(publicPath(station, withheld.getValue().publicUid()), withheld.getKey());
        }
        notFound(publicPath(station, UUID.randomUUID()), "unknown");
        notFound(publicPath(station, "not-an-id"), "malformed");
        notFound(publicPath(blogless, bloglessEntry.publicUid()), "station without a public blog");
        notFound(
                "/public/station/%s/news-teaser/%s".formatted(UUID.randomUUID(), publicNews.publicUid()), "no station");
    }

    @Test
    void anArticleDrawsEveryEntryEveryMemberMayRead() throws Exception {
        for (var news : List.of(publicNews, internalNews)) {
            var answer = server.get(memberPath(news.publicUid()));
            assertEquals(200, answer.statusCode(), news.title());
            assertEquals(
                    news.title(), LocalRouteServer.json(answer).get("title").asString());
        }
    }

    @Test
    void anArticleDrawsNothingElse() throws Exception {
        for (var withheld : newsNobodyMayName().entrySet()) {
            notFound(memberPath(withheld.getValue().publicUid()), withheld.getKey());
        }
        notFound(memberPath(UUID.randomUUID()), "unknown");
    }

    @Test
    void thePickerOffersAPageTheEntryOnThePublicBlogOnly() throws Exception {
        var page = search("PUBLIC", "drehleiter", 10);

        assertEquals(List.of(publicNews.title()), titles(page));
        assertFalse(page.get("more").asBoolean());
    }

    @Test
    void thePickerOffersAnArticleEveryEntryEveryMemberMayReadNewestFirst() throws Exception {
        assertEquals(List.of(internalNews.title(), publicNews.title()), titles(search("MEMBERS", "drehleiter", 10)));
    }

    @Test
    void thePickerSaysWhenThereIsMore() throws Exception {
        var first = search("MEMBERS", "", 1);
        assertEquals(List.of(internalNews.title()), titles(first));
        assertTrue(first.get("more").asBoolean());
        assertFalse(search("MEMBERS", "", 2).get("more").asBoolean());
    }

    @Test
    void thePickerDoesNotSearchTheText() throws Exception {
        assertTrue(titles(search("MEMBERS", "im Text", 10)).isEmpty());
    }

    @Test
    void anArticleSavesANewsBlockOnlyForAnEntryEveryMemberMayRead() throws Exception {
        var article = newsRepo.create(station.id(), "Artikel mit Block", "Text", "<p>Text</p>", null);
        newsService.switchToRich(article.id());
        try {
            var kept = saveTeaser(article, internalNews.publicUid());
            assertEquals(200, kept.statusCode(), kept.body());

            for (var withheld : newsNobodyMayName().entrySet()) {
                var refused = saveTeaser(article, withheld.getValue().publicUid());
                assertEquals(400, refused.statusCode(), withheld.getKey());
            }
        } finally {
            newsService.delete(article.id());
        }
    }

    private static java.net.http.HttpResponse<String> saveTeaser(News article, UUID newsUid) throws Exception {
        return server.put("/news/%d/blocks".formatted(article.id()), """
                {"rows": [{"sortOrder": 0, "cells": [{"sortOrder": 0, "widthPercent": 100,
                 "contentType": "NEWS_TEASER", "content": "", "config": {"newsUid": "%s"}}]}]}""".formatted(newsUid));
    }

    private static JsonNode search(String scope, String query, int limit) throws Exception {
        var answer = server.get("/news/search?scope=%s&limit=%d&q=%s"
                .formatted(scope, limit, URLEncoder.encode(query, StandardCharsets.UTF_8)));
        assertEquals(200, answer.statusCode(), answer.body());
        return LocalRouteServer.json(answer);
    }

    private static List<String> titles(JsonNode page) {
        var titles = new ArrayList<String>();
        page.get("entries").forEach(entry -> titles.add(entry.get("title").asString()));
        return titles;
    }
}
