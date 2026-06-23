/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.OutputShape;
import dev.chojo.ember.tracking.Status;
import dev.chojo.ember.tracking.TableEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exhaustively exercises the generic transfer engine on every TRACKED table:
 * <ul>
 *   <li>{@code allTrackedTablesAreExportable} — calls {@code exportTable} for every TRACKED table
 *       in the tracking JSON and asserts no SQL/binding error is thrown. Verifies the engine's
 *       SQL is syntactically valid for every table, even on an empty station.</li>
 *   <li>{@code paginationIsConsistent} — seeds 7 rows into a multi-row table, walks pages of size
 *       3, and confirms the concatenated pages equal the single-call export with no duplicates
 *       or gaps.</li>
 *   <li>{@code roundTripAllTablesImportSucceeds} — seeds data across many domains, exports, deletes
 *       source-side artefacts, imports, and asserts every table that had source rows now has the
 *       same count on the target.</li>
 * </ul>
 */
@Tag("database")
class AllTrackedTablesTransferTest extends RepositoryTestBase {

    private static StationExportService exportService;
    private static StationImportService importService;

    @BeforeAll
    static void setup() {
        exportService = new StationExportService(stationRepo);
        importService = new StationImportService(
                stationRepo, accountRepo, exportService, new dev.chojo.ember.conf.file.elements.Api());
    }

