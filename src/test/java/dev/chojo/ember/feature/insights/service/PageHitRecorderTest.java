/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.insights.service;

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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class PageHitRecorderTest extends RepositoryTestBase {

    private static Station station;
    private static Account account;
    private static StationMember member;
    private static StationPage page;
    private static Metrics metrics;

    @BeforeAll
    static void setupClass() {
        station = stationRepo.create("RecorderStation");
        account = accountRepo.create("recorder@test.example", "Rec", "Order");
        member = stationMemberRepo.create(station.id(), account.id());
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
        var snap = rec.snapshot();
        assertEquals(1, snap.size());
        assertEquals("XX", snap.getFirst().country());
        assertEquals("direct", snap.getFirst().refererDomain());
    }

    @Test
    void recordIsNoOpWhenDisabled() {
        var disabled = Mockito.mock(Metrics.class);
        Mockito.when(disabled.webStatsEnabled()).thenReturn(false);
        var rec = new PageHitRecorder(pageHitRepo, disabled);
        rec.record(page.id(), "DE", "direct", false);
        assertEquals(0, rec.bufferedBucketCount());
        rec.start();
        assertEquals(0, rec.bufferedBucketCount());
    }

    @Test
    void startSchedulesFlushAndPruneWhenEnabled() {
        var enabled = Mockito.mock(Metrics.class);
        Mockito.when(enabled.webStatsEnabled()).thenReturn(true);
        Mockito.when(enabled.webStatsRetentionDays()).thenReturn(7);
        Mockito.when(enabled.webStatsFlushIntervalSeconds()).thenReturn(3600);
        var rec = new PageHitRecorder(pageHitRepo, enabled);
        assertDoesNotThrow(rec::start);
    }

    @Test
    void flushKeepsCurrentHourBucket() {
        var rec = newRecorder();
        rec.record(page.id(), "DE", "direct", false);
        rec.flush();
        assertEquals(1, rec.bufferedBucketCount());
    }

    @Test
    void flushPersistsAgedBucketsAndCollapsesLongTail() throws Exception {
        var rec = newRecorder();
        Instant pastHour = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(5, ChronoUnit.HOURS);
        seedAgedBucket(rec, pastHour, page.id(), "DE", "obscure.example.tld", false, 1);

        rec.flush();

        var rows = pageHitRepo.findForPage(page.id(), pastHour, pastHour);
        assertEquals(1, rows.size());
        assertEquals(
                "other", rows.getFirst().refererDomain(), "Domain with no historical hits should collapse to 'other'");

        Instant pastHour2 = pastHour.plus(1, ChronoUnit.HOURS);
        for (int i = 0; i < 6; i++) {
            pageHitRepo.upsert(new PageHitBucket(pastHour2, page.id(), "DE", "popular.example.tld", false, 1));
        }
        seedAgedBucket(rec, pastHour2.plus(2, ChronoUnit.HOURS), page.id(), "DE", "popular.example.tld", false, 1);
        rec.flush();
        var preserved = pageHitRepo.findForPage(
                page.id(), pastHour2.plus(2, ChronoUnit.HOURS), pastHour2.plus(2, ChronoUnit.HOURS));
        assertTrue(preserved.stream().anyMatch(r -> r.refererDomain().equals("popular.example.tld")));
    }

    @Test
    void flushSwallowsRepositoryExceptions() throws Exception {
        var failing = Mockito.mock(PageHitRepository.class);
        Mockito.doThrow(new RuntimeException("simulated outage")).when(failing).upsert(Mockito.any());
        Mockito.when(failing.recentRefererCount(Mockito.anyInt(), Mockito.anyString(), Mockito.any()))
                .thenReturn(10L);
        var rec = new PageHitRecorder(failing, metrics);
        seedAgedBucket(rec, Instant.parse("2026-06-17T05:00:00Z"), page.id(), "DE", "direct", false, 3);
        assertDoesNotThrow(rec::flush);
        assertEquals(1, rec.bufferedBucketCount());
    }

    @Test
    void pruneDelegatesToRepositoryWithRetentionCutoff() throws Exception {
        Instant ancient = Instant.now().minus(400, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        pageHitRepo.upsert(new PageHitBucket(ancient, page.id(), "DE", "direct", false, 1));
        invokePrune(newRecorder());
        var rows = pageHitRepo.findForPage(page.id(), ancient, ancient);
        assertTrue(rows.isEmpty(), "400-day-old row should have been pruned with default 365-day retention");
    }

    @Test
    void pruneSwallowsRepositoryExceptions() {
        var failing = Mockito.mock(PageHitRepository.class);
        Mockito.when(failing.pruneBefore(Mockito.any())).thenThrow(new RuntimeException("simulated outage"));
        var rec = new PageHitRecorder(failing, metrics);
        assertDoesNotThrow(() -> invokePrune(rec));
    }

    private static void seedAgedBucket(
            PageHitRecorder rec, Instant hour, int pageId, String country, String referer, boolean isBot, long hits)
            throws Exception {
        var bucketsField = PageHitRecorder.class.getDeclaredField("buckets");
        bucketsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        var map = (ConcurrentHashMap<Object, Object>) bucketsField.get(rec);

        var keyClass = Class.forName("dev.chojo.ember.feature.insights.service.PageHitRecorder$BucketKey");
        var keyCtor =
                keyClass.getDeclaredConstructor(Instant.class, int.class, String.class, String.class, boolean.class);
        keyCtor.setAccessible(true);
        Object key = keyCtor.newInstance(hour, pageId, country, referer, isBot);

        var atomic = new AtomicLong(hits);
        map.put(key, atomic);
    }

    private static void invokePrune(PageHitRecorder rec) throws Exception {
        var m = PageHitRecorder.class.getDeclaredMethod("prune");
        m.setAccessible(true);
        m.invoke(rec);
    }
}
