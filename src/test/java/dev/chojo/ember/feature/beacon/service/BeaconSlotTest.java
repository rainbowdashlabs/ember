/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When the daily report goes, which is the part that has to survive restarts and a fleet.
 */
class BeaconSlotTest extends RepositoryTestBase {

    private ApplicationSettingRepository settings;
    private BeaconMetricsIdentity identity;
    private BeaconMetricsScheduler scheduler;

    @BeforeEach
    void fresh() {
        settings = new ApplicationSettingRepository();
        identity = new BeaconMetricsIdentity(settings);
        scheduler = new BeaconMetricsScheduler(settings, identity);
    }

    /** The instance has a name in the numbers, and it does not change from one ask to the next. */
    @Test
    void theInstanceKeepsOneMetricsName() {
        String first = identity.instanceMetricsUid();
        assertNotNull(first);
        assertEquals(first, identity.instanceMetricsUid());
        assertEquals(identity.dailySlotMinute(), identity.dailySlotMinute());
    }

    /** Before the slot, nothing is due, whatever else has happened. */
    @Test
    void nothingIsDueBeforeTheSlot() {
        settings.set(BeaconMetricsScheduler.LAST_SENT_KEY, Instant.EPOCH.toString());
        var slot = scheduler.slotOn(LocalDate.now(ZoneOffset.UTC));
        assertFalse(scheduler.due(slot.minusSeconds(60)));
    }

    /** After the slot, with nothing sent since, the day's report is due. */
    @Test
    void afterTheSlotItIsDue() {
        settings.set(BeaconMetricsScheduler.LAST_SENT_KEY, Instant.EPOCH.toString());
        var slot = scheduler.slotOn(LocalDate.now(ZoneOffset.UTC));
        assertTrue(scheduler.due(slot.plusSeconds(60)));
    }

    /** Once it has gone, it does not go again that day however often the process restarts. */
    @Test
    void itDoesNotGoTwiceInADay() {
        var slot = scheduler.slotOn(LocalDate.now(ZoneOffset.UTC));
        scheduler.markSent(slot.plusSeconds(30));
        assertFalse(scheduler.due(slot.plusSeconds(120)));
    }

    /**
     * A clock that jumped backwards leaves a mark in the future, which would otherwise hold the
     * instance silent until real time caught up.
     */
    @Test
    void aMarkFromTheFutureIsNotBelieved() {
        settings.set(
                BeaconMetricsScheduler.LAST_SENT_KEY,
                Instant.now().plusSeconds(86400).toString());
        assertFalse(scheduler.lastSent().isAfter(Instant.now().plusSeconds(5)));
    }

    /**
     * An instance whose metrics name has somehow gone mints itself another rather than reporting
     * under nothing.
     */
    @Test
    void aMissingMetricsNameIsMinted() {
        settings.set(BeaconMetricsIdentity.SETTING_KEY, "");
        String minted = identity.instanceMetricsUid();
        assertNotNull(minted);
        assertFalse(minted.isBlank());
        assertEquals(minted, identity.instanceMetricsUid());
    }

    /** A mark nobody can read is treated as never sent rather than as a reason to stay quiet. */
    @Test
    void anUnreadableMarkMeansNeverSent() {
        settings.set(BeaconMetricsScheduler.LAST_SENT_KEY, "not-a-time");
        assertNull(scheduler.lastSent());
    }
}
