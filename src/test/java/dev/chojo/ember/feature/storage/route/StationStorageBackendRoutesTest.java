/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.service.StationStorageBackendService;
import dev.chojo.ember.feature.storage.service.StationStorageBackendService.BackendOverrideResponse;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.LocalRequest;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.MigrationResponse;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ProbeResult;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.S3Request;
import io.javalin.testtools.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationStorageBackendRoutesTest {
    private static final String BASE = PREFIX + "/station/storage";
    private static final int STATION = 4;
    private static final Actor ACTOR = Actor.human(TestSessions.ACCOUNT_ID, TestSessions.MEMBER_ID);

    private StationStorageBackendService backend;
    private StorageAuditLogService auditLog;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        backend = mock(StationStorageBackendService.class);
        auditLog = mock(StorageAuditLogService.class);
        when(backend.requireStation(STATION)).thenReturn(STATION);
        harness = RouteHarness.serving(new StationStorageBackendRoutes(backend, auditLog));
    }

    private Consumer<Request.Builder> administrator() {
        return harness.as(TestSessions.member(STATION, StationPermission.STATION_ADMINISTRATOR));
    }

    @Test
    void theStationSeesWhereItsFilesAre() {
        when(backend.describe(STATION))
                .thenReturn(
                        new BackendOverrideResponse(StorageBackendType.LOCAL, null, null, "Kreisverband", true, true));

        harness.run((server, client) -> {
            var description = json(client.get(BASE + "/backend", administrator()));
            assertEquals("Kreisverband", description.path("clusterName").asString());
            assertEquals(true, description.path("locked").asBoolean());
        });
    }

    @Test
    void aStationThatIsGoneIsRefusedOnEveryRoute() {
        when(backend.requireStation(STATION)).thenThrow(Refusal.STORAGE_STATION_NOT_HERE.raise());

        harness.run((server, client) -> {
            assertEquals(Refusal.STORAGE_STATION_NOT_HERE, refusalOf(client.get(BASE + "/backend", administrator())));
            assertEquals(Refusal.STORAGE_STATION_NOT_HERE, refusalOf(client.get(BASE + "/audit", administrator())));
        });
    }

    @Test
    void theStationMovesItsFilesInTheNameOfWhoAsked() {
        when(backend.apply(any(), anyInt(), any())).thenReturn(new MigrationResponse(3, 2, 1, 0, 64));

        harness.run((server, client) -> {
            var moved = client.post(BASE + "/backend/apply", body("{\"type\": \"LOCAL\"}"), administrator());
            assertEquals(2, json(moved).path("copied").asInt());
        });

        verify(backend).apply(ACTOR, STATION, new LocalRequest());
    }

    @Test
    void theSavedStorageAndStorageNotSavedYetAreProbed() {
        var probed = new ProbeResult(true, null, "2026-09-01T10:00:00Z");
        when(backend.probe(ACTOR, STATION)).thenReturn(probed);
        when(backend.probe(any(Integer.class), any(S3Request.class))).thenReturn(probed);

        harness.run((server, client) -> {
            assertEquals(
                    true,
                    json(client.post(BASE + "/backend/probe", null, administrator()))
                            .path("healthy")
                            .asBoolean());
            var unsaved = client.post(
                    BASE + "/backend/probe-config", body("{\"type\": \"S3\", \"bucket\": \"ember\"}"), administrator());
            assertEquals(true, json(unsaved).path("healthy").asBoolean());
        });
    }

    @Test
    void theStationsHistoryIsListed() {
        when(auditLog.listForStation(anyInt(), any(), anyInt())).thenReturn(List.of());

        harness.run((server, client) -> assertEquals(
                200, client.get(BASE + "/audit?limit=7", administrator()).code()));

        verify(auditLog).listForStation(STATION, null, 7);
    }
}
