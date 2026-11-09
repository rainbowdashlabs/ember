/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.procedure.entity.StepKindChoice;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * The procedure template "Neues Mitglied aufnehmen" every demo station gets, with the admission application
 * it asks to sign ({@link DemoAdmissionTemplate}), and one procedure started from it about the last beginner
 * seeded.
 *
 * <p>The template's first step asks the new member, and their guardians, to sign the application; handing
 * out the gear and the introduction to the vehicle hall wait for it. The carers run it. The procedure is
 * started through the procedure service like one a manager starts, so its application is generated and
 * asked for.
 *
 * <p>It runs after the modules, so the outcome is the same on every seeding: at the first station the youth
 * warden has agreed to sign letters for the station by then, so the application comes out signed for its
 * issuer and only the member side is asked; at the others she is asked as the issuer as well.
 *
 * <p>A station that already has a template of this name keeps it, and the application is reused by name,
 * so seeding twice leaves one of each.
 */
@Singleton
public class DemoAdmissionSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoAdmissionSeeder.class);

    /** What the procedure template is called. */
    static final String NAME = "Neues Mitglied aufnehmen";

    /** The group of carers who run it. */
    private static final String CARERS = "Betreuer";

    private final DocumentTemplateService documentTemplates;
    private final ProcedureRepository repo;
    private final ProcedureService procedures;
    private final RestrictionService restrictions;
    private final MemberGroupRepository groups;

    @Inject
    public DemoAdmissionSeeder(
            DocumentTemplateService documentTemplates,
            ProcedureRepository repo,
            ProcedureService procedures,
            RestrictionService restrictions,
            MemberGroupRepository groups) {
        this.documentTemplates = documentTemplates;
        this.repo = repo;
        this.procedures = procedures;
        this.restrictions = restrictions;
        this.groups = groups;
    }

    @Override
    public int order() {
        return FEDERATED_MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        int stationId = station.stationId();
        boolean present = repo.findTemplatesByStation(stationId, true).stream()
                .anyMatch(template -> NAME.equals(template.name()));
        if (present) return;
        var members = station.members();
        seed(stationId, station.adminMember(), members.betreuer(), members.anfaenger());
        log.info("Demo: Created the admission procedure for station {}", stationId);
    }

    private void seed(int stationId, StationMember admin, List<StationMember> betreuer, List<StationMember> anfaenger) {
        int author = Objects.requireNonNull(admin.accountId(), "the station administrator is seeded with an account");
        var warden = betreuer.get(DemoDocumentTemplateSeeder.WARDEN_PLACE);
        var owner = new Owner.Station(stationId);
        int application = documentTemplates.list(owner, false).stream()
                .filter(template -> DemoAdmissionTemplate.NAME.equals(template.name()))
                .map(DocumentTemplateService.DocumentTemplateSummary::id)
                .findFirst()
                .orElseGet(() -> documentTemplates
                        .create(owner, DemoAdmissionTemplate.request(warden.id()), author)
                        .id());

        var template = repo.createTemplate(
                stationId, NAME, "Aufnahme mit unterschriebenem Antrag, danach Ausrüstung und Einweisung.", admin.id());
        var sign = repo.createTemplateItem(
                template.id(),
                "Aufnahmeantrag unterschreiben",
                "Das Mitglied und bei Minderjährigen die Erziehungsberechtigten unterschreiben den Antrag.",
                true,
                false,
                0,
                StepKindChoice.document(application));
        var gear = repo.createTemplateItem(
                template.id(), "Ausrüstung ausgeben", "Einsatzjacke, Hose und Helm ausgeben.", true, true, 1);
        var hall = repo.createTemplateItem(
                template.id(),
                "Einweisung Fahrzeughalle",
                "Verhalten in der Fahrzeughalle und am Fahrzeug erklären.",
                true,
                true,
                2);
        repo.setTemplateItemDependencies(
                template.id(), List.of(new int[] {gear.id(), sign.id()}, new int[] {hall.id(), sign.id()}));
        var carers = groups.findByStation(stationId).stream()
                .filter(group -> CARERS.equals(group.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("the member band seeds the group " + CARERS));
        restrictions.setAudience(
                RestrictionType.PROCEDURE_TEMPLATE,
                template.id(),
                new RestrictionAudience(List.of(), List.of(carers.id()), List.of(), List.of(), RestrictionMode.AND));

        if (anfaenger.isEmpty()) return;
        var newcomer = anfaenger.getLast();
        procedures.createProcedure(
                stationId,
                template.id(),
                NAME + ": " + newcomer.displayName(),
                null,
                true,
                admin.id(),
                Instant.now().plus(14, ChronoUnit.DAYS),
                null,
                null,
                null,
                newcomer.id());
    }
}