    @Test
    void allTrackedTablesAreExportable() throws IOException {
        var tracking = DataTrackingLoader.loadFromClasspath();
        var station = stationRepo.create("Exportability Probe");
        int stationId = station.id();

        List<String> failures = new ArrayList<>();
        for (var entry : tracking.tables().entrySet()) {
            String tableName = entry.getKey();
            TableEntry table = entry.getValue();
            if (table.stationTransfer() == null || table.stationTransfer().status() != Status.TRACKED) continue;
            try {
                var page = exportService.exportTable(stationId, tableName, 0, 100);
                // The envelope must always carry the wire key (possibly null payload for empty single-rows)
                assertEquals(tableName, page.get("table"));
            } catch (RuntimeException e) {
                failures.add(tableName + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) {
            fail("Engine could not export the following TRACKED tables:\n  " + String.join("\n  ", failures));
        }
        stationRepo.delete(stationId);
    }

    @Test
    void paginationIsConsistent() {
        var station = stationRepo.create("Pagination Probe");
        int stationId = station.id();
        // Seed 7 member groups so we can page through with limit=3
        for (int i = 0; i < 7; i++) {
            memberGroupRepo.create(stationId, "G" + i);
        }

        var single = (List<?>)
                exportService.exportTable(stationId, "member_group", 0, 1000).get("member_group");
        assertEquals(7, single.size());

        // Walk three pages of size 3 and combine them
        var p1 = (List<?>)
                exportService.exportTable(stationId, "member_group", 0, 3).get("member_group");
        var p2 = (List<?>)
                exportService.exportTable(stationId, "member_group", 3, 3).get("member_group");
        var p3 = (List<?>)
                exportService.exportTable(stationId, "member_group", 6, 3).get("member_group");
        var p4 = (List<?>)
                exportService.exportTable(stationId, "member_group", 9, 3).get("member_group");

        assertEquals(3, p1.size());
        assertEquals(3, p2.size());
        assertEquals(1, p3.size());
        assertEquals(0, p4.size(), "page past the end must be empty");

        // No overlap, no gaps
        var paged = new ArrayList<>();
        paged.addAll(p1);
        paged.addAll(p2);
        paged.addAll(p3);
        assertEquals(single.size(), paged.size(), "pages must cover the same rows as a single-call export");
        assertEquals(single, paged, "pages joined in order must equal the single-call export");

        stationRepo.delete(stationId);
    }

    @Test
    void roundTripAllTablesImportSucceeds() throws IOException {
        var tracking = DataTrackingLoader.loadFromClasspath();

        // -- Seed source data across many domains --
        var source = stationRepo.create("Round-trip Probe");
        int sourceStationId = source.id();
        stationRepo.updateLocale(sourceStationId, "de-DE");
        stationRepo.updateTimezone(sourceStationId, "Europe/Berlin");
        stationRepo.setDisabledModules(sourceStationId, Set.of(StationModule.LOST_AND_FOUND));

        var accA = accountRepo.create("rta@example.com", "Anna", "A", true);
        var accB = accountRepo.create("rtb@example.com", "Bob", "B", true);
        accountRepo.createCredential(accB.id(), "$bcrypt$src-hash");
        var memberA = stationMemberRepo.create(sourceStationId, accA.id());
        var memberB = stationMemberRepo.create(sourceStationId, accB.id());

        var groupA = memberGroupRepo.create(sourceStationId, "Group A");
        var groupB = memberGroupRepo.create(sourceStationId, "Group B");
        memberGroupRepo.addMember(groupA.id(), memberA.id());
        memberGroupRepo.addMember(groupB.id(), memberB.id());

        userTagRepo.create(sourceStationId, "Tag1");
        userTagRepo.create(sourceStationId, "Tag2");

        eventRepo.createCategory(sourceStationId, "Cat", 0, null);
        inventoryRepo.create(sourceStationId, "Inv", InventoryType.INTERNAL, false);

        // -- Snapshot per-table source counts for every TRACKED table --
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        for (String table : exportService.getTableOrder()) {
            sourceCounts.put(
                    table,
                    sizeOf(exportService
                            .exportTable(sourceStationId, table, 0, 10_000)
                            .get(table)));
        }

        // -- Collect bundle (every TRACKED table → its payload) --
        Map<String, Object> bundle = new LinkedHashMap<>();
        for (String table : exportService.getTableOrder()) {
            Object payload =
                    exportService.exportTable(sourceStationId, table, 0, 10_000).get(table);
            if (payload != null) bundle.put(table, payload);
        }

        // -- Strip source-side data so the import is the sole creator (shared testcontainer DB) --
        stationRepo.delete(sourceStationId);
        for (String email : List.of("rta@example.com", "rtb@example.com")) {
            accountRepo.findByEmail(email).ifPresent(a -> accountRepo.delete(a.id()));
        }

        // -- Import + assert per-table counts match --
        var result = importService.importStation(bundle);
        int targetStationId = result.stationId();

        List<String> mismatches = new ArrayList<>();
        for (var e : sourceCounts.entrySet()) {
            String table = e.getKey();
            int expected = e.getValue();
            if (expected == 0) continue; // nothing to compare on empty tables
            int actual = sizeOf(
                    exportService.exportTable(targetStationId, table, 0, 10_000).get(table));
            if (actual != expected) {
                mismatches.add("  %s: expected %d, got %d".formatted(table, expected, actual));
            }
        }

        // Make sure we actually exercised the round-trip on a meaningful number of tables.
        long touchedTables = sourceCounts.values().stream().filter(v -> v > 0).count();
        assertTrue(touchedTables >= 5, "expected several seeded tables, got " + touchedTables);

        // Sanity: account custom-scope round-trip preserved both seeded accounts.
        assertNotNull(accountRepo.findByEmail("rta@example.com").orElse(null));
        assertNotNull(accountRepo.findByEmail("rtb@example.com").orElse(null));
        // The credential from the source should land on the target with force_password_change=TRUE
        var importedB = accountRepo.findByEmail("rtb@example.com").orElseThrow();
        var credB = accountRepo.findCredential(importedB.id()).orElseThrow();
        assertTrue(credB.forcePasswordChange(), "imported credential must require a password reset");

        if (!mismatches.isEmpty()) {
            fail("Round-trip row counts diverged for:\n" + String.join("\n", mismatches));
        }

        // Spot-check: SINGLE-shape table (station) and FLAT-shape table (station_disabled_module) round-tripped
        Object stationPayload =
                exportService.exportTable(targetStationId, "station", 0, 1).get("station");
        assertInstanceOf(Map.class, stationPayload, "station wire entry must be SINGLE-shape (Map)");
        var disabled = (List<?>) exportService
                .exportTable(targetStationId, "station_disabled_module", 0, 10)
                .get("station_disabled_module");
        assertTrue(disabled.contains("LOST_AND_FOUND"));

        // Cleanup
        stationRepo.delete(targetStationId);
        for (String email : List.of("rta@example.com", "rtb@example.com")) {
            accountRepo.findByEmail(email).ifPresent(a -> accountRepo.delete(a.id()));
        }

        // Verify we did in fact iterate every TRACKED non-SINGLE/non-FLAT table in the comparison
        long trackedRowTables = tracking.tables().values().stream()
                .filter(t -> t.stationTransfer() != null && t.stationTransfer().status() == Status.TRACKED)
                .filter(t -> t.effectiveShape() == OutputShape.ROWS)
                .count();
        assertTrue(trackedRowTables > 50, "expected many TRACKED ROWS-shape tables, got " + trackedRowTables);
    }

    private static int sizeOf(Object payload) {
        return switch (payload) {
            case null -> 0;
            case List<?> l -> l.size();
            case Map map -> 1;
            default -> 1;
        };
    }
}
