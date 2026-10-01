/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.news.service.NewsService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Seeds demo news articles with comments.
 */
@Singleton
public class DemoNewsSeeder implements DemoPerStationSeeder {
    private final NewsService newsService;
    private final CommentService commentService;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public DemoNewsSeeder(
            NewsService newsService, CommentService commentService, StationMemberRepository stationMemberRepository) {
        this.newsService = newsService;
        this.commentService = commentService;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public int order() {
        return NEWS;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = station.members();
        station.news(seed(
                station.stationId(),
                station.adminMember().id(),
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

                        - **Terminübersicht** - Alle Übungen, Veranstaltungen und Wettbewerbe auf einen Blick
                        - **Anwesenheitsverwaltung** - Schnelles Ein- und Auschecken bei Übungen
                        - **Inventarverwaltung** - Eure Ausrüstung immer im Blick
                        - **Wiki** - Lernmaterial und Protokolle

                        ## Erste Schritte

                        1. Prüft euer **Profil** und ergänzt fehlende Daten
                        2. Schaut euch die **kommenden Termine** an
                        3. Meldet euch für den nächsten **Wettbewerb** an

                        > **Tipp:** Bei Fragen könnt ihr jederzeit die Betreuer ansprechen oder die Hilfe-Seite nutzen.

                        Wir freuen uns auf eine tolle Zeit! \uD83D\uDE92
                        """,
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
        int comment1 = comment(news1.id(), null, elternMembers.get(0), "Super, endlich eine moderne Plattform!");
        comment(news1.id(), comment1, betreuerMembers.getFirst(), "Danke! Bei Fragen einfach melden.");
        comment(news1.id(), null, elternMembers.get(1), "Kann man hier auch Abwesenheiten eintragen?");
        comment(news2.id(), null, fortgeschrittenMembers.get(0), "Ich bin dabei! \uD83D\uDCAA");
        int comment2 = comment(news2.id(), null, fortgeschrittenMembers.get(1), "Wie viele Plätze gibt es?");
        comment(news2.id(), comment2, betreuerMembers.get(0), "Wir haben 8 Plätze. Bitte schnell anmelden!");

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

                        - Das **Wissenscenter** bleibt verfügbar - nutzt die Zeit zum Lernen
                        - Prüft eure **Ausrüstung** und meldet Mängel vorab
                        - Die **Anmeldung** für den Herbst-Wettbewerb öffnet in den Ferien

                        Wir wünschen allen **schöne und erholsame Ferien**! ☀️
                        """,
                stationMemberRepository.resolveIdentity(adminMemberId),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        comment(news3.id(), null, elternMembers.get(2), "Werden die alten Helme eingesammelt?");
        comment(news3.id(), null, betreuerMembers.get(1), "Ja, bitte zur nächsten Übung mitbringen.");

        return new SeedResult(news1.id());
    }

    /**
     * Writes a demo comment on an entry, telling whoever a comment written there tells.
     *
     * @return the comment's id
     */
    private int comment(int newsId, @Nullable Integer parentId, StationMember author, String content) {
        var target = commentService.target(CommentEntityType.NEWS, newsId).orElseThrow();
        var writer = CommentWriter.local(stationMemberRepository.resolveIdentity(author.id()), "Demo User");
        return commentService
                .createOn(target, writer, new NewComment(parentId, null, content))
                .id();
    }

    public record SeedResult(int firstNewsId) {}
}
