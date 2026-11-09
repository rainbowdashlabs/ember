/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.generator.service.DocumentTemplateService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.procedure.entity.StepKindChoice;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import dev.chojo.ember.feature.procedure.service.ProcedureDraft;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
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
 * asked for, except that nobody is told it was handed to them.
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

    private final DocumentTemplateService documentTemplates;
    private final ProcedureRepository repo;
    private final ProcedureService procedures;
    private final RestrictionService restrictions;
    private final MemberIdentityFactory identities;

    @Inject
    public DemoAdmissionSeeder(
            DocumentTemplateService documentTemplates,
            ProcedureRepository repo,
            ProcedureService procedures,
            RestrictionService restrictions,
            MemberIdentityFactory identities) {
        this.documentTemplates = documentTemplates;
        this.repo = repo;
        this.procedures = procedures;
        this.restrictions = restrictions;
        this.identities = identities;
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
        seed(stationId, station.adminMember(), station.members());
        log.info("Demo: Created the admission procedure for station {}", stationId);
    }

    private void seed(int stationId, StationMember admin, DemoMemberSeeder.SeedResult members) {
        int author = Objects.requireNonNull(admin.accountId(), "the station administrator is seeded with an account");
        var warden = members.betreuer().get(DemoDocumentTemplateSeeder.WARDEN_PLACE);
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
                template.id(),
                "Ausrüstung ausgeben",
                "Einsatzjacke, Hose und Helm ausgeben.",
                true,
                true,
                1,
                StepKindChoice.CHECK);
        var hall = repo.createTemplateItem(
                template.id(),
                "Einweisung Fahrzeughalle",
                "Verhalten in der Fahrzeughalle und am Fahrzeug erklären.",
                true,
                true,
                2,
                StepKindChoice.CHECK);
        repo.setTemplateItemDependencies(
                template.id(), List.of(new int[] {gear.id(), sign.id()}, new int[] {hall.id(), sign.id()}));
        restrictions.setAudience(
                RestrictionType.PROCEDURE_TEMPLATE,
                template.id(),
                new RestrictionAudience(
                        List.of(), List.of(members.groupBetreuer().id()), List.of(), List.of(), RestrictionMode.AND));

        if (members.anfaenger().isEmpty()) return;
        var newcomer = members.anfaenger().getLast();
        procedures.createUnannounced(
                new ProcedureDraft(
                        stationId,
                        template.id(),
                        ProcedureService.namedAfter(NAME, nameOf(newcomer.id())),
                        null,
                        true,
                        admin.id(),
                        Instant.now().plus(14, ChronoUnit.DAYS),
                        null,
                        null,
                        null),
                newcomer.id());
    }

    private @Nullable String nameOf(int memberId) {
        var identity = identities.fromMemberId(memberId);
        return identity == null ? null : identity.name();
    }
}
