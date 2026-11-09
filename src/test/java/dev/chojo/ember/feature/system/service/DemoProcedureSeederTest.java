/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ProcedureAssigned;
import dev.chojo.ember.feature.procedure.service.DocumentStepSigning;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.procedure.service.SuitableDocuments;
import dev.chojo.ember.feature.restriction.RestrictionType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The demo procedures: those started from the onboarding template carry their template's steps and a party
 * naming members, and seeding them tells nobody that a procedure was handed to them.
 */
class DemoProcedureSeederTest extends DemoTestBase {
    private static DemoRunContext run;
    private static DomainEventBus bus;

    @BeforeAll
    static void seedTheDemoProcedures() {
        run = seedDemoStations();
        bus = mock(DomainEventBus.class);
        var procedures = new ProcedureService(
                procedureRepo,
                bus,
                restrictionService,
                SuitableDocuments.NONE,
                mock(DocumentStepSigning.class),
                stationMemberRepo);
        new DemoProcedureSeeder(procedureRepo, restrictionService, procedures).seedStation(run, run.primaryStation());
    }

    /** The members the seeded parties name are not told a procedure was handed to them. */
    @Test
    void seedingTellsNobody() {
        verify(bus, never()).publish(any(ProcedureAssigned.class));
    }

    /** The procedures started from the template carry its steps and a party naming members. */
    @Test
    void templateProceduresCarryTheirStepsAndParty() {
        int stationId = run.primaryStation().stationId();
        var fromTemplate = procedureRepo.findProceduresByStation(stationId, null).stream()
                .filter(procedure -> procedure.templateId() != null)
                .toList();

        assertEquals(2, fromTemplate.size());
        for (var procedure : fromTemplate) {
            var templateSteps = procedureRepo.findTemplateItems(Objects.requireNonNull(procedure.templateId()));
            assertEquals(
                    templateSteps.size(),
                    procedureRepo.findItems(procedure.id()).size());
            assertFalse(restrictionService
                    .audienceOf(RestrictionType.PROCEDURE, procedure.id())
                    .toSelection()
                    .memberIds()
                    .isEmpty());
        }
    }
}
