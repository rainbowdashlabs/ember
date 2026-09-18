/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Lets go of the answers behind refusals that can no longer be taken back.
 *
 * <p>Saying no to an appointment used to throw the answers away on the spot, which is right until the
 * no can be undone. Taking a withdrawal back has to give back the registration somebody had, questions
 * and all, so the answers now outlive the refusal by exactly as long as it can be undone.
 *
 * <p>This is what ends that. It runs often, because the window is five minutes and answers to an
 * appointment's questions are the member's own words: keeping them for an hour after the last chance
 * to want them back would be keeping them for no reason anybody could name.
 */
@Singleton
public class SettledRefusalSweeper {
    private static final Logger log = LoggerFactory.getLogger(SettledRefusalSweeper.class);
    private static final int SCAN_INTERVAL_MINUTES = 5;

    private final EventRegistrationService registrationService;

    @Inject
    public SettledRefusalSweeper(EventRegistrationService registrationService) {
        this.registrationService = registrationService;
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var thread = new Thread(r, "settled-refusal-sweeper");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::sweep, 1, SCAN_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    /** Body of the sweep, reachable by tests so they need not wait for the cadence. */
    void sweep() {
        try {
            registrationService.sweepAnswersOfSettledRefusals();
        } catch (Exception e) {
            log.warn("Could not clear the answers of settled refusals", e);
        }
    }
}
