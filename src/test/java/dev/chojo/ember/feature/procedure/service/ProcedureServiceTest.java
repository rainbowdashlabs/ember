/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.ProcedureRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ProcedureAssigned;
import dev.chojo.ember.event.events.ProcedureDocumentWithdrawn;
import dev.chojo.ember.event.events.ProcedureItemChecked;
import dev.chojo.ember.event.events.ProcedureReopened;
import dev.chojo.ember.event.events.ProcedureResolved;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureStepKind;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.feature.procedure.entity.StepKindChoice;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcedureServiceTest {

    private ProcedureRepository repository;
    private DomainEventBus eventBus;
    private RestrictionService restrictions;
    private DocumentStepSigning signing;
    private ProcedureService service;

    private static final int STATION_ID = 1;
    private static final int MEMBER_ID = 10;
    private static final int MEMBER_ID_2 = 20;
    private static final int TEMPLATE_ID = 100;
    private static final int PROCEDURE_ID = 200;
    private static final int ITEM_ID = 300;
    private static final int EVENT_ID = 400;
    private static final int SUITABLE_DOCUMENT = 500;
    private static final int ABOUT_ID = 30;
    private static final int OTHER_ABOUT_ID = 31;
    private static final int GENERATION_ID = 900;

    @BeforeEach
    void setup() {
        repository = mock(ProcedureRepository.class);
        eventBus = mock(DomainEventBus.class);
        restrictions = mock(RestrictionService.class);
        when(restrictions.audienceOf(any(), anyInt())).thenReturn(RestrictionAudience.empty());
        when(restrictions.ofStation(anyInt(), any())).thenReturn(true);
        signing = mock(DocumentStepSigning.class);
        service = new ProcedureService(
                repository,
                eventBus,
                restrictions,
                new SuitableDocuments(template -> template == SUITABLE_DOCUMENT),
                signing);
    }

    private static RestrictionAudience named(Integer... memberIds) {
        return new RestrictionAudience(List.of(), List.of(), List.of(), List.of(memberIds), RestrictionMode.AND);
    }

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
        when(repository.createTemplateItem(TEMPLATE_ID, "Step", "Desc", true, true, 1, StepKindChoice.CHECK))
                .thenReturn(item);
        assertEquals(
                item, service.createTemplateItem(TEMPLATE_ID, "Step", "Desc", true, true, 1, StepKindChoice.CHECK));
    }

    @Test
    void updateTemplateItem() {
        when(repository.updateTemplateItem(1, "S", "D", true, true, 1)).thenReturn(true);
        assertTrue(service.updateTemplateItem(1, "S", "D", true, true, 1));
    }

    @Test
    void aRequestWithoutAKindLeavesTheStepAsItIs() {
        var current = StepKindChoice.document(7);
        assertEquals(Optional.empty(), service.kindChange(STATION_ID, current, null, null, false));
        assertEquals(
                Optional.empty(),
                service.kindChange(STATION_ID, current, ProcedureStepKind.DOCUMENT, 7, false),
                "an unchanged step is not checked again, even where its template is no longer suitable");
        assertEquals(
                Optional.empty(),
                service.kindChange(STATION_ID, StepKindChoice.CHECK, ProcedureStepKind.CHECK, null, false));
    }

    @Test
    void aStepBecomesOneTickedByHand() {
        assertEquals(
                Optional.of(StepKindChoice.CHECK),
                service.kindChange(STATION_ID, StepKindChoice.document(7), ProcedureStepKind.CHECK, 7, false));
    }

    @Test
    void aDocumentStepNamesASuitableDocumentItsCallerMayGenerate() {
        assertEquals(
                Optional.of(StepKindChoice.document(SUITABLE_DOCUMENT)),
                service.kindChange(
                        STATION_ID, StepKindChoice.CHECK, ProcedureStepKind.DOCUMENT, SUITABLE_DOCUMENT, true));
        assertEquals(
                ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_MISSING,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.kindChange(
                                        STATION_ID, StepKindChoice.CHECK, ProcedureStepKind.DOCUMENT, null, true))
                        .refusal());
        assertEquals(
                ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_NOT_YOURS,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.kindChange(
                                        STATION_ID,
                                        StepKindChoice.CHECK,
                                        ProcedureStepKind.DOCUMENT,
                                        SUITABLE_DOCUMENT,
                                        false))
                        .refusal());
        assertEquals(
                ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_UNSUITABLE,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.kindChange(
                                        STATION_ID, StepKindChoice.CHECK, ProcedureStepKind.DOCUMENT, 7, true))
                        .refusal());
    }

    @Test
    void aStepKeepsItsDocumentOnceItIsMade() {
        when(repository.setItemKind(ITEM_ID, StepKindChoice.CHECK)).thenReturn(false);
        assertEquals(
                ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_ALREADY_MADE,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.setStepKind(documentStep(SUITABLE_DOCUMENT), StepKindChoice.CHECK))
                        .refusal());
    }

    @Test
    void theDocumentsOfStepsAreNamedOnceEach() {
        var ids = new ArrayList<Integer>(List.of(SUITABLE_DOCUMENT, SUITABLE_DOCUMENT));
        ids.add(null);
        assertEquals(
                List.of(new DocumentStepTemplates.StepDocument(SUITABLE_DOCUMENT, "Dokument " + SUITABLE_DOCUMENT)),
                service.stepDocuments(STATION_ID, ids));
    }

    private static ProcedureItem documentStep(int documentTemplateId) {
        return new ProcedureItem(
                ITEM_ID,
                PROCEDURE_ID,
                "Sign",
                null,
                null,
                true,
                true,
                1,
                false,
                null,
                null,
                ProcedureStepKind.DOCUMENT,
                documentTemplateId,
                null);
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
        when(repository.findTemplateItems(TEMPLATE_ID))
                .thenReturn(List.of(
                        new ProcedureTemplateItem(1, TEMPLATE_ID, "A", null, true, true, 0),
                        new ProcedureTemplateItem(2, TEMPLATE_ID, "B", null, true, true, 1)));
        var deps = List.of(new int[] {1, 2});
        service.setTemplateItemDependencies(TEMPLATE_ID, deps);
        verify(repository).setTemplateItemDependencies(TEMPLATE_ID, deps);
    }

    @Test
    void findTemplateItemInKnowsOnlyTheStepsOfThatTemplate() {
        var step = new ProcedureTemplateItem(1, TEMPLATE_ID, "A", null, true, true, 0);
        when(repository.findTemplateItems(TEMPLATE_ID)).thenReturn(List.of(step));
        assertEquals(Optional.of(step), service.findTemplateItemIn(TEMPLATE_ID, 1));
        assertTrue(service.findTemplateItemIn(TEMPLATE_ID, 2).isEmpty());
    }

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
    void findProceduresRunBy() {
        when(repository.findProceduresRunBy(STATION_ID, MEMBER_ID, ProcedureStatus.OPEN, false))
                .thenReturn(List.of());
        assertEquals(List.of(), service.findProceduresRunBy(STATION_ID, MEMBER_ID, ProcedureStatus.OPEN, false));
    }

    @Test
    void findProcedureById() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.empty());
        assertTrue(service.findProcedureById(PROCEDURE_ID).isEmpty());
    }

    @Test
    void createProcedureWithoutTemplate() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.createProcedure(STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null, null, null, null))
                .thenReturn(proc);

        var result = service.createProcedure(
                STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null, named(MEMBER_ID_2), null, null, null);

        assertEquals(proc, result);
        verify(restrictions).setAudience(RestrictionType.PROCEDURE, PROCEDURE_ID, named(MEMBER_ID_2));
        verify(repository, never()).findTemplateItems(anyInt());
        verify(eventBus).publish(any(ProcedureAssigned.class));
    }

    @Test
    void createProcedureNoAssignees() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.createProcedure(STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null, null, null, null))
                .thenReturn(proc);

        service.createProcedure(STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null, null, null, null, null);

        verify(eventBus, never()).publish(any());
    }

    /**
     * A procedure prepared for one occurrence carries the appointment and the date it was made for,
     * so that pressing the same button again can find it rather than make a second one.
     */
    @Test
    void createProcedureForOccurrenceRecordsIt() {
        var date = LocalDate.of(2026, 3, 17);
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.createProcedure(STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null, EVENT_ID, date, null))
                .thenReturn(proc);

        service.createProcedure(
                STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null, named(MEMBER_ID_2), EVENT_ID, date, null);

        verify(repository)
                .createProcedure(STATION_ID, null, "Task", "Desc", true, MEMBER_ID, null, EVENT_ID, date, null);
    }

    @Test
    void findProceduresByOccurrence() {
        var date = LocalDate.of(2026, 3, 17);
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProceduresByOccurrence(STATION_ID, EVENT_ID, date)).thenReturn(List.of(proc));

        assertEquals(List.of(proc), service.findProceduresByOccurrence(STATION_ID, EVENT_ID, date));
    }

    @Test
    void createProcedureWithTemplateSnapshots() {
        var proc = procedure(PROCEDURE_ID, TEMPLATE_ID);
        when(repository.createProcedure(
                        STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null, null, null, null))
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
                STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null, named(MEMBER_ID), null, null, null);

        assertEquals(proc, result);
        verify(repository).snapshotTemplateItem(PROCEDURE_ID, ti1);
        verify(repository).snapshotTemplateItem(PROCEDURE_ID, ti2);
        verify(repository).addItemDependency(502, 501);
        verify(eventBus).publish(any(ProcedureAssigned.class));
    }

    @Test
    void createProcedureSnapshotSkipsUnmappedDependencies() {
        var proc = procedure(PROCEDURE_ID, TEMPLATE_ID);
        when(repository.createProcedure(
                        STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null, null, null, null))
                .thenReturn(proc);

        var ti1 = new ProcedureTemplateItem(10, TEMPLATE_ID, "S1", "D1", true, true, 1);
        when(repository.findTemplateItems(TEMPLATE_ID)).thenReturn(List.of(ti1));
        int templateItemOutsideSnapshot = 99;
        when(repository.findTemplateItemDependencies(TEMPLATE_ID))
                .thenReturn(List.of(new int[] {10, templateItemOutsideSnapshot}));

        var pi1 = procedureItem(501, false);
        when(repository.snapshotTemplateItem(PROCEDURE_ID, ti1)).thenReturn(pi1);

        service.createProcedure(STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null, null, null, null, null);

        verify(repository, never()).addItemDependency(anyInt(), anyInt());
    }

    @Test
    void updateProcedure() {
        when(repository.updateProcedure(PROCEDURE_ID, "N", "D", true, null)).thenReturn(true);
        assertTrue(service.updateProcedure(PROCEDURE_ID, "N", "D", true, null));
    }

    @Test
    void resolveProcedureSuccess() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.resolveProcedure(PROCEDURE_ID, MEMBER_ID)).thenReturn(true);

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
        when(repository.resolveProcedure(PROCEDURE_ID, MEMBER_ID)).thenReturn(false);
        assertFalse(service.resolveProcedure(PROCEDURE_ID, MEMBER_ID));
        verify(eventBus, never()).publish(any());
    }

    @Test
    void reopenProcedureSuccess() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(repository.reopenProcedure(PROCEDURE_ID)).thenReturn(true);

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

    @Test
    void deleteProcedure() {
        when(repository.deleteProcedure(PROCEDURE_ID)).thenReturn(true);
        assertTrue(service.deleteProcedure(PROCEDURE_ID, MEMBER_ID));
    }

    /** A procedure started from a template without a party of its own takes the template's. */
    @Test
    void createProcedureTakesTheTemplatesParty() {
        var proc = procedure(PROCEDURE_ID, TEMPLATE_ID);
        when(repository.createProcedure(
                        STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null, null, null, null))
                .thenReturn(proc);
        when(repository.findTemplateItems(TEMPLATE_ID)).thenReturn(List.of());
        when(repository.findTemplateItemDependencies(TEMPLATE_ID)).thenReturn(List.of());
        when(restrictions.audienceOf(RestrictionType.PROCEDURE_TEMPLATE, TEMPLATE_ID))
                .thenReturn(named(MEMBER_ID_2));

        service.createProcedure(STATION_ID, TEMPLATE_ID, "Task", "Desc", true, MEMBER_ID, null, null, null, null, null);

        verify(restrictions).setAudience(RestrictionType.PROCEDURE, PROCEDURE_ID, named(MEMBER_ID_2));
        verify(eventBus).publish(any(ProcedureAssigned.class));
    }

    /** Only the members named for the first time are told the procedure was handed to them. */
    @Test
    void setPartyTellsOnlyTheNewlyNamed() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(restrictions.audienceOf(RestrictionType.PROCEDURE, PROCEDURE_ID)).thenReturn(named(MEMBER_ID));
        var captor = ArgumentCaptor.forClass(ProcedureAssigned.class);

        service.setParty(PROCEDURE_ID, named(MEMBER_ID, MEMBER_ID_2), MEMBER_ID);

        verify(restrictions).setAudience(RestrictionType.PROCEDURE, PROCEDURE_ID, named(MEMBER_ID, MEMBER_ID_2));
        verify(eventBus).publish(captor.capture());
        assertEquals(List.of(MEMBER_ID_2), captor.getValue().partyMemberIds());
    }

    @Test
    void setPartyTellsNobodyWhereNobodyIsNew() {
        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));
        when(restrictions.audienceOf(RestrictionType.PROCEDURE, PROCEDURE_ID)).thenReturn(named(MEMBER_ID));

        service.setParty(PROCEDURE_ID, named(MEMBER_ID), MEMBER_ID);

        verify(eventBus, never()).publish(any());
    }

    /** A procedure with no party is run by nobody beyond its editors, whatever the restriction check says. */
    @Test
    void anEmptyPartyRunsNothing() {
        when(restrictions.hasRestrictions(RestrictionType.PROCEDURE, PROCEDURE_ID))
                .thenReturn(false);
        when(restrictions.includes(RestrictionType.PROCEDURE, PROCEDURE_ID, MEMBER_ID))
                .thenReturn(true);
        assertFalse(service.runBy(PROCEDURE_ID, MEMBER_ID));
        assertEquals(List.of(), service.partyMembers(STATION_ID, PROCEDURE_ID));

        when(restrictions.hasRestrictions(RestrictionType.PROCEDURE, PROCEDURE_ID))
                .thenReturn(true);
        assertTrue(service.runBy(PROCEDURE_ID, MEMBER_ID));
    }

    @Test
    void findItems() {
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of());
        assertEquals(List.of(), service.findItems(PROCEDURE_ID));
    }

    @Test
    void createItem() {
        var item = procedureItem(ITEM_ID, false);
        when(repository.createItem(PROCEDURE_ID, "T", "D", true, true, 1, StepKindChoice.CHECK))
                .thenReturn(item);
        assertEquals(item, service.createItem(PROCEDURE_ID, "T", "D", true, true, 1, StepKindChoice.CHECK));
    }

    @Test
    void updateItem() {
        when(repository.updateItem(ITEM_ID, "T", "D", true, true, 1)).thenReturn(true);
        assertTrue(service.updateItem(ITEM_ID, "T", "D", true, true, 1));
    }

    @Test
    void deleteItem() {
        when(repository.deleteItem(ITEM_ID)).thenReturn(true);
        assertTrue(service.deleteItem(procedureItem(ITEM_ID, false), MEMBER_ID));
        verify(signing, never()).withdraw(anyInt(), anyInt());
    }

    @Test
    void checkItemSuccess() {
        var item = procedureItem(ITEM_ID, false);
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(item));
        when(repository.checkItem(ITEM_ID, MEMBER_ID)).thenReturn(true);

        var proc = procedure(PROCEDURE_ID, null);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(proc));

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
        var uncheckedDependency = procedureItem(ITEM_ID + 1, false);
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(item));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of(new int[] {ITEM_ID, ITEM_ID + 1}));
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(item, uncheckedDependency));

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

    @Test
    void uncheckItem() {
        when(repository.uncheckItem(ITEM_ID)).thenReturn(true);
        assertTrue(service.uncheckItem(ITEM_ID));
    }

    @Test
    void updateItemNote() {
        when(repository.updateItemNote(ITEM_ID, "note")).thenReturn(true);
        assertTrue(service.updateItemNote(ITEM_ID, "note"));
    }

    @Test
    void findItemDependencies() {
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        assertEquals(List.of(), service.findItemDependencies(PROCEDURE_ID));
    }

    @Test
    void setItemDependenciesBetweenStepsOfTheProcedure() {
        when(repository.findItems(PROCEDURE_ID))
                .thenReturn(List.of(
                        new ProcedureItem(1, PROCEDURE_ID, "A", null, null, true, true, 0, false, null, null),
                        new ProcedureItem(2, PROCEDURE_ID, "B", null, null, true, true, 1, false, null, null)));
        var deps = List.of(new int[] {2, 1});
        service.setItemDependencies(PROCEDURE_ID, deps, MEMBER_ID);
        verify(repository).setItemDependencies(PROCEDURE_ID, deps);
    }

    @Test
    void findItemInKnowsOnlyTheStepsOfThatProcedure() {
        var step = new ProcedureItem(ITEM_ID, PROCEDURE_ID, "A", null, null, true, true, 0, false, null, null);
        when(repository.findItemById(ITEM_ID)).thenReturn(Optional.of(step));
        assertEquals(Optional.of(step), service.findItemIn(PROCEDURE_ID, ITEM_ID));
        assertTrue(service.findItemIn(PROCEDURE_ID + 1, ITEM_ID).isEmpty());
    }

    @Test
    void countOpenRunByWithAvailableItems() {
        when(repository.countOpenRunByWithAvailableItems(STATION_ID, MEMBER_ID)).thenReturn(5);
        assertEquals(5, service.countOpenRunByWithAvailableItems(STATION_ID, MEMBER_ID));
    }

    @Test
    void countOpenByStation() {
        when(repository.countOpenByStation(STATION_ID)).thenReturn(3);
        assertEquals(3, service.countOpenByStation(STATION_ID));
    }

    /** A document step with nothing to wait for is generated about the member, linked and asked for. */
    @Test
    void anUnlockedDocumentStepIsAskedFor() {
        unlockedStep();
        when(signing.mayAsk(STATION_ID, MEMBER_ID_2)).thenReturn(true);
        when(signing.generate(STATION_ID, SUITABLE_DOCUMENT, ABOUT_ID, MEMBER_ID_2))
                .thenReturn(GENERATION_ID);
        when(repository.linkGeneration(ITEM_ID, GENERATION_ID)).thenReturn(true);

        service.askUnlocked(PROCEDURE_ID, MEMBER_ID_2);

        verify(repository).linkGeneration(ITEM_ID, GENERATION_ID);
        verify(signing).request(GENERATION_ID, MEMBER_ID_2, "Aufnahme");
    }

    /**
     * Whoever changed the procedure asks where they generate documents, else whoever set it up where they
     * still do, else nobody and nothing is generated.
     */
    @Test
    void onlySomebodyWhoGeneratesDocumentsAsks() {
        unlockedStep();
        when(signing.mayAsk(STATION_ID, MEMBER_ID)).thenReturn(true);
        when(signing.generate(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(GENERATION_ID);
        when(repository.linkGeneration(ITEM_ID, GENERATION_ID)).thenReturn(true);

        service.askUnlocked(PROCEDURE_ID, MEMBER_ID_2);
        verify(signing).generate(STATION_ID, SUITABLE_DOCUMENT, ABOUT_ID, MEMBER_ID);

        when(signing.mayAsk(STATION_ID, MEMBER_ID)).thenReturn(false);
        service.askUnlocked(PROCEDURE_ID, MEMBER_ID_2);
        verify(signing, times(1)).generate(anyInt(), anyInt(), anyInt(), anyInt());
    }

    /** A step waiting for an unticked one, one already generated and one ticked are left alone. */
    @Test
    void onlyUnlockedStepsWithoutADocumentAreAskedFor() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));
        var first = new ProcedureItem(1, PROCEDURE_ID, "Packen", null, null, true, true, 0, false, null, null);
        when(repository.findItems(PROCEDURE_ID))
                .thenReturn(List.of(
                        first,
                        documentStep(2, SUITABLE_DOCUMENT, null, false),
                        documentStep(3, SUITABLE_DOCUMENT, 77, false),
                        documentStep(4, SUITABLE_DOCUMENT, null, true)));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.<int[]>of(new int[] {2, 1}));
        when(signing.mayAsk(anyInt(), anyInt())).thenReturn(true);

        service.askUnlocked(PROCEDURE_ID, MEMBER_ID);

        verify(signing, never()).generate(anyInt(), anyInt(), anyInt(), anyInt());
    }

    /** A procedure about nobody, or one resolved, generates nothing. */
    @Test
    void aProcedureAboutNobodyOrResolvedAsksForNothing() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(procedure(PROCEDURE_ID, null)));
        service.askUnlocked(PROCEDURE_ID, MEMBER_ID);

        var resolved = about(ABOUT_ID);
        when(repository.findProcedureById(PROCEDURE_ID))
                .thenReturn(Optional.of(new Procedure(
                        resolved.id(),
                        resolved.stationId(),
                        null,
                        resolved.name(),
                        null,
                        true,
                        ProcedureStatus.RESOLVED,
                        MEMBER_ID,
                        null,
                        Instant.now(),
                        Instant.now(),
                        null,
                        null,
                        ABOUT_ID,
                        null)));
        service.askUnlocked(PROCEDURE_ID, MEMBER_ID);

        verify(repository, never()).findItems(anyInt());
        verify(signing, never()).generate(anyInt(), anyInt(), anyInt(), anyInt());
    }

    /** A step that cannot be generated, for whatever reason, stays without a document; nothing fails. */
    @Test
    void aDocumentThatCannotBeMadeIsLeftForLater() {
        unlockedStep();
        when(signing.mayAsk(anyInt(), anyInt())).thenReturn(true);
        when(signing.generate(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenThrow(DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED.raise())
                .thenThrow(new IllegalStateException("storage away"));

        assertDoesNotThrow(() -> service.askUnlocked(PROCEDURE_ID, MEMBER_ID));
        assertDoesNotThrow(() -> service.askUnlocked(PROCEDURE_ID, MEMBER_ID));
        verify(repository, never()).linkGeneration(anyInt(), anyInt());
    }

    /** A document whose signatures cannot be asked for stays linked, so it is not filed again. */
    @Test
    void aDocumentNotAskedForStaysWithItsStep() {
        unlockedStep();
        when(signing.mayAsk(anyInt(), anyInt())).thenReturn(true);
        when(signing.generate(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(GENERATION_ID);
        when(repository.linkGeneration(ITEM_ID, GENERATION_ID)).thenReturn(true);
        doThrow(DocumentRefusal.SIGNING_MEMBER_GONE.raise())
                .when(signing)
                .request(GENERATION_ID, MEMBER_ID, "Aufnahme");

        assertDoesNotThrow(() -> service.askUnlocked(PROCEDURE_ID, MEMBER_ID));
        verify(repository).linkGeneration(ITEM_ID, GENERATION_ID);
    }

    /** A step another call linked first keeps its document; the second is not asked for. */
    @Test
    void aSecondDocumentForTheSameStepIsNotAskedFor() {
        unlockedStep();
        when(signing.mayAsk(anyInt(), anyInt())).thenReturn(true);
        when(signing.generate(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(GENERATION_ID);
        when(repository.linkGeneration(ITEM_ID, GENERATION_ID)).thenReturn(false);

        service.askUnlocked(PROCEDURE_ID, MEMBER_ID);

        verify(signing, never()).request(anyInt(), anyInt(), any());
    }

    /** Ticking the step a document step waits for asks for that document, after the tick is told. */
    @Test
    void tickingTheLastDependencyAsksForTheDocument() {
        var first = new ProcedureItem(1, PROCEDURE_ID, "Packen", null, null, true, true, 0, false, null, null);
        var ticked =
                new ProcedureItem(1, PROCEDURE_ID, "Packen", null, null, true, true, 0, true, Instant.now(), MEMBER_ID);
        when(repository.findItemById(1)).thenReturn(Optional.of(first));
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));
        when(repository.findItems(PROCEDURE_ID))
                .thenReturn(List.of(first, documentStep(2, SUITABLE_DOCUMENT, null, false)))
                .thenReturn(List.of(ticked, documentStep(2, SUITABLE_DOCUMENT, null, false)));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.<int[]>of(new int[] {2, 1}));
        when(repository.checkItem(1, MEMBER_ID)).thenReturn(true);
        when(signing.mayAsk(STATION_ID, MEMBER_ID)).thenReturn(true);
        when(signing.generate(STATION_ID, SUITABLE_DOCUMENT, ABOUT_ID, MEMBER_ID))
                .thenReturn(GENERATION_ID);
        when(repository.linkGeneration(2, GENERATION_ID)).thenReturn(true);

        assertTrue(service.checkItem(1, MEMBER_ID));

        var order = inOrder(eventBus, signing);
        order.verify(eventBus).publish(any(ProcedureItemChecked.class));
        order.verify(signing).generate(STATION_ID, SUITABLE_DOCUMENT, ABOUT_ID, MEMBER_ID);
    }

    /**
     * Another member withdraws the open document of a step and is asked anew about them; a step whose
     * document was signed keeps it, and its template is not asked about again.
     */
    @Test
    void anotherMemberGetsTheirOwnDocument() {
        when(restrictions.currentMemberAt(STATION_ID, OTHER_ABOUT_ID)).thenReturn(true);
        when(repository.findItems(PROCEDURE_ID))
                .thenReturn(List.of(documentStep(1, SUITABLE_DOCUMENT, 70, false), documentStep(2, 7, 71, true)));
        when(repository.setAbout(PROCEDURE_ID, OTHER_ABOUT_ID)).thenReturn(true);

        service.setAbout(about(ABOUT_ID), OTHER_ABOUT_ID, MEMBER_ID_2, true, false);

        verify(signing).withdraw(70, MEMBER_ID_2);
        verify(repository).unlinkGeneration(1, 70);
        verify(signing, never()).withdraw(eq(71), anyInt());
        verify(signing, never()).generate(anyInt(), anyInt(), anyInt(), anyInt());
    }

    /** A step already signed never asks again, so its template does not hold back another member. */
    @Test
    void aSignedStepDoesNotHoldBackAnotherMember() {
        when(restrictions.currentMemberAt(STATION_ID, OTHER_ABOUT_ID)).thenReturn(true);
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(documentStep(2, 7, 71, true)));
        when(repository.setAbout(PROCEDURE_ID, OTHER_ABOUT_ID)).thenReturn(true);

        assertDoesNotThrow(() -> picky().setAbout(about(ABOUT_ID), OTHER_ABOUT_ID, MEMBER_ID, false, false));
        verify(repository).setAbout(PROCEDURE_ID, OTHER_ABOUT_ID);
    }

    /** Pointing document steps at another member takes the right to generate documents for members. */
    @Test
    void onlySomebodyWhoGeneratesDocumentsRetargetsThem() {
        when(restrictions.currentMemberAt(STATION_ID, OTHER_ABOUT_ID)).thenReturn(true);
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(documentStep(1, SUITABLE_DOCUMENT, 70, false)));

        assertEquals(
                ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_NOT_YOURS,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.setAbout(about(ABOUT_ID), OTHER_ABOUT_ID, MEMBER_ID, false, true))
                        .refusal());
        verify(repository, never()).setAbout(anyInt(), anyInt());
    }

    /** A procedure is about a current member of its own station only. */
    @Test
    void aProcedureIsAboutACurrentMemberOfItsStation() {
        when(restrictions.currentMemberAt(STATION_ID, OTHER_ABOUT_ID)).thenReturn(false);

        assertEquals(
                ProcedureRefusal.PROCEDURE_ABOUT_NOT_OF_STATION,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.setAbout(about(ABOUT_ID), OTHER_ABOUT_ID, MEMBER_ID, true, true))
                        .refusal());
        verify(repository, never()).setAbout(anyInt(), anyInt());
    }

    /** A document step needs somebody to be about, and a template meant for them, named in the refusal. */
    @Test
    void aDocumentStepFitsTheMemberItIsAbout() {
        var kind = StepKindChoice.document(SUITABLE_DOCUMENT);
        assertEquals(
                ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_NEEDS_ABOUT,
                assertThrows(RefusalResponse.class, () -> service.requireDocumentFits(STATION_ID, null, "Antrag", kind))
                        .refusal());
        assertDoesNotThrow(() -> service.requireDocumentFits(STATION_ID, null, "Packen", StepKindChoice.CHECK));

        var refused = assertThrows(
                RefusalResponse.class, () -> picky().requireDocumentFits(STATION_ID, ABOUT_ID, "Antrag", kind));
        assertEquals(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_FOR_MEMBER, refused.refusal());
        assertEquals(RefusalDetail.text("Antrag: Dokument " + SUITABLE_DOCUMENT), refused.detail());
    }

    /** A template asking to sign starts no procedure about nobody, and nothing is written. */
    @Test
    void aTemplateAskingToSignNeedsSomebodyToBeAbout() {
        when(repository.findTemplateItems(TEMPLATE_ID))
                .thenReturn(List.of(new ProcedureTemplateItem(
                        1, TEMPLATE_ID, "Antrag", null, true, true, 0, ProcedureStepKind.DOCUMENT, SUITABLE_DOCUMENT)));

        assertEquals(
                ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_NEEDS_ABOUT,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.createProcedure(
                                        STATION_ID,
                                        TEMPLATE_ID,
                                        "Aufnahme",
                                        null,
                                        true,
                                        MEMBER_ID,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null))
                        .refusal());
        verify(repository, never())
                .createProcedure(
                        anyInt(), any(), anyString(), any(), anyBoolean(), anyInt(), any(), any(), any(), any());
    }

    /** Deleting a procedure withdraws what its document steps still wait for, and keeps what was signed. */
    @Test
    void deletingAProcedureWithdrawsItsOpenDocuments() {
        when(repository.findItems(PROCEDURE_ID))
                .thenReturn(List.of(
                        documentStep(1, SUITABLE_DOCUMENT, 70, false), documentStep(2, SUITABLE_DOCUMENT, 71, true)));
        when(repository.deleteProcedure(PROCEDURE_ID)).thenReturn(true);

        assertTrue(service.deleteProcedure(PROCEDURE_ID, MEMBER_ID));

        verify(signing).withdraw(70, MEMBER_ID);
        verify(signing, never()).withdraw(eq(71), anyInt());
    }

    /** Deleting a document step withdraws what its document still waits for. */
    @Test
    void deletingADocumentStepWithdrawsItsOpenDocument() {
        when(repository.deleteItem(1)).thenReturn(true);
        assertTrue(service.deleteItem(documentStep(1, SUITABLE_DOCUMENT, 70, false), MEMBER_ID));
        verify(signing).withdraw(70, MEMBER_ID);
    }

    /** A document that stands as agreed ticks its step, tells the party and asks for what that unlocks. */
    @Test
    void anAgreedDocumentTicksItsStep() {
        var step = documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, false);
        when(repository.findItemByGeneration(GENERATION_ID)).thenReturn(Optional.of(step));
        when(repository.checkBySignatures(ITEM_ID, MEMBER_ID_2)).thenReturn(true);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of());
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());

        service.documentClosed(GENERATION_ID, true, MEMBER_ID_2);

        var checked = ArgumentCaptor.forClass(ProcedureItemChecked.class);
        verify(eventBus).publish(checked.capture());
        assertEquals(ITEM_ID, checked.getValue().itemId());
        assertEquals(MEMBER_ID_2, checked.getValue().checkedByMemberId());
        verify(repository).findItemDependencies(PROCEDURE_ID);
    }

    /** A document withdrawn without agreement, or one no step made, ticks nothing. */
    @Test
    void aDocumentNotAgreedTicksNothing() {
        service.documentClosed(GENERATION_ID, false, MEMBER_ID);
        when(repository.findItemByGeneration(GENERATION_ID)).thenReturn(Optional.empty());
        service.documentClosed(GENERATION_ID, true, MEMBER_ID);

        verify(repository, never()).checkBySignatures(anyInt(), anyInt());
        verify(eventBus, never()).publish(any());
    }

    /** A withdrawn agreement opens its step again and tells whoever resolved the procedure. */
    @Test
    void aWithdrawnAgreementOpensTheStepAndTellsWhoResolved() {
        var step = documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, true);
        when(repository.findItemByGeneration(GENERATION_ID)).thenReturn(Optional.of(step));
        when(repository.uncheckItem(ITEM_ID)).thenReturn(true);
        var resolved = new Procedure(
                PROCEDURE_ID,
                STATION_ID,
                null,
                "Aufnahme",
                null,
                true,
                ProcedureStatus.RESOLVED,
                MEMBER_ID,
                null,
                Instant.now(),
                Instant.now(),
                null,
                null,
                ABOUT_ID,
                MEMBER_ID_2);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(resolved));

        service.agreementWithdrawn(GENERATION_ID, ABOUT_ID);

        var told = ArgumentCaptor.forClass(ProcedureDocumentWithdrawn.class);
        verify(eventBus).publish(told.capture());
        assertEquals(List.of(MEMBER_ID_2), told.getValue().toldMemberIds());
        assertEquals(ABOUT_ID, told.getValue().withdrawnBy());
        verify(repository, never()).reopenProcedure(anyInt());
    }

    /** On an open procedure the step opens again and nobody extra is told. */
    @Test
    void aWithdrawnAgreementOnAnOpenProcedureTellsNobodyExtra() {
        var step = documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, true);
        when(repository.findItemByGeneration(GENERATION_ID)).thenReturn(Optional.of(step));
        when(repository.uncheckItem(ITEM_ID)).thenReturn(true);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));

        service.agreementWithdrawn(GENERATION_ID, ABOUT_ID);

        verify(repository).uncheckItem(ITEM_ID);
        verify(eventBus, never()).publish(any());
    }

    /** A corrected document takes the place of the one its step held. */
    @Test
    void aCorrectedDocumentTakesThePlaceOfTheOld() {
        service.documentReplaced(GENERATION_ID, GENERATION_ID + 1);
        verify(repository).relinkGeneration(GENERATION_ID, GENERATION_ID + 1);
    }

    /** A document made but never asked for is asked for, and nothing is made again. */
    @Test
    void askingAgainAsksForADocumentNeverAskedFor() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));
        when(repository.findItems(PROCEDURE_ID))
                .thenReturn(List.of(documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, false)));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(signing.mayAsk(STATION_ID, MEMBER_ID)).thenReturn(true);
        when(signing.stateOf(ITEM_ID, GENERATION_ID)).thenReturn(state(StepDocumentState.Progress.NOT_ASKED));

        service.askAgain(about(ABOUT_ID), documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, false), MEMBER_ID);

        verify(signing).request(GENERATION_ID, MEMBER_ID, "Aufnahme");
        verify(signing, never()).generate(anyInt(), anyInt(), anyInt(), anyInt());
    }

    /** A document whose agreement was withdrawn is made anew, linked and asked for. */
    @Test
    void askingAgainMakesAWithdrawnDocumentAnew() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));
        when(repository.findItems(PROCEDURE_ID))
                .thenReturn(List.of(documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, false)));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(signing.mayAsk(STATION_ID, MEMBER_ID)).thenReturn(true);
        when(signing.stateOf(ITEM_ID, GENERATION_ID)).thenReturn(state(StepDocumentState.Progress.WITHDRAWN));
        when(signing.generate(STATION_ID, SUITABLE_DOCUMENT, ABOUT_ID, MEMBER_ID))
                .thenReturn(GENERATION_ID + 1);
        when(repository.relinkGeneration(GENERATION_ID, GENERATION_ID + 1)).thenReturn(true);

        service.askAgain(about(ABOUT_ID), documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, false), MEMBER_ID);

        verify(repository, never()).unlinkGeneration(anyInt(), anyInt());
        verify(signing).request(GENERATION_ID + 1, MEMBER_ID, "Aufnahme");
    }

    /**
     * A step still waiting, ticked or locked is not asked again, and nobody without the right to generate
     * documents asks.
     */
    @Test
    void askingAgainIsRefusedWhereNothingWaitsForIt() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));
        var waiting = documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, false);
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(waiting));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(signing.mayAsk(STATION_ID, MEMBER_ID)).thenReturn(true);
        when(signing.stateOf(ITEM_ID, GENERATION_ID)).thenReturn(state(StepDocumentState.Progress.WAITING));
        assertRefused(ProcedureRefusal.PROCEDURE_STEP_NOT_TO_ASK_AGAIN, waiting, MEMBER_ID);

        var ticked = documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, true);
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(ticked));
        assertRefused(ProcedureRefusal.PROCEDURE_STEP_NOT_TO_ASK_AGAIN, ticked, MEMBER_ID);

        var first = new ProcedureItem(1, PROCEDURE_ID, "Packen", null, null, true, true, 0, false, null, null);
        var locked = documentStep(ITEM_ID, SUITABLE_DOCUMENT, null, false);
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(first, locked));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.<int[]>of(new int[] {ITEM_ID, 1}));
        assertRefused(ProcedureRefusal.PROCEDURE_STEP_NOT_TO_ASK_AGAIN, locked, MEMBER_ID);

        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(locked));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        assertRefused(ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_NOT_YOURS_TO_ASK, locked, MEMBER_ID_2);

        verify(signing, never()).generate(anyInt(), anyInt(), anyInt(), anyInt());
        verify(signing, never()).request(anyInt(), anyInt(), any());
    }

    /** A refused document leaves the step holding its old one; nothing is relinked or asked for. */
    @Test
    void askingAgainKeepsTheStepWhereItStoodWhenRefused() {
        var step = documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, false);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(step));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(signing.mayAsk(STATION_ID, MEMBER_ID)).thenReturn(true);
        when(signing.stateOf(ITEM_ID, GENERATION_ID)).thenReturn(state(StepDocumentState.Progress.WITHDRAWN));
        when(signing.generate(anyInt(), anyInt(), anyInt(), anyInt()))
                .thenThrow(DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED.raise());

        assertThrows(RefusalResponse.class, () -> service.askAgain(about(ABOUT_ID), step, MEMBER_ID));

        verify(repository, never()).relinkGeneration(anyInt(), anyInt());
        verify(repository, never()).unlinkGeneration(anyInt(), anyInt());
        verify(signing, never()).request(anyInt(), anyInt(), any());
    }

    /** A document made but since deleted is made anew rather than asked for. */
    @Test
    void askingAgainMakesADeletedDocumentAnew() {
        var step = documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, false);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(step));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(signing.mayAsk(STATION_ID, MEMBER_ID)).thenReturn(true);
        when(signing.stateOf(ITEM_ID, GENERATION_ID))
                .thenReturn(new StepDocumentState(ITEM_ID, null, StepDocumentState.Progress.NOT_ASKED, 0, 0, null));
        when(signing.generate(STATION_ID, SUITABLE_DOCUMENT, ABOUT_ID, MEMBER_ID))
                .thenReturn(GENERATION_ID + 2);
        when(repository.relinkGeneration(GENERATION_ID, GENERATION_ID + 2)).thenReturn(false);

        assertRefused(ProcedureRefusal.PROCEDURE_STEP_NOT_TO_ASK_AGAIN, step, MEMBER_ID);

        verify(signing).generate(STATION_ID, SUITABLE_DOCUMENT, ABOUT_ID, MEMBER_ID);
        verify(signing, never()).request(anyInt(), anyInt(), any());
    }

    /** The procedure is read under the lock: one resolved meanwhile, or about nobody now, is refused. */
    @Test
    void askingAgainReadsTheProcedureAsItStandsNow() {
        var step = documentStep(ITEM_ID, SUITABLE_DOCUMENT, null, false);
        when(repository.findItems(PROCEDURE_ID)).thenReturn(List.of(step));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
        when(signing.mayAsk(STATION_ID, MEMBER_ID)).thenReturn(true);
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(procedure(PROCEDURE_ID, null)));

        assertRefused(ProcedureRefusal.PROCEDURE_STEP_NOT_TO_ASK_AGAIN, step, MEMBER_ID);

        verify(signing, never()).generate(anyInt(), anyInt(), anyInt(), anyInt());
    }

    /** Only the document steps that made a document read where it stands. */
    @Test
    void onlyStepsWithADocumentReadTheirState() {
        when(signing.stateOf(ITEM_ID, GENERATION_ID)).thenReturn(state(StepDocumentState.Progress.AGREED));

        var states = service.documentStates(List.of(
                documentStep(ITEM_ID, SUITABLE_DOCUMENT, GENERATION_ID, true),
                documentStep(ITEM_ID + 1, SUITABLE_DOCUMENT, null, false),
                procedureItem(ITEM_ID + 2, false)));

        assertEquals(List.of(state(StepDocumentState.Progress.AGREED)), states);
    }

    private void assertRefused(Refusal refusal, ProcedureItem step, int by) {
        assertEquals(
                refusal,
                assertThrows(RefusalResponse.class, () -> service.askAgain(about(ABOUT_ID), step, by))
                        .refusal());
    }

    private static StepDocumentState state(StepDocumentState.Progress progress) {
        return new StepDocumentState(ITEM_ID, 55, progress, 0, 1, null);
    }

    /** The service with every template refused for every member, so only a step not asked anew passes. */
    private ProcedureService picky() {
        return new ProcedureService(
                repository, eventBus, restrictions, new SuitableDocuments(template -> true, false), signing);
    }

    private void unlockedStep() {
        when(repository.findProcedureById(PROCEDURE_ID)).thenReturn(Optional.of(about(ABOUT_ID)));
        when(repository.findItems(PROCEDURE_ID))
                .thenReturn(List.of(documentStep(ITEM_ID, SUITABLE_DOCUMENT, null, false)));
        when(repository.findItemDependencies(PROCEDURE_ID)).thenReturn(List.of());
    }

    private Procedure about(int aboutMemberId) {
        return new Procedure(
                PROCEDURE_ID,
                STATION_ID,
                null,
                "Aufnahme",
                null,
                true,
                ProcedureStatus.OPEN,
                MEMBER_ID,
                null,
                Instant.now(),
                null,
                null,
                null,
                aboutMemberId,
                null);
    }

    private static ProcedureItem documentStep(
            int id, int documentTemplateId, @Nullable Integer generationId, boolean checked) {
        return new ProcedureItem(
                id,
                PROCEDURE_ID,
                "Antrag",
                null,
                null,
                true,
                true,
                0,
                checked,
                checked ? Instant.now() : null,
                checked ? MEMBER_ID : null,
                ProcedureStepKind.DOCUMENT,
                documentTemplateId,
                generationId);
    }

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
                null,
                null,
                null,
                null,
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
