/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking.engine;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.FilterTableType;
import dev.chojo.ember.feature.members.entity.SavedFilter;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.tracking.DataTrackingLoader;
import dev.chojo.ember.tracking.engine.GenericTableImporter.IdRemapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generic export and import against a real database: an array column travels as the list of its
 * elements and arrives as an array of its element type, an array of ids follows the rows it names, and a
 * row whose required reference cannot be found is left behind instead of failing the import.
 */
class GenericTableImporterTest extends RepositoryTestBase {

    private static GenericTableExporter exporter;
    private static GenericTableImporter importer;

    @BeforeAll
    static void loadEngine() throws IOException {
        var tracking = DataTrackingLoader.loadFromClasspath();
        exporter = new GenericTableExporter(tracking);
        importer = new GenericTableImporter(tracking);
    }

    @Test
    void anArrayColumnIsExportedAsTheListOfItsElements() {
        Station station = stationRepo.create("Array Export");
        int group = memberGroupRepo.create(station.id(), "Atemschutz").id();
        attendanceRepo.createPreset(
                station.id(),
                "Monat",
                List.of(StationUserType.MEMBER, StationUserType.TRIAL),
                List.of(group),
                "month",
                "exact");

        var row =
                exporter.export("attendance_report_preset", station.id(), 0, 10).getFirst();

        assertEquals(List.of("MEMBER", "TRIAL"), row.get("user_types"));
        assertEquals(List.of(group), row.get("group_ids"));
    }

    @Test
    void anArrayOfIdsFollowsTheRowsItNames() {
        Station source = stationRepo.create("Array Source");
        Station destination = stationRepo.create("Array Destination");
        int kept = memberGroupRepo.create(source.id(), "Atemschutz").id();
        int gone = memberGroupRepo.create(source.id(), "Maschinisten").id();
        int arrived = memberGroupRepo.create(destination.id(), "Atemschutz").id();
        attendanceRepo.createPreset(
                source.id(), "Quartal", List.of(StationUserType.TEAM), List.of(kept, gone), "quarter", "hour");
        var idMap = new IdRemapper();
        idMap.put("member_group", kept, arrived);

        var rows = exporter.export("attendance_report_preset", source.id(), 0, 10);
        assertEquals(1, importer.importRows(destination.id(), "attendance_report_preset", rows, idMap));

        var preset = attendanceRepo.findPresets(destination.id()).getFirst();
        assertEquals("Quartal", preset.name());
        assertEquals(List.of(StationUserType.TEAM), preset.userTypes());
        assertEquals(List.of(arrived), preset.groupIds(), "the group that arrived is named by its new id");
    }

    @Test
    void arrayElementsAreBoundAsTheColumnsElementType() {
        String joined = query("SELECT array_to_string(:times, ',') AS joined;")
                .single(ArrayValues.bind(call(), "times", List.of("08:00:00", "17:30"), "_time"))
                .map(row -> row.getString("joined"))
                .first()
                .orElseThrow();

        assertEquals("08:00:00,17:30:00", joined);
    }

    @Test
    void anArrayValueThatIsNoListIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ArrayValues.elements("{a,b}"));
        assertEquals(List.of("a", "b"), ArrayValues.elements(new Object[] {"a", "b"}));
        assertTrue(ArrayValues.isArray("_int4"));
        assertFalse(ArrayValues.isArray("int4"));
        assertFalse(ArrayValues.isArray(null));
    }

    @Test
    void aRowFindsItsRequiredAccountAgainByItsAddress() {
        Station destination = stationRepo.create("Filter Destination");
        var account = accountRepo.create("filter-owner@import.test", "Fia", "Filter", true);

        int imported = importer.importRows(
                destination.id(),
                "saved_filter",
                List.of(filterRow("Aktive", account.email(), account.uid())),
                new IdRemapper());

        assertEquals(1, imported);
        assertEquals(
                List.of("Aktive"),
                savedFilterRepo.findByAccountAndTable(account.id(), FilterTableType.MEMBERS).stream()
                        .map(SavedFilter::name)
                        .toList());
    }

    @Test
    void aRowWhoseRequiredAccountIsNowhereIsLeftBehind() {
        Station destination = stationRepo.create("Filter Nowhere");

        int imported = importer.importRows(
                destination.id(),
                "saved_filter",
                List.of(filterRow("Verwaist", "nobody@import.test", UUID.randomUUID())),
                new IdRemapper());

        assertEquals(0, imported, "the filter stays behind rather than failing the import");
    }

    private static Map<String, Object> filterRow(String name, Object email, UUID uid) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 1);
        row.put("table_type", FilterTableType.MEMBERS.name());
        row.put("name", name);
        row.put("filter_data", "{}");
        row.put("position", 0);
        row.put("account_email", email);
        row.put("account_uid", uid.toString());
        return row;
    }
}
