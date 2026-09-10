/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.beacon.repository.BeaconMetricsSourceRepository;
import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What this instance sends, and what stops it sending.
 */
class BeaconReportingTest extends RepositoryTestBase {

    private static Station station;

    private ApplicationSettingRepository settings;
    private BeaconSettings config;
    private DiscoveryHttpClient httpClient;
    private BeaconMetricsService metrics;

    @BeforeAll
    static void aStationToCount() {
        station = stationRepo.create("Beacon Reporting Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @BeforeEach
    void freshServices() {
        settings = new ApplicationSettingRepository();
        config = new BeaconSettings(settings);
        httpClient = mock(DiscoveryHttpClient.class);
        var demo = mock(Demo.class);
        when(demo.enabled()).thenReturn(false);
        when(demo.dev()).thenReturn(false);
        metrics = new BeaconMetricsService(
                config,
                demo,
                new BeaconMetricsSourceRepository(),
                new BeaconMetricsIdentity(settings),
                new BeaconMetricsScheduler(settings, new BeaconMetricsIdentity(settings)),
                httpClient);
    }

    /** Nothing has been switched on, so nothing is on. */
    @Test
    void anInstanceThatWasNeverConfiguredReportsNowhere() {
        config.update(false, "", false, false, false, false, "", "");
        assertFalse(config.enabled());
        assertFalse(config.forwardProblems());
        assertFalse(config.metricsEnabled());
        assertEquals(BeaconSettings.DEFAULT_URL, config.url());
    }

    /**
     * The master switch really is one. Turning reporting off has to silence the three beneath it
     * rather than leaving them to be found switched on later.
     */
    @Test
    void theMasterSwitchSilencesTheRest() {
        config.update(true, "https://beacon.test", true, true, true, false, "Nora", "nora@example.com");
        assertTrue(config.forwardProblems());
        assertTrue(config.forwardReports());
        assertTrue(config.metricsEnabled());

        config.update(false, "https://beacon.test", true, true, true, false, "Nora", "nora@example.com");
        assertFalse(config.forwardProblems());
        assertFalse(config.forwardReports());
        assertFalse(config.metricsEnabled());
    }

    /** Clearing the address falls back to the default beacon rather than to nowhere. */
    @Test
    void anEmptyAddressFallsBackToTheDefault() {
        config.update(true, "  ", false, false, false, false, "", "");
        assertEquals(BeaconSettings.DEFAULT_URL, config.url());
    }

    /** Accepting reports is its own decision and does not follow the sending switch. */
    @Test
    void beingABeaconIsSeparateFromReporting() {
        config.update(false, "", false, false, false, true, "", "");
        assertFalse(config.enabled());
        assertTrue(config.receiving());
    }

    /** The batch names the instance and every station, and every count is a bucket. */
    @Test
    void theBatchCarriesTheInstanceAndItsStations() {
        var batch = metrics.batch("26.15.0", Instant.now());
        assertEquals("26.15.0", batch.version());
        assertTrue(batch.subjects().size() >= 2, "the instance and at least one station");
        assertEquals("INSTANCE", batch.subjects().getFirst().subject());
        for (var subject : batch.subjects()) {
            assertFalse(subject.members().matches("\\d+"), "a count should be a bucket, not a number");
        }
    }

    /** With the daily report switched off, the watch ticking changes nothing. */
    @Test
    void nothingGoesWhileTheDailyReportIsOff() {
        config.update(true, "https://beacon.test", false, false, false, false, "", "");
        assertFalse(metrics.sendIfDue("26.15.0"));
        verify(httpClient, never()).signedPost(anyString(), anyString(), any());
    }

    /** A send that goes through is written down, so the day is not reported twice. */
    @Test
    void aSuccessfulSendIsWrittenDown() {
        config.update(true, "https://beacon.test", false, false, true, false, "", "");
        when(httpClient.signedPost(anyString(), anyString(), any())).thenReturn(true);
        settings.set(BeaconMetricsScheduler.LAST_SENT_KEY, Instant.EPOCH.toString());
        var afterTheSlot = new BeaconMetricsScheduler(settings, new BeaconMetricsIdentity(settings))
                .slotOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC))
                .plusSeconds(60);

        assertTrue(metrics.sendIfDue("26.15.0", afterTheSlot));
        assertFalse(metrics.sendIfDue("26.15.0", afterTheSlot), "the same day should not go twice");
    }

    /**
     * A demo or development instance is very often a copy of a real one, carrying its identifiers.
     * Left to report, a staging box would file production's numbers under production's name.
     */
    @Test
    void aDemoInstanceStartsNothing() {
        var demo = mock(Demo.class);
        when(demo.enabled()).thenReturn(true);
        var suppressed = new BeaconMetricsService(
                config,
                demo,
                new BeaconMetricsSourceRepository(),
                new BeaconMetricsIdentity(settings),
                new BeaconMetricsScheduler(settings, new BeaconMetricsIdentity(settings)),
                httpClient);
        config.update(true, "https://beacon.test", false, false, true, false, "", "");

        suppressed.start("26.15.0");

        verify(httpClient, never()).signedPost(anyString(), anyString(), any());
    }

    /** The watch starts on an ordinary instance, and starting it sends nothing by itself. */
    @Test
    void theWatchStartsWithoutSendingAnything() {
        config.update(true, "https://beacon.test", false, false, true, false, "", "");
        metrics.start("26.15.0");
        verify(httpClient, never()).signedPost(anyString(), anyString(), any());
    }

    /**
     * A tick that throws is written down rather than allowed to escape. An exception leaving a
     * scheduled task stops it for good, and a beacon that quietly stopped a month ago is worse than
     * one that complains every ten minutes.
     */
    @Test
    void aTickThatThrowsDoesNotKillTheWatch() {
        config.update(true, "https://beacon.test", false, false, true, false, "", "");
        when(httpClient.signedPost(anyString(), anyString(), any())).thenThrow(new IllegalStateException("no"));
        settings.set(BeaconMetricsScheduler.LAST_SENT_KEY, Instant.EPOCH.toString());

        metrics.tick("26.15.0");
        metrics.tick("26.15.0");
    }

    /** A send that fails leaves no mark, so the next tick tries again rather than skipping the day. */
    @Test
    void aFailedSendIsNotWrittenDownAsDone() {
        config.update(true, "https://beacon.test", false, false, true, false, "", "");
        when(httpClient.signedPost(anyString(), anyString(), any())).thenReturn(false);
        settings.set(BeaconMetricsScheduler.LAST_SENT_KEY, Instant.EPOCH.toString());
        var afterTheSlot = new BeaconMetricsScheduler(settings, new BeaconMetricsIdentity(settings))
                .slotOn(java.time.LocalDate.now(java.time.ZoneOffset.UTC))
                .plusSeconds(60);

        metrics.sendIfDue("26.15.0", afterTheSlot);

        assertEquals(
                Instant.EPOCH.toString(),
                settings.get(BeaconMetricsScheduler.LAST_SENT_KEY).orElseThrow());
    }
}
