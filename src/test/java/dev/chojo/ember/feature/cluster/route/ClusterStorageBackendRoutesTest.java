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
import dev.chojo.ember.feature.cluster.service.ClusterStorageBackendService.PolicyResponse;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.core.BackendRequest;
import dev.chojo.ember.feature.storage.core.MigrationResponse;
import dev.chojo.ember.feature.storage.core.ProbeResult;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService.AuditEntryResponse;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageProbeService;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClusterStorageBackendRoutesTest {
    private static final int CLUSTER_ID = 5;
    private static final String BASE = PREFIX + "/cluster/storage/backend";
    private static final Actor ACTOR = Actor.human(TestSessions.ACCOUNT_ID, null);

    private ClusterStorageBackendService backend;
    private ClusterStationMoveService moves;
    private StorageAuditLogService auditLog;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        var clusters = mock(ClusterService.class);
        var cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(CLUSTER_ID);
        when(clusters.findById(CLUSTER_ID)).thenReturn(Optional.of(cluster));
        backend = mock(ClusterStorageBackendService.class);
        moves = mock(ClusterStationMoveService.class);
        auditLog = mock(StorageAuditLogService.class);
        harness = RouteHarness.serving(new ClusterStorageBackendRoutes(clusters, backend, moves, auditLog));
    }

    private Consumer<Request.Builder> storage() {
        return harness.as(TestSessions.clusterMember(CLUSTER_ID, ClusterPermission.CLUSTER_STORAGE));
    }

    /** The reason a probe failed would map the network behind the address, so it stays in the log. */
    @Test
    void theSavedStorageIsProbedAndMissingStorageRefused() {
        when(backend.probe(ACTOR, CLUSTER_ID))
                .thenReturn(new ProbeResult(false, StorageProbeService.PROBE_FAILED, "2026-09-01T10:00:00Z"))
                .thenThrow(ClusterRefusal.CLUSTER_KEEPS_NO_STORAGE.raise());

        harness.run((server, client) -> {
            assertEquals(
                    StorageProbeService.PROBE_FAILED,
                    json(client.post(BASE + "/probe", null, storage()))
                            .path("error")
                            .asString());
            assertEquals(
                    ClusterRefusal.CLUSTER_KEEPS_NO_STORAGE, refusalOf(client.post(BASE + "/probe", null, storage())));
        });
    }

    @Test
    void storageNotSavedYetIsProbed() {
        when(backend.probe(eq(CLUSTER_ID), any(BackendRequest.class)))
                .thenReturn(new ProbeResult(true, null, "2026-09-01T10:00:00Z"));

        var answer = harness.request(client ->
                client.post(BASE + "/probe-config", body("{\"type\": \"SFTP\", \"host\": \"sftp.test\"}"), storage()));

        assertEquals(true, json(answer).path("healthy").asBoolean());
    }

    @Test
    void theStorageIsSavedAndDescribedInTheNameOfWhoAsked() {
        when(backend.apply(eq(ACTOR), eq(CLUSTER_ID), any(BackendRequest.SftpRequest.class)))
                .thenReturn(new PolicyResponse(ClusterBackendReach.EVERY_STATION, true, null));
        when(backend.describe(CLUSTER_ID)).thenReturn(new PolicyResponse(ClusterBackendReach.NONE, false, null));

        harness.run((server, client) -> {
            var saved = client.post(BASE + "/apply", body("{\"type\": \"SFTP\", \"host\": \"sftp.test\"}"), storage());
            assertEquals("EVERY_STATION", json(saved).path("reach").asString());
            assertEquals("NONE", json(client.get(BASE, storage())).path("reach").asString());
        });
    }

    @Test
    void thePolicyIsSetAndTheStorageDroppedInTheNameOfWhoAsked() {
        harness.run((server, client) -> {
            assertEquals(
                    204,
                    client.put(BASE + "/policy", body("{\"reach\": \"OWN_FILES\", \"locked\": true}"), storage())
                            .code());
            assertEquals(204, client.delete(BASE, null, storage()).code());
            assertEquals(
                    204,
                    client.put(BASE + "/policy", body("{\"locked\": false}"), storage())
                            .code());
        });

        verify(backend).setPolicy(ACTOR, CLUSTER_ID, ClusterBackendReach.OWN_FILES, true);
        verify(backend).setPolicy(eq(ACTOR), eq(CLUSTER_ID), isNull(), eq(false));
        verify(backend).dropBackend(ACTOR, CLUSTER_ID);
    }

    @Test
    void aStationIsMovedInTheNameOfWhoAsked() {
        var station = UUID.fromString("00000000-0000-0000-0000-000000000004");
        when(moves.move(ACTOR, CLUSTER_ID, station.toString())).thenReturn(new MigrationResponse(3, 2, 1, 0, 64));
        when(moves.move(ACTOR, CLUSTER_ID, "nord"))
                .thenThrow(ClusterRefusal.STATION_NOT_AN_IDENTITY_ON_CLUSTER_STORAGE_MOVE.raise());

        harness.run((server, client) -> {
            var moved = client.post(BASE + "/placements/" + station + "/move", null, storage());
            assertEquals(64, json(moved).path("copiedBytes").asInt());
            assertEquals(
                    ClusterRefusal.STATION_NOT_AN_IDENTITY_ON_CLUSTER_STORAGE_MOVE,
                    refusalOf(client.post(BASE + "/placements/nord/move", null, storage())));
        });
    }

    @Test
    void theAssociationsHistoryIsListed() {
        when(auditLog.listForCluster(CLUSTER_ID, "2026-09-01T10:00:00Z", 20))
                .thenReturn(List.of(new AuditEntryResponse(
                        9,
                        "2026-09-01T09:00:00Z",
                        TestSessions.ACCOUNT_ID,
                        null,
                        null,
                        null,
                        CLUSTER_ID,
                        StorageAuditAction.POLICY_CHANGED,
                        null,
                        null,
                        StorageAuditOutcome.OK,
                        null)));

        var answer = harness.request(client ->
                client.get(PREFIX + "/cluster/storage/audit?before=2026-09-01T10:00:00Z&limit=20", storage()));

        assertEquals("POLICY_CHANGED", json(answer).path(0).path("action").asString());
    }
}
