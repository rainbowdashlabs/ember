/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.api.TestSessions;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
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

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static dev.chojo.ember.api.RouteHarness.refusalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A step reached through a procedure or a template of the station has to be one of its own steps.
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
    private RouteHarness harness;

    @BeforeEach
    void setup() {
        repository = mock(ProcedureRepository.class);
        when(repository.findProcedureById(PROCEDURE)).thenReturn(Optional.of(procedure()));
        when(repository.findItemById(OWN_STEP)).thenReturn(Optional.of(step(OWN_STEP, PROCEDURE)));
        when(repository.findItemById(FOREIGN_STEP)).thenReturn(Optional.of(step(FOREIGN_STEP, OTHER_PROCEDURE)));
        when(repository.findItems(PROCEDURE)).thenReturn(List.of(step(OWN_STEP, PROCEDURE)));
        when(repository.findAssigneeIds(PROCEDURE)).thenReturn(List.of(TestSessions.MEMBER_ID));
        when(repository.findTemplateById(TEMPLATE)).thenReturn(Optional.of(template()));
        when(repository.findTemplateItems(TEMPLATE)).thenReturn(List.of(templateStep()));
        var service = new ProcedureService(repository, mock(DomainEventBus.class));
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

        assertEquals(Refusal.PROCEDURE_STEP_NOT_IN_PROCEDURE, refusalOf(answer));
        verify(repository, never()).checkItem(anyInt(), anyInt());
        verify(repository, never()).updateItemNote(anyInt(), anyString());
    }

    @Test
    void anEditorCannotReachAStepOfAnotherProcedure() {
        harness.run((server, client) -> {
            var editor =
                    harness.as(TestSessions.member(STATION, StationPermission.USER, StationPermission.PROCEDURE_EDIT));
            String foreign = PREFIX + "/procedures/" + PROCEDURE + "/items/" + FOREIGN_STEP;

            assertEquals(
                    Refusal.PROCEDURE_STEP_NOT_IN_PROCEDURE, refusalOf(client.put(foreign, body(STEP_BODY), editor)));
            assertEquals(Refusal.PROCEDURE_STEP_NOT_IN_PROCEDURE, refusalOf(client.delete(foreign, null, editor)));
            assertEquals(
                    Refusal.PROCEDURE_STEP_NOT_IN_PROCEDURE,
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

        assertEquals(Refusal.PROCEDURE_STEP_NOT_IN_PROCEDURE, refusalOf(answer));
        verify(repository, never()).setItemDependencies(anyInt(), any());
    }

    @Test
    void aManagerCannotReachAStepOfAnotherTemplate() {
        harness.run((server, client) -> {
            var manager = harness.as(TestSessions.member(STATION, StationPermission.PROCEDURE_MANAGER));
            String foreign = PREFIX + "/procedure-templates/" + TEMPLATE + "/items/" + FOREIGN_TEMPLATE_STEP;

            assertEquals(
                    Refusal.PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE,
                    refusalOf(client.put(foreign, body(STEP_BODY), manager)));
            assertEquals(
                    Refusal.PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE, refusalOf(client.delete(foreign, null, manager)));
            assertEquals(
                    Refusal.PROCEDURE_TEMPLATE_STEP_NOT_IN_TEMPLATE,
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

    private static ProcedureTemplate template() {
        return new ProcedureTemplate(
                TEMPLATE, STATION, "Zeltlager", null, false, TestSessions.MEMBER_ID, Instant.EPOCH);
    }

    private static ProcedureTemplateItem templateStep() {
        return new ProcedureTemplateItem(OWN_TEMPLATE_STEP, TEMPLATE, "Packen", null, true, true, 0);
    }
}
