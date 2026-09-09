/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.entity;

import java.time.Instant;
import java.util.List;

/**
 * What travels between an instance and its beacon.
 *
 * <p>Every payload carries the protocol version, because a beacon and the instances reporting to it
 * run different releases by design and the older of the two has to be able to say so.
 *
 * <p>The signed payloads also carry when they were issued, a nonce, and the beacon they were meant
 * for. Signing the body alone would leave a captured report replayable, and replayable straight into
 * the one number this whole feature exists to show: how many installations have hit a fault.
 */
public final class BeaconPayloads {

    /** The contract these payloads are written against. */
    public static final int PROTOCOL_VERSION = 1;

    private BeaconPayloads() {}

    /**
     * What a signed delivery carries besides its content.
     *
     * @param issuedAt when the sender built it, checked against a drift window
     * @param nonce    remembered by the beacon, so the same delivery cannot arrive twice
     * @param audience the beacon this was meant for, so a report captured by one cannot be relayed
     *                 to another
     */
    public record Envelope(int protocolVersion, Instant issuedAt, String nonce, String audience) {}

    /**
     * A fault as its instance last knew it.
     *
     * <p>A snapshot of the group rather than one occurrence: the count is the sender's own running
     * total, so the same fault arriving twice corrects the row rather than doubling it.
     *
     * @param fingerprint the exception chain and frame names, without line numbers
     * @param occurrences what the sender counted, not an increment
     */
    public record ProblemPayload(
            Envelope envelope,
            String version,
            String contactName,
            String contactMail,
            String fingerprint,
            String level,
            String logger,
            String exceptionClass,
            String frames,
            int occurrences,
            Instant firstOccurrence,
            Instant lastOccurrence) {}

    /**
     * Somebody's own words about what went wrong, forwarded without their name.
     *
     * @param page the address they were on, with any query string already removed
     */
    public record ReportPayload(
            Envelope envelope,
            String version,
            String contactName,
            String contactMail,
            String message,
            String page,
            Instant reportedAt) {}

    /**
     * A day of bucketed counts for one subject.
     *
     * <p>Every count is a bucket such as {@code <10}, never a number: an exact trajectory can be
     * matched against publicly discoverable stations and stops being anonymous within weeks.
     */
    public record MetricsSubject(
            String metricsUid, String subject, String members, String accounts, String stations, String inventory) {}

    /**
     * One instance's whole day in one request: its own numbers and every station's.
     *
     * <p>Unsigned, and it carries no instance identity to sign with, which is the point. It is a
     * batch because forty stations should not be forty requests, and because one body gives the
     * whole day a single timestamp.
     */
    public record MetricsBatch(int protocolVersion, String version, String day, List<MetricsSubject> subjects) {}
}
