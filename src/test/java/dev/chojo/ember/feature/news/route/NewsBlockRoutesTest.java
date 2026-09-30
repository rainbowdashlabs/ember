/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.api.LocalRouteServer;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.service.CommentMentions;
import dev.chojo.ember.feature.content.service.CellDescriptions;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.entity.News;
import dev.chojo.ember.feature.news.service.NewsAttachmentService;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A news block reads the entry it names, and its picker searches for one, over HTTP. Both reach only
 * what the station's public blog shows, and the block's lookup answers everything else with the same
 * 404, saying nothing about a draft or an entry kept to part of the station.
 */
class NewsBlockRoutesTest extends RepositoryTestBase {
    private static Station station;
    private static Station otherStation;
    private static Station blogless;
    private static Account account;
    private static StationMember member;
    private static News older;
    private static News newer;
    private static News draft;
    private static News restricted;
    private static News offTheBlog;
    private static News elsewhere;
    private static News bloglessEntry;
    private static LocalRouteServer server;

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("News Block Station");
        otherStation = stationRepo.create("News Block Elsewhere");
        blogless = stationRepo.create("News Block Without Blog");
        stationRepo.updatePublicBlogEnabled(station.id(), true);
        stationRepo.updatePublicBlogEnabled(otherStation.id(), true);
        account = accountRepo.create("news-block-route@test.com", "Nina", "News");
        member = stationMemberRepo.create(station.id(), account.id());
        var group = memberGroupRepo.create(station.id(), "Vorstand");

        older = onTheBlog(station, "Neue Drehleiter", "Die **Drehleiter** ist da", "2026-01-10T10:00:00Z");
        newer = onTheBlog(station, "Drehleiter im Einsatz", "Erster Einsatz", "2026-03-10T10:00:00Z");
        draft = onTheBlog(station, "Geheimer Entwurf", "Noch nicht fertig", null);
        restricted = onTheBlog(station, "Interne Drehleiter", "Nur Vorstand", "2026-02-10T10:00:00Z");
        restrictionRepo.setRestrictions(
                RestrictionType.NEWS,
                restricted.id(),
                new RestrictionSelection(List.of(), List.of(group.id()), List.of(), List.of(), null));
        offTheBlog = newsRepo.create(station.id(), "Drehleiter nicht im Blog", "Intern", "<p>Intern</p>", null);
        elsewhere = onTheBlog(otherStation, "Drehleiter anderswo", "Andere Wache", "2026-02-01T10:00:00Z");
        bloglessEntry = onTheBlog(blogless, "Drehleiter ohne Blog", "Kein Blog", "2026-02-01T10:00:00Z");

        var newsService = new NewsService(
                newsRepo,
                mock(ContentBlockService.class),
                mock(CellDescriptions.class),
                stationRepo,
                mock(RestrictionService.class),
                new DomainEventBus(Set.of()),
                stationMemberRepo,
                mock(MemberLookupService.class),
                accountRepo,
                mock(CommentMentions.class));
        var routes = new NewsRoutes(
                newsService,
                mock(NewsAttachmentService.class),
                mock(NewsFederationService.class),
                stationRepo,
                mock(MemberNameResolver.class),
                memberIdentityFactory,
                mock(EmailService.class));
        var session = new UserSession(
                account, 1, station.id(), station.uid(), member, Set.of(StationPermission.PAGE_EDIT), Set.of(), null);
        server = LocalRouteServer.serving(stationRepo, clusterRepo, session, routes);
    }

    @AfterAll
    static void cleanupClass() {
        server.close();
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        stationRepo.delete(blogless.id());
        accountRepo.delete(account.id());
    }

    private static News onTheBlog(Station owner, String title, String body, String publishedAt) {
        var news = newsRepo.create(owner.id(), title, body, "<p>" + body + "</p>", null);
        newsRepo.updatePublicBlog(news.id(), true);
        Query.query("""
                        UPDATE news
                        SET published_at = :published_at
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind(
                                "published_at",
                                publishedAt == null ? null : Instant.parse(publishedAt),
                                INSTANT_TIMESTAMP)
                        .bind("id", news.id()))
                .update();
        return news;
    }

    private static String teaserPath(Station owner, Object newsUid) {
        return "/public/station/%s/news-teaser/%s".formatted(owner.uid(), newsUid);
    }

    @Test
    void theBlockReadsThePublicEntryItNames() throws Exception {
        var answer = server.get(teaserPath(station, older.publicUid()));

        assertEquals(200, answer.statusCode(), answer.body());
        var teaser = LocalRouteServer.json(answer);
        assertEquals(older.id(), teaser.get("id").asInt());
        assertEquals(older.publicUid().toString(), teaser.get("publicUid").asString());
        assertEquals("Neue Drehleiter", teaser.get("title").asString());
        assertEquals("Die Drehleiter ist da", teaser.get("summary").asString());
        assertTrue(teaser.hasNonNull("publishedAt"));
    }

    @Test
    void everythingThePublicBlogDoesNotShowIsTheSameNotFound() throws Exception {
        var withheld = new ArrayList<String>();
        withheld.add(teaserPath(station, draft.publicUid()));
        withheld.add(teaserPath(station, restricted.publicUid()));
        withheld.add(teaserPath(station, offTheBlog.publicUid()));
        withheld.add(teaserPath(station, elsewhere.publicUid()));
        withheld.add(teaserPath(station, UUID.randomUUID()));
        withheld.add(teaserPath(station, "not-an-id"));
        withheld.add(teaserPath(blogless, bloglessEntry.publicUid()));
        withheld.add("/public/station/%s/news-teaser/%s".formatted(UUID.randomUUID(), older.publicUid()));

        for (var path : withheld) {
            var answer = server.get(path);
            assertEquals(404, answer.statusCode(), path);
            assertFalse(answer.body().contains("Geheimer Entwurf"), path);
            assertFalse(answer.body().contains("Interne Drehleiter"), path);
        }
    }

    @Test
    void thePickerFindsPublicEntriesByTitleNewestFirst() throws Exception {
        var page = search("drehleiter", 10);

        assertEquals(List.of("Drehleiter im Einsatz", "Neue Drehleiter"), titles(page));
        assertFalse(page.get("more").asBoolean());
    }

    @Test
    void thePickerSaysWhenThereIsMore() throws Exception {
        var first = search("", 1);
        assertEquals(List.of("Drehleiter im Einsatz"), titles(first));
        assertTrue(first.get("more").asBoolean());

        var both = search("", 2);
        assertEquals(List.of("Drehleiter im Einsatz", "Neue Drehleiter"), titles(both));
        assertFalse(both.get("more").asBoolean());
    }

    @Test
    void thePickerDoesNotSearchTheText() throws Exception {
        assertTrue(titles(search("ist da", 10)).isEmpty());
    }

    private static JsonNode search(String query, int limit) throws Exception {
        var answer = server.get(
                "/news/search?limit=%d&q=%s".formatted(limit, URLEncoder.encode(query, StandardCharsets.UTF_8)));
        assertEquals(200, answer.statusCode(), answer.body());
        return LocalRouteServer.json(answer);
    }

    private static List<String> titles(JsonNode page) {
        var titles = new ArrayList<String>();
        page.get("entries").forEach(entry -> titles.add(entry.get("title").asString()));
        return titles;
    }
}
