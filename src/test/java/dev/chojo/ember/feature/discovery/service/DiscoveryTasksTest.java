/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import dev.chojo.ember.auth.signing.DatabaseReplayStore;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPeerRepository;
import dev.chojo.ember.feature.discovery.repository.DiscoveryPingRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class DiscoveryTasksTest {

    @Test
    void thePingDoesNothingWhileDiscoveryIsOff() {
        var peers = mock(DiscoveryPeerRepository.class);
        var settings = mock(DiscoverySettingsService.class);
        var task = new DiscoveryPingScheduler(peers, mock(DiscoveryPingService.class), settings)
                .scheduledTasks()
                .getFirst();

        task.work().run();

        verify(settings).isEnabled();
        verifyNoInteractions(peers);
        assertTask(
                task,
                "discovery-ping",
                Schedule.fixedDelay(
                        Duration.ofMinutes(3),
                        Duration.ofMinutes(DiscoverySettingsService.DEFAULT_PING_INTERVAL_MINUTES)));
    }

    @Test
    void theStationRefreshFetchesEverySixHours() {
        var fetcher = mock(DiscoveryStationFetcher.class);
        var task =
                new DiscoveryStationRefreshScheduler(fetcher).scheduledTasks().getFirst();

        task.work().run();

        verify(fetcher).refreshAll();
        assertTask(task, "discovery-station-refresh", Schedule.fixedDelay(Duration.ofMinutes(10), Duration.ofHours(6)));
    }

    @Test
    void theMaintenanceSweepsNoncesAndDecaysReputations() {
        var pings = mock(DiscoveryPingRepository.class);
        var peers = mock(DiscoveryPeerRepository.class);
        var tasks = new DiscoveryMaintenanceScheduler(pings, peers, mock(DatabaseReplayStore.class)).scheduledTasks();

        tasks.forEach(task -> task.work().run());

        verify(pings).deleteExpired();
        verify(peers).decayReputation(5);
        assertTask(
                tasks.get(0),
                "signed-request-nonce-sweep",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(5)));
        assertTask(
                tasks.get(1),
                "discovery-reputation-decay",
                Schedule.fixedDelay(Duration.ofHours(1), Duration.ofDays(1)));
    }

    @Test
    void thePartnerSeedRunsOnceTwoMinutesAfterTheStart() {
        var federation = mock(FederationRepository.class);
        var task = new FederationPartnerSeeder(
                        federation, mock(DiscoveryPeerRepository.class), mock(DiscoveryHttpClient.class))
                .scheduledTasks()
                .getFirst();

        task.work().run();

        verify(federation).findAllActiveRemotePartners();
        assertTask(task, "discovery-partner-seed", Schedule.once(Duration.ofMinutes(2)));
    }

    private static void assertTask(ScheduledTask task, String name, Schedule schedule) {
        assertEquals(name, task.name());
        assertEquals(schedule, task.schedule());
    }
}
