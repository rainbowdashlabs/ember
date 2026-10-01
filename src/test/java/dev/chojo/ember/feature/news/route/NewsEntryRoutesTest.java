/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.route;

import dev.chojo.ember.api.LocalRouteServer;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.comment.service.CommentMentions;
import dev.chojo.ember.feature.content.BlockReferenceTestBase;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.news.service.NewsAttachmentService;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.news.service.PublicBlogService;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A news entry as a member of its station reads it over HTTP: which station it belongs to, and
 * whether it is kept to part of that station, which is what the lock beside its title says.
 */
class NewsEntryRoutesTest extends BlockReferenceTestBase {
    private static LocalRouteServer server;

    @BeforeAll
    static void setupClass() {
        var newsService = new NewsService(
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
                account, 1, station.id(), station.uid(), member, Set.of(StationPermission.LOGIN), Set.of(), null);
        server = LocalRouteServer.serving(stationRepo, clusterRepo, session, routes);
    }

    @AfterAll
    static void cleanupClass() {
        server.close();
    }

    private static JsonNode read(int newsId) throws Exception {
        var answer = server.get("/news/%d".formatted(newsId));
        assertEquals(200, answer.statusCode(), answer.body());
        return LocalRouteServer.json(answer);
    }

    @Test
    void anEntryOpenToTheWholeStationCarriesNoLock() throws Exception {
        var entry = read(internalNews.id());

        assertFalse(entry.get("restricted").asBoolean());
        assertEquals(station.uid().toString(), entry.get("stationId").asString());
    }

    @Test
    void anEntryKeptToPartOfTheStationSaysSo() throws Exception {
        var kept = newsRepo.create(station.id(), "Nur für einige", "Text", "<p>Text</p>", null);
        restrictionRepo.setRestrictions(
                RestrictionType.NEWS,
                kept.id(),
                new RestrictionSelection(List.of(StationUserType.values()), List.of(), List.of(), List.of(), null));

        assertTrue(read(kept.id()).get("restricted").asBoolean());
    }

    @Test
    void anEntryTheInstancePublishedNamesNoStation() throws Exception {
        var system = newsRepo.createSystem("An alle Wachen", "Text", "<p>Text</p>", true);
        try {
            var entry = read(system.id());

            assertTrue(entry.get("systemEntry").asBoolean());
            assertTrue(entry.get("stationId").isNull());
        } finally {
            newsRepo.delete(system.id());
        }
    }
}
