/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.LocalRouteServer;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.content.BlockReferenceTestBase;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.PageService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * A public page is read by anybody, so saving one over HTTP keeps a news or event block only when it
 * names what is public: the entry on the public blog, the appointment on the public calendar. An
 * internal one, and everything no member-wide reader may see either, is refused with 400 and the
 * page keeps what it had.
 */
class PageBlockReferenceRoutesTest extends BlockReferenceTestBase {
    private static PageService pageService;
    private static LocalRouteServer server;
    private StationPage page;

    @BeforeAll
    static void setupClass() {
        var media = mock(MediaLibraryService.class);
        pageService = new PageService(
                pageRepo,
                contentBlocks(),
                media,
                noCellDescriptions(),
                stationMemberRepo,
                mock(AvatarService.class),
                stationRepo);
        var routes = new PageRoutes(
                pageService, media, mock(FormService.class), mock(FormAnalyticsAssembler.class), new Api());
        var editor = new UserSession(
                account, 1, station.id(), station.uid(), member, Set.of(StationPermission.PAGE_EDIT), Set.of(), null);
        server = LocalRouteServer.serving(stationRepo, clusterRepo, editor, routes);
    }

    @AfterAll
    static void cleanupClass() {
        server.close();
    }

    @BeforeEach
    void createPage() {
        page = pageService.create(station.id(), "Startseite", null, member.id());
    }

    @AfterEach
    void deletePage() {
        pageService.deletePage(page.id());
    }

    @Test
    void aPageKeepsBlocksNamingWhatIsPublic() throws Exception {
        assertEquals(
                200,
                save("NEWS_TEASER", "newsUid", publicNews.publicUid().toString())
                        .statusCode());
        assertEquals(
                200,
                save("FEATURED_EVENT", "eventUid", uidOf(publicEvent).toString())
                        .statusCode());
    }

    @Test
    void aPageRefusesAnInternalEntryOrAppointment() throws Exception {
        assertEquals(
                400,
                save("NEWS_TEASER", "newsUid", internalNews.publicUid().toString())
                        .statusCode());
        assertEquals(
                400,
                save("FEATURED_EVENT", "eventUid", uidOf(internalEvent).toString())
                        .statusCode());
    }

    @Test
    void aPageRefusesWhatNoReaderMaySee() throws Exception {
        for (var withheld : newsNobodyMayName().entrySet()) {
            assertEquals(
                    400,
                    save(
                                    "NEWS_TEASER",
                                    "newsUid",
                                    withheld.getValue().publicUid().toString())
                            .statusCode(),
                    withheld.getKey());
        }
        for (var withheld : eventsNobodyMayName().entrySet()) {
            assertEquals(
                    400,
                    save(
                                    "FEATURED_EVENT",
                                    "eventUid",
                                    uidOf(withheld.getValue()).toString())
                            .statusCode(),
                    withheld.getKey());
        }
    }

    private HttpResponse<String> save(String contentType, String field, String uid) throws Exception {
        return server.put("/pages/%d".formatted(page.id()), """
                {"title": "Startseite", "slug": "startseite",
                 "rows": [{"sortOrder": 0, "cells": [{"sortOrder": 0, "widthPercent": 100,
                 "contentType": "%s", "content": "", "config": {"%s": "%s"}}]}]}""".formatted(contentType, field, uid));
    }
}
