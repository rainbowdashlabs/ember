/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.service.FederationFanout.PartnerOutcome;
import dev.chojo.ember.lifecycle.TaskScheduler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class FederationFanoutTest {

    private static FederationPartner partner(int id, String remoteHost) {
        return new FederationPartner(
                id,
                1,
                UUID.randomUUID(),
                null,
                null,
                null,
                FederationStatus.ACTIVE,
                null,
                Instant.now(),
                Instant.now(),
                remoteHost,
                "Partner " + id);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * A partner that accepts the call and never answers costs the fan-out its time limit, not the
     * whole request: every other partner still contributes.
     */
    @Test
    void aStalledPartnerDoesNotHoldBackTheOthers() {
        var never = new CountDownLatch(1);
        var fanout = new FederationFanout(Executors.newVirtualThreadPerTaskExecutor(), Duration.ofMillis(200));
        var first = partner(1, null);
        var stalled = partner(2, "https://stalled.example");
        var last = partner(3, "https://remote.example");

        var result = assertTimeoutPreemptively(
                Duration.ofSeconds(5),
                () -> fanout.fanOut(List.of(first, stalled, last), p -> {
                    if (p == stalled) {
                        awaitQuietly(never);
                    }
                    return List.of("answer-" + p.id());
                }));

        assertEquals(List.of("answer-1", "answer-3"), result.items());
        assertEquals(
                List.of(PartnerOutcome.ANSWERED, PartnerOutcome.TIMED_OUT, PartnerOutcome.ANSWERED),
                result.answers().stream()
                        .map(FederationFanout.PartnerAnswer::outcome)
                        .toList());
        assertEquals(List.of(stalled), result.unanswered());
        never.countDown();
    }

    @Test
    void aFailingPartnerIsToldApartFromAnEmptyOne() {
        var fanout = new FederationFanout(new TaskScheduler());
        var failing = partner(1, "https://failing.example");
        var empty = partner(2, "https://empty.example");

        var result = fanout.fanOut(List.of(failing, empty), p -> {
            if (p == failing) throw new IllegalStateException("down");
            return List.<String>of();
        });

        assertEquals(List.of(), result.items());
        assertEquals(
                List.of(PartnerOutcome.FAILED, PartnerOutcome.ANSWERED),
                result.answers().stream()
                        .map(FederationFanout.PartnerAnswer::outcome)
                        .toList());
        assertEquals(List.of(failing), result.unanswered());
    }
}
