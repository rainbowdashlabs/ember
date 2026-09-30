/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.service;

import dev.chojo.ember.MovableClock;
import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.insights.entity.PageHitBucket;
import dev.chojo.ember.feature.insights.repository.PageHitRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class PageHitRecorderTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static StationPage page;
    private static Metrics metrics;

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("RecorderStation");
        account = accountRepo.create("recorder@test.example", "Rec", "Order");
        StationMember member = stationMemberRepo.create(station.id(), account.id());
        page = pageRepo.create(station.id(), "Home", "home", null, member.id());
        metrics = Mockito.mock(Metrics.class);
        Mockito.when(metrics.webStatsEnabled()).thenReturn(true);
        Mockito.when(metrics.webStatsRetentionDays()).thenReturn(365);
        Mockito.when(metrics.webStatsFlushIntervalSeconds()).thenReturn(30);
    }

    @AfterAll
    static void cleanupClass() {
        pageHitRepo.pruneBefore(Instant.now().plus(1, ChronoUnit.DAYS));
        pageRepo.delete(page.id());
        accountRepo.delete(account.id());
        stationRepo.delete(station.id());
    }

    private static PageHitRecorder newRecorder() {
        return new PageHitRecorder(pageHitRepo, metrics);
    }

    private static MovableClock clockAt(Instant hour) {
        return new MovableClock(hour.plus(10, ChronoUnit.MINUTES));
    }

    @Test
    void recordAccumulatesInMemoryWithoutFlush() {
        var rec = newRecorder();
        rec.record(page.id(), "de", "direct", false);
        rec.record(page.id(), "DE", "direct", false);
        rec.record(page.id(), "AT", "direct", false);

        var snap = rec.snapshot();
        assertEquals(2, snap.size());
        var de = snap.stream().filter(b -> b.country().equals("DE")).findFirst().orElseThrow();
        assertEquals(2, de.hits());
    }

    @Test
    void blankInputsFallBackToDefaults() {
        var rec = newRecorder();
        rec.record(page.id(), null, null, false);
        rec.record(page.id(), "DEU", "", false);
        var snap = rec.snapshot();
        assertEquals(1, snap.size());
        assertEquals("XX", snap.getFirst().country());
        assertEquals("direct", snap.getFirst().refererDomain());
        assertEquals(2, snap.getFirst().hits());
    }

    @Test
    void recordIsNoOpWhenDisabled() {
        var disabled = Mockito.mock(Metrics.class);
        Mockito.when(disabled.webStatsEnabled()).thenReturn(false);
        var rec = new PageHitRecorder(pageHitRepo, disabled);
        rec.record(page.id(), "DE", "direct", false);
        assertEquals(0, rec.bufferedBucketCount());
    }

    @Test
    void tasksRunOnlyWhileEnabled() {
        var disabled = Mockito.mock(Metrics.class);
        Mockito.when(disabled.webStatsFlushIntervalSeconds()).thenReturn(0);
        var repository = Mockito.mock(PageHitRepository.class);
        var rec = new PageHitRecorder(repository, disabled);

        var flush = new PageHitRecorder.FlushTask(rec, disabled);
        var prune = new PageHitRecorder.PruneTask(rec, disabled);
        flush.run();
        prune.run();

        assertEquals(Duration.ofSeconds(1), flush.schedule().period(), "the interval is at least a second");
        Mockito.verifyNoInteractions(repository);

        Mockito.when(disabled.webStatsEnabled()).thenReturn(true);
        prune.run();
        flush.run();
        Mockito.verify(repository).pruneBefore(Mockito.any());
    }

    @Test
    void flushKeepsCurrentHourBucket() {
        var rec = newRecorder();
        rec.record(page.id(), "DE", "direct", false);
        rec.flush();
        assertEquals(1, rec.bufferedBucketCount());
    }

    @Test
    void flushPersistsAgedBucketsAndCollapsesLongTail() {
        Instant pastHour = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(5, ChronoUnit.HOURS);
        var clock = clockAt(pastHour);
        var rec = new PageHitRecorder(pageHitRepo, metrics, clock);
        rec.record(page.id(), "DE", "obscure.example.tld", false);
        clock.advance(Duration.ofHours(1));

        rec.flush();

        var rows = pageHitRepo.findForPage(page.id(), pastHour, pastHour);
        assertEquals(1, rows.size());
        assertEquals(
                "other", rows.getFirst().refererDomain(), "Domain with no historical hits should collapse to 'other'");
        assertEquals(0, rec.bufferedBucketCount(), "a written past hour leaves memory");

        Instant pastHour2 = pastHour.plus(1, ChronoUnit.HOURS);
        for (int i = 0; i < 6; i++) {
            pageHitRepo.upsert(new PageHitBucket(pastHour2, page.id(), "DE", "popular.example.tld", false, 1));
        }
        Instant laterHour = pastHour2.plus(2, ChronoUnit.HOURS);
        var laterClock = clockAt(laterHour);
        var later = new PageHitRecorder(pageHitRepo, metrics, laterClock);
        later.record(page.id(), "DE", "popular.example.tld", false);
        laterClock.advance(Duration.ofHours(1));
        later.flush();
        var preserved = pageHitRepo.findForPage(page.id(), laterHour, laterHour);
        assertTrue(preserved.stream().anyMatch(r -> r.refererDomain().equals("popular.example.tld")));
    }

    @Test
    void flushAllWritesTheCurrentHourAndAddsOnTheNextFlush() {
        Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(30, ChronoUnit.HOURS);
        var rec = new PageHitRecorder(pageHitRepo, metrics, clockAt(hour));
        rec.record(page.id(), "FR", "direct", false);
        rec.record(page.id(), "FR", "direct", false);

        rec.flushAll();
        rec.record(page.id(), "FR", "direct", false);
        rec.flushAll();
        rec.flushAll();

        var rows = pageHitRepo.findForPage(page.id(), hour, hour).stream()
                .filter(row -> row.country().equals("FR"))
                .toList();
        assertEquals(1, rows.size());
        assertEquals(3, rows.getFirst().hits(), "the second flush adds its delta rather than overwriting");
        assertEquals("page hits", rec.name());
    }

    @Test
    void flushSwallowsRepositoryExceptions() {
        var failing = Mockito.mock(PageHitRepository.class);
        Mockito.doThrow(new RuntimeException("simulated outage")).when(failing).upsert(Mockito.any());
        Mockito.when(failing.recentRefererCount(Mockito.anyInt(), Mockito.anyString(), Mockito.any()))
                .thenThrow(new RuntimeException("simulated outage"));
        var clock = clockAt(Instant.parse("2026-06-17T05:00:00Z"));
        var rec = new PageHitRecorder(failing, metrics, clock);
        rec.record(page.id(), "DE", "some.example.tld", false);
        clock.advance(Duration.ofHours(1));
        assertDoesNotThrow(rec::flush);
        assertEquals(1, rec.bufferedBucketCount());
    }

    @Test
    void pruneDelegatesToRepositoryWithRetentionCutoff() {
        Instant ancient = Instant.now().minus(400, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        pageHitRepo.upsert(new PageHitBucket(ancient, page.id(), "DE", "direct", false, 1));
        newRecorder().prune();
        var rows = pageHitRepo.findForPage(page.id(), ancient, ancient);
        assertTrue(rows.isEmpty(), "400-day-old row should have been pruned with default 365-day retention");
    }

    @Test
    void pruneSwallowsRepositoryExceptions() {
        var failing = Mockito.mock(PageHitRepository.class);
        Mockito.when(failing.pruneBefore(Mockito.any())).thenThrow(new RuntimeException("simulated outage"));
        var rec = new PageHitRecorder(failing, metrics);
        assertDoesNotThrow(rec::prune);
    }
}
