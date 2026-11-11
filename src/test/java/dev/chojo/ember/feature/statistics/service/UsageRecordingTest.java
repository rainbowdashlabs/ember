/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.statistics.service;

import dev.chojo.ember.feature.statistics.repository.UsageRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The usage recorder and the hourly snapshot both swallow their failures, and the snapshot runs as a
 * task of its own every hour.
 */
class UsageRecordingTest {
    private final UsageRepository usage = mock(UsageRepository.class);

    @Test
    void anActiveAccountIsHandedToTheRepository() {
        new UsageRecorder(usage).markActive(7);

        verify(usage).markActive(7);
    }

    @Test
    void aFailedRecordingIsSwallowed() {
        when(usage.markActive(7)).thenThrow(new IllegalStateException("database gone"));

        assertDoesNotThrow(() -> new UsageRecorder(usage).markActive(7));
    }

    @Test
    void theSnapshotRunsEveryHour() {
        var task = new UsageSnapshotTask(usage).scheduledTasks().getFirst();

        task.work().run();

        verify(usage).snapshot(any(LocalDate.class));
        assertEquals("usage-snapshot", task.name());
        assertEquals(Duration.ofHours(1), task.schedule().period());
        assertEquals(Duration.ofMinutes(2), task.schedule().initialDelay(), "a nightly restart still records its day");
    }

    @Test
    void aFailedSnapshotIsSwallowed() {
        doThrow(new IllegalStateException("database gone")).when(usage).snapshot(any(LocalDate.class));

        assertDoesNotThrow(() -> new UsageSnapshotTask(usage).snapshot());
    }
}
