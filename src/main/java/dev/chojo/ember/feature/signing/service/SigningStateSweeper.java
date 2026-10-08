/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Seals the states no sealed version shows yet: signing acts whose own sealing failed after they were
 * recorded, such as when the store or the station's key could not be reached, and fields a manager confirmed
 * on paper, waived or withdrawn on a request somebody signed electronically.
 *
 * <p>An act is sealed right after it is recorded ({@link SigningStateSealer}); a field a manager settles is
 * left to this sweep, so a manager working through a list does not wait for the timestamp services at every
 * field. The sweep picks up the requests with such an act or field from more than {@link #GRACE} ago, so an
 * act whose own sealing is still running is left to it, and seals the state each of them stands at now. A
 * request that fails again, such as one whose station key does not open, is logged and noted as failed
 * ({@link SigningEvidenceRepository#markSealFailed}); later runs take it only after every request that never
 * failed, so requests that keep failing hold back no other. A request whose document is gone has nothing to
 * be sealed into and is left out.
 */
@Singleton
public class SigningStateSweeper implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(SigningStateSweeper.class);
    private static final Duration START_DELAY = Duration.ofMinutes(5);
    private static final Duration INTERVAL = Duration.ofMinutes(15);

    /** How long an act is left to its own sealing before the sweep takes it. */
    static final Duration GRACE = Duration.ofMinutes(2);

    /** How many requests one run seals at most, since each may wait for the timestamp services. */
    static final int MAX_PER_RUN = 20;

    private final SigningEvidenceRepository evidence;
    private final SigningStateSealer sealer;
    private final Clock clock;

    @Inject
    public SigningStateSweeper(SigningEvidenceRepository evidence, SigningStateSealer sealer) {
        this(evidence, sealer, Clock.systemUTC());
    }

    SigningStateSweeper(SigningEvidenceRepository evidence, SigningStateSealer sealer, Clock clock) {
        this.evidence = evidence;
        this.sealer = sealer;
        this.clock = clock;
    }

    /**
     * Body of the run, reachable by tests so they need not wait. A failure is logged and swallowed: what
     * was not sealed stays due and is tried on the next run.
     */
    void sweep() {
        try {
            int sealed = sweep(clock.instant());
            if (sealed > 0) log.info("Sealed the newest state of {} signing requests", sealed);
        } catch (Exception e) {
            log.warn("Sealing the newest state of signing requests failed", e);
        }
    }

    /**
     * Seals the requests with acts or settled fields no sealed version shows yet.
     *
     * @param now the time the grace of a fresh change is measured against
     * @return how many requests got a sealed version
     */
    int sweep(Instant now) {
        int sealed = 0;
        for (int requestId : evidence.requestsToSeal(now.minus(GRACE), MAX_PER_RUN)) {
            try {
                if (sealer.sealLatest(requestId)) sealed++;
            } catch (RuntimeException e) {
                log.warn("Signing request {} could not be sealed; a later run tries again", requestId, e);
                evidence.markSealFailed(requestId, now);
            }
        }
        return sealed;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(
                new ScheduledTask("signing-state-sweep", Schedule.fixedDelay(START_DELAY, INTERVAL), this::sweep));
    }
}
