/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.cluster.service.ClusterStationMoveService;
import dev.chojo.ember.feature.cluster.service.ClusterStorageBackendService;
import dev.chojo.ember.feature.storage.entity.ClusterStorageConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.MigrationResponse;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ProbeResult;
import dev.chojo.ember.feature.storage.service.StorageProbeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClusterStorageBackendRoutesTest {
    private static final int CLUSTER_ID = 5;
    private static final String BASE = PREFIX + "/cluster/storage/backend";
    private static final StationStorageBackendConfig CONFIG =
            new StationStorageBackendConfig.SftpVariant("sftp.test", 22, "ember", "", "files", null);

    private ClusterStorageBackendService backend;
    private StorageBackendPayloads payloads;
    private StorageProbeService probes;
    private ClusterStationMoveService moves;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        var clusters = mock(ClusterService.class);
        var cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(CLUSTER_ID);
        when(clusters.findById(CLUSTER_ID)).thenReturn(Optional.of(cluster));
        backend = mock(ClusterStorageBackendService.class);
        payloads = mock(StorageBackendPayloads.class);
        probes = mock(StorageProbeService.class);
        moves = mock(ClusterStationMoveService.class);
        harness = RouteHarness.serving(new ClusterStorageBackendRoutes(clusters, backend, payloads, probes, moves));
    }

    private ClusterStorageBackendService.Policy policyWith(ClusterStorageConfig current) {
        return new ClusterStorageBackendService.Policy(ClusterBackendReach.EVERY_STATION, false, current);
    }

    @Test
    void theSavedStorageIsProbedAndMissingStorageRefused() {
        var current = new ClusterStorageConfig(8, CLUSTER_ID, CONFIG, true, Instant.EPOCH, Instant.EPOCH);
        when(backend.findPolicy(CLUSTER_ID)).thenReturn(policyWith(current), policyWith(null));
        when(probes.probe(CONFIG)).thenReturn(new ProbeResult(false, "timeout", "2026-09-01T10:00:00Z"));

        harness.run((server, client) -> {
            var storage = harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_STORAGE));
            assertEquals(
                    "timeout",
                    json(client.post(BASE + "/probe", null, storage))
                            .path("error")
                            .asString());
            assertEquals(
                    ClusterRefusal.CLUSTER_KEEPS_NO_STORAGE, refusalOf(client.post(BASE + "/probe", null, storage)));
        });
    }

    @Test
    void storageNotSavedYetIsProbed() {
        when(payloads.toEntity(any())).thenReturn(CONFIG);
        when(probes.probe(CONFIG)).thenReturn(new ProbeResult(true, null, "2026-09-01T10:00:00Z"));

        var answer = harness.request(client -> client.post(
                BASE + "/probe-config",
                body("{\"type\": \"SFTP\", \"host\": \"sftp.test\"}"),
                harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_STORAGE))));

        assertEquals(true, json(answer).path("healthy").asBoolean());
    }

    @Test
    void aStationIsMovedInTheNameOfWhoAsked() {
        var station = UUID.fromString("00000000-0000-0000-0000-000000000004");
        when(moves.move(Actor.human(TestSessions.ACCOUNT_ID, null), CLUSTER_ID, station.toString()))
                .thenReturn(new MigrationResponse(3, 2, 1, 0, 64));
        when(moves.move(Actor.human(TestSessions.ACCOUNT_ID, null), CLUSTER_ID, "nord"))
                .thenThrow(ClusterRefusal.STATION_NOT_AN_IDENTITY_ON_CLUSTER_STORAGE_MOVE.raise());

        harness.run((server, client) -> {
            var storage = harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_STORAGE));
            var moved = client.post(BASE + "/placements/" + station + "/move", null, storage);
            assertEquals(64, json(moved).path("copiedBytes").asInt());
            assertEquals(
                    ClusterRefusal.STATION_NOT_AN_IDENTITY_ON_CLUSTER_STORAGE_MOVE,
                    refusalOf(client.post(BASE + "/placements/nord/move", null, storage)));
        });
    }
}
