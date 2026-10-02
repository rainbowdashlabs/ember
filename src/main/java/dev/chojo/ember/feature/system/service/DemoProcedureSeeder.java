/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.procedure.entity.Procedure;
import dev.chojo.ember.feature.procedure.entity.ProcedureItem;
import dev.chojo.ember.feature.procedure.entity.ProcedureTemplateItem;
import dev.chojo.ember.feature.procedure.repository.ProcedureRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

@Singleton
public class DemoProcedureSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoProcedureSeeder.class);

    private final ProcedureRepository repo;

    @Inject
    public DemoProcedureSeeder(ProcedureRepository repo) {
        this.repo = repo;
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
                0);
        var ob2 = repo.createTemplateItem(
                onboarding.id(),
                "Mitgliedsbeitrag überweisen",
                "Jahresbeitrag muss auf das Vereinskonto eingehen.",
                true,
                true,
                1);
        repo.createTemplateItem(
                onboarding.id(),
                "Ärztliches Attest vorlegen",
                "Sporttauglichkeitsbescheinigung vom Arzt.",
                true,
                true,
                2);
        var ob4 = repo.createTemplateItem(
                onboarding.id(),
                "Kleidung ausgeben",
                "Einsatzjacke, Hose und Helm aus dem Lager ausgeben.",
                true,
                false,
                3);
        var ob5 = repo.createTemplateItem(
                onboarding.id(),
                "Sicherheitseinweisung durchführen",
                "Einweisung in Gerätehaus, Sammelplatz und Verhalten im Notfall.",
                true,
                false,
                4);
        var ob6 = repo.createTemplateItem(
                onboarding.id(), "In Gruppe einteilen", "Mitglied einer Übungsgruppe zuweisen.", true, false, 5);
        var ob7 = repo.createTemplateItem(
                onboarding.id(),
                "Probezeit abschließen",
                "Nach 3 Monaten Probezeit: Endgültige Aufnahme bestätigen.",
                true,
                false,
                6);

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
                equipment.id(), "Größe ermitteln", "Körpergröße und Konfektionsgröße aufnehmen.", true, false, 0);
        repo.createTemplateItem(
                equipment.id(), "Helm anpassen", "Passenden Helm auswählen und Größe einstellen.", true, false, 1);
        repo.createTemplateItem(
                equipment.id(), "Jacke ausgeben", "Einsatzjacke in passender Größe ausgeben.", true, false, 2);
        repo.createTemplateItem(
                equipment.id(), "Hose ausgeben", "Einsatzhose in passender Größe ausgeben.", true, false, 3);
        repo.createTemplateItem(
                equipment.id(), "Handschuhe ausgeben", "Passende Schutzhandschuhe ausgeben.", true, false, 4);
        repo.createTemplateItem(
                equipment.id(),
                "Inventar dokumentieren",
                "Alle ausgegebenen Gegenstände im Inventarsystem erfassen.",
                true,
                false,
                5);
        repo.createTemplateItem(
                equipment.id(),
                "Bestätigung unterschreiben",
                "Mitglied/Erziehungsberechtigte unterschreibt Empfangsbestätigung.",
                true,
                true,
                6);

        var completedProc = createFromTemplate(
                stationId,
                onboarding.id(),
                "Einführung: " + anfaenger.getFirst().displayName(),
                null,
                true,
                admin.id(),
                null);
        repo.addAssignee(completedProc.id(), anfaenger.getFirst().id());
        var items1 = repo.findItems(completedProc.id());
        for (var item : items1) {
            repo.checkItem(
                    item.id(),
                    item.userAssigned()
                            ? anfaenger.getFirst().id()
                            : betreuer.getFirst().id());
        }
        repo.resolveProcedure(completedProc.id());

        var inProgressProc = createFromTemplate(
                stationId,
                onboarding.id(),
                "Einführung: " + anfaenger.get(1).displayName(),
                null,
                true,
                admin.id(),
                Instant.now().plus(14, ChronoUnit.DAYS));
        repo.addAssignee(inProgressProc.id(), anfaenger.get(1).id());
        if (!betreuer.isEmpty()) {
            repo.addAssignee(inProgressProc.id(), betreuer.getFirst().id());
        }
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
                null);
        repo.addAssignee(overdueProc.id(), betreuer.get(0).id());
        if (betreuer.size() > 1) {
            repo.addAssignee(overdueProc.id(), betreuer.get(1).id());
        }
        var adHocItem1 = repo.createItem(
                overdueProc.id(), "Anbieter kontaktieren", "DRK oder Malteser wegen Termin anfragen.", true, true, 0);
        repo.createItem(overdueProc.id(), "Termin festlegen", "Termin mit der Gruppe abstimmen.", true, true, 1);
        repo.createItem(overdueProc.id(), "Teilnehmer anmelden", "Alle Mitglieder zum Kurs anmelden.", true, true, 2);
        repo.createItem(
                overdueProc.id(), "Kosten abrechnen", "Rechnung an den Förderverein weiterleiten.", true, false, 3);
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
                null);
        repo.addAssignee(privateProc.id(), admin.id());
        repo.createItem(privateProc.id(), "Mitgliederzahlen zusammenstellen", null, false, false, 0);
        repo.createItem(privateProc.id(), "Übungsstunden auswerten", null, false, false, 1);
        repo.createItem(privateProc.id(), "Veranstaltungen dokumentieren", null, false, false, 2);
        repo.createItem(privateProc.id(), "Bericht schreiben", null, false, false, 3);
        repo.createItem(privateProc.id(), "Bericht an KFV senden", null, false, false, 4);
    }

    private Procedure createFromTemplate(
            int stationId,
            int templateId,
            String name,
            @Nullable String description,
            boolean isPublic,
            int assignedBy,
            @Nullable Instant dueAt) {
        var procedure =
                repo.createProcedure(stationId, templateId, name, description, isPublic, assignedBy, dueAt, null, null);

        var templateItems = repo.findTemplateItems(templateId);
        var templateDeps = repo.findTemplateItemDependencies(templateId);

        var idMapping = new HashMap<Integer, Integer>();
        for (ProcedureTemplateItem item : templateItems) {
            ProcedureItem created = repo.snapshotTemplateItem(procedure.id(), item);
            idMapping.put(item.id(), created.id());
        }

        for (int[] dep : templateDeps) {
            Integer newItemId = idMapping.get(dep[0]);
            Integer newDependsOnId = idMapping.get(dep[1]);
            if (newItemId != null && newDependsOnId != null) {
                repo.addItemDependency(newItemId, newDependsOnId);
            }
        }

        return procedure;
    }
}
