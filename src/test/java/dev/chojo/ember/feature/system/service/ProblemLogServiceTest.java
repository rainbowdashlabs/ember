/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.Conf;
import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.system.entity.ProblemContextLine;
import dev.chojo.ember.feature.system.entity.ProblemContextState;
import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemOccurrence;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.entity.StoredProblem;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository;
import dev.chojo.ember.feature.system.repository.ApplicationLogRepository.LogEntry;
import dev.chojo.ember.feature.system.repository.ProblemLogRepository;
import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Working through the problem log: faults and refusals are resolved apart, a problem that is gone is
 * said to be, and the sweep keeps to the configured retention.
 */
class ProblemLogServiceTest {
    private final ProblemLogRepository repository = mock(ProblemLogRepository.class);
    private final Metrics metrics = mock(Metrics.class);
    private final ApplicationLogRepository logs = mock(ApplicationLogRepository.class);
    private final Conf conf = mock(Conf.class, RETURNS_DEEP_STUBS);
    private final ProblemLogService service = new ProblemLogService(repository, logs, metrics, conf);

    private static final Instant AT = Instant.parse("2026-11-01T10:00:00Z");

    private static StoredProblem problem() {
        return new StoredProblem(
                4,
                "fp",
                ProblemLevel.ERROR,
                "dev.chojo.ember.Broken",
                "broken",
                null,
                null,
                null,
                List.of("broken"),
                1,
                AT,
                AT,
                ProblemState.OPEN,
                null,
                0);
    }

    private static LogEntry line(String logger, String thread, Instant at) {
        return new LogEntry(1, at, "ERROR", logger, thread, "line", null);
    }

    @Test
    void resolvingAllFaultsLeavesTheRefusalsAndTheOtherWayRound() {
        when(repository.resolveAllOpen(Set.of(ProblemLevel.WARN, ProblemLevel.ERROR), 7))
                .thenReturn(2);
        when(repository.resolveAllOpen(Set.of(ProblemLevel.REFUSAL), 7)).thenReturn(5);

        assertEquals(2, service.resolveAll(false, 7));
        assertEquals(5, service.resolveAll(true, 7));
    }

    @Test
    void unmutingOpensAgain() {
        when(repository.setState(4, Set.of(ProblemState.MUTED), ProblemState.OPEN, 7))
                .thenReturn(true);

        service.unmute(4, 7);

        verify(repository).setState(4, Set.of(ProblemState.MUTED), ProblemState.OPEN, 7);
    }

    @Test
    void aProblemThatIsGoneIsSaidToBe() {
        when(repository.setState(4L, Set.of(ProblemState.OPEN, ProblemState.RESOLVED), ProblemState.MUTED, 7))
                .thenReturn(false);
        when(repository.delete(4)).thenReturn(false);

        var muted = assertThrows(RefusalResponse.class, () -> service.mute(4, 7));
        var deleted = assertThrows(RefusalResponse.class, () -> service.delete(4));

        assertEquals(SystemRefusal.PROBLEM_NOT_HERE, muted.refusal());
        assertEquals(SystemRefusal.PROBLEM_NOT_HERE, deleted.refusal());
    }

    @Test
    void theSweepKeepsToTheRetention() {
        when(metrics.problemRetentionDays()).thenReturn(12);

        service.sweep();

        verify(repository).prune(12);
    }

    @Test
    void aFailedSweepWaitsForTheNextDay() {
        when(metrics.problemRetentionDays()).thenReturn(12);
        when(repository.prune(anyInt())).thenThrow(new IllegalStateException("database away"));

        assertDoesNotThrow(service::sweep);
    }

