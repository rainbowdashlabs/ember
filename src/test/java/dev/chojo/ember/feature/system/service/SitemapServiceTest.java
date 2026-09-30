/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SitemapServiceTest {
    private static final UUID OPEN = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CLOSED = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant OLDER = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant NEWER = Instant.parse("2026-09-01T10:00:00Z");

    private StationRepository stations;
    private PageService pages;
    private KnowledgeBaseService knowledgeBase;
    private SitemapService service;

    private static Station station(int id, UUID uid, boolean open) {
        var station = mock(Station.class);
        when(station.id()).thenReturn(id);
        when(station.uid()).thenReturn(uid);
        when(station.publicSlug()).thenReturn(open ? "nord" : null);
        when(station.publicCalendarEnabled()).thenReturn(open);
        when(station.publicKbMode()).thenReturn(open ? PublicKbMode.ALLOW_ALL : PublicKbMode.OFF);
        when(station.publicPagesEnabled()).thenReturn(open);
        return station;
    }

    @BeforeEach
    void setup() {
        stations = mock(StationRepository.class);
        pages = mock(PageService.class);
        knowledgeBase = mock(KnowledgeBaseService.class);
        var api = mock(Api.class);
        when(api.baseUrl()).thenReturn("https://ember.test/");
        service = new SitemapService(stations, pages, knowledgeBase, api);
    }

    @Test
    void theIndexNamesOnlyStationsWithSomethingPublic() {
        var listed = List.of(station(1, OPEN, true), station(2, CLOSED, false));
        when(stations.findAllRegular()).thenReturn(listed);

        String index = service.index();

        assertTrue(index.contains("https://ember.test/sitemap-station-" + OPEN + ".xml"));
        assertFalse(index.contains(CLOSED.toString()));
        service.index();
        verify(stations, times(1)).findAllRegular();
    }

    @Test
    void aStationMapListsItsCalendarKnowledgeBaseAndPagesUnderItsSlug() {
        var open = station(1, OPEN, true);
        when(stations.findByUid(OPEN)).thenReturn(Optional.of(open));
        when(pages.hasListedPages(1)).thenReturn(true);
        var file = mock(KbFile.class);
        when(file.id()).thenReturn(4);
        when(file.updatedAt()).thenReturn(OLDER);
        when(knowledgeBase.findAllPublicFiles(1, PublicKbMode.ALLOW_ALL)).thenReturn(List.of(file));
        var page = mock(StationPage.class);
        when(page.updatedAt()).thenReturn(NEWER);
        when(pages.listListedPages(1)).thenReturn(List.of(page));
        when(pages.getPagePath(any())).thenReturn("about");

        String map = service.forStation(OPEN);

        assertTrue(map.contains("https://ember.test/public/station/nord/calendar"));
        assertTrue(map.contains("https://ember.test/public/station/nord/knowledge/file/4"));
        assertTrue(map.contains("https://ember.test/public/station/nord/page/about"));
        assertTrue(map.contains("2026-09-01T10:00:00Z"));
    }

    @Test
    void aStationThatIsNotHereOrPublishesNothingHasNoMap() {
        var closed = station(2, CLOSED, false);
        when(stations.findByUid(CLOSED)).thenReturn(Optional.of(closed));
        when(stations.findByUid(OPEN)).thenReturn(Optional.empty());

        assertEquals(
                Refusal.SITEMAP_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.forStation(CLOSED))
                        .refusal());
        assertEquals(
                Refusal.SITEMAP_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.forStation(OPEN))
                        .refusal());
    }
}
