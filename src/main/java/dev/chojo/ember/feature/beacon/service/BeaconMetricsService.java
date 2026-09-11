/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.beacon.repository.BeaconMetricsSourceRepository;
import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The daily account of how much this instance holds.
 *
 * <p>One batch for the whole instance: its own numbers and every station's, in one body. Forty
 * stations are not forty requests, and one body gives the whole day a single timestamp.
 *
 * <p>Unsigned, because there is no identity to sign it with. That is the point rather than an
 * oversight: a signature would put the sender's name beside numbers whose only promise is that they
 * do not carry it.
 */
@Singleton
public class BeaconMetricsService {

    private static final Logger log = LoggerFactory.getLogger(BeaconMetricsService.class);
    private static final long CHECK_INTERVAL_MINUTES = 10;

    private final BeaconSettings config;
    private final Demo demo;
    private final BeaconMetricsSourceRepository source;
    private final BeaconMetricsIdentity identity;
    private final BeaconMetricsScheduler scheduler;
    private final DiscoveryHttpClient httpClient;

    @Inject
    public BeaconMetricsService(
            BeaconSettings config,
            Demo demo,
            BeaconMetricsSourceRepository source,
            BeaconMetricsIdentity identity,
            BeaconMetricsScheduler scheduler,
            DiscoveryHttpClient httpClient) {
        this.config = config;
        this.demo = demo;
        this.source = source;
        this.identity = identity;
        this.scheduler = scheduler;
        this.httpClient = httpClient;
    }

    /**
     * Starts the watch that sends the day's numbers when the instance's own slot has passed.
     *
     * <p>The watch ticks often and sends rarely. Ticking is what lets an instance that was down over
     * its slot notice as soon as it is back, without the tick itself deciding anything: the slot and
     * the written mark decide, so a restart is never a reason to report.
     *
     * <p>The watch runs whether or not reporting is switched on, and each tick asks. An operator who
     * switches it on should not have to restart to be heard.
     *
     * @param version this instance's version
     */
    public void start(String version) {
        if (suppressed()) return;
        var executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "beacon-metrics");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(
                () -> tick(version), CHECK_INTERVAL_MINUTES, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
        log.info("Beacon metrics, when switched on, go at minute {} of the UTC day", identity.dailySlotMinute());
    }

    /**
     * One turn of the watch.
     *
     * <p>Whatever goes wrong here goes wrong on a daemon thread that nobody is watching, so it is
     * written down rather than thrown: an exception escaping a scheduled task stops the task for
     * good, and a beacon that quietly stopped reporting a month ago is worse than one that logged a
     * failure every ten minutes.
     *
     * @param version this instance's version
     */
    void tick(String version) {
        try {
            sendIfDue(version);
        } catch (Exception e) {
            log.warn("The daily beacon report could not be sent", e);
        }
    }

    /**
     * Whether this instance must not report at all.
     *
     * <p>A demo or development instance is very often a copy of a real one, restored from its dump
     * and therefore carrying its metrics identifiers. Left to report, a staging box would file
     * production's numbers under production's name.
     */
    private boolean suppressed() {
        return demo.enabled() || demo.dev();
    }

    /**
     * Sends the day's numbers if the slot has passed and nothing has gone since.
     *
     * @param version this instance's version
     * @return whether anything was sent
     */
    public boolean sendIfDue(String version) {
        return sendIfDue(version, Instant.now());
    }

    /**
     * The same, judged against a given moment.
     *
     * <p>The moment is a parameter because whether a report is due depends on the time of day, and a
     * test that cannot say what time it is can only assert the case the clock happens to be in.
     *
     * @param version this instance's version
     * @param now     the moment to judge against
     * @return whether anything was sent
     */
    public boolean sendIfDue(String version, Instant now) {
        if (!config.metricsEnabled()) return false;
        if (!scheduler.due(now)) return false;
        if (httpClient.signedPost(config.url(), "/api/v1/beacon/figures", batch(version, now))) {
            scheduler.markSent(now);
            return true;
        }
        return false;
    }

    /**
     * The batch as it would be sent, which is also what an operator is shown.
     *
     * @param version this instance's version
     * @param now     the moment the day is taken from
     * @return the whole instance's day
     */
    public BeaconPayloads.MetricsBatch batch(String version, Instant now) {
        var subjects = new ArrayList<BeaconPayloads.MetricsSubject>();
        subjects.add(new BeaconPayloads.MetricsSubject(
                identity.instanceMetricsUid(),
                "INSTANCE",
                BeaconBuckets.members(source.memberCount()),
                BeaconBuckets.size(source.accountCount()),
                BeaconBuckets.size(source.stationCount()),
                BeaconBuckets.size(source.inventoryCount())));
        for (var station : source.stationCounts()) {
            subjects.add(new BeaconPayloads.MetricsSubject(
                    station.metricsUid().toString(),
                    "STATION",
                    BeaconBuckets.members(station.members()),
                    null,
                    null,
                    BeaconBuckets.size(station.inventory())));
        }
        return new BeaconPayloads.MetricsBatch(
                BeaconPayloads.PROTOCOL_VERSION,
                version,
                LocalDate.ofInstant(now, ZoneOffset.UTC).toString(),
                List.copyOf(subjects));
    }
}
