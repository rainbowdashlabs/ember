/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemTasksTest {

    @Test
    void problemReportSweepRunsEverySixHours() {
        var sweeper = mock(ProblemReportSweeper.class);
        when(sweeper.scheduledTasks()).thenCallRealMethod();
        var task = sweeper.scheduledTasks().getFirst();

        task.work().run();

        verify(sweeper).sweep();
        assertEquals("problem-report-sweep", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofHours(1), Duration.ofHours(6)), task.schedule());
    }

    @Test
    void aFailedProblemReportSweepIsSwallowed() {
        var repository = mock(ProblemReportRepository.class);
        when(repository.findAcknowledgedBefore(any())).thenThrow(new IllegalStateException("database gone"));

        assertDoesNotThrow(
                () -> new ProblemReportSweeper(repository, mock(ProblemReportScreenshotService.class)).sweep());
    }

    @Test
    void theDemoIdleResetLooksEveryMinuteAndLeavesAnOrdinaryInstanceAlone() {
        var demo = mock(Demo.class);
        var task = new DemoService(demo, null, null, null, Set.of(), null, null, null, null, null, null)
                .scheduledTasks()
                .getFirst();

        task.work().run();

        verify(demo).enabled();
        assertEquals("demo-idle-reset", task.name());
        assertEquals(Schedule.fixedRate(Duration.ofMinutes(1), Duration.ofMinutes(1)), task.schedule());
    }
}
