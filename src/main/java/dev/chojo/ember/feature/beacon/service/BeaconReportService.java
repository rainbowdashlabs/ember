/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Sending this instance's problems to its beacon.
 *
 * <p>Nothing here is called from the logging path. The error log captures every WARN and ERROR,
 * including the ones this class writes when a send fails, so forwarding straight from the appender
 * would let a failing beacon feed itself: the send fails, the failure is logged, the log becomes a
 * problem, the problem is forwarded, the send fails. Work goes on a bounded queue instead, and this
 * class's own logger is the one the forwarder never forwards.
 */
@Singleton
public class BeaconReportService {

    /** The logger whose own output is never forwarded, so a failing beacon cannot feed itself. */
    public static final String OWN_LOGGER = BeaconReportService.class.getName();

    private static final Logger log = LoggerFactory.getLogger(BeaconReportService.class);
    private static final int QUEUE_CAPACITY = 200;
    private static final long BACKOFF_SECONDS = 30;

    private final BeaconSettings config;
    private final DiscoveryHttpClient httpClient;
    private final BlockingQueue<Runnable> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    @Inject
    public BeaconReportService(BeaconSettings config, DiscoveryHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
        startWorker();
    }

    private void startWorker() {
        var worker = Executors.newSingleThreadExecutor(runnable -> {
            var thread = new Thread(runnable, "beacon-sender");
            thread.setDaemon(true);
            return thread;
        });
        worker.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    queue.take().run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.warn("A beacon send failed", e);
                    sleepBackoff();
                }
            }
        });
    }

    private void sleepBackoff() {
        try {
            TimeUnit.SECONDS.sleep(BACKOFF_SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Starts forwarding every new problem as it appears, if the operator asked for that.
     *
     * <p>The appender hands entries over on the thread that logged them, so nothing here does more
     * than put one on the queue. Entries the beacon itself logged never arrive: the appender leaves
     * them out, which is what stops a failing beacon from feeding itself.
     *
     * <p>The listener is always registered and asks the setting each time. Deciding once at boot
     * would mean an operator switching forwarding on had to restart before anything went, which is
     * the whole reason these settings are stored rather than configured.
     *
     * @param version this instance's version
     */
    public void startForwarding(String version) {
        var appender = ProblemLogAppender.instance();
        if (appender == null) return;
        appender.onNewProblem(entry -> {
            if (config.forwardProblems()) send(entry.snapshot(), version);
        });
    }

    /**
     * The payload one problem would be sent as, without sending it.
     *
     * <p>What the operator is shown before the button does anything. An exception message quotes
     * what failed, and what failed is sometimes somebody's address, so the only honest way to ask
     * for consent is to show the bytes.
     *
     * @param entry   the problem as the local log holds it
     * @param version this instance's version
     * @return the payload, ready to be shown or sent
     */
    public BeaconPayloads.ProblemPayload payloadFor(ProblemLogAppender.Snapshot entry, String version) {
        return new BeaconPayloads.ProblemPayload(
                envelope(),
                version,
                blankToNull(config.contactName()),
                blankToNull(config.contactMail()),
                BeaconFingerprint.of(entry.exceptionClass(), entry.stacktrace(), entry.logger()),
                entry.level(),
                entry.logger(),
                entry.exceptionClass(),
                String.join("\n", BeaconFingerprint.frameNames(entry.stacktrace())),
                entry.count(),
                entry.firstOccurrence(),
                entry.lastOccurrence());
    }

    /**
     * Queues one problem for its beacon. Returns at once; the sending happens on the worker.
     *
     * @param entry   the problem being forwarded
     * @param version this instance's version
     * @return whether it was queued, false when the queue is full or reporting is off
     */
    public boolean send(ProblemLogAppender.Snapshot entry, String version) {
        if (!config.enabled()) return false;
        return queue.offer(
                () -> httpClient.signedPost(config.url(), "/api/v1/beacon/problems", payloadFor(entry, version)));
    }

    /**
     * Queues a set of problems, which is what the list's own action sends.
     *
     * @param entries the problems being forwarded
     * @param version this instance's version
     * @return how many were queued
     */
    public int sendAll(List<ProblemLogAppender.Snapshot> entries, String version) {
        int queued = 0;
        for (var entry : entries) {
            if (send(entry, version)) queued++;
        }
        return queued;
    }

    /**
     * Queues one problem report, stripped of everything that names the person who wrote it.
     *
     * @param message   what they wrote
     * @param page      the address they were on, query string already removed
     * @param reportedAt when they wrote it
     * @param version   this instance's version
     * @return whether it was queued
     */
    public boolean sendReport(String message, String page, Instant reportedAt, String version) {
        if (!config.enabled()) return false;
        var payload = new BeaconPayloads.ReportPayload(
                envelope(),
                version,
                blankToNull(config.contactName()),
                blankToNull(config.contactMail()),
                message,
                page,
                reportedAt);
        return queue.offer(() -> httpClient.signedPost(config.url(), "/api/v1/beacon/reports", payload));
    }

    /**
     * A fresh envelope. The nonce and the issue time are what stop a captured delivery being replayed,
     * and the audience is what stops one captured by one beacon being handed to another.
     */
    private BeaconPayloads.Envelope envelope() {
        return new BeaconPayloads.Envelope(
                BeaconPayloads.PROTOCOL_VERSION,
                Instant.now(),
                UUID.randomUUID().toString(),
                config.url());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
