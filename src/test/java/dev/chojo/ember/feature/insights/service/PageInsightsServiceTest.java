/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.insights.entity.PageHitBucket;
import dev.chojo.ember.feature.insights.repository.PageHitRepository;
import dev.chojo.ember.feature.insights.repository.PageHitRepository.HourlyTotal;
import dev.chojo.ember.feature.insights.repository.PageHitRepository.PageLeaderboardEntry;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.repository.PageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PageInsightsServiceTest {
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-02T00:00:00Z");
    private static final Instant NINE = Instant.parse("2026-09-01T09:00:00Z");
    private static final Instant TEN = Instant.parse("2026-09-01T10:00:00Z");

    private final PageHitRepository hits = mock(PageHitRepository.class);
    private final PageRepository pages = mock(PageRepository.class);
    private final PageInsightsService service = new PageInsightsService(hits, pages);

    private static StationPage pageOf(int stationId) {
        var page = mock(StationPage.class);
        when(page.stationId()).thenReturn(stationId);
        return page;
    }

    @Test
    void theLeaderboardIsTheStationsOwn() {
        var row = new PageLeaderboardEntry(1, "Start", "start", 10, 2);
        when(hits.leaderboard(3, FROM, TO, 50)).thenReturn(List.of(row));

        assertEquals(List.of(row), service.leaderboard(3, FROM, TO, 50).rows());
    }

    @Test
    void aPageIsCountedByTheHourWithAndWithoutBots() {
        var page = pageOf(3);
        when(pages.findById(8)).thenReturn(Optional.of(page));
        when(hits.findForPage(8, FROM, TO))
                .thenReturn(List.of(
                        new PageHitBucket(TEN, 8, "DE", null, false, 2),
                        new PageHitBucket(NINE, 8, "DE", null, true, 5),
                        new PageHitBucket(NINE, 8, "AT", null, false, 1)));

        var detail = service.pageDetail(3, 8, FROM, TO);

        assertEquals(List.of(new HourlyTotal(NINE, 1), new HourlyTotal(TEN, 2)), detail.hourly());
        assertEquals(List.of(new HourlyTotal(NINE, 6), new HourlyTotal(TEN, 2)), detail.hourlyWithBots());
    }

    @Test
    void aPageOfAnotherStationOrNoneAtAllIsNotHere() {
        var foreign = pageOf(4);
        when(pages.findById(8)).thenReturn(Optional.of(foreign));
        when(pages.findById(9)).thenReturn(Optional.empty());

        assertEquals(
                Refusal.INSIGHTS_PAGE_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.pageDetail(3, 8, FROM, TO))
                        .refusal());
        assertEquals(
                Refusal.INSIGHTS_PAGE_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.pageDetail(3, 9, FROM, TO))
                        .refusal());
    }
}
