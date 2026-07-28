/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.news.service.NewsService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Seeds demo news articles with comments.
 */
@Singleton
public class DemoNewsSeeder implements DemoSeeder {
    private final NewsService newsService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public DemoNewsSeeder(NewsService newsService, StationMemberRepository stationMemberRepository) {
        this.newsService = newsService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public int order() {
        return NEWS;
    }

    @Override
    public void seed(DemoSeederContext context) {
        var members = context.members();
        context.news(seed(
                context.stationId(),
                context.adminMember().id(),
                members.betreuer(),
                members.eltern(),
                members.fortgeschritten()));
    }

    public SeedResult seed(
            int stationId,
            int adminMemberId,
            List<StationMember> betreuerMembers,
            List<StationMember> elternMembers,
            List<StationMember> fortgeschrittenMembers) {
        var news1 = newsService.create(
                stationId,
                "Willkommen bei der Jugendfeuerwehr!",
                """
                        Herzlich willkommen auf unserer neuen Plattform! Hier findet ihr alle wichtigen Informationen rund um unsere **Jugendfeuerwehr**.

                        ## Was ist neu?

                        Wir haben viele neue Funktionen für euch:

                        - **Terminübersicht** — Alle Übungen, Veranstaltungen und Wettbewerbe auf einen Blick
                        - **Anwesenheitsverwaltung** — Schnelles Ein- und Auschecken bei Übungen
                        - **Inventarverwaltung** — Eure Ausrüstung immer im Blick
                        - **Wiki** — Lernmaterial und Protokolle

                        ## Erste Schritte

                        1. Prüft euer **Profil** und ergänzt fehlende Daten
                        2. Schaut euch die **kommenden Termine** an
                        3. Meldet euch für den nächsten **Wettbewerb** an

                        > **Tipp:** Bei Fragen könnt ihr jederzeit die Betreuer ansprechen oder die Hilfe-Seite nutzen.

                        Wir freuen uns auf eine tolle Zeit! \uD83D\uDE92
                        """,
                "<p>Herzlich willkommen auf unserer neuen Plattform! Hier findet ihr alle wichtigen Informationen rund um unsere <strong>Jugendfeuerwehr</strong>.</p><h2>Was ist neu?</h2><p>Wir haben viele neue Funktionen für euch:</p><ul><li><strong>Terminübersicht</strong> — Alle Übungen, Veranstaltungen und Wettbewerbe auf einen Blick</li><li><strong>Anwesenheitsverwaltung</strong> — Schnelles Ein- und Auschecken bei Übungen</li><li><strong>Inventarverwaltung</strong> — Eure Ausrüstung immer im Blick</li><li><strong>Wiki</strong> — Lernmaterial und Protokolle</li></ul><h2>Erste Schritte</h2><ol><li>Prüft euer <strong>Profil</strong> und ergänzt fehlende Daten</li><li>Schaut euch die <strong>kommenden Termine</strong> an</li><li>Meldet euch für den nächsten <strong>Wettbewerb</strong> an</ol><blockquote><p><strong>Tipp:</strong> Bei Fragen könnt ihr jederzeit die Betreuer ansprechen oder die Hilfe-Seite nutzen.</p></blockquote><p>Wir freuen uns auf eine tolle Zeit! \uD83D\uDE92</p>",
                stationMemberRepository.resolveIdentity(adminMemberId),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        var news2 = newsService.create(
                stationId,
                "Kreiswettbewerb: Anmeldung geöffnet",
                """
                        Die Anmeldung zum **Kreiswettbewerb** am 20. des übernächsten Monats ist jetzt geöffnet!

                        ## Wichtige Infos

                        | | Details |
                        |---|---|
                        | **Datum** | 20. des übernächsten Monats |
                        | **Ort** | Sportplatz Nachbarstadt |
                        | **Treffpunkt** | Feuerwehrgerätehaus, 07:30 Uhr |

                        ### Was wird bewertet?

                        - Löschangriff
                        - Staffellauf
                        - Knotenkunde
                        - Erste Hilfe

                        Bitte meldet euch **bis spätestens nächste Woche** über die Terminseite an. Die Plätze sind begrenzt.

                        > *Teilnehmen dürfen alle Fortgeschrittenen.*
                        """,
                "<p>Die Anmeldung zum <strong>Kreiswettbewerb</strong> am 20. des übernächsten Monats ist jetzt geöffnet!</p><h2>Wichtige Infos</h2><table><tr><td></td><td>Details</td></tr><tr><td><strong>Datum</strong></td><td>20. des übernächsten Monats</td></tr><tr><td><strong>Ort</strong></td><td>Sportplatz Nachbarstadt</td></tr><tr><td><strong>Treffpunkt</strong></td><td>Feuerwehrgerätehaus, 07:30 Uhr</td></tr></table><h3>Was wird bewertet?</h3><ul><li>Löschangriff</li><li>Staffellauf</li><li>Knotenkunde</li><li>Erste Hilfe</li></ul><p>Bitte meldet euch <strong>bis spätestens nächste Woche</strong> über die Terminseite an. Die Plätze sind begrenzt.</p><blockquote><p><em>Teilnehmen dürfen alle Fortgeschrittenen.</em></p></blockquote>",
                stationMemberRepository.resolveIdentity(
                        betreuerMembers.getFirst().id()),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        // Mark first two news as blog entries
        newsService.updatePublicBlog(news1.id(), true);
        newsService.updatePublicBlog(news2.id(), true);

        // Comments on news
        var comment1 = newsService.createComment(
                stationId,
                news1.id(),
                null,
                stationMemberRepository.resolveIdentity(elternMembers.get(0).id()),
                "Demo User",
                "Super, endlich eine moderne Plattform!");
        newsService.createComment(
                stationId,
                news1.id(),
                comment1.id(),
                stationMemberRepository.resolveIdentity(
                        betreuerMembers.getFirst().id()),
                "Demo User",
                "Danke! Bei Fragen einfach melden.");
        newsService.createComment(
                stationId,
                news1.id(),
                null,
                stationMemberRepository.resolveIdentity(elternMembers.get(1).id()),
                "Demo User",
                "Kann man hier auch Abwesenheiten eintragen?");
        newsService.createComment(
                stationId,
                news2.id(),
                null,
                stationMemberRepository.resolveIdentity(
                        fortgeschrittenMembers.get(0).id()),
                "Demo User",
                "Ich bin dabei! \uD83D\uDCAA");
        var comment2 = newsService.createComment(
                stationId,
                news2.id(),
                null,
                stationMemberRepository.resolveIdentity(
                        fortgeschrittenMembers.get(1).id()),
                "Demo User",
                "Wie viele Plätze gibt es?");
        newsService.createComment(
                stationId,
                news2.id(),
                comment2.id(),
                stationMemberRepository.resolveIdentity(betreuerMembers.get(0).id()),
                "Demo User",
                "Wir haben 8 Plätze. Bitte schnell anmelden!");

        var news3 = newsService.create(
                stationId,
                "Neue Ausrüstung eingetroffen",
                """
                        Die bestellten **Helme und Handschuhe** sind eingetroffen! \uD83C\uDF89

                        ## Verteilung

                        Die Verteilung findet bei der **nächsten Übung** statt. Bitte beachtet:

                        1. Prüft eure **Größen im Inventar** vorab
                        2. Meldet euch bei Unstimmigkeiten bei den Betreuern
                        3. Bringt eure **alten Helme** zur Rückgabe mit

                        > Die neuen Helme entsprechen der aktuellen **DIN EN 443** Norm und bieten verbesserten Schutz.
                        """,
                "<p>Die bestellten <strong>Helme und Handschuhe</strong> sind eingetroffen! \uD83C\uDF89</p><h2>Verteilung</h2><p>Die Verteilung findet bei der <strong>nächsten Übung</strong> statt. Bitte beachtet:</p><ol><li>Prüft eure <strong>Größen im Inventar</strong> vorab</li><li>Meldet euch bei Unstimmigkeiten bei den Betreuern</li><li>Bringt eure <strong>alten Helme</strong> zur Rückgabe mit</li></ol><blockquote><p>Die neuen Helme entsprechen der aktuellen <strong>DIN EN 443</strong> Norm und bieten verbesserten Schutz.</p></blockquote>",
                stationMemberRepository.resolveIdentity(betreuerMembers.get(1).id()),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        newsService.create(
                stationId,
                "Sommerferien: Übungspause",
                """
                        Während der **Sommerferien** finden keine regulären Übungen statt.

                        ## Zeitraum

                        Der Übungsbetrieb **pausiert** während der gesamten Schulferien. Wir starten wieder am **ersten Montag nach den Ferien**.

                        ### Trotzdem aktiv bleiben?

                        - Das **Wissenscenter** bleibt verfügbar — nutzt die Zeit zum Lernen
                        - Prüft eure **Ausrüstung** und meldet Mängel vorab
                        - Die **Anmeldung** für den Herbst-Wettbewerb öffnet in den Ferien

                        Wir wünschen allen **schöne und erholsame Ferien**! ☀️
                        """,
                "<p>Während der <strong>Sommerferien</strong> finden keine regulären Übungen statt.</p><h2>Zeitraum</h2><p>Der Übungsbetrieb <strong>pausiert</strong> während der gesamten Schulferien. Wir starten wieder am <strong>ersten Montag nach den Ferien</strong>.</p><h3>Trotzdem aktiv bleiben?</h3><ul><li>Das <strong>Wissenscenter</strong> bleibt verfügbar — nutzt die Zeit zum Lernen</li><li>Prüft eure <strong>Ausrüstung</strong> und meldet Mängel vorab</li><li>Die <strong>Anmeldung</strong> für den Herbst-Wettbewerb öffnet in den Ferien</li></ul><p>Wir wünschen allen <strong>schöne und erholsame Ferien</strong>! ☀️</p>",
                stationMemberRepository.resolveIdentity(adminMemberId),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        newsService.createComment(
                stationId,
                news3.id(),
                null,
                stationMemberRepository.resolveIdentity(elternMembers.get(2).id()),
                "Demo User",
                "Werden die alten Helme eingesammelt?");
        newsService.createComment(
                stationId,
                news3.id(),
                null,
                stationMemberRepository.resolveIdentity(betreuerMembers.get(1).id()),
                "Demo User",
                "Ja, bitte zur nächsten Übung mitbringen.");

        return new SeedResult(news1.id());
    }

    public record SeedResult(int firstNewsId) {}
}
