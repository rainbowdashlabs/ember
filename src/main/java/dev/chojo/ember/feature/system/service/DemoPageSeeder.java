/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.entity.FormQuestionConfig;
import dev.chojo.ember.feature.form.entity.FormQuestionType;
import dev.chojo.ember.feature.form.repository.FormRepository;
import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.quiz.entity.QuizCatalog;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds demo public pages with a landing page, about section, and join page.
 */
@Singleton
public class DemoPageSeeder {
    private final PageService pageService;
    private final FormRepository formRepository;
    private final QuizCatalogRepository quizCatalogRepository;

    @Inject
    public DemoPageSeeder(
            PageService pageService, FormRepository formRepository, QuizCatalogRepository quizCatalogRepository) {
        this.pageService = pageService;
        this.formRepository = formRepository;
        this.quizCatalogRepository = quizCatalogRepository;
    }

    private static PageService.RowData row(int sortOrder, CellContentType type, String content, CellConfig config) {
        return new PageService.RowData(sortOrder, List.of(new PageService.CellData(0, 100, type, content, config)));
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

        seedShowroom(stationId, memberId);
    }

    /**
     * Adds a "Komponenten-Schaukasten" page that demonstrates every CellContentType variant.
     * Useful for QA and for content editors who want to see what each component looks like.
     */
    private void seedShowroom(int stationId, int memberId) {
        UUID pollUid = seedDemoPoll(stationId, memberId);
        UUID contactUid = seedDemoContactForm(stationId, memberId);
        var page = pageService.create(stationId, "Komponenten-Schaukasten", null, memberId);
        var rows = new ArrayList<PageService.RowData>();
        int sort = 0;

        rows.add(row(sort++, CellContentType.MARKDOWN, """
                # Komponenten-Schaukasten

                Diese Seite zeigt jede verfügbare Komponente in Aktion — eine pro Zeile, damit du
                siehst, wie sie gerendert wird.""", CellConfig.EMPTY));

        rows.add(row(
                sort++,
                CellContentType.HERO_BANNER,
                "",
                new CellConfig.HeroBannerConfig(
                        null,
                        "Willkommen im Schaukasten",
                        "Jede Komponente — von Markdown bis Quiz — in einer einzigen Vorschau.",
                        "Mehr erfahren",
                        "#")));

        rows.add(row(sort++, CellContentType.MARKDOWN, "## Text & Hervorhebungen", CellConfig.EMPTY));
        rows.add(row(
                sort++,
                CellContentType.CALLOUT,
                "Wichtige Information: Übung am Freitag fällt wegen Feiertag aus.",
                new CellConfig.CalloutConfig(CellConfig.CalloutVariant.INFO, "Hinweis")));
        rows.add(row(
                sort++,
                CellContentType.QUOTE,
                "Feuerwehr ist keine Arbeit, Feuerwehr ist eine Berufung.",
                new CellConfig.QuoteConfig("Unbekannter Feuerwehrmann", null)));
        rows.add(row(sort++, CellContentType.DIVIDER, "", new CellConfig.DividerConfig("Mehr Inhalt unten")));
        rows.add(row(sort++, CellContentType.SPACER, "", new CellConfig.SpacerConfig(40)));
        rows.add(row(
                sort++,
                CellContentType.ACCORDION,
                "Mehr Details zum Aufnahmeprozess findest du hier — von der ersten Schnupperübung bis zur offiziellen Mitgliedschaft.",
                new CellConfig.AccordionConfig("Wie werde ich Mitglied?", false)));
        rows.add(row(
                sort++,
                CellContentType.CODE_BLOCK,
                "// Beispiel-Konfiguration\nstation: musterstadt\nrole: jugendwart\n",
                new CellConfig.CodeBlockConfig("yaml")));

        rows.add(row(sort++, CellContentType.MARKDOWN, "## Medien", CellConfig.EMPTY));
        rows.add(row(
                sort++,
                CellContentType.IMAGE,
                "",
                new CellConfig.ImageConfig(
                        CellConfig.ImageFit.CONTAIN,
                        "Beispielbild",
                        240,
                        "Eine Beispielbeschreibung",
                        null,
                        null,
                        null,
                        null,
                        8,
                        0,
                        null)));
        rows.add(row(
                sort++,
                CellContentType.VIDEO,
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                new CellConfig.VideoConfig(false, false)));
        rows.add(row(
                sort++,
                CellContentType.AUDIO_EMBED,
                "",
                new CellConfig.AudioEmbedConfig("https://example.com/podcast.mp3", "Folge 1: Erste Hilfe")));
        rows.add(row(
                sort++,
                CellContentType.IMAGE_GALLERY,
                "",
                new CellConfig.ImageGalleryConfig(List.of(), 3, null, null)));
        rows.add(
                row(sort++, CellContentType.PDF, "", new CellConfig.PdfConfig("https://example.com/satzung.pdf", 500)));
        rows.add(row(
                sort++,
                CellContentType.FILE_DOWNLOAD,
                "",
                new CellConfig.FileDownloadConfig(
                        "https://example.com/anmeldeformular.pdf",
                        "Anmeldeformular",
                        "PDF, 120 KB — bitte ausgefüllt mitbringen.")));

        rows.add(row(sort++, CellContentType.MARKDOWN, "## Veranstaltungen", CellConfig.EMPTY));
        rows.add(row(
                sort++,
                CellContentType.COUNTDOWN,
                "",
                new CellConfig.CountdownConfig("2026-12-31T18:00", "Jahresabschluss", "Bis Silvester")));
        rows.add(row(
                sort++,
                CellContentType.FEATURED_EVENT,
                "",
                new CellConfig.FeaturedEventConfig(
                        "Tag der offenen Tür",
                        "2026-07-15",
                        "Gerätehaus Musterstadt",
                        "Wir laden alle ein, hinter die Kulissen zu schauen.",
                        "Jetzt anmelden",
                        "#")));
        rows.add(row(
                sort++,
                CellContentType.UPCOMING_EVENTS,
                "",
                new CellConfig.UpcomingEventsConfig(
                        "Nächste Termine",
                        List.of(
                                new CellConfig.EventItem("Übung", "Fr, 17:30", "Gerätehaus", "#"),
                                new CellConfig.EventItem("Wettbewerb", "Sa, 09:00", "Kreisstadt", "#"),
                                new CellConfig.EventItem("Zeltlager", "20.–27. Juli", "Waldsee", "#")))));
        rows.add(row(
                sort++,
                CellContentType.PAST_EVENT_RECAP,
                "",
                new CellConfig.PastEventRecapConfig(
                        "Sommerfest 2025",
                        "2025-08-10",
                        null,
                        "Über 200 Gäste, Stockbrot am Lagerfeuer, Wasserspiele und ein toller Tag.")));

        rows.add(row(sort++, CellContentType.MARKDOWN, "## Inhalte verlinken", CellConfig.EMPTY));
        rows.add(row(
                sort++, CellContentType.KB_ARTICLE, "", new CellConfig.KbArticleConfig(null, "Erste-Hilfe-Maßnahmen")));
        rows.add(row(
                sort++,
                CellContentType.NEWS_TEASER,
                "",
                new CellConfig.NewsTeaserConfig(
                        "Neue Drehleiter eingeweiht",
                        "2026-04-12",
                        "Die Freiwillige Feuerwehr Musterstadt hat eine neue 30-m-Drehleiter erhalten.",
                        "#",
                        null)));
        rows.add(row(sort++, CellContentType.PAGE_LINK, "", new CellConfig.PageLinkConfig(null, "Mehr über uns")));
        rows.add(row(
                sort++,
                CellContentType.EXTERNAL_LINK_CARD,
                "",
                new CellConfig.ExternalLinkCardConfig(
                        "https://www.jugendfeuerwehr.de",
                        "Deutsche Jugendfeuerwehr",
                        "Der Dachverband aller Jugendfeuerwehren in Deutschland.",
                        null,
                        null)));

        rows.add(row(sort++, CellContentType.MARKDOWN, "## Standort", CellConfig.EMPTY));
        rows.add(row(
                sort++,
                CellContentType.MAP,
                "",
                new CellConfig.MapConfig(52.520008, 13.404954, 14, 320, "Gerätehaus Musterstadt")));
        rows.add(row(
                sort++,
                CellContentType.ADDRESS_CARD,
                "",
                new CellConfig.AddressCardConfig(
                        "Feuerwehrstraße 1",
                        "12345",
                        "Musterstadt",
                        "DE",
                        "https://maps.example.com/musterstadt",
                        "Gerätehaus")));
        rows.add(row(
                sort++,
                CellContentType.PARTNER_STATIONS,
                "",
                new CellConfig.PartnerStationsConfig("Unsere Partner", List.of(), true)));

        rows.add(row(sort++, CellContentType.MARKDOWN, "## Menschen & Erfolge", CellConfig.EMPTY));
        rows.add(row(
                sort++,
                CellContentType.MEMBER_SPOTLIGHT,
                "",
                new CellConfig.MemberSpotlightConfig(
                        null, "Anna ist seit 8 Jahren dabei und betreut hauptsächlich die Jüngsten.", null, null)));
        rows.add(row(
                sort++,
                CellContentType.MEMBER_LIST_SPOTLIGHT,
                "",
                new CellConfig.MemberListConfig("Vorstand", null, null, null, null, null, null, null)));
        rows.add(row(
                sort++,
                CellContentType.STATS_COUNTER,
                "",
                new CellConfig.StatsCounterConfig(List.of(
                        new CellConfig.StatItem("Mitglieder", "42", null),
                        new CellConfig.StatItem("Übungen pro Jahr", "48", null),
                        new CellConfig.StatItem("Gegründet", "1985", null)))));
        rows.add(row(
                sort++,
                CellContentType.ACHIEVEMENTS,
                "",
                new CellConfig.AchievementsConfig(
                        "Unsere Erfolge",
                        List.of(
                                new CellConfig.AchievementItem("Kreismeister", "Bundeswettbewerb gewonnen", "2024"),
                                new CellConfig.AchievementItem("Landessieger", "Leistungsspange", "2023"),
                                new CellConfig.AchievementItem("Jubiläum", "40 Jahre JF Musterstadt", "2025")))));

        rows.add(row(sort++, CellContentType.MARKDOWN, "## Interaktion", CellConfig.EMPTY));
        rows.add(row(
                sort++,
                CellContentType.TABS,
                "",
                new CellConfig.TabsConfig(List.of(
                        new CellConfig.TabItem("Was wir tun", "Übungen, Wettbewerbe, Ausflüge — und Spaß."),
                        new CellConfig.TabItem(
                                "Für Eltern", "Wir sind ehrenamtlich und freuen uns über Unterstützung."),
                        new CellConfig.TabItem("Für Lehrkräfte", "Kooperationen mit Schulen sind möglich.")))));
        rows.add(row(
                sort++,
                CellContentType.BLOG_SIGNUP,
                "",
                new CellConfig.BlogSignupConfig(
                        "Blog abonnieren", "Bleib auf dem Laufenden — alle Blog-Beiträge per RSS oder Atom.")));
        rows.add(row(sort++, CellContentType.POLL_EMBED, "", new CellConfig.PollEmbedConfig(pollUid.toString(), true)));
        List<Integer> publicCatalogIds = quizCatalogRepository.findPublicByStation(stationId).stream()
                .map(QuizCatalog::id)
                .toList();
        rows.add(row(
                sort++,
                CellContentType.QUIZ_TEASER,
                "",
                new CellConfig.QuizTeaserConfig(
                        "Quiz: Wie gut kennst du die Knoten?",
                        "Eine Frage aus unserem öffentlichen Katalog — danach lüften wir die Antwort.",
                        publicCatalogIds)));
        rows.add(row(
                sort++,
                CellContentType.FORMS_CTA,
                "",
                new CellConfig.FormsCtaConfig(
                        contactUid.toString(),
                        "Werde Teil unseres Teams",
                        "Bewerbung in nur drei Schritten — wir freuen uns auf dich.")));

        pageService.savePage(
                page.id(),
                "Komponenten-Schaukasten",
                "komponenten-schaukasten",
                null,
                "Demoseite mit allen verfügbaren Komponenten",
                null,
                rows);
        pageService.setPublished(page.id(), true);
    }

