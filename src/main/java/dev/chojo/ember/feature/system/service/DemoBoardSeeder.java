/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.repository.BoardRepository;
import dev.chojo.ember.feature.board.repository.BoardTicketRepository;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Singleton
public class DemoBoardSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoBoardSeeder.class);

    private final BoardRepository boardRepo;
    private final BoardTicketRepository ticketRepo;
    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardRepository federatedBoardRepo;
    private final FederationService federationService;

    @Inject
    public DemoBoardSeeder(
            BoardRepository boardRepo,
            BoardTicketRepository ticketRepo,
            FederatedBoardService federatedBoardService,
            FederatedBoardRepository federatedBoardRepo,
            FederationService federationService) {
        this.boardRepo = boardRepo;
        this.ticketRepo = ticketRepo;
        this.federatedBoardService = federatedBoardService;
        this.federatedBoardRepo = federatedBoardRepo;
        this.federationService = federationService;
    }

    public void seed(
            int stationId,
            StationMember admin,
            List<StationMember> teamMembers,
            int teamRoleId,
            int userRoleId,
            Random rng) {

        // ── Board 1: "Dienstplanung" — SIMPLE preset, TEAM only (view + edit) ──

        var board1 = boardRepo.create(stationId, "Dienstplanung", "Interne Aufgaben für das Betreuerteam", "PLAN");
        var lane1Open = boardRepo.createLane(board1.id(), "Offen", "#3b82f6", 0);
        var lane1Work = boardRepo.createLane(board1.id(), "In Arbeit", "#f59e0b", 1);
        var lane1Done = boardRepo.createLane(board1.id(), "Erledigt", "#22c55e", 2);

        // Restrict view + edit to TEAM only
        boardRepo.setViewAccess(board1.id(), List.of(teamRoleId), List.of(), List.of());
        boardRepo.setEditAccess(board1.id(), List.of(teamRoleId), List.of(), List.of());

        // Tickets in "Offen"
        var t1 = createTicket(
                board1.id(),
                lane1Open.id(),
                1,
                "Dienstplan Juni erstellen",
                "Schichten für den Juni einteilen und an alle verteilen.",
                null,
                TicketPriority.HIGH,
                LocalDate.now().plusDays(7),
                admin.id());
        var t2 = createTicket(
                board1.id(),
                lane1Open.id(),
                2,
                "Neue Helfer einweisen",
                "Einweisung für die drei neuen Helfer organisieren.",
                teamMember(teamMembers, rng),
                TicketPriority.MEDIUM,
                LocalDate.now().plusDays(14),
                admin.id());
        var t3 = createTicket(
                board1.id(),
                lane1Open.id(),
                3,
                "Fahrzeugschlüssel-Übergabe klären",
                null,
                null,
                TicketPriority.LOW,
                null,
                admin.id());

        // Tickets in "In Arbeit"
        var t4 = createTicket(
                board1.id(),
                lane1Work.id(),
                4,
                "Funkgeräte-Inventur",
                "Alle Funkgeräte prüfen und defekte markieren.",
                teamMember(teamMembers, rng),
                TicketPriority.HIGH,
                LocalDate.now().plusDays(3),
                admin.id());
        var t5 = createTicket(
                board1.id(),
                lane1Work.id(),
                5,
                "Übungsplan Q3 abstimmen",
                "Themen und Termine für Juli–September festlegen.",
                admin.id(),
                TicketPriority.MEDIUM,
                LocalDate.now().plusDays(10),
                admin.id());

        // Tickets in "Erledigt"
        var t6 = createTicket(
                board1.id(),
                lane1Done.id(),
                6,
                "Erste-Hilfe-Material nachbestellt",
                null,
                teamMember(teamMembers, rng),
                TicketPriority.MEDIUM,
                null,
                admin.id());
        var t7 = createTicket(
                board1.id(),
                lane1Done.id(),
                7,
                "Schlüsselübergabe dokumentiert",
                null,
                admin.id(),
                TicketPriority.LOW,
                null,
                admin.id());

        // Checklist on t4
        ticketRepo.createChecklistItem(t4, "HRT-Geräte zählen", 0);
        var cl2 = ticketRepo.createChecklistItem(t4, "Akkus prüfen", 1);
        ticketRepo.updateChecklistItem(cl2.id(), "Akkus prüfen", true);
        ticketRepo.createChecklistItem(t4, "Defekte Geräte melden", 2);
        ticketRepo.createChecklistItem(t4, "Liste an Wehrführer", 3);

        // Checklist on t1
        ticketRepo.createChecklistItem(t1, "Verfügbarkeiten abfragen", 0);
        ticketRepo.createChecklistItem(t1, "Schichten einteilen", 1);
        ticketRepo.createChecklistItem(t1, "Plan versenden", 2);

        // Link: t2 blocked by t5
        ticketRepo.createLink(t2, t5, LinkType.BLOCKED_BY);
        // Link: t4 relates to t6
        ticketRepo.createLink(t4, t6, LinkType.RELATES_TO);

        // Transitions for t4 and t5
        ticketRepo.logTransition(t4, lane1Open.id(), lane1Work.id(), admin.id());
        ticketRepo.logTransition(t5, lane1Open.id(), lane1Work.id(), admin.id());
        ticketRepo.logTransition(t6, lane1Open.id(), lane1Work.id(), admin.id());
        ticketRepo.logTransition(t6, lane1Work.id(), lane1Done.id(), teamMember(teamMembers, rng));
        ticketRepo.logTransition(t7, lane1Open.id(), lane1Done.id(), admin.id());

        // Comments
        ticketRepo.createComment(t4, null, admin.id(), "Bitte bis Freitag erledigen.");
        if (!teamMembers.isEmpty()) {
            ticketRepo.createComment(t4, null, teamMembers.getFirst().id(), "Ich fange morgen damit an.");
        }
        ticketRepo.createComment(t1, null, admin.id(), "Wer hat im Juni Urlaub? Bitte melden!");

        // ── Board 2: "Jugendarbeit" — FEEDBACK preset, TEAM edit, USER view ──

        var board2 = boardRepo.create(stationId, "Jugendarbeit", "Ideen und Aufgaben für die Jugendgruppe", "JUG");
        var lane2Open = boardRepo.createLane(board2.id(), "Offen", "#3b82f6", 0);
        var lane2Work = boardRepo.createLane(board2.id(), "In Arbeit", "#f59e0b", 1);
        var lane2Feed = boardRepo.createLane(board2.id(), "Feedback", "#a855f7", 2);
        var lane2Done = boardRepo.createLane(board2.id(), "Erledigt", "#22c55e", 3);

        // Enable backlog for board 2
        var backlogLane = boardRepo.enableBacklog(board2.id());

        // View: all users; Edit: TEAM only
        boardRepo.setEditAccess(board2.id(), List.of(teamRoleId), List.of(), List.of());
        // No view restrictions = visible to all station members

        // Tickets in "Offen"
        var j1 = createTicket(
                board2.id(),
                lane2Open.id(),
                1,
                "Sommerfest planen",
                "Termin, Programm und Helfer für das Sommerfest organisieren.",
                null,
                TicketPriority.HIGH,
                LocalDate.now().plusDays(30),
                admin.id());
        var j2 = createTicket(
                board2.id(),
                lane2Open.id(),
                2,
                "T-Shirt-Design abstimmen",
                "Entwürfe sammeln und Abstimmung starten.",
                null,
                TicketPriority.LOW,
                null,
                admin.id());
        var j3 = createTicket(
                board2.id(),
                lane2Open.id(),
                3,
                "Zeltlager: Anmeldung vorbereiten",
                "Anmeldeformular erstellen und Eltern informieren.",
                teamMember(teamMembers, rng),
                TicketPriority.MEDIUM,
                LocalDate.now().plusDays(21),
                admin.id());

        // Tickets in "In Arbeit"
        var j4 = createTicket(
                board2.id(),
                lane2Work.id(),
                4,
                "Wettbewerb-Training organisieren",
                "Trainingsplan für den Kreiswettbewerb erstellen.",
                teamMember(teamMembers, rng),
                TicketPriority.HIGHEST,
                LocalDate.now().plusDays(5),
                admin.id());
        var j5 = createTicket(
                board2.id(),
                lane2Work.id(),
                5,
                "Neue Übungen für Anfänger",
                "Drei neue Übungseinheiten für die Anfänger-Gruppe ausarbeiten.",
                admin.id(),
                TicketPriority.MEDIUM,
                null,
                admin.id());

        // Tickets in "Feedback"
        var j6 = createTicket(
                board2.id(),
                lane2Feed.id(),
                6,
                "Elternabend-Präsentation",
                "Folien für den Elternabend vorbereiten — bitte prüfen.",
                admin.id(),
                TicketPriority.MEDIUM,
                LocalDate.now().plusDays(2),
                admin.id());

        // Tickets in "Erledigt"
        var j7 = createTicket(
                board2.id(),
                lane2Done.id(),
                7,
                "Gruppenfoto gemacht",
                null,
                teamMember(teamMembers, rng),
                TicketPriority.LOWEST,
                null,
                admin.id());
        var j8 = createTicket(
                board2.id(),
                lane2Done.id(),
                8,
                "Erste-Hilfe-Kurs gebucht",
                "Kurs am 15.06. mit dem DRK gebucht.",
                admin.id(),
                TicketPriority.HIGH,
                null,
                admin.id());

        // Checklist on j1
        ticketRepo.createChecklistItem(j1, "Termin festlegen", 0);
        ticketRepo.createChecklistItem(j1, "Programm erstellen", 1);
        ticketRepo.createChecklistItem(j1, "Helfer einteilen", 2);
        ticketRepo.createChecklistItem(j1, "Einkaufsliste", 3);
        ticketRepo.createChecklistItem(j1, "Eltern informieren", 4);

        // Checklist on j4 — partially done
        var jcl1 = ticketRepo.createChecklistItem(j4, "Löschangriff üben", 0);
        ticketRepo.updateChecklistItem(jcl1.id(), "Löschangriff üben", true);
        var jcl2 = ticketRepo.createChecklistItem(j4, "Staffellauf üben", 1);
        ticketRepo.updateChecklistItem(jcl2.id(), "Staffellauf üben", true);
        ticketRepo.createChecklistItem(j4, "Knotenkunde wiederholen", 2);
        ticketRepo.createChecklistItem(j4, "Generalprobe am Freitag", 3);

        // Links
        ticketRepo.createLink(j3, j1, LinkType.RELATES_TO);
        ticketRepo.createLink(j4, j6, LinkType.CAUSES);

        // Transitions
        ticketRepo.logTransition(j4, lane2Open.id(), lane2Work.id(), admin.id());
        ticketRepo.logTransition(j5, lane2Open.id(), lane2Work.id(), admin.id());
        ticketRepo.logTransition(j6, lane2Open.id(), lane2Work.id(), admin.id());
        ticketRepo.logTransition(j6, lane2Work.id(), lane2Feed.id(), admin.id());
        ticketRepo.logTransition(j7, lane2Open.id(), lane2Done.id(), teamMember(teamMembers, rng));
        ticketRepo.logTransition(j8, lane2Work.id(), lane2Done.id(), admin.id());

        // Comments
        ticketRepo.createComment(j4, null, admin.id(), "Wettbewerb ist am 20. Juni — wir müssen Gas geben!");
        if (!teamMembers.isEmpty()) {
            ticketRepo.createComment(j4, null, teamMembers.getFirst().id(), "Ich kümmere mich um den Staffellauf.");
            ticketRepo.createComment(j6, null, teamMembers.getFirst().id(), "Sieht gut aus, nur Folie 3 anpassen.");
        }
        ticketRepo.createComment(j1, null, admin.id(), "Vorschlag: 12. Juli als Termin.");

        // ── Additional tickets for Board 1 (Dienstplanung) ──
        seedExtraTicketsBoard1(board1.id(), lane1Open.id(), lane1Work.id(), lane1Done.id(), admin, teamMembers, rng);

        // ── Additional tickets for Board 2 (Jugendarbeit) ──
        seedExtraTicketsBoard2(
                board2.id(), lane2Open.id(), lane2Work.id(), lane2Feed.id(), lane2Done.id(), admin, teamMembers, rng);

        // ── Backlog tickets for Board 2 ──
        createTicket(
                board2.id(),
                backlogLane.id(),
                30,
                "Zeltlager-Konzept 2028",
                "Erste Ideen für das übernächste Zeltlager sammeln.",
                null,
                TicketPriority.LOW,
                null,
                admin.id());
        createTicket(
                board2.id(),
                backlogLane.id(),
                31,
                "Kooperation mit THW-Jugend",
                "Gemeinsame Übung planen.",
                null,
                TicketPriority.MEDIUM,
                null,
                admin.id());
        createTicket(
                board2.id(),
                backlogLane.id(),
                32,
                "T-Shirt-Design",
                "Neues Design für Gruppen-T-Shirts entwerfen.",
                teamMember(teamMembers, rng),
                TicketPriority.LOW,
                null,
                admin.id());
        createTicket(
                board2.id(),
                backlogLane.id(),
                33,
                "Elternabend organisieren",
                "Termin finden und Einladungen versenden.",
                null,
                TicketPriority.MEDIUM,
                null,
                admin.id());
        createTicket(
                board2.id(),
                backlogLane.id(),
                34,
                "Digitales Dienstbuch einführen",
                null,
                null,
                TicketPriority.LOW,
                null,
                admin.id());

        // ── Labels ──
        var bugLabel1 = boardRepo.createLabel(board1.id(), "Bug", "#ef4444");
        var urgentLabel1 = boardRepo.createLabel(board1.id(), "Dringend", "#f59e0b");
        var ideaLabel1 = boardRepo.createLabel(board1.id(), "Idee", "#3b82f6");
        var bugLabel2 = boardRepo.createLabel(board2.id(), "Bug", "#ef4444");
        var eventLabel2 = boardRepo.createLabel(board2.id(), "Veranstaltung", "#8b5cf6");
        var materialLabel2 = boardRepo.createLabel(board2.id(), "Material", "#14b8a6");
        var ideaLabel2 = boardRepo.createLabel(board2.id(), "Idee", "#3b82f6");

        // Assign labels to some tickets
        var b1Tickets = ticketRepo.findByBoard(board1.id());
        if (b1Tickets.size() > 5) {
            boardRepo.addLabelToTicket(b1Tickets.get(0).id(), urgentLabel1.id());
            boardRepo.addLabelToTicket(b1Tickets.get(1).id(), ideaLabel1.id());
            boardRepo.addLabelToTicket(b1Tickets.get(2).id(), bugLabel1.id());
            boardRepo.addLabelToTicket(b1Tickets.get(3).id(), urgentLabel1.id());
            boardRepo.addLabelToTicket(b1Tickets.get(3).id(), bugLabel1.id());
            boardRepo.addLabelToTicket(b1Tickets.get(5).id(), ideaLabel1.id());
        }
        var b2Tickets = ticketRepo.findByBoard(board2.id());
        if (b2Tickets.size() > 8) {
            boardRepo.addLabelToTicket(b2Tickets.get(0).id(), eventLabel2.id());
            boardRepo.addLabelToTicket(b2Tickets.get(1).id(), materialLabel2.id());
            boardRepo.addLabelToTicket(b2Tickets.get(2).id(), eventLabel2.id());
            boardRepo.addLabelToTicket(b2Tickets.get(2).id(), materialLabel2.id());
            boardRepo.addLabelToTicket(b2Tickets.get(4).id(), ideaLabel2.id());
            boardRepo.addLabelToTicket(b2Tickets.get(6).id(), bugLabel2.id());
            boardRepo.addLabelToTicket(b2Tickets.get(8).id(), eventLabel2.id());
        }

        // Set varying lane_entered_at for time-in-lane dot indicators
        var allBoard1 = ticketRepo.findByBoard(board1.id());
        var allBoard2 = ticketRepo.findByBoard(board2.id());
        var now = Instant.now();
        int[] daysAgo = {0, 1, 2, 4, 6, 8, 10, 14, 20, 30, 45, 60};
        for (var tickets : List.of(allBoard1, allBoard2)) {
            for (int i = 0; i < tickets.size(); i++) {
                int days = daysAgo[i % daysAgo.length];
                if (days > 0) {
                    ticketRepo.setLaneEnteredAt(tickets.get(i).id(), now.minus(Duration.ofDays(days)));
                }
            }
        }

        log.info("Demo: Created 2 boards with {} tickets total", 15 + 40);
    }

    private void seedExtraTicketsBoard1(
            int boardId,
            int openLane,
            int workLane,
            int doneLane,
            StationMember admin,
            List<StationMember> team,
            Random rng) {
        String[][] tickets = {
            {"Einsatzberichte digitalisieren", "Alte Papierberichte einscannen und archivieren."},
            {"Atemschutz-Wartung planen", "Alle Geräte bis Monatsende prüfen lassen."},
            {"Hydranten-Kontrolle Süd", "Hydranten im Südviertel auf Funktion prüfen."},
            {"Spind-Zuweisung aktualisieren", "Neue Mitglieder brauchen Spinde."},
            {"Fahrzeug TÜV-Termin vereinbaren", null},
            {"Ausbildungsnachweis-Hefte verteilen", "Neue Hefte sind bestellt, verteilen sobald da."},
            {"Gerätehaus: Tür reparieren", "Seitentür klemmt seit letzter Woche."},
            {"Einsatzkleidung nachbestellen", "Drei Hosen und zwei Jacken fehlen."},
            {"Schlauchturm aufräumen", null},
            {"Jugendfeuerwehr-Betreuer suchen", "Wir brauchen mindestens zwei weitere Betreuer."},
            {"Nächster Elternabend planen", "Termin mit Elternvertretung abstimmen."},
            {"Dienstplan Juli erstellen", "Schichten für Juli planen."},
            {"Feuerlöscher-Prüfung organisieren", "Alle Feuerlöscher müssen geprüft werden."},
            {"Notstromaggregat testen", "Monatlicher Test steht an."},
            {"Erste-Hilfe-Kurs für Neulinge", "Kurs beim DRK anfragen."},
            {"Funkrufnamen aktualisieren", "Liste an die Leitstelle senden."},
            {"Beschaffungsantrag Wärmebildkamera", "Antrag beim Förderverein einreichen."},
            {"Schlüsselplan überarbeiten", "Wer hat welche Schlüssel?"},
            {"Dienstvorschrift aktualisieren", null},
            {"Winterdienst-Material prüfen", "Salz und Streugut kontrollieren."},
        };
        TicketPriority[] prios = TicketPriority.values();
        int[] lanes = {
            openLane, openLane, openLane, workLane, workLane, workLane, workLane, doneLane, doneLane, doneLane,
            openLane, openLane, workLane, doneLane, openLane, workLane, openLane, doneLane, workLane, openLane
        };
        for (int i = 0; i < tickets.length; i++) {
            int tid = createTicket(
                    boardId,
                    lanes[i],
                    i + 8,
                    tickets[i][0],
                    tickets[i][1],
                    rng.nextInt(3) == 0 ? null : teamMember(team, rng),
                    prios[rng.nextInt(prios.length)],
                    rng.nextInt(3) == 0 ? LocalDate.now().plusDays(rng.nextInt(30)) : null,
                    admin.id());
            if (lanes[i] == workLane) {
                ticketRepo.logTransition(tid, openLane, workLane, admin.id());
            } else if (lanes[i] == doneLane) {
                ticketRepo.logTransition(tid, openLane, workLane, teamMember(team, rng));
                ticketRepo.logTransition(tid, workLane, doneLane, admin.id());
            }
            if (rng.nextInt(3) == 0) {
                ticketRepo.createComment(tid, null, admin.id(), "Bitte zeitnah erledigen.");
            }
            if (rng.nextInt(4) == 0 && !team.isEmpty()) {
                ticketRepo.createComment(
                        tid, null, team.get(rng.nextInt(team.size())).id(), "Wird gemacht!");
            }
        }
        // Add some cross-links
        var allTickets = ticketRepo.findByBoard(boardId);
        if (allTickets.size() > 5) {
            ticketRepo.createLink(allTickets.get(2).id(), allTickets.get(4).id(), LinkType.RELATES_TO);
            ticketRepo.createLink(allTickets.get(5).id(), allTickets.get(8).id(), LinkType.BLOCKS);
            ticketRepo.createLink(allTickets.get(10).id(), allTickets.get(12).id(), LinkType.CAUSED_BY);
        }
    }

    private void seedExtraTicketsBoard2(
            int boardId,
            int openLane,
            int workLane,
            int feedLane,
            int doneLane,
            StationMember admin,
            List<StationMember> team,
            Random rng) {
        String[][] tickets = {
            {"Wandertag organisieren", "Route planen, Genehmigung einholen."},
            {"Knotenkunde-Arbeitsblätter erstellen", null},
            {"Wimpel für Stadtteilfest basteln", "Material besorgen und Basteltermin planen."},
            {"Schwimmbad-Ausflug planen", "Bus reservieren, Eltern informieren."},
            {"Nachtwanderung vorbereiten", "Strecke abgehen, Sicherheitskonzept."},
            {"Feuerwehrquiz für Tag der offenen Tür", "Fragen erstellen und Preise besorgen."},
            {"Löschübung am Bach planen", "Genehmigung bei der Gemeinde einholen."},
            {"Gruppenstunden-Themen Q4", "Themen für Oktober bis Dezember festlegen."},
            {"Weihnachtsfeier planen", "Raum buchen, Programm überlegen."},
            {"Fotowand für Gerätehaus", "Fotos von allen Veranstaltungen sammeln."},
            {"Jugendleitercard beantragen", "Für drei Betreuer die JuLeiCa beantragen."},
            {"Erste-Hilfe-Parcours aufbauen", "Stationen vorbereiten und Material checken."},
            {"Lagerfeuerabend planen", "Stockbrot-Teig, Gitarre, Lieder."},
            {"Besuch bei Berufsfeuerwehr", "Termin anfragen für Besichtigung."},
            {"Spieleabend organisieren", "Spiele zusammenstellen, Getränke besorgen."},
            {"Trikots waschen und sortieren", null},
            {"Aufnahmeantrag aktualisieren", "Neues Formular mit aktuellem Datenschutz."},
            {"Video für Social Media", "Kurzfilm über die Jugendfeuerwehr drehen."},
            {"Geburtstagskarten schreiben", "Für alle Geburtstagskinder im Quartal."},
            {"Jahresplanung 2027 beginnen", "Erste Ideen sammeln und Termine blockieren."},
        };
        TicketPriority[] prios = TicketPriority.values();
        int[] lanes = {
            openLane, openLane, workLane, openLane, workLane, feedLane, openLane, workLane, openLane, doneLane,
            openLane, workLane, doneLane, openLane, feedLane, doneLane, workLane, openLane, doneLane, openLane
        };
        for (int i = 0; i < tickets.length; i++) {
            int tid = createTicket(
                    boardId,
                    lanes[i],
                    i + 9,
                    tickets[i][0],
                    tickets[i][1],
                    rng.nextInt(3) == 0 ? null : teamMember(team, rng),
                    prios[rng.nextInt(prios.length)],
                    rng.nextInt(3) == 0 ? LocalDate.now().plusDays(rng.nextInt(45)) : null,
                    admin.id());
            if (lanes[i] != openLane) {
                ticketRepo.logTransition(tid, openLane, lanes[i] == doneLane ? workLane : lanes[i], admin.id());
                if (lanes[i] == doneLane) {
                    ticketRepo.logTransition(tid, workLane, doneLane, teamMember(team, rng));
                } else if (lanes[i] == feedLane) {
                    ticketRepo.logTransition(tid, workLane, feedLane, admin.id());
                }
            }
            if (rng.nextInt(3) == 0) {
                ticketRepo.createComment(tid, null, admin.id(), "Wer kann das übernehmen?");
            }
            if (rng.nextInt(3) == 0 && !team.isEmpty()) {
                ticketRepo.createComment(
                        tid, null, team.get(rng.nextInt(team.size())).id(), "Ich mach das gerne!");
            }
        }
        var allTickets = ticketRepo.findByBoard(boardId);
        if (allTickets.size() > 10) {
            ticketRepo.createLink(allTickets.get(3).id(), allTickets.get(7).id(), LinkType.RELATES_TO);
            ticketRepo.createLink(allTickets.get(9).id(), allTickets.get(15).id(), LinkType.BLOCKS);
            ticketRepo.createLink(allTickets.get(11).id(), allTickets.get(18).id(), LinkType.CAUSES);
            ticketRepo.createLink(allTickets.get(14).id(), allTickets.get(20).id(), LinkType.RELATES_TO);
        }
    }

    /**
     * Seeds a shared board between primary and partner stations.
     * The board is owned by the primary station, shared with FULL mode to the partner.
     * View: all members, Edit: TEAM only.
     */
    public void seedSharedBoard(
            int stationId,
            int partnerStationId,
            StationMember admin,
            List<StationMember> teamMembers,
            int teamRoleId,
            int memberRoleId,
            Random rng) {
        // Find the federation partner ID
        var partners = federationService.findPartners(stationId);
        var partner = partners.stream().findFirst().orElse(null);
        if (partner == null) {
            log.warn("Demo: No federation partner found, skipping shared board");
            return;
        }

        var board = boardRepo.create(stationId, "Gemeinsame Planung", "Gemeinsames Board mit der Partnerwache", "GEM");
        var laneOpen = boardRepo.createLane(board.id(), "Offen", "#3b82f6", 0);
        var laneWork = boardRepo.createLane(board.id(), "In Arbeit", "#f59e0b", 1);
        var laneDone = boardRepo.createLane(board.id(), "Erledigt", "#22c55e", 2);

        // View: MEMBER + TEAM (all members can see), Edit: TEAM only
        boardRepo.setEditAccess(board.id(), List.of(teamRoleId), List.of(), List.of());
        // No view restrictions = visible to all station members

        // Share with partner in FULL mode
        federatedBoardService.shareBoard(
                board.id(), List.of(new FederatedBoardService.PartnerShareConfig(partner.id(), BoardShareMode.FULL)));

        // Labels
        var labelGemeinsam = boardRepo.createLabel(board.id(), "Gemeinsam", "#8b5cf6");
        var labelUebung = boardRepo.createLabel(board.id(), "Übung", "#3b82f6");
        var labelOrga = boardRepo.createLabel(board.id(), "Organisation", "#f59e0b");

        var partnerMember1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var partnerMember2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // -- Tickets from primary station members --
        var t1 = createTicket(
                board.id(),
                laneOpen.id(),
                1,
                "Gemeinsame Übung planen",
                "Termin und Thema für die nächste gemeinsame Übung mit der Partnerwache abstimmen.",
                admin.id(),
                TicketPriority.HIGH,
                LocalDate.now().plusDays(14),
                admin.id());
        boardRepo.addLabelToTicket(t1, labelGemeinsam.id());
        boardRepo.addLabelToTicket(t1, labelUebung.id());

        var t2 = createTicket(
                board.id(),
                laneWork.id(),
                2,
                "Funkkanal-Abstimmung",
                "Gemeinsamen Funkkanal für die Übung festlegen und testen.",
                teamMember(teamMembers, rng),
                TicketPriority.MEDIUM,
                LocalDate.now().plusDays(7),
                admin.id());
        ticketRepo.logTransition(t2, laneOpen.id(), laneWork.id(), admin.id());
        boardRepo.addLabelToTicket(t2, labelOrga.id());

        var t3 = createTicket(
                board.id(),
                laneDone.id(),
                3,
                "Ansprechpartner ausgetauscht",
                "Kontaktdaten der Jugendwarte beider Wehren ausgetauscht.",
                admin.id(),
                TicketPriority.LOW,
                null,
                admin.id());
        ticketRepo.logTransition(t3, laneOpen.id(), laneDone.id(), admin.id());
        boardRepo.addLabelToTicket(t3, labelOrga.id());

        var t4 = createTicket(
                board.id(),
                laneOpen.id(),
                4,
                "Wettkampf-Team zusammenstellen",
                "Gemeinsames Team für den Kreiswettbewerb aufstellen.",
                null,
                TicketPriority.HIGHEST,
                LocalDate.now().plusDays(21),
                admin.id());
        boardRepo.addLabelToTicket(t4, labelGemeinsam.id());

        var t5 = createTicket(
                board.id(),
                laneWork.id(),
                5,
                "Materialien für Übung zusammenstellen",
                "Welche Materialien bringt welche Wache mit?",
                teamMember(teamMembers, rng),
                TicketPriority.MEDIUM,
                LocalDate.now().plusDays(10),
                admin.id());
        ticketRepo.logTransition(t5, laneOpen.id(), laneWork.id(), teamMember(teamMembers, rng));
        boardRepo.addLabelToTicket(t5, labelUebung.id());

        // -- Comments from primary station --
        ticketRepo.createComment(t1, null, admin.id(), "Ich schlage den 20. Juli vor. Passt das bei euch?");
        if (!teamMembers.isEmpty()) {
            ticketRepo.createComment(t1, null, teamMembers.getFirst().id(), "Bei uns passt es, gute Idee!");
            ticketRepo.createComment(t2, null, teamMembers.getFirst().id(), "Kanal 4 wäre frei, teste ich morgen.");
        }
        ticketRepo.createComment(t4, null, admin.id(), "Wir können 5 Jugendliche stellen. Wie viele kommen von euch?");
        ticketRepo.createComment(
                t5, null, admin.id(), "Wir bringen die Schläuche mit. Könnt ihr Strahlrohre organisieren?");

        // -- Simulate federated tickets from partner station --
        // Use admin as local creator (FK constraint), mark as federated via creator table
        var t6 = createTicket(
                board.id(),
                laneOpen.id(),
                6,
                "Verpflegung für gemeinsame Übung",
                "Wir kümmern uns um Getränke und Snacks für die Übungsteilnehmer.",
                null,
                TicketPriority.LOW,
                LocalDate.now().plusDays(12),
                admin.id());
        federatedBoardRepo.setFederatedCreator(t6, partner.id(), partnerMember1);
        boardRepo.addLabelToTicket(t6, labelGemeinsam.id());

        var t7 = createTicket(
                board.id(),
                laneWork.id(),
                7,
                "Übungsgelände vorbereiten",
                "Wir bereiten das Gelände bei uns vor. Anfahrt wird noch geteilt.",
                null,
                TicketPriority.HIGH,
                LocalDate.now().plusDays(5),
                admin.id());
        federatedBoardRepo.setFederatedCreator(t7, partner.id(), partnerMember1);
        ticketRepo.logTransition(t7, laneOpen.id(), laneWork.id(), admin.id());
        boardRepo.addLabelToTicket(t7, labelUebung.id());

        // Federated comments (from partner members — use admin as local author, mark as federated)
        var fc1 = ticketRepo.createComment(t1, null, admin.id(), "Bei uns passt der 20. Juli auch! Wir sind dabei.");
        federatedBoardRepo.setFederatedCommentAuthor(fc1.id(), partner.id(), partnerMember1);

        var fc2 =
                ticketRepo.createComment(t4, null, admin.id(), "Wir können 4 Jugendliche und einen Betreuer schicken.");
        federatedBoardRepo.setFederatedCommentAuthor(fc2.id(), partner.id(), partnerMember1);

        var fc3 = ticketRepo.createComment(
                t5, null, admin.id(), "Strahlrohre sind kein Problem, wir bringen 3 Stück mit.");
        federatedBoardRepo.setFederatedCommentAuthor(fc3.id(), partner.id(), partnerMember2);

        var fc4 = ticketRepo.createComment(t6, null, admin.id(), "Wasser und Apfelsaft sind bestellt.");
        federatedBoardRepo.setFederatedCommentAuthor(fc4.id(), partner.id(), partnerMember1);

        // Checklist on t1
        ticketRepo.createChecklistItem(t1, "Termin abstimmen", 0);
        ticketRepo.createChecklistItem(t1, "Thema festlegen", 1);
        ticketRepo.createChecklistItem(t1, "Materialien klären", 2);
        ticketRepo.createChecklistItem(t1, "Anfahrt kommunizieren", 3);

        // Checklist on t4 — partially done
        var cl1 = ticketRepo.createChecklistItem(t4, "Teilnehmer unserer Wache", 0);
        ticketRepo.updateChecklistItem(cl1.id(), "Teilnehmer unserer Wache", true);
        ticketRepo.createChecklistItem(t4, "Teilnehmer Partnerwache", 1);
        ticketRepo.createChecklistItem(t4, "Positionen festlegen", 2);
        ticketRepo.createChecklistItem(t4, "Training planen", 3);

        // Links
        ticketRepo.createLink(t1, t5, LinkType.RELATES_TO);
        ticketRepo.createLink(t4, t1, LinkType.BLOCKED_BY);
        ticketRepo.createLink(t6, t1, LinkType.RELATES_TO);

        // Vary lane_entered_at for time indicators
        var now = Instant.now();
        ticketRepo.setLaneEnteredAt(t2, now.minus(Duration.ofDays(3)));
        ticketRepo.setLaneEnteredAt(t3, now.minus(Duration.ofDays(14)));
        ticketRepo.setLaneEnteredAt(t5, now.minus(Duration.ofDays(5)));
        ticketRepo.setLaneEnteredAt(t7, now.minus(Duration.ofDays(2)));

        log.info("Demo: Created shared board 'Gemeinsame Planung' with 7 tickets");
    }

    private int createTicket(
            int boardId,
            int laneId,
            int ticketNumber,
            String title,
            String description,
            Integer assignedMemberId,
            TicketPriority priority,
            LocalDate dueDate,
            int createdBy) {
        int num = boardRepo.nextTicketNumber(boardId);
        var ticket = ticketRepo.createTicket(
                boardId, laneId, num, title, description, assignedMemberId, priority, dueDate, 0, createdBy);
        return ticket.id();
    }

    private int teamMember(List<StationMember> teamMembers, Random rng) {
        if (teamMembers.isEmpty()) return 0;
        return teamMembers.get(rng.nextInt(teamMembers.size())).id();
    }
}
