/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.beacon.entity.BeaconPayloads;
import dev.chojo.ember.feature.beacon.repository.BeaconIntakeRepository;
import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * Taking in what another instance reports.
 *
 * <p>Three rules decide whether anything is written at all, and each answers a way the first draft of
 * this feature could have been abused.
 *
 * <p><b>Identity is computed, never claimed.</b> An instance identifier is
 * {@code sha256(publicKey)[0..16]}, so the key that signed a report already says who sent it. Reading
 * an identifier out of the body and trusting the first key to claim it would let anybody squat a
 * victim's identifier before that victim ever reported, and with keys that are never rotated the
 * victim would have no way back.
 *
 * <p><b>A delivery arrives once.</b> The signature covers an issue time, a nonce and the beacon it
 * was meant for. Without that a captured report can be replayed to inflate the count of how many
 * installations hit a fault, which is the one number the whole feature exists to show, and a report
 * captured by one beacon can be handed to another.
 *
 * <p><b>Nothing here is a permission check.</b> Anybody may report; that is the point of a beacon.
 * The key makes a report attributable, not permitted, and abuse is held off by the limits the route
 * applies before any of this runs.
 */
@Singleton
public class BeaconIntakeService {

    private static final Logger log = LoggerFactory.getLogger(BeaconIntakeService.class);

    /** How far a sender's clock may be out before its report is refused. */
    public static final Duration DRIFT = Duration.ofMinutes(10);

    private static final int MAX_MESSAGE = 4000;
    private static final int MAX_FIELD = 500;
    private static final int MAX_SUBJECTS = 500;
    private static final Pattern MAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern VERSION = Pattern.compile("^[0-9A-Za-z.\\-+ @:]{1,60}$");

    private final BeaconIntakeRepository repository;

    @Inject
    public BeaconIntakeService(BeaconIntakeRepository repository) {
        this.repository = repository;
    }

    /**
     * Establishes who sent a signed delivery and that it has not been sent before.
     *
     * @param publicKey the raw Ed25519 key the signature was verified against
     * @param envelope  what the delivery says about itself
     * @return the identifier computed from the key
     */
    public String accept(byte[] publicKey, BeaconPayloads.Envelope envelope, String ownUrl) {
        if (envelope == null || envelope.issuedAt() == null || envelope.nonce() == null) {
            throw new BadRequestResponse("A report has to say when it was issued and carry a nonce");
        }
        if (envelope.protocolVersion() > BeaconPayloads.PROTOCOL_VERSION) {
            throw new BadRequestResponse("This beacon does not speak that protocol version yet");
        }
        var now = Instant.now();
        if (Duration.between(envelope.issuedAt(), now).abs().compareTo(DRIFT) > 0) {
            throw new ForbiddenResponse("The report was issued too far from now");
        }
        if (envelope.audience() == null || !sameHost(envelope.audience(), ownUrl)) {
            throw new ForbiddenResponse("The report was addressed to another beacon");
        }
        String instanceId = DiscoveryKeyService.computeInstanceId(publicKey);
        if (!repository.recordNonce(instanceId, envelope.nonce(), envelope.issuedAt())) {
            throw new ForbiddenResponse("That report has already been delivered");
        }
        repository.pruneNonces(now.minus(DRIFT).minus(DRIFT));
        return instanceId;
    }

    /**
     * A payload addressed to this beacon rather than to another. Compared by host, so a trailing
     * slash or a spelled-out port does not turn an honest report away.
     */
    private static boolean sameHost(String audience, String ownUrl) {
        try {
            String theirs = URI.create(audience).getHost();
            String ours = URI.create(ownUrl).getHost();
            return theirs != null && ours != null && theirs.equalsIgnoreCase(ours);
        } catch (Exception e) {
            return false;
        }
    }

    /** Files a fault and what this instance knows about it. */
    public void storeProblem(String instanceId, String publicKey, BeaconPayloads.ProblemPayload payload) {
        if (payload.fingerprint() == null || payload.fingerprint().isBlank()) {
            throw new BadRequestResponse("A fault needs a fingerprint");
        }
        String version = validVersion(payload.version());
        repository.touchInstance(
                instanceId,
                publicKey,
                clamp(payload.contactName(), MAX_FIELD),
                validMail(payload.contactMail()),
                version);
        int problemId = repository.upsertProblem(payload);
        repository.upsertProblemInstance(problemId, instanceId, version, payload);
    }

    /** Stores somebody's own words. */
    public void storeReport(String instanceId, String publicKey, BeaconPayloads.ReportPayload payload) {
        if (payload.message() == null || payload.message().isBlank()) {
            throw new BadRequestResponse("A report needs a message");
        }
        repository.touchInstance(
                instanceId,
                publicKey,
                clamp(payload.contactName(), MAX_FIELD),
                validMail(payload.contactMail()),
                validVersion(payload.version()));
        repository.insertReport(instanceId, payload);
    }

    /**
     * Stores a day of numbers.
     *
     * <p>Unsigned and unattributed on purpose, so this writes what it is given. Last write for a day
     * wins, which is the only correction available when there is no identity behind the rows.
     */
    public int storeMetrics(BeaconPayloads.MetricsBatch batch) {
        if (batch.subjects() == null || batch.subjects().isEmpty()) {
            throw new BadRequestResponse("A metrics batch needs subjects");
        }
        if (batch.subjects().size() > MAX_SUBJECTS) {
            throw new BadRequestResponse("That batch carries more subjects than a beacon accepts");
        }
        LocalDate day;
        try {
            day = LocalDate.parse(batch.day());
        } catch (Exception e) {
            throw new BadRequestResponse("A metrics batch needs a day");
        }
        if (day.isAfter(LocalDate.now().plusDays(1))) {
            throw new BadRequestResponse("A metrics batch cannot be for a day that has not happened");
        }
        String version = validVersion(batch.version());
        int stored = 0;
        for (var subject : batch.subjects()) {
            if (subject.metricsUid() == null) continue;
            try {
                repository.upsertMetrics(subject, day, version);
                stored++;
            } catch (Exception e) {
                log.debug("A metrics subject was refused", e);
            }
        }
        return stored;
    }

    private static String clamp(String value, int max) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** A contact address that is not one is dropped rather than stored and later rendered. */
    private static String validMail(String value) {
        String clamped = clamp(value, MAX_FIELD);
        return clamped != null && MAIL.matcher(clamped).matches() ? clamped : null;
    }

    /** A version that is not one is dropped, so nothing arbitrary reaches a screen as a version. */
    private static String validVersion(String value) {
        String clamped = clamp(value, 60);
        return clamped != null && VERSION.matcher(clamped).matches() ? clamped : null;
    }

    /** The longest message a report may carry, so one sender cannot fill the disk with prose. */
    public static String clampMessage(String message) {
        return clamp(message, MAX_MESSAGE);
    }
}
