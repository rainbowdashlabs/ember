/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * Seeds demo test protocol data modeled after the Jugendflamme Stufe 1 Prüfungsbogen.
 */
@Singleton
public class DemoProtocolSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoProtocolSeeder.class);
    private static final double ONE = 1.0;
    private static final double HALF = 0.5;

    private final TestProtocolRepository repo;

    @Inject
    public DemoProtocolSeeder(TestProtocolRepository repo) {
        this.repo = repo;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seed(DemoSeederContext context) {
        var testees = context.members().anfaenger().stream().map(m -> m.id()).toList();
        seed(context.stationId(), context.adminMember().id(), testees);
        log.info("Demo: Created Test Protocol data");
    }

    public void seed(int stationId, int createdBy, List<Integer> memberIds) {
        var protocol = repo.createProtocol(
                stationId,
                "Jugendflamme Stufe 1",
                "Prüfungsbogen für die Jugendflamme der Jugendfeuerwehr, Stufe 1.",
                50);

        // === Notruf (8P) ===
        var notruf = repo.createSection(protocol.id(), null, "Notruf", "", 8, null, 0);

        var wFragen = repo.createSection(protocol.id(), notruf.id(), "5 W-Fragen", "", 5, null, 0);
        repo.createItem(wFragen.id(), "Wo?", "", ONE, 0);
        repo.createItem(wFragen.id(), "Was?", "", ONE, 1);
        repo.createItem(wFragen.id(), "Wie viele?", "", ONE, 2);
        repo.createItem(wFragen.id(), "Wer?", "", ONE, 3);
        repo.createItem(wFragen.id(), "Warten?", "", ONE, 4);

        var notrufNummern = repo.createSection(protocol.id(), notruf.id(), "Notrufnummern", "", 3, null, 1);
        repo.createItem(notrufNummern.id(), "112", "", ONE, 0);
        repo.createItem(notrufNummern.id(), "110", "", ONE, 1);
        repo.createItem(notrufNummern.id(), "116117", "", ONE, 2);

        // === Knoten und Stiche (11P) ===
        var knoten = repo.createSection(protocol.id(), null, "Knoten und Stiche", "", 11, null, 1);

        var dreiVonVier = repo.createSection(protocol.id(), knoten.id(), "3 von 4 Knoten", "", 3, null, 0);
        repo.createItem(dreiVonVier.id(), "Mastwurf - Gelegt", "", HALF, 0);
        repo.createItem(dreiVonVier.id(), "Mastwurf - Gestochen", "", HALF, 1);
        repo.createItem(dreiVonVier.id(), "Ankerstich - Gelegt ohne Spierenstich", "", HALF, 2);
        repo.createItem(dreiVonVier.id(), "Ankerstich - Gestochen mit Spierenstich", "", HALF, 3);
        repo.createItem(dreiVonVier.id(), "Zimmermannsschlag", "", ONE, 4);
        repo.createItem(dreiVonVier.id(), "Achterknoten", "", ONE, 5);

        var unterschiede = repo.createSection(
                protocol.id(), knoten.id(), "Unterschiede Arbeitsleine und Feuerwehrleine", "", 3, null, 1);
        repo.createItem(unterschiede.id(), "Geprüft oder Ungeprüft", "", ONE, 0);
        repo.createItem(unterschiede.id(), "Farbe", "", ONE, 1);
        repo.createItem(unterschiede.id(), "Länge", "", ONE, 2);

        var sinnKnoten = repo.createSection(protocol.id(), knoten.id(), "Sinn und Zweck von Knoten", "", 3, null, 2);
        repo.createItem(sinnKnoten.id(), "Sichern", "", ONE, 0);
        repo.createItem(sinnKnoten.id(), "Anschlagen", "", ONE, 1);
        repo.createItem(sinnKnoten.id(), "Transportieren", "", ONE, 2);

        var einbinden = repo.createSection(protocol.id(), knoten.id(), "Einbinden eines Strahlrohres", "", 2, null, 3);
        repo.createItem(einbinden.id(), "Korrekt gesicherte Strahlrohrkupplung (Ankerstich oder Mastwurf)", "", ONE, 0);
        repo.createItem(einbinden.id(), "Korrekt gesichertes Strahlrohr", "", ONE, 1);

        // === Schläuche (15P) ===
        var schlauche = repo.createSection(protocol.id(), null, "Schläuche", "", 15, null, 2);

        var groessen =
                repo.createSection(protocol.id(), schlauche.id(), "Schlauchgrößen und Längen benennen", "", 4, null, 0);
        repo.createItem(groessen.id(), "D-Schlauch", "", ONE, 0);
        repo.createItem(groessen.id(), "C-Schlauch", "", ONE, 1);
        repo.createItem(groessen.id(), "B-Schlauch", "", ONE, 2);
        repo.createItem(groessen.id(), "A-Saugschlauch", "", ONE, 3);

        var teile = repo.createSection(protocol.id(), schlauche.id(), "3 Teile eines Schlauches", "", 3, null, 1);
        repo.createItem(teile.id(), "Kupplung", "", ONE, 0);
        repo.createItem(teile.id(), "Schlauch", "", ONE, 1);
        repo.createItem(teile.id(), "Dichtung", "", ONE, 2);

        var lagerung = repo.createSection(protocol.id(), schlauche.id(), "Lagerung eines Schlauches", "", 2, null, 2);
        repo.createItem(lagerung.id(), "Einfach", "", ONE, 0);
        repo.createItem(lagerung.id(), "Doppelt", "", ONE, 1);

        var auswerfen = repo.createSection(
                protocol.id(), schlauche.id(), "Schlauch Auswerfen - Korrekte Durchführung", "", 6, null, 3);
        repo.createItem(auswerfen.id(), "Aufnehmen", "", ONE, 0);
        repo.createItem(auswerfen.id(), "Tragen", "", ONE, 1);
        repo.createItem(auswerfen.id(), "Auswerfen", "", ONE, 2);
        repo.createItem(auswerfen.id(), "Auslegen", "", ONE, 3);
        repo.createItem(auswerfen.id(), "Kuppeln", "", ONE, 4);
        repo.createItem(auswerfen.id(), "Aufrollen", "", ONE, 5);

        // === Verteiler (10P) ===
        var verteiler = repo.createSection(protocol.id(), null, "Verteiler", "", 10, null, 3);

        var verteilerBenennung =
                repo.createSection(protocol.id(), verteiler.id(), "Benennung der Verteiler", "", 2, null, 0);
        repo.createItem(verteilerBenennung.id(), "(B-)CBC", "", ONE, 0);
        repo.createItem(verteilerBenennung.id(), "(C-)DCD", "", ONE, 1);

        var rohreBenennung =
                repo.createSection(protocol.id(), verteiler.id(), "Benennung der Rohre und Teile", "", 4, null, 1);
        repo.createItem(rohreBenennung.id(), "1. Rohr", "", ONE, 0);
        repo.createItem(rohreBenennung.id(), "2. Rohr", "", ONE, 1);
        repo.createItem(rohreBenennung.id(), "3. Rohr / Sonderrohr", "", ONE, 2);
        repo.createItem(rohreBenennung.id(), "Übergangsstück BC", "", ONE, 3);

        var ventile = repo.createSection(
                protocol.id(), verteiler.id(), "Erkennung unterschiedlicher Ventile", "", 2, null, 2);
        repo.createItem(ventile.id(), "Niederschraubventil", "", ONE, 0);
        repo.createItem(ventile.id(), "Kugelabsperrventil", "", ONE, 1);

        var sinnVerteiler = repo.createSection(protocol.id(), verteiler.id(), "Sinn und Zweck", "", 2, null, 3);
        repo.createItem(sinnVerteiler.id(), "Kontrollierbare Wasserversorgung", "", ONE, 0);

        // === Strahlrohr (11P) ===
        var strahlrohr = repo.createSection(protocol.id(), null, "Strahlrohr", "", 11, null, 4);

        var strahlrohrName = repo.createSection(protocol.id(), strahlrohr.id(), "Name", "", 1, null, 0);
        repo.createItem(strahlrohrName.id(), "Mehrzweckstrahlrohr", "", ONE, 0);

        var funktionen =
                repo.createSection(protocol.id(), strahlrohr.id(), "Funktionen mit korrekter Stellung", "", 3, null, 1);
        repo.createItem(funktionen.id(), "Vollstrahl", "", ONE, 0);
        repo.createItem(funktionen.id(), "Sprühstrahl", "", ONE, 1);
        repo.createItem(funktionen.id(), "Halt", "", ONE, 2);

        var mundstueck = repo.createSection(protocol.id(), strahlrohr.id(), "Wirkungsweise Mundstück", "", 1, null, 2);
        repo.createItem(mundstueck.id(), "Verdoppelung der Durchflussmenge bei Abnahme", "", ONE, 0);

        var wasserabgabe = repo.createSection(
                protocol.id(), strahlrohr.id(), "Wasserabgabe mit und ohne Mundstück", "", 6, null, 3);
        repo.createItem(wasserabgabe.id(), "D mit Mundstück", "", ONE, 0);
        repo.createItem(wasserabgabe.id(), "B mit Mundstück", "", ONE, 1);
        repo.createItem(wasserabgabe.id(), "C ohne Mundstück", "", ONE, 2);
        repo.createItem(wasserabgabe.id(), "D ohne Mundstück", "", ONE, 3);
        repo.createItem(wasserabgabe.id(), "B ohne Mundstück", "", ONE, 4);
        repo.createItem(wasserabgabe.id(), "C mit Mundstück", "", ONE, 5);

        // === Erste Hilfe (12P) ===
        var ersteHilfe = repo.createSection(protocol.id(), null, "Erste Hilfe", "", 12, null, 5);

        var rettungskette = repo.createSection(protocol.id(), ersteHilfe.id(), "Rettungskette", "", 5, null, 0);
        repo.createItem(rettungskette.id(), "Absichern & Eigenschutz", "", ONE, 0);
        repo.createItem(rettungskette.id(), "Notruf & Sofortmaßnahmen", "", ONE, 1);
        repo.createItem(rettungskette.id(), "Erste Hilfe", "", ONE, 2);
        repo.createItem(rettungskette.id(), "Rettungsdienst", "", ONE, 3);
        repo.createItem(rettungskette.id(), "Krankenhaus", "", ONE, 4);

        var rettungsgeraete =
                repo.createSection(protocol.id(), ersteHilfe.id(), "Nenne mind. 4 Rettungsgeräte", "", 4, null, 1);
        repo.createItem(rettungsgeraete.id(), "Schleifkorbtrage", "", ONE, 0);
        repo.createItem(rettungsgeraete.id(), "Spineboard", "", ONE, 1);
        repo.createItem(rettungsgeraete.id(), "Tragetuch", "", ONE, 2);
        repo.createItem(rettungsgeraete.id(), "Tragestuhl / Trage / Schaufeltrage", "", ONE, 3);

        var druckverband = repo.createSection(
                protocol.id(), ersteHilfe.id(), "Praxis: Lege einen Druckverband an", "", 3, null, 2);
        repo.createItem(druckverband.id(), "Korrekte Ausführung", "", ONE, 0);

        // === Unterflurhydrant (5P) ===
        var hydrant = repo.createSection(protocol.id(), null, "Unterflurhydrant", "", 5, null, 6);

        var hydrantSchild = repo.createSection(
                protocol.id(), hydrant.id(), "Benennung der Zahlen eines Hydrantenschildes", "", 4, null, 0);
        repo.createItem(hydrantSchild.id(), "„H“ für Hydrant", "", ONE, 0);
        repo.createItem(hydrantSchild.id(), "Meter nach rechts", "", ONE, 1);
        repo.createItem(hydrantSchild.id(), "Meter nach links", "", ONE, 2);
        repo.createItem(hydrantSchild.id(), "Meter nach vorne", "", ONE, 3);

        var hydrantFinden = repo.createSection(
                protocol.id(),
                hydrant.id(),
                "Beispielhaftes Auffinden eines Hydranten anhand eines Schildes",
                "",
                1,
                null,
                1);
        repo.createItem(hydrantFinden.id(), "Korrekt gefunden", "", ONE, 0);

        // === Open run for 2026 ===
        if (!memberIds.isEmpty()) {
            var run = repo.createRun(protocol.id(), stationId, "Übungsprüfung 2026", LocalDate.now(), createdBy);
            for (int memberId : memberIds) {
                repo.addRunMember(run.id(), memberId);
            }
        }

        // === Completed run from 2025 ===
        if (!memberIds.isEmpty()) {
            var allItems = repo.findAllItemsByProtocol(protocol.id());
            var rng = new Random(42);

            var lastYear = LocalDate.now().minusYears(1);
            var pastRun = repo.createRun(
                    protocol.id(),
                    stationId,
                    "Jugendflamme " + lastYear.getYear(),
                    LocalDate.of(lastYear.getYear(), 6, 14),
                    createdBy);

            for (int memberId : memberIds) {
                var rm = repo.addRunMember(pastRun.id(), memberId);

                // Randomly check 60-95% of items
                double passRate = 0.60 + rng.nextDouble() * 0.35;
                double score = 0;
                for (var item : allItems) {
                    boolean passed = rng.nextDouble() < passRate;
                    repo.upsertCheck(rm.id(), item.id(), passed, createdBy);
                    if (passed) score += item.points();
                }

                // Mark all top-level sections as done
                var sections = repo.findSections(protocol.id());
                for (var s : sections) {
                    if (s.parentId() == null) {
                        repo.markSectionDone(rm.id(), s.id(), createdBy);
                    }
                }

                repo.completeMember(rm.id(), score);
            }
            repo.closeRun(pastRun.id());
        }
    }
}
