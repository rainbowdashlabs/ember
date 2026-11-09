/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.generator.service.GeneratorTestBase;
import dev.chojo.ember.feature.generator.service.ProcedureDocumentTemplates;
import dev.chojo.ember.feature.procedure.entity.ProcedureStepKind;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplate;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.signing.entity.FieldRole;
import dev.chojo.ember.feature.signing.entity.RequestedSignature;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admission procedure every demo station gets: a template whose first step asks to sign an admission
 * application the member side signs, the steps after it waiting for it, run by the carers, and one
 * procedure about the last beginner seeded, whose application is asked for. Seeding twice leaves one
 * template and one application.
 */
class DemoAdmissionSeederTest extends DemoTestBase {
    private static DemoRunContext run;
    private static GeneratorTestBase.Wiring wiring;
    private static ProcedureDocumentTemplates documentSteps;
    private static final SignatureRequestRepository requestRepo = new SignatureRequestRepository();

    private static DemoAdmissionSeeder seeder;

    @BeforeAll
    static void seedTheDemoStations() {
        run = seedDemoStations();
        wiring = GeneratorTestBase.wire(run.primaryStation().station());
        documentSteps = new ProcedureDocumentTemplates(wiring.templates(), wiring.generator());
        var signing = GeneratorTestBase.signing(wiring, requestRepo, new DomainEventBus(Set.of()));
        var procedures = new ProcedureService(
                procedureRepo,
                new DomainEventBus(Set.of()),
                restrictionService,
                documentSteps,
                signing.procedures(),
                stationMemberRepo);
        seeder = new DemoAdmissionSeeder(
                wiring.templates(), procedureRepo, procedures, restrictionService, memberIdentityFactory);
        seeder.seedStation(run, run.primaryStation());
    }

    /**
     * The first step asks to sign an application a step may generate, the two after it wait for it, and the
     * carers run what is started from it.
     */
    @Test
    void theTemplateAsksToSignTheApplicationFirst() {
        int stationId = run.primaryStation().stationId();
        var template = admission(stationId);
        var steps = procedureRepo.findTemplateItems(template.id());

        assertEquals(3, steps.size());
        var sign = steps.getFirst();
        assertEquals(ProcedureStepKind.DOCUMENT, sign.kind());
        assertTrue(documentSteps.suitable(stationId, sign.documentTemplateId()), "the member side signs it");
        assertEquals(
                DemoAdmissionTemplate.NAME,
                documentSteps
                        .named(stationId, List.of(sign.documentTemplateId()))
                        .getFirst()
                        .name());
        var waiting = procedureRepo.findTemplateItemDependencies(template.id()).stream()
                .filter(pair -> pair[1] == sign.id())
                .count();
        assertEquals(2, waiting);
        assertFalse(restrictionService
                .audienceOf(RestrictionType.PROCEDURE_TEMPLATE, template.id())
                .toSelection()
                .groupIds()
                .isEmpty());
    }

    /**
     * One procedure is started from it, about the last beginner seeded, and its application is generated
     * about them and asked for: from the member and from every guardian.
     */
    @Test
    void oneProcedureIsAboutTheLastBeginnerAndAsksForTheApplication() {
        var station = run.primaryStation();
        var newcomer = station.members().anfaenger().getLast();

        var started = procedureRepo.findProceduresByStation(station.stationId(), null).stream()
                .filter(procedure -> Objects.equals(
                        procedure.templateId(), admission(station.stationId()).id()))
                .toList();

        assertEquals(1, started.size());
        assertEquals(newcomer.id(), started.getFirst().aboutMemberId());
        assertEquals(
                DemoAdmissionSeeder.NAME + ": "
                        + memberIdentityFactory.fromMemberId(newcomer.id()).name(),
                started.getFirst().name());
        var sign = procedureRepo.findItems(started.getFirst().id()).stream()
                .filter(step -> step.kind() == ProcedureStepKind.DOCUMENT)
                .findFirst()
                .orElseThrow();
        var generationId = Objects.requireNonNull(sign.generationId(), "the application was generated");
        var request = requestRepo.findLiveFor(generationId).orElseThrow();
        var roles = requestRepo.fieldsOf(request.id()).stream()
                .map(RequestedSignature::role)
                .toList();
        assertTrue(roles.contains(FieldRole.PARTICIPANT), roles.toString());
        assertTrue(roles.stream().anyMatch(role -> role == FieldRole.GUARDIAN || role == FieldRole.ANY_GUARDIAN));
        assertEquals(started.getFirst().name(), request.purpose());
    }

    /**
     * Seeding again leaves the one template the station already has, and a station that lost the template
     * but kept its application gets the template back pointing at the application it has.
     */
    @Test
    void seedingAgainLeavesOne() {
        seeder.seedStation(run, run.primaryStation());
        int stationId = run.primaryStation().stationId();
        var lost = admission(stationId);
        var application = procedureRepo.findTemplateItems(lost.id()).getFirst().documentTemplateId();
        procedureRepo.updateTemplate(lost.id(), "Umbenannt", null);

        seeder.seedStation(run, run.primaryStation());

        assertEquals(
                application,
                procedureRepo
                        .findTemplateItems(admission(stationId).id())
                        .getFirst()
                        .documentTemplateId());
        assertEquals(
                1,
                procedureRepo.findTemplatesByStation(stationId, true).stream()
                        .filter(template -> DemoAdmissionSeeder.NAME.equals(template.name()))
                        .count());
        assertEquals(
                1,
                wiring.templates().list(new Owner.Station(stationId), false).stream()
                        .filter(template -> DemoAdmissionTemplate.NAME.equals(template.name()))
                        .count());
    }

    private static ProcedureTemplate admission(int stationId) {
        return procedureRepo.findTemplatesByStation(stationId, true).stream()
                .filter(template -> DemoAdmissionSeeder.NAME.equals(template.name()))
                .findFirst()
                .orElseThrow();
    }
}
