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
                                        0,
                                        100,
                                        CellContentType.MARKDOWN,
                                        "# Willkommen bei der Jugendfeuerwehr Musterstadt\n\nWir sind eine aktive Jugendfeuerwehr mit rund 40 Mitgliedern im Alter von 10 bis 18 Jahren. "
                                                + "Bei uns lernst du alles rund um die Feuerwehr — von Erster Hilfe über Brandbekämpfung bis hin zu Wettbewerben und Zeltlagern.\n\n"
                                                + "## Was wir bieten\n\n- Regelmäßige Übungen jeden Freitag\n- Wettbewerbe auf Kreis- und Landesebene\n"
                                                + "- Zeltlager und Ausflüge\n- Erste-Hilfe-Kurse\n- Kameradschaft und Teamarbeit",
                                        CellConfig.EMPTY))),
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
                        List.of(new PageService.CellData(
                                0,
                                100,
                                CellContentType.MARKDOWN,
                                "# Über uns\n\nDie Jugendfeuerwehr Musterstadt wurde 1985 gegründet und ist seitdem fester Bestandteil der Freiwilligen Feuerwehr Musterstadt. "
                                        + "Wir sind stolz auf unsere lange Tradition und freuen uns über jedes neue Mitglied.\n\n"
                                        + "## Unsere Geschichte\n\nSeit der Gründung haben über 200 Jugendliche den Weg zu uns gefunden. "
                                        + "Viele sind heute aktive Mitglieder der Einsatzabteilung.",
                                CellConfig.EMPTY)))));
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
                        List.of(new PageService.CellData(
                                0,
                                100,
                                CellContentType.MARKDOWN,
                                "# Unser Team\n\n## Jugendfeuerwehrwart\n**Max Mustermann** — seit 2015 dabei, leitet die Übungen und organisiert Wettbewerbe.\n\n"
                                        + "## Stellvertretende Jugendfeuerwehrwartin\n**Anna Schmidt** — kümmert sich um die Ausbildung und Zeltlager.\n\n"
                                        + "## Betreuer\n- Thomas Müller\n- Lisa Weber\n- Jonas Fischer",
                                CellConfig.EMPTY)))));
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
                        List.of(new PageService.CellData(
                                0,
                                100,
                                CellContentType.MARKDOWN,
                                "# Ausrüstung\n\nJedes Mitglied erhält bei Eintritt:\n\n"
                                        + "- Jugendfeuerwehr-Uniform (Hose, Jacke, Helm)\n- Handschuhe\n- Sicherheitsschuhe\n"
                                        + "- Jugendfeuerwehr-T-Shirt\n\nDie Ausrüstung wird von der Gemeinde gestellt und muss bei Austritt zurückgegeben werden.",
                                CellConfig.EMPTY)))));
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
                                        0,
                                        100,
                                        CellContentType.MARKDOWN,
                                        "# Mitmachen\n\nDu bist zwischen 10 und 18 Jahre alt und hast Lust auf Feuerwehr? Dann komm einfach vorbei!\n\n"
                                                + "## So geht's\n\n1. Komm freitags um 17:30 Uhr zu einer Schnupperübung\n"
                                                + "2. Bring deine Eltern mit — sie können alles fragen\n3. Nach 2-3 Schnupperübungen entscheidest du dich\n"
                                                + "4. Anmeldeformular ausfüllen — fertig!\n\n"
                                                + "**Wichtig:** Du brauchst keine Vorkenntnisse. Wir bringen dir alles bei!",
                                        CellConfig.EMPTY))),
                        new PageService.RowData(
                                1,
                                List.of(
                                        new PageService.CellData(
                                                0,
                                                60,
                                                CellContentType.MARKDOWN,
                                                "## Was du mitbringen solltest\n\n- Spaß an Teamarbeit\n- Neugier\n- Feste Schuhe für die ersten Übungen\n\n"
                                                        + "Alles andere stellen wir!",
                                                CellConfig.EMPTY),
                                        new PageService.CellData(
                                                1,
                                                40,
                                                CellContentType.MARKDOWN,
                                                "## Häufige Fragen\n\n**Kostet das etwas?**\nNein, die Mitgliedschaft ist kostenlos.\n\n"
                                                        + "**Wie oft sind Übungen?**\nJeden Freitag, außer in den Ferien.\n\n"
                                                        + "**Ab welchem Alter?**\nAb 10 Jahren.",
                                                CellConfig.EMPTY)))));
        pageService.setPublished(join.id(), true);
    }
}
