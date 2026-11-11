/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemRecord;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.repository.ProblemLogRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the problem log gathers reaches the database every few seconds; an outage only delays it, and
 * nothing logged while writing is gathered again.
 */
class ProblemLogFlusherTest {
    private static final LoggerContext CONTEXT = new LoggerContext();

    private static LoggingEvent event(String logger, Level level, String message, Throwable thrown, String thread) {
        var event = new LoggingEvent(
                ProblemLogFlusherTest.class.getName(), CONTEXT.getLogger(logger), level, message, thrown, null);
        event.setThreadName(thread);
        return event;
    }

    private static LoggingEvent warning(String message) {
        return event("dev.chojo.ember.Some", Level.WARN, message, null, "worker-1");
    }

    /** A repository that keeps what it is given, refuses everything once told to, or refuses one message. */
    private static final class Recording extends ProblemLogRepository {
        final List<ProblemRecord> written = new ArrayList<>();
        boolean refusing;
        String refusedMessage;
        int acceptBeforeRefusing = -1;
        boolean answers = true;

        @Override
        public boolean reachable() {
            return answers && !refusing;
        }

        @Override
        public Recorded record(ProblemRecord record) {
            if (acceptBeforeRefusing == 0) refusing = true;
            if (acceptBeforeRefusing > 0) acceptBeforeRefusing--;
            if (refusing) throw new IllegalStateException("database away");
            if (record.message().equals(refusedMessage)) throw new IllegalArgumentException("refused");
            written.add(record);
            return new Recorded(written.size(), ProblemState.OPEN, null);
        }
    }

    @Test
    void theSameProblemIsOneRecordThatCountsEveryOccurrence() {
        var appender = new ProblemLogAppender(false);
        var repository = new Recording();
        var flusher = new ProblemLogFlusher(repository, () -> appender);

        appender.append(warning("disk almost full"));
        appender.append(warning("disk almost full"));
        appender.append(
                event("dev.chojo.ember.Other", Level.ERROR, "broken", new IllegalStateException("x"), "worker-2"));

        assertEquals(2, flusher.flush());
        var warned = repository.written.stream()
                .filter(r -> r.level() == ProblemLevel.WARN)
                .findFirst()
                .orElseThrow();
        assertEquals(2, warned.count());
        assertEquals(List.of("disk almost full"), warned.messages());
        assertEquals(2, warned.occurrences().size());
        assertEquals(64, warned.fingerprint().length(), "the key is stored as its hash");
        var failed = repository.written.stream()
                .filter(r -> r.level() == ProblemLevel.ERROR)
                .findFirst()
                .orElseThrow();
        assertEquals("java.lang.IllegalStateException", failed.exceptionClass());
        assertTrue(failed.stacktrace().startsWith("java.lang.IllegalStateException: x"));
        assertEquals("worker-2", failed.occurrences().getFirst().thread());

        assertEquals(0, flusher.flush(), "nothing is written twice");
    }

    @Test
    void anOutageKeepsEverythingForTheNextRun() {
        var appender = new ProblemLogAppender(false);
        var repository = new Recording();
        var flusher = new ProblemLogFlusher(repository, () -> appender);
        appender.append(warning("first"));
        repository.refusing = true;

        assertEquals(0, flusher.flush());
        appender.append(warning("first"));
        repository.refusing = false;

        assertEquals(1, flusher.flush());
        assertEquals(2, repository.written.getFirst().count(), "what came meanwhile joins what was kept");
    }

    @Test
    void whatIsLoggedWhileWritingIsNotGatheredAgain() {
        var appender = new ProblemLogAppender(false);
        var repository = mock(ProblemLogRepository.class);
        when(repository.record(any())).thenAnswer(call -> {
            appender.append(warning("the database complained"));
            return new ProblemLogRepository.Recorded(1, ProblemState.OPEN, null);
        });
        var flusher = new ProblemLogFlusher(repository, () -> appender);
        appender.append(warning("something"));

        assertEquals(1, flusher.flush());
        assertTrue(appender.drainPending().isEmpty(), "the write's own warning was left out");
    }

