/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Seeds a second demo station and federates it with the primary station.
 * Shares knowledge base content, a quiz catalog, and a test protocol.
 */
@Singleton
public class DemoFederationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoFederationSeeder.class);

    private final StationRepository stationRepository;
    private final FederationService federationService;
    private final KnowledgeBaseService kbService;
    private final QuizService quizService;
    private final TestProtocolService protocolService;
    private final EventService eventService;
    private final EventFederationService eventFederationService;
    private final EventFederationRepository eventFederationRepository;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final PasswordHasher passwordHasher;
    private final NewsService newsService;
    private final NewsFederationService newsFederationService;
    private final CommentService commentService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final Demo demoConfig;
    private final Api apiConfig;

    @Inject
    public DemoFederationSeeder(
            StationRepository stationRepository,
            FederationService federationService,
            KnowledgeBaseService kbService,
            QuizService quizService,
            TestProtocolService protocolService,
            EventService eventService,
            EventFederationService eventFederationService,
            EventFederationRepository eventFederationRepository,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            PasswordHasher passwordHasher,
            NewsService newsService,
            NewsFederationService newsFederationService,
            CommentService commentService,
            MemberIdentityFactory memberIdentityFactory,
            Demo demoConfig,
            Api apiConfig) {
        this.stationRepository = stationRepository;
        this.federationService = federationService;
        this.kbService = kbService;
        this.quizService = quizService;
        this.protocolService = protocolService;
        this.eventService = eventService;
        this.eventFederationService = eventFederationService;
        this.eventFederationRepository = eventFederationRepository;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.passwordHasher = passwordHasher;
        this.newsService = newsService;
        this.newsFederationService = newsFederationService;
        this.commentService = commentService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.demoConfig = demoConfig;
        this.apiConfig = apiConfig;
    }

    public record SeedResult(int partnerStationId, int partnerMemberId) {}

    /**
     * Seeds a partner station, federates it with the primary station, and shares content.
     *
     * @param primaryStationId the primary station ID
     * @param createdBy        the member ID creating the data
     * @return the seed result with partner station and member IDs
     */
    public SeedResult seed(int primaryStationId, int createdBy) {
        // Opt primary station into discovery
        stationRepository.updateDiscoverySettings(
                primaryStationId,
                DiscoveryVisibility.PUBLIC,
                "Unsere Jugendfeuerwehr — Ausbildung, Technik und Gemeinschaft",
                true);

        // Create a second station
        var partnerStation = stationRepository.create("JF Partnerwache");
        log.info("Demo: Created partner station '{}' (id={})", partnerStation.name(), partnerStation.id());
        stationRepository.updateDiscoverySettings(
                partnerStation.id(),
                DiscoveryVisibility.PUBLIC,
                "Partnerwache für gemeinsame Übungen und Ausbildung",
                false);

        // Create a manager account on the partner station
        var partnerAccount = accountRepository.create("partner@demo.ember", "Partner", "Manager", true);
        accountRepository.createCredential(partnerAccount.id(), passwordHasher.hash("demo"));
        var partnerMember = stationMemberRepository.create(partnerStation.id(), partnerAccount.id());
        var managerRole = stationMemberRepository.findRoleByName(Roles.MANAGER).orElseThrow();
        var loginRole = stationMemberRepository.findRoleByName(Roles.LOGIN).orElseThrow();
        stationMemberRepository.addRole(partnerMember.id(), managerRole.id());
        stationMemberRepository.addRole(partnerMember.id(), loginRole.id());
        log.info("Demo: Created partner manager account partner@demo.ember");

        // Create team members on the partner station
        var teamRole = stationMemberRepository.findRoleByName(Roles.TEAM).orElseThrow();
        var memberRole = stationMemberRepository.findRoleByName(Roles.MEMBER).orElseThrow();
        var guardianRole =
                stationMemberRepository.findRoleByName(Roles.GUARDIAN).orElseThrow();

        var team1Account = accountRepository.create("team1@partner.ember", "Lisa", "Brandmeister", true);
        accountRepository.createCredential(team1Account.id(), passwordHasher.hash("demo"));
        var team1 = stationMemberRepository.create(partnerStation.id(), team1Account.id());
        stationMemberRepository.addRole(team1.id(), loginRole.id());
        stationMemberRepository.addRole(team1.id(), teamRole.id());

        var team2Account = accountRepository.create("team2@partner.ember", "Jonas", "Löschzug", true);
        accountRepository.createCredential(team2Account.id(), passwordHasher.hash("demo"));
        var team2 = stationMemberRepository.create(partnerStation.id(), team2Account.id());
        stationMemberRepository.addRole(team2.id(), loginRole.id());
        stationMemberRepository.addRole(team2.id(), teamRole.id());

        var member1Account = accountRepository.create("member1@partner.ember", "Emma", "Schlauch", true);
        accountRepository.createCredential(member1Account.id(), passwordHasher.hash("demo"));
        var member1 = stationMemberRepository.create(partnerStation.id(), member1Account.id());
        stationMemberRepository.addRole(member1.id(), loginRole.id());
        stationMemberRepository.addRole(member1.id(), memberRole.id());

        var member2Account = accountRepository.create("member2@partner.ember", "Felix", "Strahlrohr", true);
        accountRepository.createCredential(member2Account.id(), passwordHasher.hash("demo"));
        var member2 = stationMemberRepository.create(partnerStation.id(), member2Account.id());
        stationMemberRepository.addRole(member2.id(), loginRole.id());
        stationMemberRepository.addRole(member2.id(), memberRole.id());

        var guardianAccount = accountRepository.create("guardian@partner.ember", "Petra", "Elternbeirat", true);
        accountRepository.createCredential(guardianAccount.id(), passwordHasher.hash("demo"));
        var guardian = stationMemberRepository.create(partnerStation.id(), guardianAccount.id());
        stationMemberRepository.addRole(guardian.id(), loginRole.id());
        stationMemberRepository.addRole(guardian.id(), guardianRole.id());

        log.info("Demo: Created 5 additional members on partner station");

        // Create a KB entry on the partner station
        kbService.createMarkdownFile(
                partnerStation.id(),
                null,
                "Ausbildungsleitfaden",
                "Gemeinsamer Ausbildungsleitfaden der Partnerwache",
                """
                        # Ausbildungsleitfaden

                        Dieser Leitfaden enthält die wichtigsten Themen für die gemeinsame Ausbildung.

                        ## Themen

                        - Grundlagen der Brandbekämpfung
                        - Technische Hilfeleistung
                        - Erste Hilfe Auffrischung
                        - Funkausbildung

                        > Dieser Inhalt wurde von der Partnerwache geteilt.
                        """,
                partnerMember.id());

        // Create a KB folder with files on the partner station
        var partnerFolder = kbService.createFolder(
                partnerStation.id(),
                null,
                "Gemeinsame Dokumente",
                "Geteilte Ausbildungsunterlagen",
                partnerMember.id());
        kbService.createMarkdownFile(
                partnerStation.id(),
                partnerFolder.id(),
                "Einsatzablauf",
                "Standard-Einsatzablauf für gemeinsame Übungen",
                """
                        # Einsatzablauf

                        ## Alarmierung
                        1. Alarmierung über Funkmeldeempfänger
                        2. Anfahrt zum Gerätehaus
                        3. Einkleiden und Fahrzeugbesetzung

                        ## Anfahrt
                        - Einsatzort anfahren
                        - Rückmeldung an Leitstelle

                        ## Einsatzstelle
                        - Erkundung durch Einsatzleiter
                        - Aufgabenverteilung
                        - Durchführung
                        """,
                partnerMember.id());
        kbService.createMarkdownFile(
                partnerStation.id(),
                partnerFolder.id(),
                "Funkrufnamen",
                "Übersicht der Funkrufnamen beider Wehren",
                """
                        # Funkrufnamen

                        | Fahrzeug | Rufname |
                        |----------|---------|
                        | LF 10    | Florian Musterstadt 1-44-1 |
                        | MTW      | Florian Musterstadt 1-19-1 |
                        | TSF-W    | Florian Partnerwache 1-43-1 |
                        """,
                partnerMember.id());

        // Federate the two stations
        // When federationForceHttp is set, register partners as remote (same host, exercising HTTP path)
        String remoteHost = demoConfig.federationForceHttp() ? "http://localhost:" + apiConfig.port() : null;

        var initiatingKeyPair = federationService.generateKeyPair();
        stationRepository.updateFederationPrivateKey(
                partnerStation.id(), federationService.encodePrivateKey(initiatingKeyPair));
        var partner = federationService.acceptInvite(
                primaryStationId,
                partnerStation.id(),
                federationService.encodePublicKey(initiatingKeyPair),
                remoteHost,
                remoteHost);

        // Share the partner station's KB with the primary station (files + folders)
        var kbFiles = kbService.findFiles(partnerStation.id(), null);
        for (var file : kbFiles) {
            federationService.createKbShare(partnerStation.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        }
        var kbFolders = kbService.findFolders(partnerStation.id(), null);
        for (var folder : kbFolders) {
            federationService.createKbShare(partnerStation.id(), null, folder.id(), ShareScope.ALL_PARTNERS);
        }

        // Share the primary station's KB with the partner station
        var primaryKbFiles = kbService.findFiles(primaryStationId, null);
        for (var file : primaryKbFiles) {
            federationService.createKbShare(primaryStationId, file.id(), null, ShareScope.ALL_PARTNERS);
        }

        // Create and share a quiz catalog on the partner station
        var partnerCatalog = quizService.createCatalog(
                partnerStation.id(), "Grundwissen Feuerwehr", "Quiz zur Grundausbildung der Partnerwache", true);
        var partnerCategory = quizService.createCategory(partnerStation.id(), "Allgemein", "Allgemeine Fragen", 0);
        quizService.createQuestion(
                partnerCatalog.id(),
                partnerCategory.id(),
                QuizQuestionType.MULTIPLE_CHOICE,
                "Was bedeutet RLBS?",
                "Die vier Grundaufgaben der Feuerwehr",
                null,
                1,
                true,
                new QuestionConfig.MultipleChoice(
                        List.of(
                                new QuestionConfig.MultipleChoice.Option("Retten, Löschen, Bergen, Schützen", true),
                                new QuestionConfig.MultipleChoice.Option("Räumen, Löschen, Bauen, Sichern", false),
                                new QuestionConfig.MultipleChoice.Option("Retten, Leiten, Bergen, Senden", false)),
                        1),
                0);
        quizService.createQuestion(
                partnerCatalog.id(),
                partnerCategory.id(),
                QuizQuestionType.TRUE_FALSE,
                "Der Notruf 112 ist kostenlos",
                "Gilt in ganz Europa",
                null,
                1,
                true,
                new QuestionConfig.TrueFalse(true),
                1);
        federationService.createQuizShare(partnerStation.id(), partnerCatalog.id(), ShareScope.ALL_PARTNERS);

        // Create and share a test protocol on the partner station
        var partnerProtocol = protocolService.createProtocol(
                partnerStation.id(), "Grundausbildung Prüfung", "Prüfungsbogen der Partnerwache", 70);
        var protoSection = protocolService.createSection(
                partnerProtocol.id(), null, "Theorie", "Theoretische Grundlagen", 20, null, 0);
        protocolService.createItem(protoSection.id(), "Notruf absetzen", "5 W-Fragen", 5, 0);
        protocolService.createItem(protoSection.id(), "RLBS erklären", "Vier Grundaufgaben", 5, 1);
        protocolService.createItem(protoSection.id(), "Fahrzeugkunde", "Fahrzeugtypen benennen", 5, 2);
        protocolService.createItem(protoSection.id(), "Dienstgrade", "Dienstgrade der Feuerwehr", 5, 3);
        federationService.createProtocolShare(partnerStation.id(), partnerProtocol.id(), ShareScope.ALL_PARTNERS);

        // Enable federation capabilities
        for (var cap : List.of(
                CapabilityType.EVENT_SHARE,
                CapabilityType.BOARD_SHARE,
                CapabilityType.KB_SHARE,
                CapabilityType.NEWS_SHARE,
                CapabilityType.INVENTORY_LEND)) {
            federationService.setCapability(partner.id(), cap, Direction.IMPORT, true);
            federationService.setCapability(partner.id(), cap, Direction.EXPORT, true);
        }

        // Create a public event on the partner station (visible via federation)
        var eventCategory = eventService.createCategory(partnerStation.id(), "Gemeinsame Übung", 0);
        Instant nextSatStart = LocalDate.now()
                .plusDays(14 - LocalDate.now().getDayOfWeek().getValue() % 7)
                .atTime(9, 0)
                .toInstant(ZoneOffset.UTC);
        Instant nextSatEnd = nextSatStart.plusSeconds(4 * 3600);
        var fedEvent = eventService.create(
                partnerStation.id(),
                "Gemeinsame Großübung",
                "Übergreifende Übung mit beiden Wehren — Einsatzszenarien Brand und THL",
                StationEvent.EventType.ONE_TIME,
                null,
                nextSatStart,
                nextSatEnd,
                null,
                true,
                null,
                true,
                eventCategory.id(),
                null,
                null,
                null);
        eventFederationService.setShare(fedEvent.id(), "ALL_PARTNERS", List.of());

        // -- Share news with partner --
        var partnerMember1Uid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var partnerMember2Uid = UUID.fromString("00000000-0000-0000-0000-000000000002");

        // Cache display names for federated partner members
        eventFederationRepository.cacheName(partner.id(), partnerMember1Uid, "Max Feuermann");
        eventFederationRepository.cacheName(partner.id(), partnerMember2Uid, "Sabine Lösch");

        var primaryNews = newsService.findByStation(primaryStationId, 0, 10);
        // news1 = "Willkommen..." (most recent last in DESC order, so reverse lookup)
        var news1 = primaryNews.stream()
                .filter(n -> n.title().startsWith("Willkommen"))
                .findFirst()
                .orElse(null);
        var news2 = primaryNews.stream()
                .filter(n -> n.title().startsWith("Kreiswettbewerb"))
                .findFirst()
                .orElse(null);

        if (news1 != null) {
            // Share news1 with all partners, visibility MEMBER
            newsFederationService.setShare(news1.id(), "ALL_PARTNERS", "MEMBER", List.of());
            // Federated comment from partner member on news1
            var nc1 = newsFederationService.createRemoteComment(
                    primaryStationId,
                    news1.id(),
                    partner.id(),
                    partnerMember1Uid,
                    "Max Feuermann",
                    null,
                    "Toll, dass es jetzt so eine Plattform gibt! Wir nutzen das bei uns auch seit Kurzem.");
            // Reply from local admin
            newsService.createComment(
                    primaryStationId,
                    news1.id(),
                    nc1.id(),
                    stationMemberRepository.resolveIdentity(createdBy),
                    "Admin",
                    "Freut uns! Vielleicht können wir mal Erfahrungen austauschen.");
        }
        if (news2 != null) {
            // Share news2 with specific partner, visibility TEAM
            newsFederationService.setShare(news2.id(), "SPECIFIC", "TEAM", List.of(partner.id()));
            // Federated comment from partner member on news2
            newsFederationService.createRemoteComment(
                    primaryStationId,
                    news2.id(),
                    partner.id(),
                    partnerMember2Uid,
                    "Sabine Lösch",
                    null,
                    "Dürfen wir auch ein Team zum Kreiswettbewerb schicken? Wäre super!");
        }
        // Create a news post on the PARTNER station and share it back
        var partnerNews = newsService.create(
                partnerStation.id(),
                "Neue Drehleiter für die Partnerwache",
                """
                Wir haben eine neue **Drehleiter DLA(K) 23/12** erhalten! Das Fahrzeug wird in den nächsten Wochen in den Dienst gestellt.

                ## Besichtigung

                Alle Partnereinheiten sind herzlich eingeladen, sich das Fahrzeug bei der nächsten gemeinsamen Übung anzuschauen.

                Wir freuen uns auf euren Besuch! 🚒
                """,
                "<p>Wir haben eine neue <strong>Drehleiter DLA(K) 23/12</strong> erhalten! Das Fahrzeug wird in den nächsten Wochen in den Dienst gestellt.</p><h2>Besichtigung</h2><p>Alle Partnereinheiten sind herzlich eingeladen, sich das Fahrzeug bei der nächsten gemeinsamen Übung anzuschauen.</p><p>Wir freuen uns auf euren Besuch! 🚒</p>",
                stationMemberRepository.resolveIdentity(partnerMember.id()),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        newsFederationService.setShare(partnerNews.id(), "ALL_PARTNERS", "MEMBER", List.of());
        // Find the reverse partner record (partner station's view of the primary station)
        var reversePartner = federationService.findPartners(partnerStation.id()).stream()
                .filter(p -> p.stationId() == partnerStation.id())
                .findFirst()
                .orElse(null);
        if (reversePartner != null) {
            // Comment from primary station admin on partner's news (stored on partner station)
            var primaryAdmin = stationMemberRepository.findById(createdBy).orElseThrow();
            String primaryAdminName = accountRepository
                    .findById(primaryAdmin.accountId())
                    .map(a -> (a.firstName() + " " + a.lastName()).trim())
                    .orElse("Admin");
            var pnc1 = newsFederationService.createRemoteComment(
                    partnerStation.id(),
                    partnerNews.id(),
                    reversePartner.id(),
                    primaryAdmin.uid(),
                    primaryAdminName,
                    null,
                    "Glückwunsch! Können wir die bei der Übung auch mal testen?");
            // Reply from partner station
            newsService.createComment(
                    partnerStation.id(),
                    partnerNews.id(),
                    pnc1.id(),
                    stationMemberRepository.resolveIdentity(partnerMember.id()),
                    "Partner Manager",
                    "Natürlich, das lässt sich einrichten!");
        }

        log.info("Demo: Shared news with partner and added federated comments");

        // -- Event comments (local + federated) --
        var primaryEvents = eventService.findByStation(primaryStationId);
        primaryEvents.stream()
                     .filter(e -> "Übung".equals(e.name()) && e.eventType() == StationEvent.EventType.RECURRING)
                     .findFirst().ifPresent(evUebung -> commentService.create(
                             primaryStationId,
                             evUebung.id(),
                             null,
                             memberIdentityFactory.local(primaryStationId, createdBy),
                             "Admin",
                             "Nächste Woche üben wir den Löschangriff — bitte Sportkleidung mitbringen!"));

        // Federated comments on the shared event "Gemeinsame Großübung" (event lives on partner station)
        var primaryAdmin = stationMemberRepository.findById(createdBy).orElseThrow();
        String primaryAdminName = accountRepository
                .findById(primaryAdmin.accountId())
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("Admin");

        // Find reverse partner (partner station's view of primary station)
        var reversePartnerForEvents = federationService.findPartners(partnerStation.id()).stream()
                .filter(p -> p.stationId() == partnerStation.id())
                .findFirst()
                .orElse(null);
        if (reversePartnerForEvents != null) {
            var fc1 = eventFederationService.createRemoteComment(
                    reversePartnerForEvents,
                    fedEvent.id(),
                    primaryAdmin.uid(),
                    primaryAdminName,
                    null,
                    "Wir kommen mit 6 Leuten! Brauchen wir eigene Schläuche?");
            // Local reply from partner station member
            commentService.create(
                    partnerStation.id(),
                    fedEvent.id(),
                    fc1.id(),
                    memberIdentityFactory.local(partnerStation.id(), partnerMember.id()),
                    "Partner Manager",
                    "Nein, wir haben genug Material da. Einfach nur Schutzkleidung mitbringen.");
            eventFederationService.createRemoteComment(
                    reversePartnerForEvents,
                    fedEvent.id(),
                    primaryAdmin.uid(),
                    primaryAdminName,
                    null,
                    "Gibt es eine Lageskizze vorab?");
        }
        log.info("Demo: Added event comments (local + federated)");

        // -- KB comments (local + federated) --
        var primaryKbFilesForComments = kbService.findFiles(primaryStationId, null);
        if (!primaryKbFilesForComments.isEmpty()) {
            var kbFile = primaryKbFilesForComments.getFirst();
            // Local comment on a KB file
            kbService.createComment(kbFile.id(), null, createdBy, "Sehr hilfreich, danke!");
        }
        var partnerKbFiles = kbService.findFiles(partnerStation.id(), null);
        if (!partnerKbFiles.isEmpty() && reversePartnerForEvents != null) {
            // Federated comment from primary station admin on partner's shared KB file
            var sharedKbFile = partnerKbFiles.getFirst();
            var kc1 = kbService.createRemoteComment(
                    sharedKbFile.id(),
                    reversePartnerForEvents.id(),
                    primaryAdmin.uid(),
                    primaryAdminName,
                    null,
                    "Können wir den Ausbildungsleitfaden auch als PDF bekommen?");
            // Reply from the partner station member (local on partner station)
            kbService.createComment(
                    sharedKbFile.id(),
                    kc1.id(),
                    partnerMember.id(),
                    "Klar, ich lade diese Woche eine PDF-Version hoch.");
        }
        log.info("Demo: Added KB comments (local + federated)");

        log.info("Demo: Federated station {} with partner station {}", primaryStationId, partnerStation.id());

        // === Third station (not federated) ===
        var thirdStation = stationRepository.create("JF Nachbarstadt");
        log.info("Demo: Created third station '{}' (id={})", thirdStation.name(), thirdStation.id());
        stationRepository.updateDiscoverySettings(
                thirdStation.id(), DiscoveryVisibility.PUBLIC, "Nachbarstadt sucht Partner für Übungsaustausch", true);

        var thirdAccount = accountRepository.create("nachbar@demo.ember", "Nachbar", "Manager", true);
        accountRepository.createCredential(thirdAccount.id(), passwordHasher.hash("demo"));
        var thirdMember = stationMemberRepository.create(thirdStation.id(), thirdAccount.id());
        stationMemberRepository.addRole(thirdMember.id(), managerRole.id());
        stationMemberRepository.addRole(thirdMember.id(), loginRole.id());

        kbService.createMarkdownFile(
                thirdStation.id(),
                null,
                "Funkausbildung",
                "Materialien zur Funkausbildung der Nachbarstadt",
                """
                        # Funkausbildung

                        ## Grundlagen

                        - Buchstabiertafel NATO
                        - Funkdisziplin
                        - Geräteeinweisung FuG 10/11

                        ## Übungsfunkverkehr

                        - Anmeldung bei der Leitstelle
                        - Statusmeldungen
                        - Lagemeldungen

                        > Dieses Material stammt von der JF Nachbarstadt.
                        """,
                thirdMember.id());

        log.info("Demo: Created third station with manager nachbar@demo.ember (not federated)");

        return new SeedResult(partnerStation.id(), partnerMember.id());
    }
}
