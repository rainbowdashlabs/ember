/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Deciding when the daily count goes out.
 *
 * <p>Every other scheduler in this project counts from the moment the process started, which is
 * right for a sweep nobody else can see and wrong for something a hundred instances do at a shared
 * destination. Two failures follow from counting from boot, and this class exists to avoid both.
 *
 * <p>An instance that reports on the way up reports every time it comes up, so one restarting every
 * half hour would send a day's worth before the day was out. So the last report is written down and
 * the day's slot has to have passed since, whatever the process has been doing.
 *
 * <p>A fleet updated together comes back up together, so an interval counted from boot would have
 * every instance arrive within seconds of the others. So each instance has its own minute of the
 * day, drawn from its secret metrics identifier, and a restart does not move it. Waiting for "the
 * interval to expire" after an outage would put the whole fleet back in step, which is exactly what
 * a slot avoids: an instance that was down across its slot waits for the next one.
 */
@Singleton
public class BeaconMetricsScheduler {

    /** When the last daily report actually went out. */
    public static final String LAST_SENT_KEY = "beacon_metrics_last_sent";

    private static final Logger log = LoggerFactory.getLogger(BeaconMetricsScheduler.class);
    private static final int JITTER_MINUTES = 5;

    private final ApplicationSettingRepository settings;
    private final BeaconMetricsIdentity identity;

    @Inject
    public BeaconMetricsScheduler(ApplicationSettingRepository settings, BeaconMetricsIdentity identity) {
        this.settings = settings;
        this.identity = identity;
    }

    /**
     * Whether the day's report is due.
     *
     * <p>Due means the slot has passed today and nothing has been sent since it. Never at boot, and
     * never twice in a day however often the process restarts.
     *
     * @param now the moment being judged, in UTC
     * @return whether to send
     */
    public boolean due(Instant now) {
        var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var slot = slotOn(today);
        if (now.isBefore(slot)) return false;
        var last = lastSent();
        return last == null || last.isBefore(slot);
    }

    /**
     * The moment this instance's slot falls on a given day, jitter included.
     *
     * <p>The jitter is drawn from the identifier and the day together, not from a random source.
     * This method is asked the same question many times a day, and a jitter redrawn per call would
     * move the slot between one check and the next: the instance would see itself as due, then not
     * due, then due again, and the mark it writes could never line up with the slot it was judged
     * against. Per day it varies; within a day it is fixed.
     *
     * @param day the UTC day
     * @return the moment the report is due that day
     */
    public Instant slotOn(LocalDate day) {
        int minute = identity.dailySlotMinute();
        int jitter = BeaconMetricsIdentity.slotFor(identity.instanceMetricsUid() + day) % (JITTER_MINUTES + 1);
        return day.atTime(LocalTime.MIDNIGHT)
                .plusMinutes((long) minute + jitter)
                .toInstant(ZoneOffset.UTC);
    }

    /**
     * When the last report went out, or null if none has.
     *
     * <p>A clock that jumped backwards leaves a mark in the future, which would hold the instance
     * silent until real time caught up. A mark later than now is not believed.
     */
    public Instant lastSent() {
        return settings.get(LAST_SENT_KEY)
                .map(value -> {
                    try {
                        var parsed = Instant.parse(value);
                        return parsed.isAfter(Instant.now()) ? Instant.now() : parsed;
                    } catch (Exception e) {
                        log.warn("Unreadable beacon report mark, treating it as never sent: {}", value);
                        return null;
                    }
                })
                .orElse(null);
    }

    /** Writes down that the day's report has gone. */
    public void markSent(Instant when) {
        settings.set(LAST_SENT_KEY, when.toString());
    }
}
