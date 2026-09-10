/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The name a fault keeps across the releases that report it.
 */
class BeaconFingerprintTest {

    private static String trace(int firstLine, int secondLine) {
        return """
                java.lang.IllegalStateException: nope
                \tat dev.chojo.ember.feature.events.service.EventService.load(EventService.java:%d)
                \tat dev.chojo.ember.feature.events.route.EventRoutes.list(EventRoutes.java:%d)
                """.formatted(firstLine, secondLine);
    }

    /**
     * The point of the whole class. The local error log groups on a frame that carries its line
     * number, so the same fault would arrive as a new fault after any release that moved the code,
     * and "which versions has this appeared in" could never be answered across the boundary where it
     * matters.
     */
    @Test
    void aMovedLineIsStillTheSameFault() {
        assertEquals(
                BeaconFingerprint.of("java.lang.IllegalStateException", trace(120, 44), "logger"),
                BeaconFingerprint.of("java.lang.IllegalStateException", trace(133, 47), "logger"));
    }

    /** Different code is a different fault, however similar the exception. */
    @Test
    void aDifferentPathIsADifferentFault() {
        String other = """
                java.lang.IllegalStateException: nope
                \tat dev.chojo.ember.feature.board.service.BoardService.load(BoardService.java:120)
                """;
        assertNotEquals(
                BeaconFingerprint.of("java.lang.IllegalStateException", trace(120, 44), "logger"),
                BeaconFingerprint.of("java.lang.IllegalStateException", other, "logger"));
    }

    /**
     * Lambda and synthetic frames carry a counter the compiler hands out, so two builds of the same
     * source can disagree about them. They are left out rather than trusted.
     */
    @Test
    void framesTheCompilerInventedAreLeftOut() {
        String withLambda = """
                java.lang.IllegalStateException: nope
                \tat dev.chojo.ember.feature.events.service.EventService.lambda$load$3(EventService.java:120)
                \tat dev.chojo.ember.feature.events.service.EventService.load(EventService.java:118)
                """;
        var frames = BeaconFingerprint.frameNames(withLambda);
        assertEquals(1, frames.size());
        assertEquals("dev.chojo.ember.feature.events.service.EventService.load", frames.getFirst());
    }

    /** A fault with no stacktrace still has a name, so it can still be grouped. */
    @Test
    void aFaultWithoutAStacktraceStillHasAName() {
        assertTrue(
                BeaconFingerprint.of(null, null, "dev.chojo.ember.Something").startsWith("dev.chojo.ember.Something"));
    }
}
