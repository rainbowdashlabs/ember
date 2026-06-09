/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ProcedureAssigned;
import dev.chojo.ember.event.events.ProcedureItemChecked;
import dev.chojo.ember.event.events.ProcedureReopened;
import dev.chojo.ember.event.events.ProcedureResolved;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcedureServiceTest {

    private ProcedureRepository repository;
    private DomainEventBus eventBus;
    private ProcedureService service;

    private static final int STATION_ID = 1;
    private static final int MEMBER_ID = 10;
    private static final int MEMBER_ID_2 = 20;
    private static final int TEMPLATE_ID = 100;
    private static final int PROCEDURE_ID = 200;
    private static final int ITEM_ID = 300;

    @BeforeEach
    void setup() {
        repository = mock(ProcedureRepository.class);
        eventBus = mock(DomainEventBus.class);
        service = new ProcedureService(repository, eventBus);
    }

    // ── Template delegates ──

    @Test
    void findTemplatesByStation() {
        var templates = List.of(new ProcedureTemplate(1, STATION_ID, "T1", "D1", false, MEMBER_ID, Instant.now()));
        when(repository.findTemplatesByStation(STATION_ID, false)).thenReturn(templates);
        assertEquals(templates, service.findTemplatesByStation(STATION_ID, false));
    }

    @Test
    void findTemplatesByStationIncludeArchived() {
        when(repository.findTemplatesByStation(STATION_ID, true)).thenReturn(List.of());
        assertEquals(List.of(), service.findTemplatesByStation(STATION_ID, true));
    }

    @Test
    void findTemplateById() {
        var t = new ProcedureTemplate(TEMPLATE_ID, STATION_ID, "T", "D", false, MEMBER_ID, Instant.now());
        when(repository.findTemplateById(TEMPLATE_ID)).thenReturn(Optional.of(t));
        assertTrue(service.findTemplateById(TEMPLATE_ID).isPresent());
    }

    @Test
    void findTemplateByIdNotFound() {
        when(repository.findTemplateById(99)).thenReturn(Optional.empty());
        assertTrue(service.findTemplateById(99).isEmpty());
    }

    @Test
    void createTemplate() {
        var t = new ProcedureTemplate(TEMPLATE_ID, STATION_ID, "T", "D", false, MEMBER_ID, Instant.now());
        when(repository.createTemplate(STATION_ID, "T", "D", MEMBER_ID)).thenReturn(t);
        assertEquals(t, service.createTemplate(STATION_ID, "T", "D", MEMBER_ID));
    }

    @Test
    void updateTemplateSuccess() {
        var t = new ProcedureTemplate(TEMPLATE_ID, STATION_ID, "New", "New", false, MEMBER_ID, Instant.now());
        when(repository.updateTemplate(TEMPLATE_ID, "New", "New")).thenReturn(true);
        when(repository.findTemplateById(TEMPLATE_ID)).thenReturn(Optional.of(t));
        var result = service.updateTemplate(TEMPLATE_ID, "New", "New");
        assertTrue(result.isPresent());
        assertEquals("New", result.get().name());
    }

    @Test
    void updateTemplateNotFound() {
        when(repository.updateTemplate(99, "X", "X")).thenReturn(false);
        assertTrue(service.updateTemplate(99, "X", "X").isEmpty());
    }

    @Test
    void archiveTemplate() {
        when(repository.archiveTemplate(TEMPLATE_ID)).thenReturn(true);
        assertTrue(service.archiveTemplate(TEMPLATE_ID));
    }

    @Test
    void findTemplateItems() {
        when(repository.findTemplateItems(TEMPLATE_ID)).thenReturn(List.of());
        assertEquals(List.of(), service.findTemplateItems(TEMPLATE_ID));
    }

    @Test
    void createTemplateItem() {
        var item = new ProcedureTemplateItem(1, TEMPLATE_ID, "Step", "Desc", true, true, 1);
        when(repository.createTemplateItem(TEMPLATE_ID, "Step", "Desc", true, true, 1))
                .thenReturn(item);
        assertEquals(item, service.createTemplateItem(TEMPLATE_ID, "Step", "Desc", true, true, 1));
    }

    @Test
    void updateTemplateItem() {
        when(repository.updateTemplateItem(1, "S", "D", true, true, 1)).thenReturn(true);
        assertTrue(service.updateTemplateItem(1, "S", "D", true, true, 1));
    }

    @Test
    void deleteTemplateItem() {
        when(repository.deleteTemplateItem(1)).thenReturn(true);
        assertTrue(service.deleteTemplateItem(1));
    }

    @Test
    void findTemplateItemDependencies() {
        when(repository.findTemplateItemDependencies(TEMPLATE_ID)).thenReturn(List.of());
        assertEquals(List.of(), service.findTemplateItemDependencies(TEMPLATE_ID));
    }

    @Test
    void setTemplateItemDependencies() {
        var deps = List.of(new int[] {1, 2});
        service.setTemplateItemDependencies(TEMPLATE_ID, deps);
        verify(repository).setTemplateItemDependencies(TEMPLATE_ID, deps);
    }

    // ── Procedure queries ──

    @Test
    void findProceduresByStation() {
        when(repository.findProceduresByStation(STATION_ID, null)).thenReturn(List.of());
        assertEquals(List.of(), service.findProceduresByStation(STATION_ID, null));
    }

    @Test
    void findProceduresByStationWithStatus() {
        when(repository.findProceduresByStation(STATION_ID, ProcedureStatus.OPEN))
                .thenReturn(List.of());
        assertEquals(List.of(), service.findProceduresByStation(STATION_ID, ProcedureStatus.OPEN));
    }

    @Test
    void findProceduresByAssignee() {
        when(repository.findProceduresByAssignee(STATION_ID, MEMBER_ID, ProcedureStatus.OPEN, false))
                .thenReturn(List.of());
        assertEquals(List.of(), service.findProceduresByAssignee(STATION_ID, MEMBER_ID, ProcedureStatus.OPEN, false));
    }

    @Test
    void findProcedureById() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.empty());
        assertTrue(service.findProcedureById(PROCEDURE_ID).isEmpty());
    }

    // ── Create procedure without template ──

    @Test
    void createProcedureWithoutTemplate() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.createProcedure(STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null))
                .thenReturn(proc);

        var result =
                service.createProcedure(STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null, List.of(MEMBER_ID_2));

        assertEquals(proc, result);
        verify(repository).addAssignee(PROCEDURE_ID, MEMBER_ID_2);
        verify(repository, never()).findTemplateItems(anyInt());
        verify(eventBus).publish(any(ProcedureAssigned.class));
    }

    @Test
    void createProcedureNoAssignees() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.createProcedure(STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null))
                .thenReturn(proc);

        service.createProcedure(STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null, List.of());

        verify(eventBus, never()).publish(any());
    }

    // ── Create procedure with template (snapshot) ──

    @Test
    void createProcedureWithTemplateSnapshots() {
        var proc = procedure(PROCEDURE_ID, TEMPLATE_ID);
        when(repository.createProcedure(STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null))
                .thenReturn(proc);

        var ti1 = new ProcedureTemplateItem(10, TEMPLATE_ID, "S1", "D1", true, true, 1);
        var ti2 = new ProcedureTemplateItem(11, TEMPLATE_ID, "S2", "D2", true, true, 2);
        when(repository.findTemplateItems(TEMPLATE_ID)).thenReturn(List.of(ti1, ti2));
        when(repository.findTemplateItemDependencies(TEMPLATE_ID)).thenReturn(List.of(new int[] {11, 10}));

        var pi1 = procedureItem(501, false);
        var pi2 = procedureItem(502, false);
        when(repository.snapshotTemplateItem(PROCEDURE_ID, ti1)).thenReturn(pi1);
        when(repository.snapshotTemplateItem(PROCEDURE_ID, ti2)).thenReturn(pi2);

        var result = service.createProcedure(
                STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null, List.of(MEMBER_ID));

        assertEquals(proc, result);
        verify(repository).snapshotTemplateItem(PROCEDURE_ID, ti1);
        verify(repository).snapshotTemplateItem(PROCEDURE_ID, ti2);
        verify(repository).addItemDependency(502, 501);
        verify(eventBus).publish(any(ProcedureAssigned.class));
    }

    @Test
    void createProcedureSnapshotSkipsUnmappedDependencies() {
        var proc = procedure(PROCEDURE_ID, TEMPLATE_ID);
        when(repository.createProcedure(STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null))
                .thenReturn(proc);

        var ti1 = new ProcedureTemplateItem(10, TEMPLATE_ID, "S1", "D1", true, true, 1);
        when(repository.findTemplateItems(TEMPLATE_ID)).thenReturn(List.of(ti1));
        // Dependency references a template item ID that doesn't exist in the snapshot
        when(repository.findTemplateItemDependencies(TEMPLATE_ID)).thenReturn(List.of(new int[] {10, 99}));

        var pi1 = procedureItem(501, false);
        when(repository.snapshotTemplateItem(PROCEDURE_ID, ti1)).thenReturn(pi1);

        service.createProcedure(STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null, List.of());

        verify(repository, never()).addItemDependency(anyInt(), anyInt());
    }

    // ── Update procedure ──

    @Test
    void updateProcedure() {
        when(repository.updateProcedure(PROCEDURE_ID, "N", "D", true, null)).thenReturn(true);
        assertTrue(service.updateProcedure(PROCEDURE_ID, "N", "D", true, null));
    }

    // ── Resolve / Reopen ──

    @Test
    void resolveProcedureSuccess() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.resolveProcedure(PROCEDURE_ID)).thenReturn(true);
        when(repository.findAssigneeIds(PROCEDURE_ID)).thenReturn(List.of(MEMBER_ID));

        assertTrue(service.resolveProcedure(PROCEDURE_ID, MEMBER_ID));
        verify(eventBus).publish(any(ProcedureResolved.class));
    }

    @Test
    void resolveProcedureNotFound() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.empty());
        assertFalse(service.resolveProcedure(PROCEDURE_ID, MEMBER_ID));
        verify(eventBus, never()).publish(any());
    }

    @Test
    void resolveProcedureAlreadyResolved() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.resolveProcedure(PROCEDURE_ID)).thenReturn(false);
        assertFalse(service.resolveProcedure(PROCEDURE_ID, MEMBER_ID));
        verify(eventBus, never()).publish(any());
    }

    @Test
    void reopenProcedureSuccess() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.reopenProcedure(PROCEDURE_ID)).thenReturn(true);
        when(repository.findAssigneeIds(PROCEDURE_ID)).thenReturn(List.of(MEMBER_ID));

        assertTrue(service.reopenProcedure(PROCEDURE_ID, MEMBER_ID));
        verify(eventBus).publish(any(ProcedureReopened.class));
    }

    @Test
    void reopenProcedureNotFound() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.empty());
        assertFalse(service.reopenProcedure(PROCEDURE_ID, MEMBER_ID));
        verify(eventBus, never()).publish(any());
    }

    @Test
    void reopenProcedureAlreadyOpen() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.reopenProcedure(PROCEDURE_ID)).thenReturn(false);
        assertFalse(service.reopenProcedure(PROCEDURE_ID, MEMBER_ID));
        verify(eventBus, never()).publish(any());
    }

    // ── Delete procedure ──

    @Test
    void deleteProcedure() {
        when(repository.deleteProcedure(PROCEDURE_ID)).thenReturn(true);
        assertTrue(service.deleteProcedure(PROCEDURE_ID));
    }

    // ── Assignees ──

    @Test
    void findAssigneeIds() {
        when(repository.findAssigneeIds(PROCEDURE_ID)).thenReturn(List.of(MEMBER_ID));
        assertEquals(List.of(MEMBER_ID), service.findAssigneeIds(PROCEDURE_ID));
    }

    @Test
    void addAssigneesDeduplication() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.findAssigneeIds(PROCEDURE_ID)).thenReturn(List.of(MEMBER_ID));

        service.addAssignees(PROCEDURE_ID, List.of(MEMBER_ID, MEMBER_ID_2), MEMBER_ID);

        // Only MEMBER_ID_2 should be added (MEMBER_ID already exists)
        verify(repository).addAssignee(PROCEDURE_ID, MEMBER_ID_2);
        verify(repository, never()).addAssignee(PROCEDURE_ID, MEMBER_ID);
        verify(eventBus).publish(any(ProcedureAssigned.class));
    }

    @Test
    void addAssigneesAllExisting() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.findAssigneeIds(PROCEDURE_ID)).thenReturn(List.of(MEMBER_ID));

        service.addAssignees(PROCEDURE_ID, List.of(MEMBER_ID), MEMBER_ID);

        verify(eventBus, never()).publish(any());
    }

    @Test
    void addAssigneesProcedureNotFound() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.empty());
        service.addAssignees(PROCEDURE_ID, List.of(MEMBER_ID), MEMBER_ID);
        verify(repository, never()).addAssignee(anyInt(), anyInt());
        verify(eventBus, never()).publish(any());
    }

    @Test
    void removeAssignee() {
        when(repository.removeAssignee(PROCEDURE_ID, MEMBER_ID)).thenReturn(true);
        assertTrue(service.removeAssignee(PROCEDURE_ID, MEMBER_ID));
    }

    // ── Items ──

    @Test
    void findItems() {
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of());
        assertEquals(List.of(), service.findItems(PROCEDURE_ID));
    }

    @Test
    void createItem() {
        var item = procedureItem(ITEM_ID, false);
        when(repository.createItem(PROCEDURE_ID, "T", "D", true, true, 1)).thenReturn(item);
        assertEquals(item, service.createItem(PROCEDURE_ID, "T", "D", true, true, 1));
    }

    @Test
    void updateItem() {
        when(repository.updateItem(ITEM_ID, "T", "D", true, true, 1)).thenReturn(true);
        assertTrue(service.updateItem(ITEM_ID, "T", "D", true, true, 1));
    }

    @Test
    void deleteItem() {
        when(repository.deleteItem(ITEM_ID)).thenReturn(true);
        assertTrue(service.deleteItem(ITEM_ID));
    }

    // ── Check item ──

    @Test
    void checkItemSuccess() {
        var item = procedureItem(ITEM_ID, false);
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(item));
        when(repository.checkItem(ITEM_ID, MEMBER_ID)).thenReturn(true);

        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.findAssigneeIds(PROCEDURE_ID)).thenReturn(List.of(MEMBER_ID));

        assertTrue(service.checkItem(ITEM_ID, MEMBER_ID));
        verify(eventBus).publish(any(ProcedureItemChecked.class));
    }

    @Test
    void checkItemNotFound() {
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.empty());
        assertFalse(service.checkItem(ITEM_ID, MEMBER_ID));
        verify(eventBus, never()).publish(any());
    }

    @Test
    void checkItemDependencyNotMet() {
        var item = procedureItem(ITEM_ID, false);
        var depItem = procedureItem(ITEM_ID + 1, false); // dependency is unchecked
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of(new int[] {ITEM_ID, ITEM_ID + 1}));
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(item, depItem));

        assertFalse(service.checkItem(ITEM_ID, MEMBER_ID));
        verify(repository, never()).checkItem(anyInt(), anyInt());
    }

    @Test
    void checkItemDependencyMet() {
        var item = procedureItem(ITEM_ID, false);
        var depItem = new ProcedureItem(
                ITEM_ID + 1, PROCEDURE_ID, "Dep", "D", null, true, true, 1, true, Instant.now(), MEMBER_ID);
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of(new int[] {ITEM_ID, ITEM_ID + 1}));
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(item, depItem));
        when(repository.checkItem(ITEM_ID, MEMBER_ID)).thenReturn(true);

        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.findAssigneeIds(PROCEDURE_ID)).thenReturn(List.of(MEMBER_ID));

        assertTrue(service.checkItem(ITEM_ID, MEMBER_ID));
    }

    @Test
    void checkItemRepoReturnsFalse() {
        var item = procedureItem(ITEM_ID, false);
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(item));
        when(repository.checkItem(ITEM_ID, MEMBER_ID)).thenReturn(false);

        assertFalse(service.checkItem(ITEM_ID, MEMBER_ID));
        verify(eventBus, never()).publish(any());
    }

    @Test
    void checkItemProcedureNotFoundAfterCheck() {
        var item = procedureItem(ITEM_ID, false);
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(item));
        when(repository.checkItem(ITEM_ID, MEMBER_ID)).thenReturn(true);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.empty());

        assertTrue(service.checkItem(ITEM_ID, MEMBER_ID));
        verify(eventBus, never()).publish(any());
    }

    // ── Uncheck item ──

    @Test
    void uncheckItem() {
        when(repository.uncheckItem(ITEM_ID)).thenReturn(true);
        assertTrue(service.uncheckItem(ITEM_ID));
    }

    // ── Update item note ──

    @Test
    void updateItemNote() {
        when(repository.updateItemNote(ITEM_ID, "note")).thenReturn(true);
        assertTrue(service.updateItemNote(ITEM_ID, "note"));
    }

    // ── Item dependencies ──

    @Test
    void findItemDependencies() {
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        assertEquals(List.of(), service.findItemDependencies(PROCEDURE_ID));
    }

    // ── Sidebar counts ──

    @Test
    void countOpenByAssigneeWithAvailableItems() {
        when(repository.countOpenByAssigneeWithAvailableItems(STATION_ID, MEMBER_ID))
                .thenReturn(5);
        assertEquals(5, service.countOpenByAssigneeWithAvailableItems(STATION_ID, MEMBER_ID));
    }

    @Test
    void countOpenByStation() {
        when(repository.countOpenByStation(STATION_ID)).thenReturn(3);
        assertEquals(3, service.countOpenByStation(STATION_ID));
    }

    // ── Helpers ──

    private Procedure procedure(int id, Integer templateId) {
        return new Procedure(
                id,
                STATION_ID,
                templateId,
                "Task",
                "Desc",
                true,
                ProcedureStatus.OPEN,
                MEMBER_ID,
                null,
                Instant.now(),
                null);
    }

    private ProcedureItem procedureItem(int id, boolean checked) {
        return new ProcedureItem(
                id,
                PROCEDURE_ID,
                "Item",
                "Desc",
                null,
                true,
                true,
                1,
                checked,
                checked ? Instant.now() : null,
                checked ? MEMBER_ID : null);
    }
}
