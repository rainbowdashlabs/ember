/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.checklist.entity.Checklist;
import dev.chojo.ember.feature.checklist.entity.ChecklistColumn;
import dev.chojo.ember.feature.checklist.service.ChecklistExportService;
import dev.chojo.ember.feature.checklist.service.ChecklistService;
import dev.chojo.ember.feature.checklist.service.ChecklistService.ManualAddResult;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.json;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Placing members on a checklist and ticking a column for many rows, over HTTP: only members of the
 * station and rows of the checklist are acted on.
 */
class ChecklistRoutesTest {
    @Test
    void membersAndRowsAreNarrowedToTheStationAndTheChecklist() {
        var checklists = mock(ChecklistService.class);
        when(checklists.findById(5))
                .thenReturn(Optional.of(new Checklist(5, 3, "Kleidung", null, null, null, null, null, null, null)));
        when(checklists.findColumn(8)).thenReturn(Optional.of(new ChecklistColumn(8, 5, 0, "Hose", null)));
        when(checklists.membersOfStation(List.of(11, 12), 3)).thenReturn(List.of(11));
        when(checklists.addMembers(5, List.of(11))).thenReturn(new ManualAddResult(1, 0, 0));
        when(checklists.rowsOfChecklist(List.of(1, 2), 5)).thenReturn(List.of(1));
        when(checklists.bulkSetColumn(eq(8), anyList(), eq(true), anyInt())).thenReturn(1);
        var harness = RouteHarness.serving(new ChecklistRoutes(
                checklists,
                mock(ChecklistExportService.class),
                mock(MemberNameResolver.class),
                mock(EventCrudService.class),
                mock(EventRestrictionService.class)));

        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(3, StationPermission.CHECKLIST_MANAGE));
            assertEquals(
                    1,
                    json(client.post(PREFIX + "/checklist/5/entry", body("{\"memberIds\": [11, 12]}"), manager))
                            .path("added")
                            .asInt());
            assertEquals(
                    Refusal.CHECKLIST_NAMES_NO_MEMBERS,
                    refusalOf(client.post(PREFIX + "/checklist/5/entry", body("{\"memberIds\": []}"), manager)));
            assertEquals(
                    1,
                    json(client.post(
                                    PREFIX + "/checklist/5/column/8/bulk",
                                    body("{\"entryIds\": [1, 2], \"checked\": true}"),
                                    manager))
                            .path("updated")
                            .asInt());
        });

        verify(checklists).bulkSetColumn(8, List.of(1), true, TestSessions.MEMBER_ID);
    }

    @Test
    void theFilterIsReadAndAnsweredByItsUserTypesAndItsMode() {
        var checklists = mock(ChecklistService.class);
        var before = new Checklist(5, 3, "Kleidung", "", RestrictionMode.AND, null, null, null, null, null);
        var after = new Checklist(5, 3, "Kleidung", "", RestrictionMode.OR, null, null, null, null, null);
        when(checklists.findById(5)).thenReturn(Optional.of(before));
        when(checklists.update(eq(5), eq("Kleidung"), eq(""), eq(RestrictionMode.OR), any()))
                .thenReturn(after);
        when(checklists.findFilterRows(5))
                .thenReturn(List.of(new Restriction(1, StationUserType.TEAM, null, null, null)));
        var harness = RouteHarness.serving(new ChecklistRoutes(
                checklists,
                mock(ChecklistExportService.class),
                mock(MemberNameResolver.class),
                mock(EventCrudService.class),
                mock(EventRestrictionService.class)));

        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(3, StationPermission.CHECKLIST_MANAGE));
            var detail = json(client.patch(
                    PREFIX + "/checklist/5",
                    body("{\"restriction\": {\"userTypes\": [\"TEAM\"], \"mode\": \"OR\"}}"),
                    manager));
            assertEquals("OR", detail.path("mode").asString());
            assertEquals("OR", detail.path("restriction").path("mode").asString());
            assertEquals(
                    "TEAM", detail.path("restriction").path("userTypes").get(0).asString());
            assertEquals(
                    Refusal.BODY_DOES_NOT_MATCH,
                    refusalOf(client.patch(
                            PREFIX + "/checklist/5", body("{\"restriction\": {\"mode\": \"XOR\"}}"), manager)));
        });

        verify(checklists)
                .update(eq(5), eq("Kleidung"), eq(""), eq(RestrictionMode.OR), argThat(filter -> filter.userTypes()
                        .equals(List.of(StationUserType.TEAM))));
    }
}
