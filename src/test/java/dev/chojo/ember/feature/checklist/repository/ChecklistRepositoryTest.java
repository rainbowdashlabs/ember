/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.checklist.entity.ChecklistEntry;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChecklistRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static StationMember member;
    private static StationMember secondMember;
    private static int checklistId;
    private static int columnId;
    private static int entryId;
    private static int cellId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Checklist Station");
        Account account = accountRepo.create("checklist@test.com", "Check", "List");
        member = stationMemberRepo.create(station.id(), account.id());
        var account2 = accountRepo.create("checklist2@test.com", "Check", "Two");
        secondMember = stationMemberRepo.create(station.id(), account2.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void createChecklist() {
        var checklist =
                checklistRepo.create(station.id(), "Safety", "All required steps", RestrictionMode.AND, member.id());
        assertNotNull(checklist);
        assertEquals("Safety", checklist.name());
        assertEquals(RestrictionMode.AND, checklist.mode());
        assertNotNull(checklist.lastRefreshedAt());
        checklistId = checklist.id();
    }

    @Test
    @Order(2)
    void findById() {
        assertTrue(checklistRepo.findById(checklistId).isPresent());
        assertTrue(checklistRepo.findById(9_999_999).isEmpty());
    }

    @Test
    @Order(3)
    void updateMetadata() {
        checklistRepo.updateMetadata(checklistId, "Safety Briefing", "", RestrictionMode.OR);
        var checklist = checklistRepo.findById(checklistId).orElseThrow();
        assertEquals("Safety Briefing", checklist.name());
        assertEquals(RestrictionMode.OR, checklist.mode());
        assertEquals("", checklist.description());
    }

    @Test
    @Order(4)
    void touchRefreshed() {
        checklistRepo.touchRefreshed(checklistId);
        assertNotNull(checklistRepo.findById(checklistId).orElseThrow().lastRefreshedAt());
    }

    @Test
    @Order(5)
    void addColumns() {
        int pos0 = checklistRepo.nextColumnPosition(checklistId);
        assertEquals(0, pos0);
        var column = checklistRepo.createColumn(checklistId, pos0, "Briefing", "Read the briefing");
        columnId = column.id();
        var second =
                checklistRepo.createColumn(checklistId, checklistRepo.nextColumnPosition(checklistId), "Signed", "");
        assertEquals(1, second.position());
        assertEquals(2, checklistRepo.findColumns(checklistId).size());
    }

    @Test
    @Order(6)
    void findColumn() {
        assertTrue(checklistRepo.findColumn(columnId).isPresent());
        assertTrue(checklistRepo.findColumn(0).isEmpty());
    }

    @Test
    @Order(7)
    void updateColumn() {
        checklistRepo.updateColumn(columnId, "Briefing read", "Updated description", 0);
        var column = checklistRepo.findColumn(columnId).orElseThrow();
        assertEquals("Briefing read", column.label());
        assertEquals("Updated description", column.description());
    }

    @Test
    @Order(7)
    void reorderColumns() {
        var columns = checklistRepo.findColumns(checklistId);
        assertEquals(2, columns.size());
        var reversed = List.of(columns.get(1).id(), columns.get(0).id());
        checklistRepo.reorderColumns(checklistId, reversed);
        var after = checklistRepo.findColumns(checklistId);
        assertEquals(reversed.get(0), after.get(0).id());
        assertEquals(reversed.get(1), after.get(1).id());
        checklistRepo.reorderColumns(
                checklistId, List.of(columns.get(0).id(), columns.get(1).id()));
    }

    @Test
    @Order(8)
    void replaceFilter() {
        var group = memberGroupRepo.create(station.id(), "Filter Group");
        var tag = userTagRepo.create(station.id(), "filter-tag");
        checklistRepo.replaceFilter(
                checklistId,
                List.of(StationUserType.MEMBER),
                List.of(group.id()),
                List.of(tag.id()),
                List.of(member.id()));
        var rows = checklistRepo.findFilterRows(checklistId);
        assertEquals(4, rows.size());
        checklistRepo.replaceFilter(
                checklistId, List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(member.id()));
        assertEquals(2, checklistRepo.findFilterRows(checklistId).size());
    }

    @Test
    @Order(9)
    void createEntry() {
        var entry = checklistRepo.createEntry(checklistId, member.id());
        entryId = entry.id();
        assertEquals(member.id(), entry.memberId());
        assertNull(entry.deletedAt());
    }

    @Test
    @Order(10)
    void findEntryByMember() {
        assertTrue(checklistRepo.findEntryByMember(checklistId, member.id()).isPresent());
        assertTrue(
                checklistRepo.findEntryByMember(checklistId, secondMember.id()).isEmpty());
    }

    @Test
    @Order(11)
    void upsertCellInsert() {
        var cell = checklistRepo.upsertCell(entryId, columnId, true, "Looked good", member.id());
        cellId = cell.id();
        assertTrue(cell.checked());
        assertEquals("Looked good", cell.note());
    }

    @Test
    @Order(12)
    void upsertCellUpdate() {
        var cell = checklistRepo.upsertCell(entryId, columnId, false, "Reverted", member.id());
        assertEquals(cellId, cell.id());
        assertFalse(cell.checked());
        assertEquals("Reverted", cell.note());
    }

    @Test
    @Order(13)
    void findCell() {
        assertTrue(checklistRepo.findCell(entryId, columnId).isPresent());
        assertTrue(checklistRepo.findCell(0, columnId).isEmpty());
    }

    @Test
    @Order(14)
    void appendAndFindNoteHistory() {
        checklistRepo.appendNoteHistory(cellId, null, "first", member.id());
        checklistRepo.appendNoteHistory(cellId, "first", "second", member.id());
        var history = checklistRepo.findNoteHistory(cellId);
        assertEquals(2, history.size());
        assertEquals("second", history.getFirst().newNote());
    }

    @Test
    @Order(15)
    void findCellsForChecklist() {
        assertEquals(1, checklistRepo.findCellsForChecklist(checklistId).size());
    }

    @Test
    @Order(16)
    void countCheckedCellsInColumn() {
        var entry2 = checklistRepo.createEntry(checklistId, secondMember.id());
        checklistRepo.upsertCell(entry2.id(), columnId, true, null, member.id());
        assertEquals(1, checklistRepo.countCheckedCellsInColumn(columnId));
    }

    @Test
    @Order(17)
    void bulkSetCheckedInsertAndUpdate() {
        var entries = checklistRepo.findEntries(checklistId, false);
        var ids = entries.stream().map(ChecklistEntry::id).toList();
        int updated = checklistRepo.bulkSetChecked(ids, columnId, true, member.id());
        assertTrue(updated >= 1);
        int unchanged = checklistRepo.bulkSetChecked(List.of(), columnId, true, member.id());
        assertEquals(0, unchanged);
        int cleared = checklistRepo.bulkSetChecked(ids, columnId, false, member.id());
        assertEquals(2, cleared);
    }

    @Test
    @Order(18)
    void softDeleteAndRestoreEntry() {
        checklistRepo.softDeleteEntry(entryId);
        var entry = checklistRepo.findEntry(entryId).orElseThrow();
        assertNotNull(entry.deletedAt());
        assertEquals(1, checklistRepo.findEntries(checklistId, false).size());
        assertEquals(2, checklistRepo.findEntries(checklistId, true).size());
        checklistRepo.restoreEntry(entryId);
        assertNull(checklistRepo.findEntry(entryId).orElseThrow().deletedAt());
    }

    @Test
    @Order(19)
    void findSummariesByStation() {
        var summaries = checklistRepo.findSummariesByStation(station.id());
        assertEquals(1, summaries.size());
        assertEquals("Safety Briefing", summaries.getFirst().name());
        assertEquals(2, summaries.getFirst().columnCount());
    }

    @Test
    @Order(98)
    void deleteColumn() {
        assertTrue(checklistRepo.deleteColumn(columnId));
        assertFalse(checklistRepo.deleteColumn(columnId));
    }

    @Test
    @Order(99)
    void deleteChecklist() {
        assertTrue(checklistRepo.delete(checklistId));
        assertFalse(checklistRepo.delete(checklistId));
    }
}
