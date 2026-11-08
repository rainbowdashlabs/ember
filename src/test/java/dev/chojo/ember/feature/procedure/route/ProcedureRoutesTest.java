/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.ProcedureRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureStatus;
import dev.chojo.ember.feature.procedure.entity.ProcedureStepKind;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.feature.procedure.entity.StepKindChoice;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.procedure.service.SuitableDocuments;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may change which step of a procedure or a template, over HTTP.
 */
class ProcedureRoutesTest {
    private static final int STATION = 3;
    private static final int PROCEDURE = 1;
    private static final int OTHER_PROCEDURE = 2;
    private static final int OWN_STEP = 10;
    private static final int FOREIGN_STEP = 20;
    private static final int TEMPLATE = 5;
    private static final int OWN_TEMPLATE_STEP = 50;
    private static final int FOREIGN_TEMPLATE_STEP = 60;
    private static final String STEP_BODY =
            "{\"title\": \"Packen\", \"isPublic\": true, \"userAssigned\": true, \"position\": 0}";

    private ProcedureRepository repository;
    private RestrictionService restrictions;
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        repository = mock(ProcedureRepository.class);
        when(repository.findProcedureById(PROCEDURE)).thenReturn(Optional.of(procedure()));
        when(repository.findItemById(OWN_STEP)).thenReturn(Optional.of(step(OWN_STEP, PROCEDURE)));
        when(repository.findItemById(FOREIGN_STEP)).thenReturn(Optional.of(step(FOREIGN_STEP, OTHER_PROCEDURE)));
        when(repository.findItems(PROCEDURE)).thenReturn(List.of(step(OWN_STEP, PROCEDURE)));
        when(repository.findTemplateById(TEMPLATE)).thenReturn(Optional.of(template()));
        when(repository.findTemplateItems(TEMPLATE)).thenReturn(List.of(templateStep()));
        restrictions = mock(RestrictionService.class);
        when(restrictions.ofStation(anyInt(), any())).thenReturn(true);
        when(restrictions.hasRestrictions(RestrictionType.PROCEDURE, PROCEDURE)).thenReturn(true);
        when(restrictions.includes(RestrictionType.PROCEDURE, PROCEDURE, TestSessions.MEMBER_ID))
                .thenReturn(true);
        when(restrictions.audienceOf(any(), anyInt())).thenReturn(RestrictionAudience.empty());
        var service =
                new ProcedureService(repository, mock(DomainEventBus.class), restrictions, SuitableDocuments.NONE);
        harness = RouteHarness.serving(new ProcedureRoutes(service, mock(MemberIdentityFactory.class)));
    }

    @Test
    void anAssigneeTicksAStepOfTheirOwnProcedure() {
        when(repository.checkItem(OWN_STEP, TestSessions.MEMBER_ID)).thenReturn(true);

        var answer = harness.request(client -> client.patch(
                PREFIX + "/procedures/" + PROCEDURE + "/items/" + OWN_STEP,
                body("{\"checked\": true}"),
                harness.as(TestSessions.member(STATION, StationPermission.USER))));

        assertEquals(200, answer.code());
        verify(repository).checkItem(OWN_STEP, TestSessions.MEMBER_ID);
    }

    @Test
    void anAssigneeCannotTickAStepOfAnotherProcedure() {
        var answer = harness.request(client -> client.patch(
                PREFIX + "/procedures/" + PROCEDURE + "/items/" + FOREIGN_STEP,
                body("{\"checked\": true, \"note\": \"erledigt\"}"),
                harness.as(TestSessions.member(STATION, StationPermission.USER))));

        assertEquals(ProcedureRefusal.PROCEDURE_STEP_NOT_IN_PROCEDURE, refusalOf(answer));
        verify(repository, never()).checkItem(anyInt(), anyInt());
        verify(repository, never()).updateItemNote(anyInt(), anyString());
    }

    @Test
    void onlyWhoeverRunsTheProcedureWritesANoteOnAStep() {
        harness.run((server, client) -> {
            String step = PREFIX + "/procedures/" + PROCEDURE + "/items/" + OWN_STEP;
            var assignee = harness.as(TestSessions.member(STATION, StationPermission.USER));
            var editor =
                    harness.as(TestSessions.member(STATION, StationPermission.USER, StationPermission.PROCEDURE_EDIT));

            assertEquals(
                    ProcedureRefusal.PROCEDURE_STEP_NOTE_NOT_YOURS,
                    refusalOf(client.patch(step, body("{\"checked\": true, \"note\": \"erledigt\"}"), assignee)));
            verify(repository, never()).checkItem(anyInt(), anyInt());
            verify(repository, never()).updateItemNote(anyInt(), anyString());

            assertEquals(
                    200,
                    client.patch(step, body("{\"note\": \"erledigt\"}"), editor).code());
            verify(repository).updateItemNote(OWN_STEP, "erledigt");
        });
    }

    @Test
    void anEditorCannotReachAStepOfAnotherProcedure() {
        harness.run((server, client) -> {
            var editor =
                    harness.as(TestSessions.member(STATION, StationPermission.USER, StationPermission.PROCEDURE_EDIT));
            String foreign = PREFIX + "/procedures/" + PROCEDURE + "/items/" + FOREIGN_STEP;

            assertEquals(
                    ProcedureRefusal.PROCEDURE_STEP_NOT_IN_PROCEDURE,
                    refusalOf(client.put(foreign, body(STEP_BODY), editor)));
            assertEquals(
                    ProcedureRefusal.PROCEDURE_STEP_NOT_IN_PROCEDURE, refusalOf(client.delete(foreign, null, editor)));
            assertEquals(
                    ProcedureRefusal.PROCEDURE_STEP_NOT_IN_PROCEDURE,
                    refusalOf(client.patch(foreign, body("{\"checked\": false}"), editor)));
        });

        verify(repository, never()).updateItem(anyInt(), anyString(), any(), anyBoolean(), anyBoolean(), anyInt());
        verify(repository, never()).deleteItem(anyInt());
        verify(repository, never()).uncheckItem(anyInt());
    }

    @Test
    void anEditorCannotMakeAStepWaitOnAStepOfAnotherProcedure() {
        var answer = harness.request(client -> client.put(
                PREFIX + "/procedures/" + PROCEDURE + "/dependencies",
                body("{\"dependencies\": [{\"itemId\": " + OWN_STEP + ", \"dependsOnItemId\": " + FOREIGN_STEP + "}]}"),
                harness.as(TestSessions.member(STATION, StationPermission.PROCEDURE_EDIT))));

        assertEquals(ProcedureRefusal.PROCEDURE_STEP_NOT_IN_PROCEDURE, refusalOf(answer));
        verify(repository, never()).setItemDependencies(anyInt(), any());
    }

    @Test
    void aManagerCannotReachAStepOfAnotherTemplate() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(STATION, StationPermission.PROCEDURE_MANAGER));
            String foreign = PREFIX + "/procedure-templates/" + TEMPLATE + "/items/" + FOREIGN_TEMPLATE_STEP;

            assertEquals(
                    ProcedureRefusal.PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE,
                    refusalOf(client.put(foreign, body(STEP_BODY), manager)));
            assertEquals(
                    ProcedureRefusal.PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE,
                    refusalOf(client.delete(foreign, null, manager)));
            assertEquals(
                    ProcedureRefusal.PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE,
                    refusalOf(client.put(
                            PREFIX + "/procedure-templates/" + TEMPLATE + "/dependencies",
                            body("{\"dependencies\": [{\"itemId\": " + FOREIGN_TEMPLATE_STEP + ", \"dependsOnItemId\": "
                                    + OWN_TEMPLATE_STEP + "}]}"),
                            manager)));
        });

        verify(repository, never())
                .updateTemplateItem(anyInt(), anyString(), any(), anyBoolean(), anyBoolean(), anyInt());
        verify(repository, never()).deleteTemplateItem(anyInt());
        verify(repository, never()).setTemplateItemDependencies(anyInt(), any());
    }

    @Test
    void aManagerChangesAStepOfTheTemplate() {
        when(repository.updateTemplateItem(OWN_TEMPLATE_STEP, "Packen", null, true, true, 0))
                .thenReturn(true);

        var answer = harness.request(client -> client.put(
                PREFIX + "/procedure-templates/" + TEMPLATE + "/items/" + OWN_TEMPLATE_STEP,
                body(STEP_BODY),
                harness.as(TestSessions.member(STATION, StationPermission.PROCEDURE_MANAGER))));

        assertEquals(204, answer.code());
    }

    /** A step asking for a document the station cannot have signed is refused before anything is written. */
    @Test
    void aStepAsksOnlyForADocumentThatCanBeSigned() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(
                    STATION,
                    StationPermission.PROCEDURE_MANAGER,
                    StationPermission.PROCEDURE_EDIT,
                    StationPermission.DOCUMENT_EDIT_MEMBER));
            var withoutDocuments = harness.as(TestSessions.member(
                    STATION, StationPermission.PROCEDURE_MANAGER, StationPermission.PROCEDURE_EDIT));
            String unsuitable = "{\"title\": \"Unterschreiben\", \"isPublic\": true, \"userAssigned\": true,"
                    + " \"position\": 0, \"kind\": \"DOCUMENT\", \"documentTemplateId\": 7}";
            String missing = "{\"title\": \"Unterschreiben\", \"isPublic\": true, \"userAssigned\": true,"
                    + " \"position\": 0, \"kind\": \"DOCUMENT\"}";

            assertEquals(
                    ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_UNSUITABLE,
                    refusalOf(client.post(
                            PREFIX + "/procedure-templates/" + TEMPLATE + "/items", body(unsuitable), manager)));
            assertEquals(
                    ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_MISSING,
                    refusalOf(client.put(
                            PREFIX + "/procedure-templates/" + TEMPLATE + "/items/" + OWN_TEMPLATE_STEP,
                            body(missing),
                            manager)));
            assertEquals(
                    ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_UNSUITABLE,
                    refusalOf(client.post(PREFIX + "/procedures/" + PROCEDURE + "/items", body(unsuitable), manager)));
            assertEquals(
                    ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_NOT_YOURS,
                    refusalOf(client.post(
                            PREFIX + "/procedure-templates/" + TEMPLATE + "/items",
                            body(unsuitable),
                            withoutDocuments)));
        });

        verify(repository, never())
                .createTemplateItem(anyInt(), anyString(), any(), anyBoolean(), anyBoolean(), anyInt(), any());
        verify(repository, never())
                .updateTemplateItem(anyInt(), anyString(), any(), anyBoolean(), anyBoolean(), anyInt());
        verify(repository, never()).setTemplateItemKind(anyInt(), any());
        verify(repository, never()).setItemKind(anyInt(), any());
    }

    /**
     * A step sent without a kind keeps what it asks for, and one sent unchanged is not checked again, so a
     * document step whose template is no longer suitable still saves its other fields.
     */
    @Test
    void anEditLeavesAStepsDocumentAsItIs() {
        when(repository.findItemById(OWN_STEP)).thenReturn(Optional.of(documentStep(OWN_STEP, 7, null)));
        when(repository.updateItem(eq(OWN_STEP), anyString(), any(), anyBoolean(), anyBoolean(), anyInt()))
                .thenReturn(true);

        harness.run((server, client) -> {
            var editor = harness.as(TestSessions.member(STATION, StationPermission.PROCEDURE_EDIT));
            String step = PREFIX + "/procedures/" + PROCEDURE + "/items/" + OWN_STEP;

            assertEquals(204, client.put(step, body(STEP_BODY), editor).code());
            assertEquals(
                    204,
                    client.put(
                                    step,
                                    body("{\"title\": \"Packen\", \"isPublic\": true, \"userAssigned\": true,"
                                            + " \"position\": 0, \"kind\": \"DOCUMENT\", \"documentTemplateId\": 7}"),
                                    editor)
                            .code());
        });

        verify(repository, never()).setItemKind(anyInt(), any());
        verify(repository, times(2)).updateItem(eq(OWN_STEP), anyString(), any(), anyBoolean(), anyBoolean(), anyInt());
    }

    /** A step that already made its document keeps asking for it, and nothing else of it is changed either. */
    @Test
    void aStepThatMadeItsDocumentKeepsIt() {
        when(repository.findItemById(OWN_STEP)).thenReturn(Optional.of(documentStep(OWN_STEP, 7, 900)));
        when(repository.setItemKind(OWN_STEP, StepKindChoice.CHECK)).thenReturn(false);

        var answer = harness.request(client -> client.put(
                PREFIX + "/procedures/" + PROCEDURE + "/items/" + OWN_STEP,
                body("{\"title\": \"Packen\", \"isPublic\": true, \"userAssigned\": true, \"position\": 0,"
                        + " \"kind\": \"CHECK\"}"),
                harness.as(TestSessions.member(STATION, StationPermission.PROCEDURE_EDIT))));

        assertEquals(ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_ALREADY_MADE, refusalOf(answer));
        verify(repository, never()).updateItem(anyInt(), anyString(), any(), anyBoolean(), anyBoolean(), anyInt());
    }

    /** A template asking to sign a document starts only for those who generate documents for members. */
    @Test
    void aTemplateAskingToSignStartsOnlyForThoseWhoGenerateDocuments() {
        when(repository.findTemplateItems(TEMPLATE))
                .thenReturn(List.of(new ProcedureTemplateItem(
                        OWN_TEMPLATE_STEP, TEMPLATE, "Antrag", null, true, true, 0, ProcedureStepKind.DOCUMENT, 7)));

        var answer = harness.request(client -> client.post(
                PREFIX + "/procedures",
                body("{\"name\": \"Aufnahme\", \"isPublic\": true, \"templateId\": " + TEMPLATE + "}"),
                harness.as(TestSessions.member(STATION, StationPermission.PROCEDURE_EDIT))));

        assertEquals(ProcedureRefusal.PROCEDURE_STEP_DOCUMENT_NOT_YOURS, refusalOf(answer));
        verify(repository, never()).snapshotTemplateItem(anyInt(), any());
    }

    /** The party of the station's own procedure is set; another station's procedure is not found. */
    @Test
    void aPartyIsSetOnlyOnTheStationsOwnProcedure() {
        when(repository.findProcedureById(OTHER_PROCEDURE))
                .thenReturn(Optional.of(procedureAt(OTHER_PROCEDURE, 99, true)));
        harness.run((server, client) -> {
            var editor =
                    harness.as(TestSessions.member(STATION, StationPermission.USER, StationPermission.PROCEDURE_EDIT));
            String party =
                    "{\"userTypes\": [], \"groupIds\": [], \"tagIds\": [], \"memberIds\": [], \"mode\": \"AND\"}";

            assertEquals(
                    404,
                    client.put(PREFIX + "/procedures/" + OTHER_PROCEDURE + "/party", body(party), editor)
                            .code());
            assertEquals(
                    200,
                    client.put(PREFIX + "/procedures/" + PROCEDURE + "/party", body(party), editor)
                            .code());

            when(restrictions.ofStation(anyInt(), any())).thenReturn(false);
            assertEquals(
                    ProcedureRefusal.PROCEDURE_PARTY_NOT_OF_STATION,
                    refusalOf(client.put(PREFIX + "/procedures/" + PROCEDURE + "/party", body(party), editor)));
        });
    }

    /** A member the party does not take in neither opens the procedure nor ticks its steps. */
    @Test
    void aMemberOutsideThePartyNeitherOpensNorTicksIt() {
        when(restrictions.includes(RestrictionType.PROCEDURE, PROCEDURE, TestSessions.MEMBER_ID))
                .thenReturn(false);
        harness.run((server, client) -> {
            var member = harness.as(TestSessions.member(STATION, StationPermission.USER));

            assertEquals(
                    ProcedureRefusal.PROCEDURE_NOT_YOURS,
                    refusalOf(client.get(PREFIX + "/procedures/" + PROCEDURE, member)));
            assertEquals(
                    ProcedureRefusal.PROCEDURE_NOT_HANDED_TO_YOU,
                    refusalOf(client.patch(
                            PREFIX + "/procedures/" + PROCEDURE + "/items/" + OWN_STEP,
                            body("{\"checked\": true}"),
                            member)));
        });
        verify(repository, never()).checkItem(anyInt(), anyInt());
    }

    /** A procedure without a party is run by nobody but those who edit procedures. */
    @Test
    void aProcedureWithoutAPartyIsTickedByNobodyButEditors() {
        when(restrictions.hasRestrictions(RestrictionType.PROCEDURE, PROCEDURE)).thenReturn(false);

        var answer = harness.request(client -> client.patch(
                PREFIX + "/procedures/" + PROCEDURE + "/items/" + OWN_STEP,
                body("{\"checked\": true}"),
                harness.as(TestSessions.member(STATION, StationPermission.USER))));

        assertEquals(ProcedureRefusal.PROCEDURE_NOT_HANDED_TO_YOU, refusalOf(answer));
        verify(repository, never()).checkItem(anyInt(), anyInt());
    }

    /** The party does not tick a step of a procedure that is not public, which it may not open either. */
    @Test
    void thePartyDoesNotTickAProcedureThatIsNotPublic() {
        when(repository.findProcedureById(PROCEDURE)).thenReturn(Optional.of(procedureAt(PROCEDURE, STATION, false)));

        var answer = harness.request(client -> client.patch(
                PREFIX + "/procedures/" + PROCEDURE + "/items/" + OWN_STEP,
                body("{\"checked\": true}"),
                harness.as(TestSessions.member(STATION, StationPermission.USER))));

        assertEquals(ProcedureRefusal.PROCEDURE_NOT_PUBLIC, refusalOf(answer));
        verify(repository, never()).checkItem(anyInt(), anyInt());
    }

    private static Procedure procedureAt(int id, int stationId, boolean isPublic) {
        return new Procedure(
                id,
                stationId,
                null,
                "Zeltlager",
                null,
                isPublic,
                ProcedureStatus.OPEN,
                TestSessions.MEMBER_ID,
                null,
                Instant.EPOCH,
                null,
                null,
                null);
    }

    private static Procedure procedure() {
        return new Procedure(
                PROCEDURE,
                STATION,
                null,
                "Zeltlager",
                null,
                true,
                ProcedureStatus.OPEN,
                TestSessions.MEMBER_ID,
                null,
                Instant.EPOCH,
                null,
                null,
                null);
    }

    private static ProcedureItem step(int id, int procedureId) {
        return new ProcedureItem(id, procedureId, "Packen", null, null, true, true, 0, false, null, null);
    }

    private static ProcedureItem documentStep(int id, int documentTemplateId, @Nullable Integer generationId) {
        return new ProcedureItem(
                id,
                PROCEDURE,
                "Packen",
                null,
                null,
                true,
                true,
                0,
                false,
                null,
                null,
                ProcedureStepKind.DOCUMENT,
                documentTemplateId,
                generationId);
    }

    private static ProcedureTemplate template() {
        return new ProcedureTemplate(
                TEMPLATE, STATION, "Zeltlager", null, false, TestSessions.MEMBER_ID, Instant.EPOCH);
    }

    private static ProcedureTemplateItem templateStep() {
        return new ProcedureTemplateItem(OWN_TEMPLATE_STEP, TEMPLATE, "Packen", null, true, true, 0);
    }
}
