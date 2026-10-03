/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.PublicOffer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.waitinglist.service.WaitingListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Whether a link to a station's public page leads anywhere: only where the page has something to
 * open, not merely a form reached by its own link.
 */
class PublicStationPageTest {
    private static final int STATION_ID = 7;

    private PageService pages;
    private WaitingListService waitingLists;
    private NewsService news;
    private FormService forms;
    private PublicStationInfoService service;

    @BeforeEach
    void setup() {
        pages = mock(PageService.class);
        waitingLists = mock(WaitingListService.class);
        news = mock(NewsService.class);
        forms = mock(FormService.class);
        service = new PublicStationInfoService(
                mock(StationRepository.class), mock(StationLogoService.class), pages, waitingLists, news, forms);
    }

    private static Station station(StationKind kind) {
        var station = mock(Station.class);
        when(station.id()).thenReturn(STATION_ID);
        when(station.stationKind()).thenReturn(kind);
        when(station.publicKbMode()).thenReturn(PublicKbMode.OFF);
        return station;
    }

    @Test
    void aStationThatPublishesNothingHasNoPublicPage() {
        when(forms.hasOpenlyAddressedForms(STATION_ID)).thenReturn(true);

        assertTrue(service.offer(station(StationKind.REGULAR)).isEmpty());
    }

    @Test
    void aStationWithAPublicWikiCalendarListedPageWaitingListOrBlogHasOne() {
        var wiki = station(StationKind.REGULAR);
        when(wiki.publicKbMode()).thenReturn(PublicKbMode.ALLOW_ALL);
        var calendar = station(StationKind.REGULAR);
        when(calendar.publicCalendarEnabled()).thenReturn(true);
        var page = station(StationKind.REGULAR);
        when(page.publicPagesEnabled()).thenReturn(true);
        var waitlist = station(StationKind.REGULAR);
        when(waitlist.publicWaitlistEnabled()).thenReturn(true);
        var blog = station(StationKind.REGULAR);
        when(blog.publicBlogEnabled()).thenReturn(true);
        when(pages.withListedPages(List.of(STATION_ID))).thenReturn(Set.of(STATION_ID));
        when(waitingLists.withPublicWaitlists(List.of(STATION_ID))).thenReturn(Set.of(STATION_ID));
        when(news.withPublicBlogEntries(List.of(STATION_ID))).thenReturn(Set.of(STATION_ID));

        for (var station : List.of(wiki, calendar, page, waitlist, blog)) {
            assertFalse(service.offer(station).isEmpty());
        }
        assertEquals(new PublicOffer(true, false, false, false, false), service.offer(wiki));
        assertEquals(new PublicOffer(false, false, false, true, false), service.offer(waitlist));
        assertEquals(new PublicOffer(false, false, false, false, true), service.offer(blog));
    }

    @Test
    void manyStationsAreAskedAboutEachKindInOneQueryAndOnlyWhereSwitchedOn() {
        var page = station(StationKind.REGULAR);
        when(page.publicPagesEnabled()).thenReturn(true);
        var other = mock(Station.class);
        when(other.id()).thenReturn(8);
        when(other.stationKind()).thenReturn(StationKind.REGULAR);
        when(other.publicKbMode()).thenReturn(PublicKbMode.OFF);
        when(other.publicPagesEnabled()).thenReturn(true);
        when(other.publicBlogEnabled()).thenReturn(true);
        var home = mock(Station.class);
        when(home.id()).thenReturn(9);
        when(home.stationKind()).thenReturn(StationKind.CLUSTER_HOME);
        when(home.publicKbMode()).thenReturn(PublicKbMode.ALLOW_ALL);
        when(home.publicPagesEnabled()).thenReturn(true);
        when(pages.withListedPages(List.of(STATION_ID, 8))).thenReturn(Set.of(8));
        when(news.withPublicBlogEntries(List.of(8))).thenReturn(Set.of(8));

        var offers = service.offers(List.of(page, other, home));

        assertEquals(new PublicOffer(false, false, false, false, false), offers.get(STATION_ID));
        assertEquals(new PublicOffer(false, false, true, false, true), offers.get(8));
        assertEquals(new PublicOffer(true, false, false, false, false), offers.get(9));
        verify(pages).withListedPages(List.of(STATION_ID, 8));
        verify(waitingLists).withPublicWaitlists(List.of());
        verifyNoMoreInteractions(pages);
    }

    @Test
    void aWaitingListOrBlogSwitchedOnWithNothingInItIsNoOffer() {
        var waitlist = station(StationKind.REGULAR);
        when(waitlist.publicWaitlistEnabled()).thenReturn(true);
        when(waitlist.publicBlogEnabled()).thenReturn(true);

        assertTrue(service.offer(waitlist).isEmpty());
    }

    @Test
    void anAssociationsStationHasOneOnlyWithItsWiki() {
        var home = station(StationKind.CLUSTER_HOME);
        when(home.publicCalendarEnabled()).thenReturn(true);

        assertTrue(service.offer(home).isEmpty());

        when(home.publicKbMode()).thenReturn(PublicKbMode.ALLOW_ALL);
        assertFalse(service.offer(home).isEmpty());
    }

    @Test
    void theDiscoveryCardLeavesTheWikiOutUnlessTheStationShowsItThere() {
        var everything = new PublicOffer(true, true, true, true, true);
        var hidden = station(StationKind.REGULAR);
        var shown = station(StationKind.REGULAR);
        when(shown.discoveryShowKb()).thenReturn(true);

        assertEquals(new PublicOffer(false, true, true, true, true), everything.inDiscoveryOf(hidden));
        assertEquals(everything, everything.inDiscoveryOf(shown));
    }
}
