/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableColumn;
import dev.chojo.ember.feature.members.entity.MemberTablePreset;
import dev.chojo.ember.feature.members.service.MemberTablePresetService;
import dev.chojo.ember.feature.members.service.MemberTableRenderer;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The register drawn as a table, and the selections of columns a station keeps, over HTTP.
 */
class MemberTableRoutesTest {
    private static final int STATION_ID = 3;
    private static final MemberTableColumn NAME = MemberTableColumn.builtin("name");

    private MemberTableService tables;
    private MemberTablePresetService presets;
    private StationService stations;
    private RouteHarness harness;
    private UserSession reader;

    @BeforeEach
    void setup() {
        tables = mock(MemberTableService.class);
        presets = mock(MemberTablePresetService.class);
        stations = mock(StationService.class);
        harness =
                RouteHarness.serving(new MemberTableRoutes(tables, mock(MemberTableRenderer.class), presets, stations));
        reader = TestSessions.member(STATION_ID, StationPermission.MEMBER_READ, StationPermission.MEMBER_FIELDS);
        when(presets.findById(4))
                .thenReturn(Optional.of(new MemberTablePreset(4, STATION_ID, "Jugend", List.of(NAME))));
        when(presets.findById(5)).thenReturn(Optional.of(new MemberTablePreset(5, 99, "Fremd", List.of(NAME))));
    }

    @Test
    void selectionsAreListedSavedAndDeletedForTheStation() {
        when(presets.list(STATION_ID)).thenReturn(List.of());
        when(presets.save(STATION_ID, "Jugend", List.of(NAME)))
                .thenReturn(new MemberTablePreset(4, STATION_ID, "Jugend", List.of(NAME)));

        harness.run((server, client) -> {
            assertEquals(
                    200,
                    client.get(PREFIX + "/member-table/presets", harness.as(reader))
                            .code());
            var saved = client.put(
                    PREFIX + "/member-table/presets",
                    body("{\"name\": \"Jugend\", \"columns\": [{\"kind\": \"BUILTIN\", \"key\": \"name\"}]}"),
                    harness.as(reader));
            assertEquals("Jugend", json(saved).path("name").asString());
            assertEquals(
                    204,
                    client.delete(PREFIX + "/member-table/presets/4", null, harness.as(reader))
                            .code());
            assertEquals(
                    GeneralRefusal.NOT_HERE_OR_NOT_YOURS,
                    refusalOf(client.delete(PREFIX + "/member-table/presets/5", null, harness.as(reader))));
        });

        verify(presets).delete(4, STATION_ID);
    }

    @Test
    void theTableIsDrawnForTheCallersStation() {
        var station = mock(Station.class);
        when(stations.findById(STATION_ID)).thenReturn(Optional.of(station));
        when(tables.build(eq(station), any(), eq(List.of(NAME)), any(), anyMap()))
                .thenReturn(new MemberTable(List.of(), List.of()));

        var drawn = harness.request(client -> client.post(
                PREFIX + "/member-table",
                body("{\"memberIds\": [7], \"columns\": [{\"kind\": \"BUILTIN\", \"key\": \"name\"}]}"),
                harness.as(reader)));

        assertEquals(200, drawn.code());
    }

    @Test
    void aStationThatIsGoneDrawsNoTable() {
        var refused = harness.request(
                client -> client.post(PREFIX + "/member-table", body("{\"memberIds\": []}"), harness.as(reader)));

        assertEquals(MemberRefusal.STATION_NOT_HERE_FOR_MEMBER_TABLE, refusalOf(refused));
    }
}
