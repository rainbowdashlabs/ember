/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.Lookup;
import dev.chojo.ember.tracking.TableEntry;
import dev.chojo.ember.tracking.TrackingStatus;
import dev.chojo.ember.tracking.TransferContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        eventCategoryRepo.create(station.id(), "TestCategory", 1, null);

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
        assertFalse(first.containsKey("station_id"), "the importing side already knows its own station");
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
                new TransferContext(TrackingStatus.TRACKED, null, List.of("station_id"), List.of(), null),
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

    /**
     * A visibility override names a folder or a file. Both reach the station, each through its own
     * reference, and another station's override reaches neither.
     */
    @Test
    void aRowNamingEitherOfTwoParentsIsReachedThroughEither() {
        Station elsewhere = stationRepo.create("ExporterOtherStation");
        int author = stationMemberRepo
                .create(
                        station.id(),
                        accountRepo
                                .create("kb-export@test.com", "Kim", "Wiki", true)
                                .id())
                .id();
        var folder = knowledgeBaseRepo.createFolder(station.id(), null, "Handbuch", "", author);
        var file = knowledgeBaseRepo.createFile(
                station.id(), folder.id(), "Lageplan", "", KbFileType.TEXT, "text/plain", 1, null, author);
        var otherFolder = knowledgeBaseRepo.createFolder(elsewhere.id(), null, "Fremd", "", author);
        knowledgeBaseRepo.setPublicVisibility(folder.id(), null, true);
        knowledgeBaseRepo.setPublicVisibility(null, file.id(), false);
        knowledgeBaseRepo.setPublicVisibility(otherFolder.id(), null, true);

        var rows = exporter.export("kb_public_visibility", station.id(), 0, 100);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(row -> Integer.valueOf(folder.id()).equals(row.get("folder_id"))));
        assertTrue(rows.stream().anyMatch(row -> Integer.valueOf(file.id()).equals(row.get("file_id"))));
    }

    /**
     * A lending request belongs to both of its stations, so it goes with the one asking and with the
     * one lending, and its lines go with it. A request between two other stations goes with neither.
     */
    @Test
    void aLendingRequestGoesWithEitherOfItsStations() {
        Station asking = stationRepo.create("ExporterAsking");
        Station lending = stationRepo.create("ExporterLending");
        Station other = stationRepo.create("ExporterOther");
        var requests = new LendingRepository();
        var request = requests.createRequest(
                UUID.randomUUID(),
                asking.uid(),
                lending.uid(),
                LocalDate.now(),
                LocalDate.now(),
                null,
                null,
                null,
                "Übung");
        requests.addRequestItem(request.id(), null, null, null, 2, null);
        requests.createRequest(
                UUID.randomUUID(), other.uid(), asking.uid(), LocalDate.now(), LocalDate.now(), null, null, null, "");

        assertEquals(
                List.of(request.id()),
                exporter.export("federation_lending_request", lending.id(), 0, 100).stream()
                        .map(row -> row.get("id"))
                        .toList());
        assertEquals(
                2,
                exporter.export("federation_lending_request", asking.id(), 0, 100)
                        .size());
        assertEquals(
                1,
                exporter.export("federation_lending_request_item", lending.id(), 0, 100)
                        .size());
        assertEquals(
                1,
                exporter.export("federation_lending_request_item", asking.id(), 0, 100)
                        .size());
    }

    @Test
    void refusesIgnoredTableForTransfer() {
        var ex = assertThrows(
                IllegalStateException.class, () -> exporter.export("account_session", station.id(), 0, 100));
        assertTrue(ex.getMessage().contains("not TRACKED"));
    }
}