    /**
     * Seeds a public poll form that the showroom POLL_EMBED cell links to. Returns the form's
     * {@code public_uid} so the cell config can reference it.
     */
    private UUID seedDemoPoll(int stationId, int memberId) {
        var poll = formRepository.create(
                stationId,
                "Welche Aktivität wünscht ihr euch?",
                "Wähle deinen Favoriten — die Mehrheitswahl landet im nächsten Programm.",
                false,
                false,
                null,
                null,
                memberId,
                FormPurpose.POLL);
        formRepository.updateStatus(poll.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                poll.id(),
                0,
                FormQuestionType.CHOICE,
                "Was sollen wir als nächstes machen?",
                "Eine Auswahl bitte.",
                true,
                false,
                new FormQuestionConfig.Choice(
                        List.of("Zeltlager", "Wettkampftag", "Übung mit der Feuerwehr", "Filmabend"),
                        false,
                        false,
                        false,
                        FormQuestionConfig.MultiLimitType.NONE,
                        null));
        return poll.publicUid();
    }

    /**
     * Seeds a public contact form that the showroom FORMS_CTA cell links to. Returns the
     * form's {@code public_uid} so the cell config can reference it.
     */
    private UUID seedDemoContactForm(int stationId, int memberId) {
        var contact = formRepository.create(
                stationId,
                "Kontakt aufnehmen",
                "Schreib uns — wir melden uns innerhalb weniger Tage zurück.",
                false,
                false,
                null,
                null,
                memberId,
                FormPurpose.CONTACT);
        formRepository.updateStatus(contact.id(), Form.FormStatus.OPEN);
        formRepository.createQuestion(
                contact.id(),
                0,
                FormQuestionType.TEXT,
                "Dein Name",
                "Wie sollen wir dich ansprechen?",
                true,
                false,
                new FormQuestionConfig.Text(false));
        formRepository.createQuestion(
                contact.id(),
                1,
                FormQuestionType.TEXT,
                "E-Mail-Adresse",
                "Damit wir dir antworten können.",
                true,
                false,
                new FormQuestionConfig.Text(false));
        formRepository.createQuestion(
                contact.id(),
                2,
                FormQuestionType.TEXT,
                "Deine Nachricht",
                "Worum geht es?",
                true,
                false,
                new FormQuestionConfig.Text(true));
        return contact.publicUid();
    }
}
