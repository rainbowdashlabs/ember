/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.repository.StorageUsageRepository;
import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

class StorageTasksTest {

    @Test
    void theReconciliationRunsAtTheConfiguredInterval() {
        var reconciliation = spy(new StorageReconciliationService(
                mock(StorageUsageRepository.class),
                mock(StationRepository.class),
                mock(StorageService.class),
                new Storage()));
        doNothing().when(reconciliation).reconcileAll();
        var task = reconciliation.scheduledTasks().getFirst();

        task.work().run();

        verify(reconciliation).reconcileAll();
        assertEquals("storage-reconciliation", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(1), Duration.ofHours(24)), task.schedule());
    }
}
