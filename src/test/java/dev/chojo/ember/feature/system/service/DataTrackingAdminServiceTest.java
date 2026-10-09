/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.feature.system.service.DataTrackingAdminService.TableUpdate;
import dev.chojo.ember.tracking.ColumnEntry;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.GdprDeletionContext;
import dev.chojo.ember.tracking.GdprExportContext;
import dev.chojo.ember.tracking.TableEntry;
import dev.chojo.ember.tracking.TrackingStatus;
import dev.chojo.ember.tracking.TransferContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the dev-only admin service that drives the {@code /admin/data-tracking} UI.
 *
 * <p>Each test uses a temp file so the real {@code data_tracking.json} isn't touched.
 */
class DataTrackingAdminServiceTest {

    @TempDir
    Path tempDir;

    private DataTrackingAdminService service;

    @BeforeEach
    void setup() throws IOException {
        Path trackingFile = tempDir.resolve("data_tracking.json");
        DataTrackingLoader.write(trackingFile, sampleTracking());
        service = new DataTrackingAdminService(trackingFile);
    }

    @Test
    void loadReturnsContentsOfTrackingFile() throws IOException {
        var loaded = service.load();
        assertEquals(2, loaded.tables().size());
        assertNotNull(loaded.tables().get("station"));
        assertNotNull(loaded.tables().get("station_member"));
    }

    /**
     * The fixture's station has two verified columns and is tracked for transfer, ignored for both GDPR
     * dimensions; its station member has one of two columns verified and is unverified for transfer,
     * tracked for export and ignored for deletion.
     */
    @Test
    void summarizeCountsAcrossEveryDimension() throws IOException {
        var summary = service.summarize();
        assertEquals(2, summary.totalTables());
        assertEquals(4, summary.totalColumns());
        assertEquals(3, summary.verifiedColumns());
        assertEquals(1, summary.stationTransfer().tracked());
        assertEquals(0, summary.stationTransfer().ignored());
        assertEquals(1, summary.stationTransfer().unverified());
        assertEquals(1, summary.gdprExport().tracked());
        assertEquals(1, summary.gdprExport().ignored());
        assertEquals(0, summary.gdprExport().unverified());
        assertEquals(0, summary.gdprDeletion().tracked());
        assertEquals(2, summary.gdprDeletion().ignored());
        assertEquals(0, summary.gdprDeletion().unverified());
    }

    @Test
    void updateTableFlipsStationTransferStatus() throws IOException {
        var update = new TableUpdate(
                null,
                null,
                new TransferContext(
                        TrackingStatus.TRACKED, null, List.of("station_id"), List.of(), "promoted from UNVERIFIED"),
                null,
                null);
        var result = service.updateTable("station_member", update);
        assertEquals(TrackingStatus.TRACKED, result.stationTransfer().status());
        assertEquals(List.of("station_id"), result.stationTransfer().ignoredColumns());
        assertEquals("promoted from UNVERIFIED", result.stationTransfer().rationale());

        var reloaded = service.load();
        assertEquals(
                TrackingStatus.TRACKED,
                reloaded.tables().get("station_member").stationTransfer().status());
    }

    /** The display name is already verified and stays so; the former flag is flipped to verified. */
    @Test
    void updateTableHonoursColumnVerifiedOverrides() throws IOException {
        var overrides = new LinkedHashMap<String, Boolean>();
        overrides.put("display_name", true);
        overrides.put("former", true);
        var update = new TableUpdate(null, overrides, null, null, null);
        var result = service.updateTable("station_member", update);
        assertTrue(result.columns().stream().allMatch(ColumnEntry::verified));
    }

    @Test
    void updateTableRejectsUnknownTable() {
        var ex = assertThrows(
                RefusalResponse.class,
                () -> service.updateTable("does_not_exist", new TableUpdate(null, null, null, null, null)));
        assertEquals(SystemRefusal.TRACKED_TABLE_NOT_HERE_TO_UPDATE, ex.refusal());
        assertTrue(ex.getMessage().contains("does_not_exist"));
    }

    @Test
    void verifyAllColumnsFlipsEveryColumn() throws IOException {
        var result = service.verifyAllColumns("station_member");
        assertTrue(result.columns().stream().allMatch(ColumnEntry::verified));
        var reloaded = service.load();
        assertTrue(reloaded.tables().get("station_member").columns().stream().allMatch(ColumnEntry::verified));
    }

    @Test
    void verifyAllColumnsRejectsUnknownTable() {
        var ex = assertThrows(RefusalResponse.class, () -> service.verifyAllColumns("does_not_exist"));
        assertEquals(SystemRefusal.TRACKED_TABLE_NOT_HERE_TO_VERIFY, ex.refusal());
    }

    @Test
    void updateTableUpdatesFeatureWhenSupplied() throws IOException {
        var update = new TableUpdate("custom-feature", null, null, null, null);
        var result = service.updateTable("station_member", update);
        assertEquals("custom-feature", result.feature());
    }

    private static DataTracking sampleTracking() {
        var stationCols = List.of(
                new ColumnEntry("id", "int4", false, true, "PK"),
                new ColumnEntry("name", "text", false, true, "Display name"));
        var memberCols = List.of(
                new ColumnEntry("display_name", "text", false, true, null),
                new ColumnEntry("former", "bool", false, false, null));

        var station = new TableEntry(
                "station",
                null,
                "sha256:station",
                stationCols,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                TransferContext.tracked(),
                GdprExportContext.ignored("aggregate"),
                GdprDeletionContext.ignored("aggregate"),
                "Station table");
        var stationMember = new TableEntry(
                "members",
                null,
                "sha256:sm",
                memberCols,
                List.of(),
                null,
                null,
                null,
                null,
                null,
                TransferContext.unverified(),
                GdprExportContext.tracked(List.of()),
                GdprDeletionContext.ignored("for now"),
                "Station member");

        var tables = new LinkedHashMap<String, TableEntry>();
        tables.put("station", station);
        tables.put("station_member", stationMember);
        return new DataTracking(
                DataTracking.CURRENT_VERSION, "sha256:top", Instant.now(), tables, new LinkedHashMap<>());
    }
}
