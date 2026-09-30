/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.lifecycle.Schedule;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FederationTasksTest {

    @Test
    void theVersionBroadcastAsksForThePartnersEveryFifteenMinutes() {
        var repository = mock(FederationRepository.class);
        var task = new FederationVersionBroadcaster(repository, mock(FederationContractRefreshService.class))
                .scheduledTasks()
                .getFirst();

        task.work().run();

        verify(repository).findAllActiveRemotePartners();
        assertEquals("federation-version-broadcast", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(2), Duration.ofMinutes(15)), task.schedule());
    }
}
