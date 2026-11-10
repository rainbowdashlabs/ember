/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.procedure.entity.StepKindChoice;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import dev.chojo.ember.feature.procedure.service.ProcedureDraft;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Singleton
public class DemoProcedureSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoProcedureSeeder.class);

    private final ProcedureRepository repo;
    private final RestrictionService restrictions;
    private final ProcedureService procedures;

    @Inject
    public DemoProcedureSeeder(ProcedureRepository repo, RestrictionService restrictions, ProcedureService procedures) {
        this.repo = repo;
        this.restrictions = restrictions;
        this.procedures = procedures;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = station.members();
        seed(station.stationId(), station.adminMember(), members.betreuer(), members.anfaenger(), new Random(42_020));
        log.info("Demo: Created Procedure data");
    }

    public void seed(
            int stationId,
            StationMember admin,
            List<StationMember> betreuer,
            List<StationMember> anfaenger,
            Random rng) {
        var onboarding = repo.createTemplate(
                stationId,
                "Einführung neues Mitglied",
                "Ablauf für die Aufnahme und Einführung eines neuen Mitglieds in die Jugendfeuerwehr.",
                admin.id());

        var ob1 = repo.createTemplateItem(
                onboarding.id(),
                "Anmeldeformular ausfüllen",
                "Eltern müssen das Anmeldeformular unterschrieben abgeben.",
                true,
                true,
                0,
                StepKindChoice.CHECK);
        var ob2 = repo.createTemplateItem(
                onboarding.id(),
                "Mitgliedsbeitrag überweisen",
                "Jahresbeitrag muss auf das Vereinskonto eingehen.",
                true,
                true,
                1,
                StepKindChoice.CHECK);
        repo.createTemplateItem(
                onboarding.id(),
                "Ärztliches Attest vorlegen",
                "Sporttauglichkeitsbescheinigung vom Arzt.",
                true,
                true,
                2,
                StepKindChoice.CHECK);
        var ob4 = repo.createTemplateItem(
                onboarding.id(),
                "Kleidung ausgeben",
                "Einsatzjacke, Hose und Helm aus dem Lager ausgeben.",
                true,
                false,
                3,
                StepKindChoice.CHECK);
        var ob5 = repo.createTemplateItem(
                onboarding.id(),
                "Sicherheitseinweisung durchführen",
                "Einweisung in Gerätehaus, Sammelplatz und Verhalten im Notfall.",
                true,
                false,
                4,
                StepKindChoice.CHECK);
        var ob6 = repo.createTemplateItem(
                onboarding.id(),
                "In Gruppe einteilen",
                "Mitglied einer Übungsgruppe zuweisen.",
                true,
                false,
                5,
                StepKindChoice.CHECK);
        var ob7 = repo.createTemplateItem(
                onboarding.id(),
                "Probezeit abschließen",
                "Nach 3 Monaten Probezeit: Endgültige Aufnahme bestätigen.",
                true,
                false,
                6,
                StepKindChoice.CHECK);

        repo.setTemplateItemDependencies(
                onboarding.id(),
                List.of(
                        new int[] {ob4.id(), ob1.id()},
                        new int[] {ob4.id(), ob2.id()},
                        new int[] {ob5.id(), ob4.id()},
                        new int[] {ob6.id(), ob5.id()},
                        new int[] {ob7.id(), ob6.id()}));

        var equipment = repo.createTemplate(
                stationId,
                "Ausrüstungsausgabe",
                "Checkliste für die Ausgabe persönlicher Schutzausrüstung.",
                admin.id());

        repo.createTemplateItem(
                equipment.id(),
                "Größe ermitteln",
                "Körpergröße und Konfektionsgröße aufnehmen.",
                true,
                false,
                0,
                StepKindChoice.CHECK);
        repo.createTemplateItem(
                equipment.id(),
                "Helm anpassen",
                "Passenden Helm auswählen und Größe einstellen.",
                true,
                false,
                1,
                StepKindChoice.CHECK);
        repo.createTemplateItem(
                equipment.id(),
                "Jacke ausgeben",
                "Einsatzjacke in passender Größe ausgeben.",
                true,
                false,
                2,
                StepKindChoice.CHECK);
        repo.createTemplateItem(
                equipment.id(),
                "Hose ausgeben",
                "Einsatzhose in passender Größe ausgeben.",
                true,
                false,
                3,
                StepKindChoice.CHECK);
        repo.createTemplateItem(
                equipment.id(),
                "Handschuhe ausgeben",
                "Passende Schutzhandschuhe ausgeben.",
                true,
                false,
                4,
                StepKindChoice.CHECK);
        repo.createTemplateItem(
                equipment.id(),
                "Inventar dokumentieren",
                "Alle ausgegebenen Gegenstände im Inventarsystem erfassen.",
                true,
                false,
                5,
                StepKindChoice.CHECK);
        repo.createTemplateItem(
                equipment.id(),
                "Bestätigung unterschreiben",
                "Mitglied/Erziehungsberechtigte unterschreibt Empfangsbestätigung.",
                true,
                true,
                6,
                StepKindChoice.CHECK);

        var completedProc = procedures.createUnannounced(
                new ProcedureDraft(
                        stationId,
                        onboarding.id(),
                        "Einführung: " + anfaenger.getFirst().displayName(),
                        null,
                        true,
                        admin.id(),
                        null,
                        partyOf(List.of(anfaenger.getFirst().id())),
                        null,
                        null),
                null);
        var items1 = repo.findItems(completedProc.id());
        for (var item : items1) {
            repo.checkItem(
                    item.id(),
                    item.userAssigned()
                            ? anfaenger.getFirst().id()
                            : betreuer.getFirst().id());
        }
        repo.resolveProcedure(completedProc.id(), betreuer.getFirst().id());

        var inProgressParty = new ArrayList<>(List.of(anfaenger.get(1).id()));
        if (!betreuer.isEmpty()) inProgressParty.add(betreuer.getFirst().id());
        var inProgressProc = procedures.createUnannounced(
                new ProcedureDraft(
                        stationId,
                        onboarding.id(),
                        "Einführung: " + anfaenger.get(1).displayName(),
                        null,
                        true,
                        admin.id(),
                        Instant.now().plus(14, ChronoUnit.DAYS),
                        partyOf(inProgressParty),
                        null,
                        null),
                null);
        var items2 = repo.findItems(inProgressProc.id());
        for (int i = 0; i < Math.min(3, items2.size()); i++) {
            repo.checkItem(items2.get(i).id(), anfaenger.get(1).id());
        }
        if (items2.size() > 3) {
            repo.updateItemNote(items2.get(3).id(), "Größe XS bestellt, wird nächste Woche geliefert.");
        }

        var overdueProc = repo.createProcedure(
                stationId,
                null,
                "Erste-Hilfe-Kurs organisieren",
                "Termin für den jährlichen Erste-Hilfe-Auffrischungskurs finden und buchen.",
                true,
                admin.id(),
                Instant.now().minus(3, ChronoUnit.DAYS),
                null,
                null,
                null);
        setParty(
                overdueProc.id(),
                betreuer.stream().limit(2).map(StationMember::id).toList());
        var adHocItem1 = repo.createItem(
                overdueProc.id(),
                "Anbieter kontaktieren",
                "DRK oder Malteser wegen Termin anfragen.",
                true,
                true,
                0,
                StepKindChoice.CHECK);
        repo.createItem(
                overdueProc.id(),
                "Termin festlegen",
                "Termin mit der Gruppe abstimmen.",
                true,
                true,
                1,
                StepKindChoice.CHECK);
        repo.createItem(
                overdueProc.id(),
                "Teilnehmer anmelden",
                "Alle Mitglieder zum Kurs anmelden.",
                true,
                true,
                2,
                StepKindChoice.CHECK);
        repo.createItem(
                overdueProc.id(),
                "Kosten abrechnen",
                "Rechnung an den Förderverein weiterleiten.",
                true,
                false,
                3,
                StepKindChoice.CHECK);
        repo.checkItem(adHocItem1.id(), betreuer.getFirst().id());

        var privateProc = repo.createProcedure(
                stationId,
                null,
                "Jahresbericht erstellen",
                "Vorbereitung und Abgabe des Jahresberichts an den Kreisfeuerwehrverband.",
                false,
                admin.id(),
                Instant.now().plus(30, ChronoUnit.DAYS),
                null,
                null,
                null);
        setParty(privateProc.id(), List.of(admin.id()));
        repo.createItem(
                privateProc.id(), "Mitgliederzahlen zusammenstellen", null, false, false, 0, StepKindChoice.CHECK);
        repo.createItem(privateProc.id(), "Übungsstunden auswerten", null, false, false, 1, StepKindChoice.CHECK);
        repo.createItem(privateProc.id(), "Veranstaltungen dokumentieren", null, false, false, 2, StepKindChoice.CHECK);
        repo.createItem(privateProc.id(), "Bericht schreiben", null, false, false, 3, StepKindChoice.CHECK);
        repo.createItem(privateProc.id(), "Bericht an KFV senden", null, false, false, 4, StepKindChoice.CHECK);
    }

    /** Gives a procedure a managing party naming the given members, all at once. */
    private void setParty(int procedureId, List<Integer> memberIds) {
        restrictions.setAudience(RestrictionType.PROCEDURE, procedureId, partyOf(memberIds));
    }

    private static RestrictionAudience partyOf(List<Integer> memberIds) {
        return new RestrictionAudience(List.of(), List.of(), List.of(), memberIds, RestrictionMode.AND);
    }
}
