/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.repository.FederationRepository.PublicPartnerSummary;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.page.service.PublicSiteService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationLogoService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The editor's member list preview, a station's public pages and a page reached by its link, over
 * HTTP.
 */
class PageSiteRoutesTest {
    private static final UUID STATION_UID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void theEditorPreviewsAMemberListOfItsOwnStation() {
        var pages = mock(PageService.class);
        when(pages.resolveMemberList(anyInt(), any())).thenReturn(List.of());
        var harness = RouteHarness.serving(new PageRoutes(
                pages,
                mock(MediaLibraryService.class),
                mock(FormService.class),
                mock(FormAnalyticsAssembler.class),
                new Api()));

        var answer = harness.request(client -> client.post(
                PREFIX + "/pages/member-list/resolve",
                body("{\"source\": {}, \"sortBy\": \"ORDER\"}"),
                harness.as(TestSessions.member(3, StationPermission.PAGE_EDIT))));

        assertEquals(200, answer.code());
        verify(pages).resolveMemberList(eq(3), any(JsonNode.class));
    }

    @Test
    void theSiteOfAnOpenStationListsItsPagesAndPartners() {
        var pages = mock(PageService.class);
        var site = mock(PublicSiteService.class);
        when(site.openStation("wache")).thenReturn(3);
        when(site.openStation("closed")).thenThrow(Refusal.PUBLIC_PAGES_SWITCHED_OFF.raise());
        when(pages.listListedPages(3)).thenReturn(List.of());
        when(site.partners(3, "a,b")).thenReturn(List.of(new PublicPartnerSummary(STATION_UID, "Nord", null, null)));
        var harness = RouteHarness.serving(new PublicPageRoutes(pages, site));

        harness.run((server, client) -> {
            assertEquals(200, client.get(PREFIX + "/public/pages/wache").code());
            assertEquals(
                    "Nord",
                    json(client.get(PREFIX + "/public/pages/wache/partners?uids=a,b"))
                            .get(0)
                            .path("name")
                            .asString());
            assertEquals(Refusal.PUBLIC_PAGES_SWITCHED_OFF, refusalOf(client.get(PREFIX + "/public/pages/closed")));
        });
    }

    @Test
    void aSharedPageNamesTheStationAroundIt() {
        var pages = mock(PageService.class);
        var site = mock(PublicSiteService.class);
        var page = mock(StationPage.class);
        when(pages.getSharedPage("t")).thenReturn(Optional.of(page));
        var station = mock(Station.class);
        when(station.uid()).thenReturn(STATION_UID);
        when(station.name()).thenReturn("Wache");
        when(station.timezone()).thenReturn("Europe/Berlin");
        when(site.sharedPageStation(page)).thenReturn(station);
        var harness = RouteHarness.serving(new SharedPageRoutes(pages, site, mock(StationLogoService.class)));

        harness.run((server, client) -> {
            assertEquals(
                    "Wache",
                    json(client.get(PREFIX + "/public/shared/t/brand"))
                            .path("name")
                            .asString());
            assertEquals(Refusal.PAGE_LINK_UNKNOWN, refusalOf(client.get(PREFIX + "/public/shared/x/brand")));
        });
    }
}
