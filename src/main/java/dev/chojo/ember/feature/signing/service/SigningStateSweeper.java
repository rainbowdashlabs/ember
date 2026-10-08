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
 * Seals the signing acts whose own sealing failed after they were recorded, such as when the store or the
 * station's key could not be reached.
 *
 * <p>An act is sealed right after it is recorded ({@link SigningStateSealer}); this sweep only picks up the
 * requests with an act no sealed version carries yet, recorded more than {@link #GRACE} ago so an act whose
 * own sealing is still running is left to it, and seals the state each of them stands at now. A request that
 * fails again is logged and tried on the next run. A request whose document is gone has nothing to be sealed
 * into and is left out.
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
            if (sealed > 0) log.info("Sealed {} signing requests whose acts were not sealed yet", sealed);
        } catch (Exception e) {
            log.warn("Sealing signing acts that were not sealed yet failed", e);
        }
    }

    /**
     * Seals the requests with acts no sealed version carries yet.
     *
     * @param now the time the grace of a fresh act is measured against
     * @return how many requests got a sealed version
     */
    int sweep(Instant now) {
        int sealed = 0;
        for (int requestId : evidence.requestsWithUnsealedActs(now.minus(GRACE), MAX_PER_RUN)) {
            try {
                if (sealer.sealLatest(requestId)) sealed++;
            } catch (RuntimeException e) {
                log.warn("Signing request {} could not be sealed; the next run tries again", requestId, e);
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