    @Test
    void theSweepRunsDaily() {
        var sweeping = mock(ProblemLogService.class);
        when(sweeping.scheduledTasks()).thenCallRealMethod();
        var task = sweeping.scheduledTasks().getFirst();

        task.work().run();

        verify(sweeping).sweep();
        assertEquals("problem-log-sweep", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(10), Duration.ofDays(1)), task.schedule());
    }

    @Test
    void theLinesAroundTheChosenOccurrenceMarkTheOccurrence() {
        when(repository.find(4)).thenReturn(Optional.of(problem()));
        var older = new ProblemOccurrence(AT.minusSeconds(600), "worker-2");
        when(repository.occurrences(4)).thenReturn(List.of(new ProblemOccurrence(AT, "worker-1"), older));
        when(conf.main().logging().databaseEnabled()).thenReturn(true);
        when(logs.around(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        line(
                                "dev.chojo.ember.Other",
                                "worker-2",
                                older.occurredAt().minusSeconds(1)),
                        line("dev.chojo.ember.Broken", "worker-2", older.occurredAt())));

        var context = service.context(4, older.occurredAt(), false);

        verify(logs)
                .around(
                        older.occurredAt(),
                        "worker-2",
                        older.occurredAt().minus(ProblemLogService.CONTEXT_BEFORE),
                        older.occurredAt().plus(ProblemLogService.CONTEXT_AFTER),
                        ProblemLogService.CONTEXT_BEFORE_LINES,
                        ProblemLogService.CONTEXT_AFTER_LINES);
        assertEquals(ProblemContextState.LINES, context.state());
        assertEquals(1, context.occurrence());
        assertEquals(
                List.of(false, true),
                context.lines().stream().map(ProblemContextLine::problem).toList());
    }

    @Test
    void everyThreadIsAskedForWhenWanted() {
        when(repository.find(4)).thenReturn(Optional.of(problem()));
        when(repository.occurrences(4)).thenReturn(List.of(new ProblemOccurrence(AT, "worker-1")));

        service.context(4, null, true);

        verify(logs).around(eq(AT), isNull(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void anEmptyWindowSaysWhy() {
        when(repository.find(4)).thenReturn(Optional.of(problem()));
        when(repository.occurrences(4)).thenReturn(List.of(new ProblemOccurrence(AT, "worker-1")));

        when(conf.main().logging().databaseEnabled()).thenReturn(false);
        assertEquals(
                ProblemContextState.LOGGING_OFF, service.context(4, null, false).state());

        when(conf.main().logging().databaseEnabled()).thenReturn(true);
        assertEquals(
                ProblemContextState.NOTHING_KEPT,
                service.context(4, null, false).state());
    }

    @Test
    void aProblemWithoutOccurrencesHasNothingToLookAround() {
        when(repository.find(4)).thenReturn(Optional.of(problem()));
        when(repository.occurrences(4)).thenReturn(List.of());

        assertEquals(
                ProblemContextState.NO_OCCURRENCE,
                service.context(4, null, false).state());
    }

    @Test
    void anOccurrenceNoLongerKeptFallsBackToTheNewest() {
        when(repository.find(4)).thenReturn(Optional.of(problem()));
        when(repository.occurrences(4)).thenReturn(List.of(new ProblemOccurrence(AT, "worker-1")));

        var context = service.context(4, AT.minusSeconds(3600), false);

        assertEquals(0, context.occurrence());
        verify(logs).around(eq(AT), eq("worker-1"), any(), any(), anyInt(), anyInt());
    }

    @Test
    void onlyTheFirstLineOfTheProblemsLoggerAndLevelIsMarked() {
        when(repository.find(4)).thenReturn(Optional.of(problem()));
        when(repository.occurrences(4)).thenReturn(List.of(new ProblemOccurrence(AT, "worker-1")));
        when(logs.around(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(
                        line("dev.chojo.ember.Other", "worker-1", AT),
                        new LogEntry(2, AT, "INFO", "dev.chojo.ember.Broken", "worker-1", "info", null),
                        line("dev.chojo.ember.Broken", "worker-1", AT),
                        line("dev.chojo.ember.Broken", "worker-1", AT)));

        var context = service.context(4, null, false);

        assertEquals(
                List.of(false, false, true, false),
                context.lines().stream().map(ProblemContextLine::problem).toList());
    }

    @Test
    void aProblemThatIsGoneHasNoContext() {
        when(repository.find(4)).thenReturn(Optional.empty());

        var refused = assertThrows(RefusalResponse.class, () -> service.context(4, null, false));

        assertEquals(SystemRefusal.PROBLEM_NOT_HERE, refused.refusal());
    }
}
