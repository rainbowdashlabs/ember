/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.file.elements.Metrics;
import dev.chojo.ember.feature.system.entity.ProblemLevel;
import dev.chojo.ember.feature.system.entity.ProblemState;
import dev.chojo.ember.feature.system.repository.ProblemLogRepository;
import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
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
    private final ProblemLogService service = new ProblemLogService(repository, metrics);

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
}
