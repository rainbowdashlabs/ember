/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.Lookup;
import dev.chojo.ember.tracking.Status;
import dev.chojo.ember.tracking.TableEntry;
import dev.chojo.ember.tracking.TransferContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests the {@link GenericTableExporter} against a real PostgreSQL database
 * on five simple tables. Demonstrates that the metadata-driven engine produces
 * the same columns the hand-written exporter currently emits, including
 * column exclusion via {@code ignoredColumns} and join-flattened {@link Lookup}s.
 */
class GenericTableExporterTest extends RepositoryTestBase {

    private static GenericTableExporter exporter;
    private static DataTracking tracking;
    private static Station station;

    @BeforeAll
    static void seed() throws IOException {
        tracking = DataTrackingLoader.loadFromClasspath();
        exporter = new GenericTableExporter(tracking);

        station = stationRepo.create("ExporterTestStation");

        memberGroupRepo.create(station.id(), "Alpha");
        memberGroupRepo.create(station.id(), "Beta");

        userTagRepo.create(station.id(), "vip");

        eventRepo.createCategory(station.id(), "TestCategory", 1, null);

        inventoryRepo.create(station.id(), "InvA", InventoryType.INTERNAL, false);
        inventoryRepo.create(station.id(), "InvB", InventoryType.EXTERNAL, true);
    }

    @Test
    void exportsDirectlyScopedTable() {
        var rows = exporter.export("member_group", station.id(), 0, 100);
        assertEquals(2, rows.size());
        var first = rows.getFirst();
        assertTrue(first.containsKey("id"));
        assertTrue(first.containsKey("name"));
        // After transfer-metadata backfill, station_id is in ignoredColumns: the import side
        // already knows the target station, so the source id is intentionally omitted.
        assertFalse(first.containsKey("station_id"));
    }

    @Test
    void exportsEventCategory() {
        var rows = exporter.export("event_category", station.id(), 0, 100);
        assertEquals(1, rows.size());
        assertEquals("TestCategory", rows.getFirst().get("name"));
    }

    @Test
    void exportsInventoryWithMultipleRows() {
        var rows = exporter.export("inventory", station.id(), 0, 100);
        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> "InvA".equals(r.get("name"))));
        assertTrue(rows.stream().anyMatch(r -> "InvB".equals(r.get("name"))));
    }

    @Test
    void honoursIgnoredColumnsFromTransferContext() {
        // Build a synthetic tracking that ignores 'station_id' on member_group.
        var original = tracking.tables().get("member_group");
        var tweaked = new TableEntry(
                original.feature(),
                original.scope(),
                original.tableHash(),
                original.columns(),
                original.foreignKeys(),
                original.lookups(),
                original.outputShape(),
                original.flatField(),
                original.customScope(),
                new TransferContext(Status.TRACKED, null, List.of("station_id"), null),
                original.gdprExport(),
                original.gdprDeletion());
        var customTables = new LinkedHashMap<>(tracking.tables());
        customTables.put("member_group", tweaked);
        var custom = new DataTracking(
                tracking.version(), tracking.schemaHash(), tracking.generatedAt(), customTables, tracking.fileStores());

        var rows = new GenericTableExporter(custom).export("member_group", station.id(), 0, 100);
        assertFalse(rows.isEmpty());
        for (var row : rows) {
            assertFalse(row.containsKey("station_id"), "station_id should be excluded by ignoredColumns");
        }
    }

    @Test
    void honoursLookupFromFkMetadata() {
        // user_tag has a station_id FK; flatten station.name as 'station_name' via lookup.
        var original = tracking.tables().get("user_tag");
        var tweaked = new TableEntry(
                original.feature(),
                original.scope(),
                original.tableHash(),
                original.columns(),
                original.foreignKeys(),
                List.of(new Lookup("station_id", "name", "station_name")),
                original.outputShape(),
                original.flatField(),
                original.customScope(),
                original.stationTransfer(),
                original.gdprExport(),
                original.gdprDeletion());
        var customTables = new LinkedHashMap<>(tracking.tables());
        customTables.put("user_tag", tweaked);
        var custom = new DataTracking(
                tracking.version(), tracking.schemaHash(), tracking.generatedAt(), customTables, tracking.fileStores());

        var rows = new GenericTableExporter(custom).export("user_tag", station.id(), 0, 100);
        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            assertTrue(row.containsKey("station_name"), "lookup should add 'station_name' field");
            assertEquals("ExporterTestStation", row.get("station_name"));
        }
    }

    @Test
    void refusesIgnoredTableForTransfer() {
        // 'account_external_auth' remains IGNORED for stationTransfer
        var ex = assertThrows(
                IllegalStateException.class, () -> exporter.export("account_external_auth", station.id(), 0, 100));
        assertTrue(ex.getMessage().contains("not TRACKED"));
    }
}
