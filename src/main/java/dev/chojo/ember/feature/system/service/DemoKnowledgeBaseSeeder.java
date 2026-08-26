/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Seeds demo knowledge base content with folders, markdown files, a YouTube video, and a PDF.
 */
@Singleton
public class DemoKnowledgeBaseSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoKnowledgeBaseSeeder.class);

    private final KnowledgeBaseService kbService;
    private final KbContentService contentService;
    private final KnowledgeBaseRepository kbRepository;

    @Inject
    public DemoKnowledgeBaseSeeder(
            KnowledgeBaseService kbService, KbContentService contentService, KnowledgeBaseRepository kbRepository) {
        this.kbService = kbService;
        this.contentService = contentService;
        this.kbRepository = kbRepository;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        seed(station.stationId(), station.adminMember().id());
        log.info("Demo: Created Knowledge Base content");
    }

    public void seed(int stationId, int createdBy) {
        // === Root-level files ===
        var welcomeFile =
                kbService.createMarkdownFile(stationId, null, "Willkommen", "Startseite des Wikis", """
                        # Willkommen im Wiki

                        Hier findest du alle wichtigen Informationen rund um die Jugendfeuerwehr.

                        ## Was findest du hier?

                        - **Grundlagen**: Alles über die Feuerwehr und ihre Aufgaben
                        - **Ausrüstung**: Persönliche Schutzausrüstung und Geräte
                        - **Erste Hilfe**: Wichtige Maßnahmen bei Notfällen
                        - **Übungen**: Anleitungen und Tipps für Übungen

                        > Tipp: Nutze die Suchfunktion oben, um schnell das Richtige zu finden!
                        """, createdBy);

        // Edit the welcome file to create a version history
        contentService.updateMarkdownContent(welcomeFile.id(), """
                # Willkommen im Wiki

                Hier findest du alle wichtigen Informationen rund um die Jugendfeuerwehr.
                Schau dich um und lerne etwas Neues!

                ## Was findest du hier?

                - **Grundlagen**: Alles über die Feuerwehr und ihre Aufgaben
                - **Ausrüstung**: Persönliche Schutzausrüstung und Geräte
                - **Erste Hilfe**: Wichtige Maßnahmen bei Notfällen
                - **Übungen**: Anleitungen und Tipps für Übungen
                - **Videos**: Lehrvideos zu verschiedenen Themen

                > Tipp: Nutze die Suchfunktion oben, um schnell das Richtige zu finden!

                ---
                *Zuletzt aktualisiert von der Jugendleitung.*
                """, createdBy);

        // === Formatting Showcase (root level) ===
        var showcaseFile = kbService.createMarkdownFile(
                stationId,
                null,
                "Formatierungsbeispiele",
                "Zeigt alle unterstützten Formatierungen des Editors",
                """
                        # Formatierungsbeispiele

                        Diese Datei zeigt alle Formatierungen, die der Texteditor unterstützt. Du kannst sie als Vorlage verwenden.

                        ## Textformatierung

                        Normaler Text kann **fett**, *kursiv*, <u>unterstrichen</u>, ~~durchgestrichen~~ oder als `Inline-Code` formatiert werden.

                        Du kannst auch ==markierten Text== verwenden, um wichtige Stellen hervorzuheben.

                        Kombinationen sind möglich: ***fett und kursiv***, **~~fett und durchgestrichen~~**.

                        <span style="color: #ec2929">Roter Text</span>, <span style="color: #3694FF">blauer Text</span> und <span style="color: #00C507">grüner Text</span> sind ebenfalls möglich.

                        ## Überschriften

                        Überschriften gibt es in drei Ebenen:

                        ### Ebene 3 - Unterabschnitt

                        Text unter einer H3-Überschrift.

                        ## Listen

                        ### Aufzählung (ungeordnet)

                        - Erster Punkt
                        - Zweiter Punkt
                          - Unterpunkt A
                          - Unterpunkt B
                        - Dritter Punkt

                        ### Nummerierte Liste

                        1. Schritt eins
                        2. Schritt zwei
                        3. Schritt drei

                        ## Zitate

                        > Das ist ein Zitat. Zitate eignen sich gut, um wichtige Aussagen hervorzuheben
                        > oder Quellen zu kennzeichnen.

                        ## Code

                        Inline-Code: `const name = "Ember"`

                        Codeblock:

                        ```
                        function hallo() {
                          console.log("Hallo Welt!");
                          return 42;
                        }
                        ```

                        ## Tabellen

                        | Spalte A     | Spalte B       | Spalte C   |
                        |--------------|----------------|------------|
                        | Zeile 1      | Wert 1         | Daten 1    |
                        | Zeile 2      | Wert 2         | Daten 2    |
                        | Zeile 3      | Wert 3         | Daten 3    |
                        | **Summe**    | **Gesamt**     | **42**     |

                        ## Links

                        Hier ist ein [Link zur Startseite](/) und ein [externer Link](https://example.com).

                        ---

                        ## Horizontale Trennlinie

                        Oben und unten siehst du horizontale Trennlinien. Sie eignen sich, um Abschnitte visuell zu trennen.

                        ---

                        ## Zusammenfassung

                        Der Editor unterstützt:

                        1. **Textformatierung** - fett, kursiv, unterstrichen, durchgestrichen, Code, Hervorhebung, Farben
                        2. **Struktur** - Überschriften (H1–H3), Absätze, Listen, Zitate
                        3. **Medien** - Tabellen, Links, Bilder, YouTube-Videos
                        4. **Sonstiges** - Codeblöcke, horizontale Trennlinien

                        > Tipp: Markiere Text im Editor, um die Schnellformatierung zu nutzen!
                        """,
                createdBy);
        kbRepository.setFileTags(showcaseFile.id(), List.of("beispiel", "formatierung", "editor"), stationId);

        // === Grundlagen Folder ===
        var grundlagenFolder =
                kbService.createFolder(stationId, null, "Grundlagen", "Basiswissen für alle Mitglieder", createdBy);

        kbService.createMarkdownFile(
                stationId, grundlagenFolder.id(), "Aufgaben der Feuerwehr", "Die vier Grundaufgaben", """
                        # Aufgaben der Feuerwehr

                        Die Feuerwehr hat vier Grundaufgaben, die oft als **RLBS** abgekürzt werden:

                        ## 1. Retten
                        Menschen und Tiere aus lebensbedrohlichen Situationen befreien.

                        ## 2. Löschen
                        Brände bekämpfen und das Feuer unter Kontrolle bringen.

                        ## 3. Bergen
                        Gegenstände und Sachwerte aus Gefahrenbereichen sicherstellen.

                        ## 4. Schützen
                        Vorbeugende Maßnahmen treffen, um Schäden zu verhindern.

                        ---

                        | Aufgabe  | Beispiel                        |
                        |----------|---------------------------------|
                        | Retten   | Person aus brennendem Haus      |
                        | Löschen  | Zimmerbrand mit C-Rohr löschen  |
                        | Bergen   | Auto aus Graben ziehen          |
                        | Schützen | Ölspur auf Straße binden        |
                        """, createdBy);

        kbService.createMarkdownFile(
                stationId, grundlagenFolder.id(), "Notruf absetzen", "Die 5 W-Fragen", """
                        # Notruf richtig absetzen

                        Beim Notruf über **112** sind die 5 W-Fragen wichtig:

                        1. **Wo** ist es passiert?
                        2. **Was** ist passiert?
                        3. **Wie viele** Verletzte gibt es?
                        4. **Welche** Verletzungen liegen vor?
                        5. **Warten** auf Rückfragen!

                        ## Wichtig zu wissen

                        - Der Notruf 112 ist **kostenlos** und funktioniert in ganz Europa
                        - Auch ohne SIM-Karte kann man den Notruf wählen
                        - Lege **nie** einfach auf - warte auf Rückfragen der Leitstelle
                        - Bleibe **ruhig** und sprich deutlich
                        """, createdBy);

        // === Ausrüstung Folder ===
        var ausruestungFolder = kbService.createFolder(
                stationId, null, "Ausrüstung", "Persönliche und technische Ausrüstung", createdBy);

        var psaFile = kbService.createMarkdownFile(
                stationId,
                ausruestungFolder.id(),
                "Persönliche Schutzausrüstung",
                "Was du bei jedem Einsatz trägst",
                """
                        # Persönliche Schutzausrüstung (PSA)

                        Die PSA schützt dich bei Einsätzen und Übungen.

                        ## Bestandteile

                        - **Helm**: Schützt den Kopf vor herabfallenden Teilen
                        - **Handschuhe**: Schutz vor Hitze, Schnitten und Chemikalien
                        - **Einsatzjacke**: Flammschutz und Schutz vor Wärmestrahlung
                        - **Einsatzhose**: Wie die Jacke, zusätzlich Knieschutz
                        - **Stiefel**: Durchtrittsichere Sohle, Knöchelschutz

                        ## Anziehreihenfolge

                        1. Hose
                        2. Stiefel
                        3. Jacke
                        4. Handschuhe
                        5. Helm

                        > Merke: Die PSA muss immer vollständig getragen werden!
                        """,
                createdBy);

        // Edit PSA file to show version history
        contentService.updateMarkdownContent(psaFile.id(), """
                # Persönliche Schutzausrüstung (PSA)

                Die PSA schützt dich bei Einsätzen und Übungen. Sie muss regelmäßig gepflegt und kontrolliert werden.

                ## Bestandteile

                - **Helm** mit Visier: Schützt den Kopf vor herabfallenden Teilen und Hitze
                - **Handschuhe**: Schutz vor Hitze, Schnitten und Chemikalien
                - **Einsatzjacke**: Flammschutz und Schutz vor Wärmestrahlung
                - **Einsatzhose**: Wie die Jacke, zusätzlich Knieschutz
                - **Stiefel**: Durchtrittsichere Sohle, Knöchelschutz
                - **Flammschutzhaube**: Schutz für Nacken und Ohren (bei Atemschutzeinsätzen)

                ## Anziehreihenfolge

                1. Hose
                2. Stiefel
                3. Jacke
                4. Flammschutzhaube (falls benötigt)
                5. Handschuhe
                6. Helm

                ## Pflege

                - Nach jedem Einsatz auf Beschädigungen prüfen
                - Verschmutzte Kleidung waschen (Herstellerangaben beachten)
                - Defekte Teile sofort melden

                > Merke: Die PSA muss immer vollständig getragen werden!
                """, createdBy);

        kbService.createMarkdownFile(
                stationId,
                ausruestungFolder.id(),
                "Schläuche und Armaturen",
                "Überblick über Schlauchtypen",
                """
                        # Schläuche und Armaturen

                        ## Schlauchtypen

                        | Bezeichnung | Durchmesser | Einsatzzweck                |
                        |-------------|-------------|-----------------------------|
                        | A-Schlauch  | 110 mm      | Saugschlauch (wasserführend)|
                        | B-Schlauch  | 75 mm       | Zubringerleitung            |
                        | C-Schlauch  | 52 mm       | Angriffsleitung (Standard)  |
                        | D-Schlauch  | 25 mm       | Kleine Löschangriffe        |

                        ## Armaturen

                        - **Verteiler**: Teilt eine B-Leitung in zwei C-Leitungen
                        - **Strahlrohr**: Formt den Wasserstrahl (Voll-/Sprühstrahl)
                        - **Kupplungen**: Storz-Kupplungen zum Verbinden

                        ## Merksatz

                        > „Von der Pumpe zum Verteiler, vom Verteiler zum Strahlrohr."
                        """,
                createdBy);

        // === Erste Hilfe Folder ===
        var ersteHilfeFolder =
                kbService.createFolder(stationId, null, "Erste Hilfe", "Wichtige Maßnahmen bei Notfällen", createdBy);

        kbService.createMarkdownFile(
                stationId,
                ersteHilfeFolder.id(),
                "Stabile Seitenlage",
                "Anleitung für bewusstlose Personen",
                """
                        # Stabile Seitenlage

                        Die stabile Seitenlage wird bei **bewusstlosen** Personen angewendet,
                        die noch **selbständig atmen**.

                        ## Anleitung

                        1. Neben die Person knien
                        2. Arm auf deiner Seite angewinkelt neben den Kopf legen
                        3. Anderen Arm über die Brust legen, Hand an die Wange
                        4. Fernes Bein anwinkeln
                        5. Person vorsichtig zu dir herüberziehen
                        6. Kopf überstrecken, Mund leicht öffnen
                        7. Regelmäßig die Atmung kontrollieren!

                        ## Wann anwenden?

                        - Person ist bewusstlos
                        - Person atmet noch selbständig
                        - Kein Verdacht auf Wirbelsäulenverletzung

                        ## Wann NICHT anwenden?

                        - Bei Atemstillstand → **Reanimation beginnen!**
                        - Bei Verdacht auf Rückenverletzung → Person nicht bewegen
                        """,
                createdBy);

        // === Übungen Folder with subfolder ===
        var uebungenFolder =
                kbService.createFolder(stationId, null, "Übungen", "Anleitungen für Übungsabende", createdBy);

        var loeschFolder = kbService.createFolder(
                stationId, uebungenFolder.id(), "Löschangriff", "Übungen zum Löschangriff", createdBy);

        kbService.createMarkdownFile(
                stationId, loeschFolder.id(), "Löschangriff nass", "Standardübung mit Wasserentnahme", """
                        # Löschangriff nass

                        Der Löschangriff nass ist eine Standardübung der Jugendfeuerwehr.

                        ## Ablauf

                        1. **Wasserentnahmestelle** einrichten (Hydrant oder offenes Gewässer)
                        2. **Saugleitung** kuppeln (bei offenem Gewässer)
                        3. **Verteiler** setzen
                        4. **Angriffstrupp** stellt C-Rohr bereit
                        5. **Wassertrupp** stellt Wasserversorgung sicher
                        6. **Schlauchtrupp** verlegt die Schlauchleitung

                        ## Kommandos

                        - „Wasser marsch!" - Pumpe einschalten
                        - „Wasser halt!" - Pumpe stoppen
                        - „Zum Abmarsch fertig!" - Geräte verstauen

                        ## Tipps

                        - Immer auf **Knickstellen** in den Schläuchen achten
                        - Kupplungen richtig zusammendrücken, nicht drehen
                        - Kommunikation mit dem Maschinisten ist wichtig!
                        """, createdBy);

        // === Videos section with YouTube ===
        var videosFolder = kbService.createFolder(stationId, null, "Videos", "Lehrvideos und Tutorials", createdBy);

        kbService.createYoutubeFile(
                stationId,
                videosFolder.id(),
                "Feuerwehr-Grundausbildung",
                "Einführungsvideo zur Grundausbildung bei der Feuerwehr",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                createdBy);

        kbService.createYoutubeFile(
                stationId,
                videosFolder.id(),
                "Knoten und Stiche",
                "Die wichtigsten Knoten für die Feuerwehr",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                createdBy);

        // === Demo PDF ===
        try {
            byte[] pdfData = TypstCompiler.compile("""
                    #set page(paper: "a4", margin: 2cm)
                    #set text(font: "Liberation Sans", size: 11pt, lang: "de")

                    #align(center)[
                      #text(size: 20pt, weight: "bold")[Dienstanweisung]
                      #v(0.5em)
                      #text(size: 14pt)[Jugendfeuerwehr Musterstadt]
                      #v(2em)
                    ]

                    = 1. Allgemeines
                    Diese Dienstanweisung regelt den Übungsbetrieb der Jugendfeuerwehr Musterstadt.

                    = 2. Übungszeiten
                    - *Anfänger:* Montags 17:30 – 19:00 Uhr
                    - *Fortgeschrittene:* Mittwochs 18:00 – 19:30 Uhr
                    - *Gesamtübung:* Erster Samstag im Monat, 10:00 – 13:00 Uhr

                    = 3. Persönliche Schutzausrüstung
                    Alle Mitglieder tragen bei Übungen die vollständige PSA. Defekte Ausrüstung ist sofort zu melden.

                    = 4. Verhalten bei Übungen
                    + Pünktlich erscheinen
                    + Anweisungen der Betreuer befolgen
                    + Auf Sicherheit achten
                    + Geräte nach Gebrauch reinigen und verstauen

                    = 5. Abmeldung
                    Bei Verhinderung ist eine rechtzeitige Abmeldung über die App erforderlich.
                    """);
            kbService.createUploadedFile(
                    stationId,
                    ausruestungFolder.id(),
                    "Dienstanweisung.pdf",
                    "Dienstanweisung für den Übungsbetrieb",
                    pdfData,
                    "application/pdf",
                    createdBy);
        } catch (Exception e) {
            log.warn("Demo: Could not generate PDF (typst not available?)", e);
        }

        // === Tags ===
        kbRepository.setFileTags(welcomeFile.id(), List.of("wichtig", "einstieg"), stationId);
        kbRepository.setFolderTags(grundlagenFolder.id(), List.of("grundlagen", "theorie"), stationId);
        kbRepository.setFolderTags(ausruestungFolder.id(), List.of("ausrüstung", "praxis"), stationId);
        kbRepository.setFolderTags(ersteHilfeFolder.id(), List.of("erste hilfe", "wichtig"), stationId);
        kbRepository.setFolderTags(uebungenFolder.id(), List.of("übungen", "praxis"), stationId);
        kbRepository.setFolderTags(videosFolder.id(), List.of("videos", "multimedia"), stationId);
    }
}
