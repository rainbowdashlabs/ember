/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.checklist.service.ChecklistService.ColumnSpec;
import dev.chojo.ember.feature.checklist.service.ChecklistService.FilterSpec;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecklistServiceTest extends RepositoryTestBase {
    private static ChecklistService service;
    private static Station station;
    private static Account account;
    private static StationMember managerMember;
    private static StationMember other;

    @BeforeAll
    static void setupService() {
        service = new ChecklistService(checklistRepo, stationMemberRepo, memberGroupRepo, userTagRepo);
        station = stationRepo.create("Service Station");
        account = accountRepo.create("svc@test.com", "Svc", "Member");
        managerMember = stationMemberRepo.create(station.id(), account.id());
        var account2 = accountRepo.create("svc2@test.com", "Svc", "Two");
        other = stationMemberRepo.create(station.id(), account2.id());
    }

    @Test
    void fullLifecycle() {
        var filter = new FilterSpec(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(managerMember.id()));
        var checklist = service.create(
                station.id(),
                "Full Test",
                "desc",
                RestrictionMode.OR,
                List.of(new ColumnSpec("Step 1", "desc"), new ColumnSpec("Step 2", "")),
                filter,
                managerMember.id());
        assertNotNull(checklist);
        assertEquals(2, service.findColumns(checklist.id()).size());
        assertEquals(2, service.findEntries(checklist.id(), false).size());

        var summaries = service.findSummaries(station.id());
        assertTrue(summaries.stream().anyMatch(s -> s.id() == checklist.id()));

        var refresh1 = service.refresh(checklist.id());
        assertEquals(0, refresh1.added());
        assertEquals(2, refresh1.alreadyPresent());

        var entry = service.findEntries(checklist.id(), false).get(0);
        var column = service.findColumns(checklist.id()).get(0);

        var write1 = service.writeCell(entry.id(), column.id(), true, "first note", managerMember.id());
        assertTrue(write1.noteChanged());
        assertTrue(write1.cell().checked());
        assertEquals("first note", write1.cell().note());

        var write2 = service.writeCell(entry.id(), column.id(), false, "first note", managerMember.id());
        assertFalse(write2.noteChanged());
        var write3 = service.writeCell(entry.id(), column.id(), false, "", managerMember.id());
        assertTrue(write3.noteChanged());
        assertNull(write3.cell().note());

        var history = service.findNoteHistory(entry.id(), column.id());
        assertEquals(2, history.size());

        var historyMissingCell = service.findNoteHistory(entry.id(), 999_999);
        assertTrue(historyMissingCell.isEmpty());

        var allEntries = service.findEntries(checklist.id(), false);
        int bulkCount = service.bulkSetColumn(
                column.id(), allEntries.stream().map(e -> e.id()).toList(), true, managerMember.id());
        assertTrue(bulkCount >= 1);

        service.softDeleteEntry(entry.id());
        assertEquals(
                allEntries.size() - 1,
                service.findEntries(checklist.id(), false).size());

        var manual = service.addMembers(checklist.id(), List.of(entry.memberId(), other.id()));
        assertEquals(1, manual.restored());
        assertTrue(manual.skipped() >= 1 || manual.added() == 0);
        assertNull(service.findEntry(entry.id()).orElseThrow().deletedAt());

        var updated = service.update(checklist.id(), "renamed", "newer", RestrictionMode.AND, FilterSpec.empty());
        assertEquals("renamed", updated.name());
        assertEquals(RestrictionMode.AND, updated.mode());

        assertTrue(service.findRestrictionSet(updated).restrictions().isEmpty());
        assertTrue(service.findFilterRows(updated.id()).isEmpty());

        var addedColumn = service.addColumn(checklist.id(), "Late", "added later", null);
        var renamedColumn = service.updateColumn(addedColumn.id(), "Late renamed", "", 2);
        assertEquals("Late renamed", renamedColumn.label());
        assertTrue(service.findColumn(addedColumn.id()).isPresent());
        assertEquals(0, service.countCheckedCellsInColumn(addedColumn.id()));

        var columnsForReorder = service.findColumns(checklist.id());
        var reversedIds = columnsForReorder.stream()
                .map(dev.chojo.ember.feature.checklist.entity.ChecklistColumn::id)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        java.util.Collections.reverse(reversedIds);
        service.reorderColumns(checklist.id(), reversedIds);
        var afterReorder = service.findColumns(checklist.id());
        assertEquals(reversedIds.get(0), afterReorder.get(0).id());
        try {
            service.reorderColumns(checklist.id(), List.of(reversedIds.get(0)));
            throw new AssertionError("expected reject");
        } catch (IllegalArgumentException expected) {
        }

        assertTrue(service.deleteColumn(addedColumn.id()));

        assertTrue(service.findById(checklist.id()).isPresent());
        var allCells = service.findCells(checklist.id());
        assertFalse(allCells.isEmpty());

        var resolvedEmpty = service.resolveMatchingMemberIds(checklist.id(), station.id(), RestrictionMode.AND);
        assertTrue(resolvedEmpty.isEmpty());

        assertTrue(service.delete(checklist.id()));
        assertFalse(service.delete(checklist.id()));
    }

    @Test
    void recordsCoverConstructorLines() {
        var refresh = new ChecklistService.RefreshResult(1, 2);
        assertEquals(1, refresh.added());
        assertEquals(2, refresh.alreadyPresent());
        var manual = new ChecklistService.ManualAddResult(3, 4, 5);
        assertEquals(3, manual.added());
        assertEquals(4, manual.restored());
        assertEquals(5, manual.skipped());
        var column = new ColumnSpec("a", "b");
        assertEquals("a", column.label());
        var emptyFilter = FilterSpec.empty();
        assertTrue(emptyFilter.userTypes().isEmpty());
    }
}
