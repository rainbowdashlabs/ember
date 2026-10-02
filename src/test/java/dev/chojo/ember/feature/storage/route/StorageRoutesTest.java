/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.feature.storage.core.BackendRequest;
import dev.chojo.ember.feature.storage.core.BackendSummary;
import dev.chojo.ember.feature.storage.core.MigrationResponse;
import dev.chojo.ember.feature.storage.core.ProbeResult;
import dev.chojo.ember.feature.storage.entity.QuotaOrigin;
import dev.chojo.ember.feature.storage.entity.StorageQuotaPreset;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceMigrateRequest;
import dev.chojo.ember.feature.storage.service.InstanceStorageSettingsService.InstanceMigrationStatusResponse;
import dev.chojo.ember.feature.storage.service.StorageAuditLogService;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService.PresetRequest;
import dev.chojo.ember.feature.storage.service.StorageQuotaAdminService.QuotaUpdateRequest;
import dev.chojo.ember.feature.storage.service.StorageUsageReportService;
import dev.chojo.ember.feature.storage.service.StorageUsageReportService.AdminStationUsage;
import dev.chojo.ember.feature.storage.service.StorageUsageReportService.StationUsageResponse;
import io.javalin.testtools.HttpClient;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageRoutesTest {
    private static final String BASE = PREFIX + "/admin/storage";
    private static final UUID STATION = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final ProbeResult PROBED = new ProbeResult(true, null, "2026-09-01T10:00:00Z");

    private StorageUsageReportService usage;
    private StorageQuotaAdminService quotas;
    private InstanceStorageSettingsService instance;
    private StorageAuditLogService auditLog;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        usage = mock(StorageUsageReportService.class);
        quotas = mock(StorageQuotaAdminService.class);
        instance = mock(InstanceStorageSettingsService.class);
        auditLog = mock(StorageAuditLogService.class);
        harness = RouteHarness.serving(new StorageRoutes(usage, quotas, instance, auditLog));
    }

    private Response get(HttpClient client, String path) {
        return client.get(BASE + path, harness.as(TestSessions.administrator()));
    }

    private Response post(HttpClient client, String path, Object json) {
        return client.post(BASE + path, json, harness.as(TestSessions.administrator()));
    }

    @Test
    void aManagerSeesTheirOwnStationsUsage() {
        when(usage.stationUsage(4)).thenReturn(new StationUsageResponse(List.of(), 10, 100, 10, Map.of(), false));

        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(4, StationPermission.STATION_MANAGER));
            assertEquals(
                    10,
                    json(client.get(PREFIX + "/storage/usage", manager))
                            .path("quotaUsedPercent")
                            .asInt());
        });
    }

    @Test
    void theAdministratorSeesTheUsageOfEveryStation() {
        when(usage.adminUsage())
                .thenReturn(List.of(new AdminStationUsage(
                        STATION.toString(),
                        "Nord",
                        1,
                        2,
                        50,
                        List.of(),
                        null,
                        null,
                        false,
                        QuotaOrigin.INSTANCE_DEFAULT)));

        harness.run((server, client) -> {
            var rows = json(get(client, "/usage"));
            assertEquals("Nord", rows.get(0).path("stationName").asString());
            assertEquals(
                    403,
                    client.get(BASE + "/usage", harness.as(TestSessions.member(4, StationPermission.STATION_MANAGER)))
                            .code());
        });
    }

    @Test
    void usageIsCountedAgainForAllOrForOneStation() {
        doThrow(StorageRefusal.STATION_NOT_HERE_FOR_STORAGE_ADMIN.raise())
                .when(quotas)
                .recalculateStation(STATION);

        harness.run((server, client) -> {
            assertEquals(202, post(client, "/recalculate", null).code());
            assertEquals(
                    StorageRefusal.STATION_NOT_HERE_FOR_STORAGE_ADMIN,
                    refusalOf(post(client, "/recalculate/" + STATION, null)));
            assertEquals(GeneralRefusal.ADDRESS_NOT_AN_IDENTIFIER, refusalOf(post(client, "/recalculate/nord", null)));
        });

        verify(quotas).recalculateAll();
    }

    @Test
    void presetsAreListedCreatedChangedDeletedAndHandedOut() {
        var preset = new StorageQuotaPreset(3, "Klein", 10, 1, 2, 3, 4, 5, 6);
        when(quotas.presets()).thenReturn(List.of(preset));
        when(quotas.createPreset(any())).thenReturn(preset);
        when(quotas.updatePreset(any(Integer.class), any())).thenReturn(preset);
        var request = body("""
                {"name": "Klein", "total": 10, "kb": 1, "board": 2, "images": 3, "pages": 4,
                 "perFile": 5, "perImage": 6}""");
        var admin = TestSessions.administrator();

        harness.run((server, client) -> {
            assertEquals(
                    "Klein", json(get(client, "/presets")).get(0).path("name").asString());
            assertEquals(
                    3,
                    json(client.post(BASE + "/presets", request, harness.as(admin)))
                            .path("id")
                            .asInt());
            assertEquals(
                    200,
                    client.put(BASE + "/presets/3", request, harness.as(admin)).code());
            assertEquals(
                    204,
                    client.delete(BASE + "/presets/3", null, harness.as(admin)).code());
            assertEquals(
                    200,
                    post(client, "/presets/3/apply", body("{\"stationUids\": [\"" + STATION + "\"]}"))
                            .code());
        });

        verify(quotas).createPreset(new PresetRequest("Klein", 10, 1, 2, 3, 4, 5, 6));
        verify(quotas).deletePreset(3);
        verify(quotas).applyPreset(3, List.of(STATION.toString()));
    }

    @Test
    void aStationsLimitsAreSetAndReset() {
        harness.run((server, client) -> {
            var admin = harness.as(TestSessions.administrator());
            assertEquals(
                    200,
                    client.put(BASE + "/stations/" + STATION + "/quotas", body("{\"totalBytes\": 5}"), admin)
                            .code());
            assertEquals(
                    200,
                    client.delete(BASE + "/stations/" + STATION + "/quotas", null, admin)
                            .code());
        });

        verify(quotas).updateStationQuotas(STATION, new QuotaUpdateRequest(5L, null, null, null, null, null, null));
        verify(quotas).resetStationQuotas(STATION);
    }

    @Test
    void theInstanceStorageIsDescribedProbedAndWatched() {
        when(instance.summary()).thenReturn(new BackendSummary.LocalSummary("data"));
        when(instance.probe(Actor.human(TestSessions.ACCOUNT_ID, null))).thenReturn(PROBED);
        when(instance.probe(any(BackendRequest.S3Request.class)))
                .thenReturn(new ProbeResult(false, "no bucket", "now"));
        when(instance.status()).thenReturn(new InstanceMigrationStatusResponse(true));

        harness.run((server, client) -> {
            var summary = json(get(client, "/backend"));
            assertEquals("LOCAL", summary.path("type").asString());
            assertEquals("data", summary.path("root").asString());
            assertEquals(
                    true,
                    json(post(client, "/backend/probe", null)).path("healthy").asBoolean());
            var unsaved = post(client, "/backend/probe-config", body("{\"type\": \"S3\", \"bucket\": \"ember\"}"));
            assertEquals("no bucket", json(unsaved).path("error").asString());
            assertEquals(
                    true,
                    json(get(client, "/backend/apply/status"))
                            .path("migrationInFlight")
                            .asBoolean());
        });
    }

    @Test
    void theInstanceMovesOntoNewStorageInTheNameOfWhoAsked() {
        when(instance.apply(any(), any())).thenReturn(new MigrationResponse(3, 2, 1, 2, 42));

        harness.run((server, client) -> {
            var moved = post(client, "/backend/apply", body("{\"target\": {\"type\": \"LOCAL\", \"root\": \"data\"}}"));
            assertEquals(42, json(moved).path("copiedBytes").asInt());
        });

        verify(instance)
                .apply(
                        Actor.human(TestSessions.ACCOUNT_ID, null),
                        new InstanceMigrateRequest(new BackendRequest.LocalRequest("data"), null));
    }

    @Test
    void theHistoryIsListedWithTheFiltersPassedOn() {
        when(auditLog.list(any(), any(), any(Integer.class))).thenReturn(List.of());

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    get(client, "/audit?before=2026-09-01T10:00:00Z&stationUid=" + STATION + "&limit=5")
                            .code());
            assertEquals(200, get(client, "/audit").code());
        });

        verify(auditLog).list("2026-09-01T10:00:00Z", STATION.toString(), 5);
        verify(auditLog).list(null, null, StorageAuditLogService.DEFAULT_LIMIT);
    }
}
