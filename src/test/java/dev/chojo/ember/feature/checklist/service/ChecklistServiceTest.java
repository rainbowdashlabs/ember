/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.checklist.entity.ChecklistColumn;
import dev.chojo.ember.feature.checklist.entity.ChecklistEntry;
import dev.chojo.ember.feature.checklist.service.ChecklistService.ColumnSpec;
import dev.chojo.ember.feature.checklist.service.ChecklistService.FilterSpec;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecklistServiceTest extends RepositoryTestBase {
    private static ChecklistService service;
    private static Station station;
    private static StationMember managerMember;
    private static StationMember other;

    @BeforeAll
    static void setupService() {
        service = new ChecklistService(
                checklistRepo, stationMemberRepo, memberGroupRepo, userTagRepo, eventRegistrationRepo);
        station = stationRepo.create("Service Station");
        Account account = accountRepo.create("svc@test.com", "Svc", "Member");
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

        var entry = service.findEntries(checklist.id(), false).getFirst();
        var column = service.findColumns(checklist.id()).getFirst();

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
                column.id(), allEntries.stream().map(ChecklistEntry::id).toList(), true, managerMember.id());
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
        var reversedIds =
                columnsForReorder.stream().map(ChecklistColumn::id).collect(Collectors.toCollection(ArrayList::new));
        Collections.reverse(reversedIds);
        service.reorderColumns(checklist.id(), reversedIds);
        var afterReorder = service.findColumns(checklist.id());
        assertEquals(reversedIds.getFirst(), afterReorder.getFirst().id());
        try {
            service.reorderColumns(checklist.id(), List.of(reversedIds.getFirst()));
            throw new AssertionError("expected reject");
        } catch (IllegalArgumentException expected) {
        }

        assertTrue(service.deleteColumn(addedColumn.id()));

        assertTrue(service.findById(checklist.id()).isPresent());
        var allCells = service.findCells(checklist.id());
        assertFalse(allCells.isEmpty());

        var resolvedEmpty =
                service.resolveMembership(service.findById(checklist.id()).orElseThrow());
        assertFalse(resolvedEmpty.following());
        assertTrue(resolvedEmpty.memberIds().isEmpty());

        assertTrue(service.delete(checklist.id()));
        assertFalse(service.delete(checklist.id()));
    }

    /**
     * The whole life of a list that follows an evening: it starts with the people who had already
     * taken a place, a late sign-up arrives on the next refresh, somebody who cancels stays on it
     * and is marked, a row taken off by hand never comes back, and the appointment being deleted
     * leaves the list standing with everything on it.
     */
    @Test
    void followsOneOccurrenceOfAnAppointment() {
        var account3 = accountRepo.create("svc3@test.com", "Svc", "Three");
        var late = stationMemberRepo.create(station.id(), account3.id());
        var event = createEvent("Dienstabend");
        LocalDate evening = LocalDate.of(2026, 3, 3);
        LocalDate otherEvening = LocalDate.of(2026, 3, 10);

        eventRegistrationRepo.create(event.id(), managerMember.id(), evening, RegistrationStatus.ACCEPTED, null);
        eventRegistrationRepo.create(event.id(), other.id(), evening, RegistrationStatus.PENDING, null);
        eventRegistrationRepo.create(event.id(), late.id(), otherEvening, RegistrationStatus.ACCEPTED, null);

        var checklist = service.create(
                station.id(),
                "Dienstabend 03.03.",
                "",
                RestrictionMode.AND,
                List.of(new ColumnSpec("Zettel abgegeben", "")),
                FilterSpec.empty(),
                new ChecklistService.OccurrenceSpec(event.id(), evening),
                managerMember.id());

        // Only the one evening counts: the accepted sign-up on the other Tuesday is somebody else's.
        assertEquals(
                List.of(managerMember.id()),
                service.findEntries(checklist.id(), false).stream()
                        .map(ChecklistEntry::memberId)
                        .toList());
        assertTrue(service.findFilterRows(checklist.id()).isEmpty());
        assertTrue(service.findById(checklist.id()).orElseThrow().followsEvent());

        // A late sign-up arrives, and only a refresh brings it in.
        eventRegistrationRepo.create(event.id(), late.id(), evening, RegistrationStatus.ACCEPTED, null);
        var refreshed = service.refresh(checklist.id());
        assertEquals(1, refreshed.added());
        assertEquals(1, refreshed.alreadyPresent());

        // Somebody cancels: their row stays and the list knows they no longer match.
        eventRegistrationRepo.create(event.id(), late.id(), evening, RegistrationStatus.DECLINED, null);
        var afterCancel =
                service.resolveMembership(service.findById(checklist.id()).orElseThrow());
        assertTrue(afterCancel.following());
        assertFalse(afterCancel.memberIds().contains(late.id()));
        assertTrue(service.findEntries(checklist.id(), false).stream().anyMatch(e -> e.memberId() == late.id()));

        // A row taken off by hand stays off, however often refresh runs.
        var managerEntry = service.findEntries(checklist.id(), false).stream()
                .filter(e -> e.memberId() == managerMember.id())
                .findFirst()
                .orElseThrow();
        service.softDeleteEntry(managerEntry.id());
        var afterRemoval = service.refresh(checklist.id());
        assertEquals(0, afterRemoval.added());
        assertTrue(
                service.findEntries(checklist.id(), false).stream().noneMatch(e -> e.memberId() == managerMember.id()));

        // The appointment goes away: the reference is cleared, the rows are not.
        int rowsBefore = service.findEntries(checklist.id(), true).size();
        assertTrue(eventRepo.delete(event.id()));
        var orphaned = service.findById(checklist.id()).orElseThrow();
        assertFalse(orphaned.followsEvent());
        assertNull(orphaned.sourceEventId());
        assertEquals(rowsBefore, service.findEntries(checklist.id(), true).size());
        var stopped = service.resolveMembership(orphaned);
        assertFalse(stopped.following());

        assertTrue(service.delete(checklist.id()));
    }

    /**
     * A list follows one thing at a time, and saying so is what switching between them means.
     */
    @Test
    void followingAndFilteringReplaceEachOther() {
        var event = createEvent("Wechsel");
        LocalDate evening = LocalDate.of(2026, 4, 7);
        eventRegistrationRepo.create(event.id(), other.id(), evening, RegistrationStatus.ACCEPTED, null);

        var checklist = service.create(
                station.id(),
                "Wechselliste",
                "",
                RestrictionMode.OR,
                List.of(new ColumnSpec("Schritt", "")),
                new FilterSpec(List.of(), List.of(), List.of(), List.of(managerMember.id())),
                managerMember.id());
        assertFalse(service.findFilterRows(checklist.id()).isEmpty());

        var following =
                service.followOccurrence(checklist.id(), new ChecklistService.OccurrenceSpec(event.id(), evening));
        assertTrue(following.followsEvent());
        assertEquals(evening, following.sourceEventDate());
        assertTrue(service.findFilterRows(checklist.id()).isEmpty());

        var backToFilter = service.update(
                checklist.id(),
                "Wechselliste",
                "",
                RestrictionMode.OR,
                new FilterSpec(List.of(), List.of(), List.of(), List.of(managerMember.id())));
        assertFalse(backToFilter.followsEvent());
        assertNull(backToFilter.sourceEventDate());
        assertFalse(service.findFilterRows(checklist.id()).isEmpty());

        assertTrue(service.delete(checklist.id()));
        eventRepo.delete(event.id());
    }

    private static StationEvent createEvent(String name) {
        return eventRepo.create(
                station.id(),
                name,
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                true,
                null,
                true,
                null,
                null,
                null,
                null,
                null);
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
