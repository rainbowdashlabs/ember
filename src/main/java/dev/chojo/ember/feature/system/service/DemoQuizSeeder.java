/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.quiz.entity.QuizQuestion;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import dev.chojo.ember.feature.quiz.service.QuizService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeder for demo quiz catalogs, questions, and tests.
 */
@Singleton
public class DemoQuizSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoQuizSeeder.class);
    private final QuizCatalogRepository quizCatalogRepository;
    private final QuizTestRepository quizTestRepository;
    private final QuizService quizService;
    private final ImageService imageService;

    @Inject
    public DemoQuizSeeder(
            QuizCatalogRepository quizCatalogRepository,
            QuizTestRepository quizTestRepository,
            QuizService quizService,
            ImageService imageService) {
        this.quizCatalogRepository = quizCatalogRepository;
        this.quizTestRepository = quizTestRepository;
        this.quizService = quizService;
        this.imageService = imageService;
    }

    public void seedQuiz(int stationId, int createdBy, List<Integer> memberIds) {
        // -- Anfänger-Katalog (40+ questions) --
        var anfaengerCatalog = quizCatalogRepository.create(
                stationId, "Anfänger-Wissen", "Grundlagenwissen für neue Mitglieder der Jugendfeuerwehr", true);
        var catGrundlagen =
                quizCatalogRepository.createCategory(stationId, "Grundlagen", "Allgemeine Grundlagen der Feuerwehr", 0);
        var catAusruestung = quizCatalogRepository.createCategory(
                stationId, "Ausrüstung", "Persönliche und technische Ausrüstung", 1);
        var catVerhalten = quizCatalogRepository.createCategory(
                stationId, "Verhalten im Einsatz", "Richtiges Verhalten bei Brand und Notfall", 2);
        var catNotruf = quizCatalogRepository.createCategory(
                stationId, "Notruf & Erste Hilfe", "Notruf absetzen und Erste Hilfe leisten", 3);

        // Grundlagen (MC)
        createMcQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Was ist die Hauptaufgabe der Feuerwehr?",
                0,
                List.of("Brände löschen", "Retten", "Bergen", "Schützen"),
                List.of(0, 1, 2, 3),
                0.5);
        createMcQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Welche Notrufnummer gilt in ganz Europa?",
                1,
                List.of("112", "110", "911", "118"),
                List.of(0),
                0.5);
        createMcQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Ab welchem Alter kann man in die Jugendfeuerwehr eintreten?",
                2,
                List.of("10 Jahre", "8 Jahre", "12 Jahre", "6 Jahre"),
                List.of(0),
                0.5);
        createMcQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Was bedeutet die Abkürzung 'FW'?",
                3,
                List.of("Feuerwehr", "Freiwillige Wache", "Feuerwache", "Feuerwehrwagen"),
                List.of(0),
                0.5);
        createTfQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Die Feuerwehr ist nur für das Löschen von Bränden zuständig.",
                4,
                false);
        createTfQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Jede Gemeinde in Deutschland muss eine Feuerwehr haben.",
                5,
                true);
        createMcQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Welche Arten von Feuerwehren gibt es in Deutschland?",
                6,
                List.of("Berufsfeuerwehr", "Freiwillige Feuerwehr", "Werkfeuerwehr", "Feuerwehr-Polizei"),
                List.of(0, 1, 2),
                0.5);
        createFreeQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Nenne drei Aufgaben der Feuerwehr.",
                7,
                3,
                List.of("Retten", "Löschen", "Bergen", "Schützen"));
        createMcQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Wer leitet einen Feuerwehreinsatz?",
                8,
                List.of("Der Einsatzleiter", "Der Bürgermeister", "Der Feuerwehrmann", "Der Polizist"),
                List.of(0),
                0.5);
        createTfQuestion(
                anfaengerCatalog.id(),
                catGrundlagen.id(),
                "Die Jugendfeuerwehr darf an echten Einsätzen teilnehmen.",
                9,
                false);

        // Ausrüstung (MC + Connect + Ordering)
        createMcQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Welches Gerät wird zum Löschen von Bränden verwendet?",
                0,
                List.of("C-Rohr", "Axt", "Spreizer", "Rettungsschere"),
                List.of(0),
                0.5);
        createMcQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Was gehört zur persönlichen Schutzausrüstung?",
                1,
                List.of("Helm", "Handschuhe", "Stiefel", "Sandalen"),
                List.of(0, 1, 2),
                0.5);
        createConnectQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Ordne die Ausrüstung ihrer Funktion zu.",
                2,
                List.of("C-Rohr", "Spreizer", "Rettungsleine", "Atemschutzgerät"),
                List.of("Wasser spritzen", "Fahrzeug öffnen", "Person sichern", "Atemschutz"));
        createMcQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Welche Farbe hat ein Feuerwehrhelm in Deutschland üblicherweise?",
                3,
                List.of("Gelb/Neongelb", "Rot", "Blau", "Schwarz"),
                List.of(0),
                0.5);
        createOrderingQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Bringe die Schutzausrüstung in die richtige Anziehreihenfolge.",
                4,
                List.of("Hose", "Stiefel", "Jacke", "Handschuhe", "Helm"));
        createMcQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Was ist ein Strahlrohr?",
                5,
                List.of("Ein Gerät zum Löschen", "Eine Leuchte", "Ein Messgerät", "Ein Funkgerät"),
                List.of(0),
                0.5);
        createTfQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Ein D-Schlauch hat einen größeren Durchmesser als ein C-Schlauch.",
                6,
                false);
        createFillBlankQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Der ___ wird zum Schutz des Kopfes getragen. ___ schützen die Hände vor Verletzungen.",
                7,
                List.of("Helm", "Handschuhe"));
        createMcQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Welcher Schlauch wird am häufigsten bei Löscharbeiten verwendet?",
                8,
                List.of("C-Schlauch", "A-Schlauch", "B-Schlauch", "D-Schlauch"),
                List.of(0),
                0.5);
        createConnectQuestion(
                anfaengerCatalog.id(),
                catAusruestung.id(),
                "Ordne die Schlauchbezeichnungen dem Durchmesser zu.",
                9,
                List.of("A-Schlauch", "B-Schlauch", "C-Schlauch", "D-Schlauch"),
                List.of("110 mm", "75 mm", "52 mm", "25 mm"));

        // Verhalten im Einsatz
        createMcQuestion(
                anfaengerCatalog.id(),
                catVerhalten.id(),
                "Was solltest du als Erstes tun, wenn du Rauch in einem Gebäude bemerkst?",
                0,
                List.of("Gebäude verlassen und Notruf wählen", "Fenster öffnen", "Feuer selbst löschen", "Abwarten"),
                List.of(0),
                0.5);
        createTfQuestion(
                anfaengerCatalog.id(), catVerhalten.id(), "Bei einem Brand sollte man den Aufzug benutzen.", 1, false);
        createMcQuestion(
                anfaengerCatalog.id(),
                catVerhalten.id(),
                "Wie verhältst du dich, wenn deine Kleidung Feuer fängt?",
                2,
                List.of("Hinlegen, Rollen, Flammen ersticken", "Weglaufen", "Wasser suchen", "Kleidung ausziehen"),
                List.of(0),
                0.5);
        createOrderingQuestion(
                anfaengerCatalog.id(),
                catVerhalten.id(),
                "Bringe die Schritte bei Brandentdeckung in die richtige Reihenfolge.",
                3,
                List.of(
                        "Brand melden",
                        "Personen warnen",
                        "Gebäude verlassen",
                        "Am Sammelplatz melden",
                        "Auf die Feuerwehr warten"));
        createFreeQuestion(
                anfaengerCatalog.id(),
                catVerhalten.id(),
                "Erkläre, warum man bei einem Brand nicht den Aufzug benutzen soll.",
                4,
                3,
                List.of());
        createTfQuestion(
                anfaengerCatalog.id(),
                catVerhalten.id(),
                "Türen sollte man beim Verlassen eines brennenden Gebäudes schließen.",
                5,
                true);
        createMcQuestion(
                anfaengerCatalog.id(),
                catVerhalten.id(),
                "Was ist ein Sammelplatz?",
                6,
                List.of(
                        "Ein festgelegter Treffpunkt nach einer Evakuierung",
                        "Ein Lagerraum",
                        "Der Eingang des Gebäudes",
                        "Die Feuerwache"),
                List.of(0),
                0.5);
        createFillBlankQuestion(
                anfaengerCatalog.id(),
                catVerhalten.id(),
                "Bei ___ Sicht kriecht man am Boden, weil der Rauch nach ___ steigt.",
                7,
                List.of("schlechter", "oben"));
        createTfQuestion(
                anfaengerCatalog.id(),
                catVerhalten.id(),
                "Man sollte eine geschlossene Tür im Brandfall erst anfassen, bevor man sie öffnet.",
                8,
                true);
        createMcQuestion(
                anfaengerCatalog.id(),
                catVerhalten.id(),
                "Warum sollte man Türen im Brandfall geschlossen halten?",
                9,
                List.of(
                        "Um die Ausbreitung von Feuer und Rauch zu verlangsamen",
                        "Damit niemand reinkommt",
                        "Aus Gewohnheit",
                        "Um Zugluft zu vermeiden"),
                List.of(0),
                0.5);

        // Notruf & Erste Hilfe
        createMcQuestion(
                anfaengerCatalog.id(),
                catNotruf.id(),
                "Welche Informationen gehören zu einem Notruf?",
                0,
                List.of("Wer ruft an?", "Was ist passiert?", "Wo ist es passiert?", "Wie heißt der Nachbar?"),
                List.of(0, 1, 2),
                0.5);
        createOrderingQuestion(
                anfaengerCatalog.id(),
                catNotruf.id(),
                "Bringe die 5 W-Fragen des Notrufs in die richtige Reihenfolge.",
                1,
                List.of("Wo?", "Was?", "Wie viele?", "Welche Verletzungen?", "Warten auf Rückfragen"));
        createMcQuestion(
                anfaengerCatalog.id(),
                catNotruf.id(),
                "Was ist die stabile Seitenlage?",
                2,
                List.of(
                        "Eine Lagerung für bewusstlose Personen",
                        "Eine Übung",
                        "Ein Feuerwehrkommando",
                        "Eine Schlafposition"),
                List.of(0),
                0.5);
        createTfQuestion(anfaengerCatalog.id(), catNotruf.id(), "Ein Notruf über 112 ist immer kostenlos.", 3, true);
        createFreeQuestion(
                anfaengerCatalog.id(),
                catNotruf.id(),
                "Beschreibe die stabile Seitenlage in eigenen Worten.",
                4,
                5,
                List.of());
        createConnectQuestion(
                anfaengerCatalog.id(),
                catNotruf.id(),
                "Ordne die Notrufnummern den richtigen Diensten zu.",
                5,
                List.of("112", "110", "116117", "0800 111 0 111"),
                List.of("Feuerwehr/Rettungsdienst", "Polizei", "Ärztlicher Bereitschaftsdienst", "Telefonseelsorge"));
        createMcQuestion(
                anfaengerCatalog.id(),
                catNotruf.id(),
                "Was sollte man NICHT bei einer Verbrennung tun?",
                6,
                List.of("Butter auftragen", "Mit Wasser kühlen", "Verband anlegen", "Arzt aufsuchen"),
                List.of(0),
                0.5);
        createTfQuestion(
                anfaengerCatalog.id(),
                catNotruf.id(),
                "Bei Bewusstlosigkeit sollte man den Kopf überstrecken, um die Atemwege freizumachen.",
                7,
                true);
        createFillBlankQuestion(
                anfaengerCatalog.id(),
                catNotruf.id(),
                "Die ___ Seitenlage schützt bewusstlose Personen vor dem ___.",
                8,
                List.of("stabile", "Ersticken"));
        createMcQuestion(
                anfaengerCatalog.id(),
                catNotruf.id(),
                "Wann darfst du die Reanimation beenden?",
                9,
                List.of("Wenn der Rettungsdienst übernimmt", "Wenn man müde wird", "Nach 5 Minuten", "Gar nicht"),
                List.of(0),
                0.5);

        // -- Fortgeschrittenen-Katalog (20+ questions) --
        var fortCatalog = quizCatalogRepository.create(
                stationId,
                "Fortgeschrittenen-Wissen",
                "Vertiefendes Wissen für erfahrene Jugendfeuerwehr-Mitglieder",
                true);
        var catBrandlehre = quizCatalogRepository.createCategory(
                stationId, "Brandlehre", "Verbrennungsdreieck, Brandklassen und Löschmittel", 4);
        var catTaktik = quizCatalogRepository.createCategory(
                stationId, "Einsatztaktik", "Löschgruppe, Truppaufgaben und Einsatzablauf", 5);
        var catTechnik = quizCatalogRepository.createCategory(
                stationId, "Fahrzeug & Technik", "Fahrzeugtypen, Geräte und Technik", 6);

        // Brandlehre
        createMcQuestion(
                fortCatalog.id(),
                catBrandlehre.id(),
                "Welche Voraussetzungen braucht ein Feuer (Verbrennungsdreieck)?",
                0,
                List.of("Brennstoff", "Sauerstoff", "Zündtemperatur", "Wasser"),
                List.of(0, 1, 2),
                0.5);
        createMcQuestion(
                fortCatalog.id(),
                catBrandlehre.id(),
                "Welche Brandklasse umfasst brennbare feste Stoffe?",
                1,
                List.of("A", "B", "C", "D"),
                List.of(0),
                0.5);
        createConnectQuestion(
                fortCatalog.id(),
                catBrandlehre.id(),
                "Ordne die Brandklassen den Stoffgruppen zu.",
                2,
                List.of("A", "B", "C", "D", "F"),
                List.of("Feste Stoffe", "Flüssige Stoffe", "Gasförmige Stoffe", "Metalle", "Speisefette"));
        createMcQuestion(
                fortCatalog.id(),
                catBrandlehre.id(),
                "Welches Löschmittel ist für Fettbrände geeignet?",
                3,
                List.of("Spezieller Fettbrandlöscher", "Wasser", "CO2-Löscher", "Schaum"),
                List.of(0),
                0.5);
        createTfQuestion(
                fortCatalog.id(),
                catBrandlehre.id(),
                "Wasser ist für alle Brandklassen als Löschmittel geeignet.",
                4,
                false);
        createFreeQuestion(
                fortCatalog.id(),
                catBrandlehre.id(),
                "Erkläre das Verbrennungsdreieck und wie man ein Feuer löscht.",
                5,
                5,
                List.of());
        createMcQuestion(
                fortCatalog.id(),
                catBrandlehre.id(),
                "Was passiert bei einer Rauchgasdurchzündung (Flashover)?",
                6,
                List.of(
                        "Alle brennbaren Oberflächen entzünden sich gleichzeitig",
                        "Das Feuer erlischt",
                        "Es entsteht eine Explosion",
                        "Der Rauch wird klar"),
                List.of(0),
                0.5);
        createTfQuestion(
                fortCatalog.id(), catBrandlehre.id(), "CO2 löscht Feuer, indem es den Sauerstoff verdrängt.", 7, true);

        // Einsatztaktik
        createMcQuestion(
                fortCatalog.id(),
                catTaktik.id(),
                "Was bedeutet 'Angriffstrupp'?",
                0,
                List.of(
                        "Trupp, der das Feuer direkt bekämpft",
                        "Trupp, der draußen wartet",
                        "Der Gruppenführer",
                        "Ein Rettungstrupp"),
                List.of(0),
                0.5);
        createOrderingQuestion(
                fortCatalog.id(),
                catTaktik.id(),
                "Bringe die Einsatzphasen in die richtige Reihenfolge.",
                1,
                List.of("Alarmierung", "Anfahrt", "Erkundung", "Befehlsgebung", "Einsatzausführung", "Einsatzende"));
        createMcQuestion(
                fortCatalog.id(),
                catTaktik.id(),
                "Wie viele Feuerwehrleute bilden eine Löschgruppe?",
                2,
                List.of("9 (1/8)", "6 (1/5)", "12 (1/11)", "3 (1/2)"),
                List.of(0),
                0.5);
        createConnectQuestion(
                fortCatalog.id(),
                catTaktik.id(),
                "Ordne die Truppbezeichnungen ihren Aufgaben zu.",
                3,
                List.of("Angriffstrupp", "Wassertrupp", "Schlauchtrupp", "Melder"),
                List.of(
                        "Brandbekämpfung vorne",
                        "Wasserversorgung",
                        "Schlauchverlegung",
                        "Verbindung zum Gruppenführer"));
        createFreeQuestion(
                fortCatalog.id(),
                catTaktik.id(),
                "Beschreibe die Aufgabenverteilung in einer Löschgruppe.",
                4,
                5,
                List.of());
        createTfQuestion(
                fortCatalog.id(), catTaktik.id(), "Der Melder ist für die Schlauchverlegung zuständig.", 5, false);
        createMcQuestion(
                fortCatalog.id(),
                catTaktik.id(),
                "Was ist ein Bereitstellungsraum?",
                6,
                List.of(
                        "Ein Bereich, in dem Einsatzkräfte auf ihren Einsatz warten",
                        "Das Feuerwehrhaus",
                        "Ein Lagerraum",
                        "Die Einsatzleitung"),
                List.of(0),
                0.5);
        createFillBlankQuestion(
                fortCatalog.id(),
                catTaktik.id(),
                "Der ___ leitet den Einsatz. Der ___ bekämpft das Feuer direkt.",
                7,
                List.of("Gruppenführer", "Angriffstrupp"));

        // Fahrzeug & Technik
        createMcQuestion(
                fortCatalog.id(),
                catTechnik.id(),
                "Wofür steht die Abkürzung 'HLF 20'?",
                0,
                List.of(
                        "Hilfeleistungslöschgruppenfahrzeug 20",
                        "Hauptlöschfahrzeug 20",
                        "Hilfsleiterfahrzeug 20",
                        "Hochleistungsfahrzeug 20"),
                List.of(0),
                0.5);
        createConnectQuestion(
                fortCatalog.id(),
                catTechnik.id(),
                "Ordne die Fahrzeugtypen ihrer Hauptaufgabe zu.",
                1,
                List.of("TLF", "DLK", "RW", "ELW"),
                List.of("Tanklöschfahrzeug", "Drehleiter mit Korb", "Rüstwagen", "Einsatzleitwagen"));
        createMcQuestion(
                fortCatalog.id(),
                catTechnik.id(),
                "Was ist eine Tragkraftspritze?",
                2,
                List.of("Eine tragbare Pumpe", "Ein Feuerlöscher", "Ein Wasserwerfer", "Ein Hydrant"),
                List.of(0),
                0.5);
        createTfQuestion(
                fortCatalog.id(),
                catTechnik.id(),
                "Ein Tanklöschfahrzeug (TLF) hat einen eigenen Wassertank.",
                3,
                true);
        createMcQuestion(
                fortCatalog.id(),
                catTechnik.id(),
                "Wie viel Wasser fasst ein TLF 4000 ungefähr?",
                4,
                List.of("4000 Liter", "2000 Liter", "1000 Liter", "6000 Liter"),
                List.of(0),
                0.5);
        createFillBlankQuestion(
                fortCatalog.id(),
                catTechnik.id(),
                "Die Abkürzung DLK steht für Drehleiter mit ___. Ein ELW ist ein ___.",
                5,
                List.of("Korb", "Einsatzleitwagen"));

        // -- Test: Jugendfeuerwehr-Prüfung --
        var test = quizTestRepository.create(
                stationId,
                "Jugendfeuerwehr-Prüfung",
                "Wissensprüfung mit Fragen für Anfänger und Fortgeschrittene",
                45,
                false,
                createdBy);
        // Section 1: Anfänger (20 questions from anfänger catalog)
        var section1 =
                quizTestRepository.createSection(test.id(), "Grundwissen", "20 Fragen aus dem Anfänger-Katalog", 0);
        quizTestRepository.createSource(section1.id(), anfaengerCatalog.id(), null, 20);
        // Section 2: Fortgeschrittene (10 questions from fort catalog)
        var section2 = quizTestRepository.createSection(
                test.id(), "Fortgeschrittenen-Wissen", "10 Fragen aus dem Fortgeschrittenen-Katalog", 1);
        quizTestRepository.createSource(section2.id(), fortCatalog.id(), null, 10);
        // Set start/end dates and activate the test
        quizService.updateTest(
                test.id(),
                test.title(),
                test.description(),
                test.timeLimit(),
                test.shuffle(),
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS));
        quizService.activateTest(test.id());

        // -- Test 2: Draft test (not yet active) --
        var draftTest = quizTestRepository.create(
                stationId,
                "Brandschutz-Übungstest",
                "Übungstest zum Thema Brandlehre und Einsatztaktik (noch nicht freigegeben)",
                30,
                true,
                createdBy);
        var draftSection1 = quizTestRepository.createSection(draftTest.id(), "Brandlehre", "Fragen zur Brandlehre", 0);
        quizTestRepository.createSource(draftSection1.id(), fortCatalog.id(), catBrandlehre.id(), 5);
        var draftSection2 =
                quizTestRepository.createSection(draftTest.id(), "Einsatztaktik", "Fragen zur Einsatztaktik", 1);
        quizTestRepository.createSource(draftSection2.id(), fortCatalog.id(), catTaktik.id(), 5);

        // -- Test 3: Showcase test with every question type --
        var showcaseCatalog = quizCatalogRepository.create(
                stationId, "Showcase-Katalog", "Ein Katalog mit je einer Frage pro Typ", true);
        var catShowcase = quizCatalogRepository.createCategory(stationId, "Showcase", "Demo aller Fragetypen", 7);

        createMcQuestion(
                showcaseCatalog.id(),
                catShowcase.id(),
                "Welche Farben hat die deutsche Flagge?",
                0,
                List.of("Schwarz", "Rot", "Gold", "Blau", "Weiß"),
                List.of(0, 1, 2),
                0.5);
        createTfQuestion(showcaseCatalog.id(), catShowcase.id(), "Die Feuerwehr nutzt blaues Blinklicht.", 1, true);
        createFreeQuestion(
                showcaseCatalog.id(),
                catShowcase.id(),
                "Beschreibe die Aufgaben der Feuerwehr.",
                2,
                4,
                List.of("Retten, Löschen, Bergen, Schützen"));
        createFillBlankQuestion(
                showcaseCatalog.id(),
                catShowcase.id(),
                "Fülle die Lücken aus.",
                "Die Feuerwehr löscht ___ und rettet ___.",
                3,
                List.of("Brände", "Menschen"));
        createFillBlankQuestionWithDistractors(
                showcaseCatalog.id(),
                catShowcase.id(),
                "Setze die richtigen Wörter ein.",
                "Der ___ schützt den Kopf und die ___ schützen die Hände.",
                4,
                List.of("Helm", "Handschuhe"),
                List.of("Stiefel", "Jacke"));
        // IMAGE_TEXT question with actual image
        var imageTextQuestion = quizCatalogRepository.createQuestion(
                showcaseCatalog.id(),
                catShowcase.id(),
                QuizQuestionType.IMAGE_TEXT,
                "Was siehst du auf dem Bild?",
                "Beschreibe das abgebildete Logo.",
                "uploaded",
                2,
                false,
                "{\"answer\":\"Das Ember-Logo der Jugendfeuerwehr-Plattform.\"}",
                5);
        try (var logoStream = getClass().getResourceAsStream("/logo/NoBG_NoGlow.png")) {
            if (logoStream != null) {
                imageService.store(
                        ImageCategory.QUIZ_QUESTIONS,
                        String.valueOf(imageTextQuestion.id()),
                        logoStream.readAllBytes(),
                        "image/png");
            }
        } catch (IOException e) {
            log.warn("Failed to store demo quiz question image", e);
        }
        createConnectQuestion(
                showcaseCatalog.id(),
                catShowcase.id(),
                "Ordne die Begriffe richtig zu.",
                6,
                List.of("Helm", "Schlauch", "Leiter", "Funkgerät"),
                List.of("Kopfschutz", "Wasserförderung", "Rettung aus Höhen", "Kommunikation"));
        createOrderingQuestion(
                showcaseCatalog.id(),
                catShowcase.id(),
                "Bringe die Schritte in die richtige Reihenfolge.",
                7,
                List.of("Notruf wählen", "Lage schildern", "Rückfragen abwarten", "Auflegen"));

        var showcaseTest = quizTestRepository.create(
                stationId,
                "Showcase: Alle Fragetypen",
                "Dieser Test zeigt alle verfügbaren Fragetypen",
                20,
                false,
                createdBy);
        var showcaseSection =
                quizTestRepository.createSection(showcaseTest.id(), "Alle Typen", "Je eine Frage pro Fragetyp", 0);
        quizTestRepository.createSource(showcaseSection.id(), showcaseCatalog.id(), null, 0);
        quizService.updateTest(
                showcaseTest.id(),
                showcaseTest.title(),
                showcaseTest.description(),
                showcaseTest.timeLimit(),
                showcaseTest.shuffle(),
                Instant.now().minus(3, ChronoUnit.DAYS),
                Instant.now().plus(60, ChronoUnit.DAYS));
        quizService.activateTest(showcaseTest.id());

        // -- Demo attempts for the main test --
        seedAttempts(test.id(), memberIds);

        // -- Demo attempt on showcase test needing manual correction --
        seedShowcaseAttemptForGrading(showcaseTest.id(), memberIds);
    }

    private void seedAttempts(int testId, List<Integer> memberIds) {
        if (memberIds.size() < 3) return;

        // Create 3 demo attempts with varying quality
        int[][] memberAnswerQuality = {{0, 80}, {1, 50}, {2, 30}}; // memberIndex, correctPercent

        for (var maq : memberAnswerQuality) {
            int memberId = memberIds.get(maq[0]);
            int correctPercent = maq[1];
            try {
                var attempt = quizService.startAttempt(testId, memberId);
                var questions = quizService.findAttemptQuestions(attempt.id());

                for (var aq : questions) {
                    var question = quizService.findQuestion(aq.questionId());
                    if (question.isEmpty()) continue;
                    var q = question.get();
                    boolean correct = Math.random() * 100 < correctPercent;
                    String answer = generateDemoAnswer(q, correct);
                    if (answer != null) {
                        quizService.saveAnswer(attempt.id(), q.id(), answer);
                    }
                }

                quizService.submitAttempt(attempt.id());
            } catch (Exception e) {
                // Skip if attempt creation fails
            }
        }
    }

    /**
     * Creates a submitted attempt on the showcase test that needs manual grading.
     * Auto-gradable answers (MC, TF, connect, ordering, fill-blank) are filled in correctly.
     * FREE_ANSWER and IMAGE_TEXT answers are provided but left for manual grading by {@link QuizService#submitAttempt}.
     */
    private void seedShowcaseAttemptForGrading(int showcaseTestId, List<Integer> memberIds) {
        if (memberIds.size() < 4) return;
        // Use the 4th member (index 3) to avoid conflicts with the main test attempts (indices 0-2)
        int memberId = memberIds.get(3);
        try {
            var attempt = quizService.startAttempt(showcaseTestId, memberId);
            var questions = quizService.findAttemptQuestions(attempt.id());

            for (var aq : questions) {
                var question = quizService.findQuestion(aq.questionId());
                if (question.isEmpty()) continue;
                var q = question.get();
                // Provide correct answers for auto-gradable types, meaningful text for manual types
                String answer = generateShowcaseAnswer(q);
                if (answer != null) {
                    quizService.saveAnswer(attempt.id(), q.id(), answer);
                }
            }

            // submitAttempt auto-grades MC, TF, connect, ordering, fill-blank
            // but leaves FREE_ANSWER and IMAGE_TEXT ungraded (graded=false)
            quizService.submitAttempt(attempt.id());
        } catch (Exception e) {
            // Skip if attempt creation fails (e.g. member already has an attempt)
        }
    }

    private String generateShowcaseAnswer(QuizQuestion q) {
        try {
            var cfg = q.configNode();
            return switch (q.quizQuestionType()) {
                case MULTIPLE_CHOICE -> {
                    // Select all correct options
                    var options = cfg.get("options");
                    if (options == null) yield null;
                    var selected = new ArrayList<Integer>();
                    for (int i = 0; i < options.size(); i++) {
                        if (options.get(i).has("correct")
                                && options.get(i).get("correct").asBoolean()) {
                            selected.add(i);
                        }
                    }
                    yield "{\"selected\":" + selected + "}";
                }
                case TRUE_FALSE -> {
                    boolean answer =
                            cfg.has("correctAnswer") && cfg.get("correctAnswer").asBoolean();
                    yield "{\"value\":" + answer + "}";
                }
                case CONNECT -> {
                    var pairs = cfg.get("pairs");
                    if (pairs == null) yield null;
                    var map = new HashMap<String, String>();
                    for (int i = 0; i < pairs.size(); i++) {
                        map.put(String.valueOf(i), pairs.get(i).get("right").asString());
                    }
                    var sb = new StringBuilder("{\"pairs\":{");
                    for (var entry : map.entrySet()) {
                        if (sb.length() > 10) sb.append(",");
                        sb.append("\"")
                                .append(entry.getKey())
                                .append("\":\"")
                                .append(entry.getValue().replace("\"", "\\\""))
                                .append("\"");
                    }
                    sb.append("}}");
                    yield sb.toString();
                }
                case ORDERING -> {
                    var items = cfg.get("items");
                    if (items == null) yield null;
                    var order = new ArrayList<Integer>();
                    for (int i = 0; i < items.size(); i++) order.add(i);
                    yield "{\"order\":" + order + "}";
                }
                case FILL_IN_THE_BLANK -> {
                    var answers = cfg.get("answers");
                    if (answers == null || !answers.isArray()) yield "{\"gaps\":{}}";
                    var gapsSb = new StringBuilder("{\"gaps\":{");
                    for (int i = 0; i < answers.size(); i++) {
                        if (i > 0) gapsSb.append(",");
                        gapsSb.append("\"")
                                .append(i)
                                .append("\":\"")
                                .append(answers.get(i).asString().replace("\"", "\\\""))
                                .append("\"");
                    }
                    gapsSb.append("}}");
                    yield gapsSb.toString();
                }
                case FREE_ANSWER ->
                    "{\"text\":\"Die Feuerwehr hat vier Hauptaufgaben: Retten, Löschen, Bergen und Schützen. "
                            + "Retten bedeutet, Menschen und Tiere aus Gefahrensituationen zu befreien. "
                            + "Löschen ist die Bekämpfung von Bränden mit geeigneten Löschmitteln.\"}";
                case IMAGE_TEXT ->
                    "{\"text\":\"Auf dem Bild ist ein Feuerwehrfahrzeug zu sehen, vermutlich ein HLF 20. "
                            + "Es hat eine rote Lackierung und Blaulicht auf dem Dach. "
                            + "An der Seite sind Gerätefächer mit verschiedenen Werkzeugen sichtbar.\"}";
                case ENUMERATION -> {
                    var answers = cfg.get("answers");
                    if (answers == null || !answers.isArray()) yield null;
                    var items = new ArrayList<String>();
                    int count =
                            cfg.has("requiredCount") ? cfg.get("requiredCount").asInt() : answers.size();
                    for (int i = 0; i < Math.min(count, answers.size()); i++) {
                        items.add("\"" + answers.get(i).asString().replace("\"", "\\\"") + "\"");
                    }
                    yield "{\"items\":[" + String.join(",", items) + "]}";
                }
            };
        } catch (Exception e) {
            return null;
        }
    }

    private String generateDemoAnswer(QuizQuestion q, boolean correct) {
        try {
            var cfg = q.configNode();
            return switch (q.quizQuestionType()) {
                case MULTIPLE_CHOICE -> {
                    var options = cfg.get("options");
                    if (options == null) yield null;
                    var selected = new ArrayList<Integer>();
                    for (int i = 0; i < options.size(); i++) {
                        boolean isCorrect = options.get(i).has("correct")
                                && options.get(i).get("correct").asBoolean();
                        if (correct && isCorrect) selected.add(i);
                        else if (!correct && !isCorrect && selected.isEmpty()) selected.add(i);
                    }
                    if (selected.isEmpty() && !options.isEmpty()) selected.add(0);
                    yield "{\"selected\":" + selected + "}";
                }
                case TRUE_FALSE -> {
                    boolean answer =
                            cfg.has("correctAnswer") && cfg.get("correctAnswer").asBoolean();
                    if (!correct) answer = !answer;
                    yield "{\"value\":" + answer + "}";
                }
                case CONNECT -> {
                    var pairs = cfg.get("pairs");
                    if (pairs == null) yield null;
                    var map = new HashMap<String, String>();
                    for (int i = 0; i < pairs.size(); i++) {
                        if (correct) {
                            map.put(String.valueOf(i), pairs.get(i).get("right").asString());
                        } else {
                            // Shift answers by one
                            int wrongIdx = (i + 1) % pairs.size();
                            map.put(
                                    String.valueOf(i),
                                    pairs.get(wrongIdx).get("right").asString());
                        }
                    }
                    yield new ObjectMapper().writeValueAsString(Map.of("pairs", map));
                }
                case ORDERING -> {
                    var items = cfg.get("items");
                    if (items == null) yield null;
                    var order = new ArrayList<Integer>();
                    for (int i = 0; i < items.size(); i++) order.add(i);
                    if (!correct) Collections.shuffle(order);
                    yield "{\"order\":" + order + "}";
                }
                case FILL_IN_THE_BLANK -> {
                    var answers = cfg.get("answers");
                    if (answers == null || !answers.isArray()) yield "{\"gaps\":{}}";
                    var gaps = new HashMap<String, String>();
                    for (int i = 0; i < answers.size(); i++) {
                        gaps.put(String.valueOf(i), correct ? answers.get(i).asString() : "???");
                    }
                    var gapsSb2 = new StringBuilder("{\"gaps\":{");
                    for (var entry : gaps.entrySet()) {
                        if (gapsSb2.length() > 10) gapsSb2.append(",");
                        gapsSb2.append("\"")
                                .append(entry.getKey())
                                .append("\":\"")
                                .append(entry.getValue().replace("\"", "\\\""))
                                .append("\"");
                    }
                    gapsSb2.append("}}");
                    yield gapsSb2.toString();
                }
                case FREE_ANSWER ->
                    "{\"text\":\"" + (correct ? "Löschen, Retten, Bergen, Schützen" : "Ich weiß es nicht") + "\"}";
                case IMAGE_TEXT -> "{\"text\":\"" + (correct ? "Ein Feuerwehrlogo" : "Ich sehe etwas") + "\"}";
                case ENUMERATION -> {
                    var answers = cfg.get("answers");
                    if (answers == null || !answers.isArray()) yield "{\"items\":[]}";
                    var items = new ArrayList<String>();
                    int count =
                            cfg.has("requiredCount") ? cfg.get("requiredCount").asInt() : answers.size();
                    for (int i = 0; i < Math.min(count, answers.size()); i++) {
                        items.add("\"" + (correct ? answers.get(i).asString() : "???").replace("\"", "\\\"") + "\"");
                    }
                    yield "{\"items\":[" + String.join(",", items) + "]}";
                }
            };
        } catch (Exception e) {
            return null;
        }
    }

    private void createMcQuestion(
            int catalogId,
            int categoryId,
            String title,
            int position,
            List<String> options,
            List<Integer> correctIndices,
            double pointsPerCorrect) {
        var config = new StringBuilder("{\"options\":[");
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) config.append(",");
            config.append("{\"text\":\"")
                    .append(options.get(i))
                    .append("\",\"correct\":")
                    .append(correctIndices.contains(i))
                    .append("}");
        }
        config.append("],\"pointsPerCorrect\":").append(pointsPerCorrect).append("}");
        double points = correctIndices.size() * pointsPerCorrect;
        quizCatalogRepository.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.MULTIPLE_CHOICE,
                title,
                "",
                null,
                points,
                true,
                config.toString(),
                position);
    }

    private void createTfQuestion(int catalogId, int categoryId, String title, int position, boolean correctAnswer) {
        var config = "{\"correctAnswer\":" + correctAnswer + "}";
        quizCatalogRepository.createQuestion(
                catalogId, categoryId, QuizQuestionType.TRUE_FALSE, title, "", null, 1, true, config, position);
    }

    private void createFreeQuestion(
            int catalogId, int categoryId, String title, int position, int lines, List<String> possibleAnswers) {
        var config = new StringBuilder("{\"lines\":").append(lines);
        if (!possibleAnswers.isEmpty()) {
            config.append(",\"enumeration\":[");
            for (int i = 0; i < possibleAnswers.size(); i++) {
                if (i > 0) config.append(",");
                config.append("\"").append(possibleAnswers.get(i)).append("\"");
            }
            config.append("]");
        }
        config.append("}");
        quizCatalogRepository.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.FREE_ANSWER,
                title,
                "",
                null,
                Math.max(1, possibleAnswers.size()),
                !possibleAnswers.isEmpty(),
                config.toString(),
                position);
    }

    private void createConnectQuestion(
            int catalogId, int categoryId, String title, int position, List<String> left, List<String> right) {
        var config = new StringBuilder("{\"pairs\":[");
        for (int i = 0; i < left.size(); i++) {
            if (i > 0) config.append(",");
            config.append("{\"left\":\"")
                    .append(left.get(i))
                    .append("\",\"right\":\"")
                    .append(right.get(i))
                    .append("\"}");
        }
        config.append("]}");
        quizCatalogRepository.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.CONNECT,
                title,
                "",
                null,
                left.size(),
                true,
                config.toString(),
                position);
    }

    private void createOrderingQuestion(int catalogId, int categoryId, String title, int position, List<String> items) {
        var config = new StringBuilder("{\"items\":[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) config.append(",");
            config.append("\"").append(items.get(i)).append("\"");
        }
        config.append("]}");
        quizCatalogRepository.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.ORDERING,
                title,
                "",
                null,
                items.size(),
                true,
                config.toString(),
                position);
    }

    private void createFillBlankQuestion(
            int catalogId, int categoryId, String title, int position, List<String> answers) {
        createFillBlankQuestion(catalogId, categoryId, "Fülle die Lücken aus.", title, position, answers);
    }

    private void createFillBlankQuestion(
            int catalogId, int categoryId, String title, String text, int position, List<String> answers) {
        var config = new StringBuilder("{\"text\":\"")
                .append(text.replace("\"", "\\\""))
                .append("\",\"answers\":[");
        for (int i = 0; i < answers.size(); i++) {
            if (i > 0) config.append(",");
            config.append("\"").append(answers.get(i)).append("\"");
        }
        config.append("]}");
        quizCatalogRepository.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.FILL_IN_THE_BLANK,
                title,
                "",
                null,
                answers.size(),
                true,
                config.toString(),
                position);
    }

    private void createFillBlankQuestionWithDistractors(
            int catalogId,
            int categoryId,
            String title,
            String text,
            int position,
            List<String> answers,
            List<String> distractors) {
        var config = new StringBuilder("{\"text\":\"")
                .append(text.replace("\"", "\\\""))
                .append("\",\"answers\":[");
        for (int i = 0; i < answers.size(); i++) {
            if (i > 0) config.append(",");
            config.append("\"").append(answers.get(i)).append("\"");
        }
        config.append("],\"distractors\":[");
        for (int i = 0; i < distractors.size(); i++) {
            if (i > 0) config.append(",");
            config.append("\"").append(distractors.get(i)).append("\"");
        }
        config.append("]}");
        quizCatalogRepository.createQuestion(
                catalogId,
                categoryId,
                QuizQuestionType.FILL_IN_THE_BLANK,
                title,
                "",
                null,
                answers.size(),
                true,
                config.toString(),
                position);
    }
}
