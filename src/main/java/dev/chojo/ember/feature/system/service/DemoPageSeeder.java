/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.page.service.PageService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Seeds demo public pages with a landing page, about section, and join page.
 */
@Singleton
public class DemoPageSeeder {
    private final PageService pageService;

    @Inject
    public DemoPageSeeder(PageService pageService) {
        this.pageService = pageService;
    }

    public void seed(int stationId, int memberId) {
        // Landing page: Willkommen
        var welcome = pageService.create(stationId, "Willkommen", null, memberId);
        pageService.savePage(
                welcome.id(),
                "Willkommen",
                "willkommen",
                null,
                "Willkommen bei der Jugendfeuerwehr Musterstadt",
                null,
                List.of(
                        new PageService.RowData(
                                0,
                                List.of(new PageService.CellData(
                                        0, 100, CellContentType.MARKDOWN, """
                                                # Willkommen bei der Jugendfeuerwehr Musterstadt

                                                Wir sind eine aktive Jugendfeuerwehr mit rund 40 Mitgliedern im Alter von 10 bis 18 Jahren. \
                                                Bei uns lernst du alles rund um die Feuerwehr — von Erster Hilfe über Brandbekämpfung bis hin zu Wettbewerben und Zeltlagern.

                                                ## Was wir bieten

                                                - Regelmäßige Übungen jeden Freitag
                                                - Wettbewerbe auf Kreis- und Landesebene
                                                - Zeltlager und Ausflüge
                                                - Erste-Hilfe-Kurse
                                                - Kameradschaft und Teamarbeit""", CellConfig.EMPTY))),
                        new PageService.RowData(
                                1,
                                List.of(
                                        new PageService.CellData(
                                                0,
                                                50,
                                                CellContentType.MARKDOWN,
                                                "## Übungszeiten\n\n**Freitags** von 17:30 bis 19:00 Uhr\n\nGerätehaus Musterstadt\nFeuerwehrstraße 1\n12345 Musterstadt",
                                                CellConfig.EMPTY),
                                        new PageService.CellData(
                                                1,
                                                50,
                                                CellContentType.MARKDOWN,
                                                "## Kontakt\n\nJugendfeuerwehrwart: Max Mustermann\n\nE-Mail: jf@musterstadt.de\nTelefon: 01234 / 56789",
                                                CellConfig.EMPTY)))));
        pageService.setPublished(welcome.id(), true);
        pageService.setLandingPage(stationId, welcome.id());

        // About page with child pages
        var about = pageService.create(stationId, "Über uns", null, memberId);
        pageService.savePage(
                about.id(),
                "Über uns",
                "ueber-uns",
                null,
                "Erfahre mehr über unsere Jugendfeuerwehr",
                null,
                List.of(new PageService.RowData(
                        0,
                        List.of(new PageService.CellData(0, 100, CellContentType.MARKDOWN, """
                                        # Über uns

                                        Die Jugendfeuerwehr Musterstadt wurde 1985 gegründet und ist seitdem fester Bestandteil der Freiwilligen Feuerwehr Musterstadt. \
                                        Wir sind stolz auf unsere lange Tradition und freuen uns über jedes neue Mitglied.

                                        ## Unsere Geschichte

                                        Seit der Gründung haben über 200 Jugendliche den Weg zu uns gefunden. \
                                        Viele sind heute aktive Mitglieder der Einsatzabteilung.""", CellConfig.EMPTY)))));
        pageService.setPublished(about.id(), true);

        // Child: Team
        var team = pageService.create(stationId, "Unser Team", about.id(), memberId);
        pageService.savePage(
                team.id(),
                "Unser Team",
                "unser-team",
                about.id(),
                null,
                null,
                List.of(new PageService.RowData(
                        0,
                        List.of(new PageService.CellData(0, 100, CellContentType.MARKDOWN, """
                                        # Unser Team

                                        ## Jugendfeuerwehrwart
                                        **Max Mustermann** — seit 2015 dabei, leitet die Übungen und organisiert Wettbewerbe.

                                        ## Stellvertretende Jugendfeuerwehrwartin
                                        **Anna Schmidt** — kümmert sich um die Ausbildung und Zeltlager.

                                        ## Betreuer
                                        - Thomas Müller
                                        - Lisa Weber
                                        - Jonas Fischer""", CellConfig.EMPTY)))));
        pageService.setPublished(team.id(), true);

        // Child: Ausrüstung
        var equipment = pageService.create(stationId, "Ausrüstung", about.id(), memberId);
        pageService.savePage(
                equipment.id(),
                "Ausrüstung",
                "ausruestung",
                about.id(),
                null,
                null,
                List.of(new PageService.RowData(
                        0,
                        List.of(new PageService.CellData(0, 100, CellContentType.MARKDOWN, """
                                        # Ausrüstung

                                        Jedes Mitglied erhält bei Eintritt:

                                        - Jugendfeuerwehr-Uniform (Hose, Jacke, Helm)
                                        - Handschuhe
                                        - Sicherheitsschuhe
                                        - Jugendfeuerwehr-T-Shirt

                                        Die Ausrüstung wird von der Gemeinde gestellt und muss bei Austritt zurückgegeben werden.""", CellConfig.EMPTY)))));
        pageService.setPublished(equipment.id(), true);

        // Mitmachen page
        var join = pageService.create(stationId, "Mitmachen", null, memberId);
        pageService.savePage(
                join.id(),
                "Mitmachen",
                "mitmachen",
                null,
                "So kannst du bei uns mitmachen",
                null,
                List.of(
                        new PageService.RowData(
                                0,
                                List.of(new PageService.CellData(
                                        0, 100, CellContentType.MARKDOWN, """
                                                # Mitmachen

                                                Du bist zwischen 10 und 18 Jahre alt und hast Lust auf Feuerwehr? Dann komm einfach vorbei!

                                                ## So geht's

                                                1. Komm freitags um 17:30 Uhr zu einer Schnupperübung
                                                2. Bring deine Eltern mit — sie können alles fragen
                                                3. Nach 2-3 Schnupperübungen entscheidest du dich
                                                4. Anmeldeformular ausfüllen — fertig!

                                                **Wichtig:** Du brauchst keine Vorkenntnisse. Wir bringen dir alles bei!""", CellConfig.EMPTY))),
                        new PageService.RowData(
                                1,
                                List.of(
                                        new PageService.CellData(
                                                0, 60, CellContentType.MARKDOWN, """
                                                        ## Was du mitbringen solltest

                                                        - Spaß an Teamarbeit
                                                        - Neugier
                                                        - Feste Schuhe für die ersten Übungen

                                                        Alles andere stellen wir!""", CellConfig.EMPTY),
                                        new PageService.CellData(
                                                1, 40, CellContentType.MARKDOWN, """
                                                        ## Häufige Fragen

                                                        **Kostet das etwas?**
                                                        Nein, die Mitgliedschaft ist kostenlos.

                                                        **Wie oft sind Übungen?**
                                                        Jeden Freitag, außer in den Ferien.

                                                        **Ab welchem Alter?**
                                                        Ab 10 Jahren.""", CellConfig.EMPTY)))));
        pageService.setPublished(join.id(), true);
    }
}