    @Test
    void tooManyDifferentProblemsAreCountedOnOneEntry() {
        var appender = new ProblemLogAppender(false);
        for (int i = 0; i < ProblemLogAppender.PENDING_LIMIT + 3; i++) {
            appender.append(warning("problem " + i));
        }

        var drained = appender.drainPending();

        assertEquals(ProblemLogAppender.PENDING_LIMIT + 1, drained.size());
        var overflow = drained.stream()
                .filter(p -> p.fingerprint().equals(ProblemLogAppender.OVERFLOW_FINGERPRINT))
                .findFirst()
                .orElseThrow()
                .record();
        assertEquals(3, overflow.count());
        assertNull(overflow.exceptionClass());
    }

    @Test
    void aProblemPutBackTakesInWhatWasGatheredMeanwhile() {
        var earlier = new PendingProblem(
                "fp",
                ProblemLevel.ERROR,
                "dev.chojo.ember.Some",
                "one",
                "java.lang.IllegalStateException",
                Instant.parse("2026-11-11T10:00:00Z"));
        earlier.add("one", "early", () -> "trace early", Instant.parse("2026-11-11T10:00:00Z"), "a");
        var current = new PendingProblem(
                "fp",
                ProblemLevel.ERROR,
                "dev.chojo.ember.Some",
                "two",
                "java.lang.IllegalStateException",
                Instant.parse("2026-11-11T10:05:00Z"));
        current.add("two", "late", () -> "trace late", Instant.parse("2026-11-11T10:05:00Z"), "b");

        current.absorb(earlier);

        var record = current.record();
        assertEquals(2, record.count());
        assertEquals(List.of("one", "two"), record.messages(), "the earlier messages first");
        assertEquals("trace late", record.stacktrace());
        assertEquals("late", record.exceptionMessage());
        assertEquals(Instant.parse("2026-11-11T10:00:00Z"), record.firstSeen());
        assertEquals("b", record.occurrences().getFirst().thread(), "the newest occurrence first");
        assertNotEquals(record.occurrences().getFirst(), record.occurrences().getLast());
    }

    @Test
    void anOutageNeverDropsAnything() {
        var appender = new ProblemLogAppender(false);
        var repository = new Recording();
        var flusher = new ProblemLogFlusher(repository, () -> appender);
        appender.append(warning("first"));
        appender.append(warning("second"));
        repository.refusing = true;

        for (int run = 0; run < ProblemLogFlusher.MAX_FAILED_WRITES + 2; run++) {
            assertEquals(0, flusher.flush());
        }
        repository.refusing = false;

        assertEquals(2, flusher.flush(), "both waited out the outage");
    }

    @Test
    void aProblemTheDatabaseRefusesDoesNotHoldUpTheOthersAndIsDroppedInTheEnd() {
        var appender = new ProblemLogAppender(false);
        var repository = new Recording();
        var flusher = new ProblemLogFlusher(repository, () -> appender);
        repository.refusedMessage = "poison";
        appender.append(warning("poison"));
        appender.append(warning("fine one"));
        appender.append(warning("fine two"));

        assertEquals(2, flusher.flush(), "the others are written in the same run");
        for (int run = 1; run < ProblemLogFlusher.MAX_FAILED_WRITES; run++) {
            appender.append(warning("keeps the database busy " + run));
            flusher.flush();
        }

        assertTrue(appender.drainPending().isEmpty(), "the refused problem was dropped");
        assertTrue(repository.written.stream().noneMatch(r -> r.message().equals("poison")));
    }

