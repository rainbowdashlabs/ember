/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.insights.entity.PageHitBucket;
import dev.chojo.ember.feature.insights.repository.PageHitRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PageHitRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static StationPage page;
    private static StationPage otherPage;
    private static final Instant HOUR = Instant.parse("2026-06-18T10:00:00Z").truncatedTo(ChronoUnit.HOURS);
    private static final Instant HOUR_LATER = HOUR.plus(1, ChronoUnit.HOURS);

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("PageHitStation");
        account = accountRepo.create("pagehit@test.example", "Page", "Hit");
        StationMember member = stationMemberRepo.create(station.id(), account.id());
        page = pageRepo.create(station.id(), "Welcome", "welcome", null, member.id());
        otherPage = pageRepo.create(station.id(), "About", "about", null, member.id());
    }

    @AfterAll
    static void cleanupClass() {
        pageHitRepo.pruneBefore(Instant.now().plus(1, ChronoUnit.DAYS));
        pageRepo.delete(page.id());
        pageRepo.delete(otherPage.id());
        accountRepo.delete(account.id());
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void upsertAccumulatesHits() {
        pageHitRepo.upsert(new PageHitBucket(HOUR, page.id(), "DE", "direct", false, 5));
        pageHitRepo.upsert(new PageHitBucket(HOUR, page.id(), "DE", "direct", false, 3));

        var rows = pageHitRepo.findForPage(page.id(), HOUR, HOUR);
        assertEquals(1, rows.size());
        assertEquals(8, rows.getFirst().hits());
        assertEquals("DE", rows.getFirst().country());
        assertEquals("direct", rows.getFirst().refererDomain());
        assertFalse(rows.getFirst().isBot());
    }

    @Test
    @Order(2)
    void upsertKeepsDimensionsSeparate() {
        pageHitRepo.upsert(new PageHitBucket(HOUR, page.id(), "AT", "direct", false, 2));
        pageHitRepo.upsert(new PageHitBucket(HOUR, page.id(), "DE", "google.com", false, 4));
        pageHitRepo.upsert(new PageHitBucket(HOUR, page.id(), "DE", "direct", true, 7));

        var rows = pageHitRepo.findForPage(page.id(), HOUR, HOUR);
        assertEquals(4, rows.size());
        assertTrue(rows.stream().anyMatch(r -> r.country().equals("AT") && r.hits() == 2));
        assertTrue(rows.stream().anyMatch(r -> r.refererDomain().equals("google.com") && r.hits() == 4));
        assertTrue(rows.stream().anyMatch(r -> r.isBot() && r.hits() == 7));
    }

    @Test
    @Order(3)
    void leaderboardSumsHitsAndBots() {
        pageHitRepo.upsert(new PageHitBucket(HOUR, otherPage.id(), "DE", "direct", false, 11));
        pageHitRepo.upsert(new PageHitBucket(HOUR, otherPage.id(), "DE", "direct", true, 9));

        var rows = pageHitRepo.leaderboard(station.id(), HOUR, HOUR, 50);
        assertEquals(2, rows.size());

        var welcome =
                rows.stream().filter(r -> r.pageId() == page.id()).findFirst().orElseThrow();
        assertEquals(8 + 4 + 2, welcome.hits());
        assertEquals(7, welcome.botHits());

        var about = rows.stream()
                .filter(r -> r.pageId() == otherPage.id())
                .findFirst()
                .orElseThrow();
        assertEquals(11, about.hits());
        assertEquals(9, about.botHits());
    }

    @Test
    @Order(4)
    void leaderboardRespectsLimit() {
        var rows = pageHitRepo.leaderboard(station.id(), HOUR, HOUR, 1);
        assertEquals(1, rows.size());
    }

    @Test
    @Order(5)
    void countryTotalsExcludeBots() {
        var totals = pageHitRepo.countryTotalsForPage(page.id(), HOUR, HOUR);
        var de = totals.stream()
                .filter(t -> t.dimension().equals("DE"))
                .findFirst()
                .orElseThrow();
        assertEquals(8 + 4, de.hits());
        var at = totals.stream()
                .filter(t -> t.dimension().equals("AT"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, at.hits());
    }

    @Test
    @Order(6)
    void refererTotalsExcludeBots() {
        var totals = pageHitRepo.refererTotalsForPage(page.id(), HOUR, HOUR);
        var direct = totals.stream()
                .filter(t -> t.dimension().equals("direct"))
                .findFirst()
                .orElseThrow();
        assertEquals(8 + 2, direct.hits());
        var google = totals.stream()
                .filter(t -> t.dimension().equals("google.com"))
                .findFirst()
                .orElseThrow();
        assertEquals(4, google.hits());
    }

    @Test
    @Order(7)
    void hourlyTotalsBucketByHour() {
        pageHitRepo.upsert(new PageHitBucket(HOUR_LATER, page.id(), "DE", "direct", false, 6));
        var totals = pageHitRepo.stationHourlyTotals(station.id(), HOUR, HOUR_LATER, false);
        assertEquals(2, totals.size());
        assertTrue(totals.stream().anyMatch(t -> t.hour().equals(HOUR_LATER) && t.hits() == 6));
    }

    @Test
    @Order(8)
    void hourlyTotalsIncludeBotsWhenRequested() {
        var noBots = pageHitRepo.stationHourlyTotals(station.id(), HOUR, HOUR, false);
        var withBots = pageHitRepo.stationHourlyTotals(station.id(), HOUR, HOUR, true);
        long noBotsTotal =
                noBots.stream().mapToLong(PageHitRepository.HourlyTotal::hits).sum();
        long withBotsTotal =
                withBots.stream().mapToLong(PageHitRepository.HourlyTotal::hits).sum();
        assertTrue(withBotsTotal > noBotsTotal);
    }

    @Test
    @Order(9)
    void recentRefererCountSumsHits() {
        long count = pageHitRepo.recentRefererCount(page.id(), "google.com", HOUR.minus(1, ChronoUnit.HOURS));
        assertEquals(4, count);
        long missing = pageHitRepo.recentRefererCount(page.id(), "unknown.example", HOUR.minus(1, ChronoUnit.HOURS));
        assertEquals(0, missing);
    }

    @Test
    @Order(10)
    void pruneRemovesOldRows() {
        Instant ancient = HOUR.minus(400, ChronoUnit.DAYS);
        pageHitRepo.upsert(new PageHitBucket(ancient, page.id(), "DE", "direct", false, 1));
        int removed = pageHitRepo.pruneBefore(ancient.plus(1, ChronoUnit.HOURS));
        assertTrue(removed >= 1);
        assertTrue(pageHitRepo.findForPage(page.id(), ancient, ancient).isEmpty());
    }
}