    @Test
    void putBackProblemsStayWithinTheLimit() {
        var appender = new ProblemLogAppender(false);
        var repository = new Recording();
        var flusher = new ProblemLogFlusher(repository, () -> appender);
        appender.append(warning("old one"));
        appender.append(warning("old two"));
        repository.refusing = true;
        var drained = appender.drainPending();
        for (int i = 0; i < ProblemLogAppender.PENDING_LIMIT; i++) {
            appender.append(warning("new " + i));
        }
        drained.forEach(appender::restore);

        var after = appender.drainPending();

        assertEquals(
                ProblemLogAppender.PENDING_LIMIT + 1, after.size(), "the put back ones went to the overflow entry");
        var overflow = after.stream()
                .filter(p -> p.fingerprint().equals(ProblemLogAppender.OVERFLOW_FINGERPRINT))
                .findFirst()
                .orElseThrow()
                .record();
        assertEquals(2, overflow.count());
        assertEquals(0, flusher.flush());
    }

    @Test
    void aStacktraceIsRenderedOnlyForTheLatestOccurrenceAndCut() {
        var problem = new PendingProblem(
                "fp",
                ProblemLevel.ERROR,
                "dev.chojo.ember.Some",
                "x",
                "java.lang.IllegalStateException",
                Instant.parse("2026-11-11T10:00:00Z"));
        int[] rendered = {0};
        problem.add(
                "x",
                "m",
                () -> {
                    rendered[0]++;
                    return "a\u0000" + "b".repeat(PendingProblem.STACKTRACE_LIMIT + 10);
                },
                Instant.parse("2026-11-11T10:00:00Z"),
                "t");
        problem.add(
                "x",
                "m",
                () -> {
                    rendered[0]++;
                    return "older";
                },
                Instant.parse("2026-11-11T09:00:00Z"),
                "t");

        var record = problem.record();
        assertEquals(1, rendered[0], "an older occurrence renders nothing");
        assertEquals(PendingProblem.STACKTRACE_LIMIT, record.stacktrace().length());
        assertTrue(record.stacktrace().startsWith("ab"), "characters the database refuses are taken out");
    }

    @Test
    void anOutageThatStartsDuringARunPutsTheRestBackUncounted() {
        var appender = new ProblemLogAppender(false);
        var repository = new Recording();
        var flusher = new ProblemLogFlusher(repository, () -> appender);
        for (int i = 0; i < 10; i++) appender.append(warning("problem " + i));
        repository.acceptBeforeRefusing = 1;

        assertEquals(1, flusher.flush());

        var left = appender.drainPending();
        assertEquals(9, left.size(), "two refusals in a row stop the run");
        assertTrue(left.stream().allMatch(p -> p.failedWrites() == 0), "an outage counts against nobody");
    }

    @Test
    void aLargeProblemPutBackOntoTheOverflowEntryKeepsItsCount() {
        var appender = new ProblemLogAppender(false);
        var big = new PendingProblem(
                "big",
                ProblemLevel.ERROR,
                "dev.chojo.ember.Some",
                "flood",
                null,
                Instant.parse("2026-11-11T10:00:00Z"));
        var flood = new ProblemRecord(
                "big",
                ProblemLevel.ERROR,
                "dev.chojo.ember.Some",
                "flood",
                null,
                null,
                null,
                List.of("flood"),
                1_000_000,
                Instant.parse("2026-11-11T10:00:00Z"),
                Instant.parse("2026-11-11T11:00:00Z"),
                List.of());
        big.countIn(flood);
        for (int i = 0; i < ProblemLogAppender.PENDING_LIMIT; i++) appender.append(warning("filler " + i));

        appender.restore(big);

        var overflow = appender.drainPending().stream()
                .filter(p -> p.fingerprint().equals(ProblemLogAppender.OVERFLOW_FINGERPRINT))
                .findFirst()
                .orElseThrow()
                .record();
        assertEquals(1_000_000, overflow.count());
    }

    @Test
    void aRefusedProblemPendingOnItsOwnIsDroppedWhileTheDatabaseAnswers() {
        var appender = new ProblemLogAppender(false);
        var repository = new Recording();
        var flusher = new ProblemLogFlusher(repository, () -> appender);
        repository.refusedMessage = "poison";
        appender.append(warning("poison"));

        for (int run = 0; run < ProblemLogFlusher.MAX_FAILED_WRITES; run++) flusher.flush();

        assertTrue(appender.drainPending().isEmpty());
    }
}
