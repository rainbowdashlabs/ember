/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.service.EventCategoryService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.news.entity.NewsVisibilityRole;
import dev.chojo.ember.feature.news.service.NewsFederationService;
import dev.chojo.ember.feature.news.service.NewsService;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.entity.CreateQuestionCommand;
import dev.chojo.ember.feature.quiz.entity.QuestionConfig;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Seeds a second demo station and federates it with the primary station.
 * Shares knowledge base content, a quiz catalog, and a test protocol.
 */
@Singleton
public class DemoFederationSeeder implements DemoSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoFederationSeeder.class);

    private final StationRepository stationRepository;
    private final FederationService federationService;
    private final KnowledgeBaseService kbService;
    private final KnowledgeBaseFederationService kbFederationService;
    private final QuizService quizService;
    private final TestProtocolService protocolService;
    private final EventCrudService crudService;
    private final EventCategoryService categoryService;
    private final EventFederationService eventFederationService;
    private final EventFederationRepository eventFederationRepository;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberLookupService memberLookupService;
    private final PasswordHasher passwordHasher;
    private final NewsService newsService;
    private final NewsFederationService newsFederationService;
    private final CommentService commentService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final Demo demoConfig;
    private final Api apiConfig;
    private final DemoClock clock;

    @Inject
    public DemoFederationSeeder(
            StationRepository stationRepository,
            FederationService federationService,
            KnowledgeBaseService kbService,
            KnowledgeBaseFederationService kbFederationService,
            QuizService quizService,
            TestProtocolService protocolService,
            EventCrudService crudService,
            EventCategoryService categoryService,
            EventFederationService eventFederationService,
            EventFederationRepository eventFederationRepository,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            MemberLookupService memberLookupService,
            PasswordHasher passwordHasher,
            NewsService newsService,
            NewsFederationService newsFederationService,
            CommentService commentService,
            MemberIdentityFactory memberIdentityFactory,
            Demo demoConfig,
            Api apiConfig,
            DemoClock clock) {
        this.clock = clock;
        this.stationRepository = stationRepository;
        this.federationService = federationService;
        this.kbService = kbService;
        this.kbFederationService = kbFederationService;
        this.quizService = quizService;
        this.protocolService = protocolService;
        this.crudService = crudService;
        this.categoryService = categoryService;
        this.eventFederationService = eventFederationService;
        this.eventFederationRepository = eventFederationRepository;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberLookupService = memberLookupService;
        this.passwordHasher = passwordHasher;
        this.newsService = newsService;
        this.newsFederationService = newsFederationService;
        this.commentService = commentService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.demoConfig = demoConfig;
        this.apiConfig = apiConfig;
    }

    /**
     * The name a member goes by, from their account, or "Admin" for a member without one.
     */
    private String calledName(StationMember member) {
        Integer accountId = member.accountId();
        if (accountId == null) return "Admin";
        return accountRepository
                .findById(accountId)
                .map(a -> NameParts.of(a).called())
                .orElse("Admin");
    }

    /**
     * Writes a demo comment, telling whoever a comment written there tells.
     */
    private void seedComment(CommentEntityType type, int targetId, CommentWriter writer, NewComment comment) {
        commentService.target(type, targetId).ifPresent(target -> commentService.createOn(target, writer, comment));
    }

    /**
     * Returns the next upcoming occurrence date of a recurring event, which a comment on such an event is
     * scoped to (a one-off event's comments carry no date). For weekly events we
     * walk forward to the next matching {@code day_of_week}; for non-weekly recurrences we
     * fall back to the event's anchor start time so the demo data still gets a sensible
     * date attached to the comment.
     */
    private LocalDate nextOccurrenceOf(StationEvent event) {
        ZoneId zone = StationFormat.timezoneOf(
                stationRepository.findById(event.stationId()).orElse(null));
        var today = LocalDate.now(zone);
        Integer dayOfWeek = event.dayOfWeek();
        if (event.eventType() == StationEvent.EventType.RECURRING && dayOfWeek != null) {
            DayOfWeek target = DayOfWeek.of(dayOfWeek);
            int delta = (target.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
            return today.plusDays(delta == 0 ? 7 : delta);
        }
        return event.startTime() != null ? event.startTime().atZone(zone).toLocalDate() : today;
    }

    @Override
    public int order() {
        return MODULES;
    }

    /**
     * Every full station gets the partner, so federation can be seen at a station inside an association as well
     * as at one outside it. The partner's shared content is seeded once, with the first station, because it is
     * the partner's content and not a station's.
     */
    @Override
    public void seed(DemoRunContext run) {
        var primary = run.primaryStation();
        List<Integer> alsoFederated = run.stations().stream()
                .filter(station -> station != primary)
                .map(DemoStationContext::stationId)
                .toList();
        run.federation(seed(primary.stationId(), primary.adminMember().id(), alsoFederated));
        log.info("Demo: Created federation data");
    }

    /**
     * Seeds a partner station, federates it with the primary station, and shares content.
     *
     * <p>The partner is marked as set up, since a station that has not finished the setup assistant sends its
     * manager straight back into it, and it gets an owner, since only an owned station can ask a cluster for a
     * place. Further stations are federated under the key the partner already uses: a second key pair would
     * replace the first and break the pairing. Partner member names are cached in both directions, so a viewer
     * on either station sees the remote member's name rather than the station's.
     *
     * @param primaryStationId the primary station ID
     * @param createdBy        the member ID creating the data
     * @param alsoFederated    further stations to federate with the same partner, sharing no content of
     *                         their own
     * @return the seed result with partner station and member IDs
     */
    public SeedResult seed(int primaryStationId, int createdBy, List<Integer> alsoFederated) {
        stationRepository.updateDiscoverySettings(
                primaryStationId,
                DiscoveryVisibility.PUBLIC,
                "Unsere Jugendfeuerwehr - Ausbildung, Technik und Gemeinschaft",
                true);

        var partnerStation = stationRepository.create("JF Partnerwache", DemoUids.station("jf-partnerwache"));
        stationRepository.updatePublicSlug(partnerStation.id(), "jf-partnerwache");
        stationRepository.markSetupComplete(partnerStation.id());
        log.info("Demo: Created partner station '{}' (id={})", partnerStation.name(), partnerStation.id());
        stationRepository.updateDiscoverySettings(
                partnerStation.id(),
                DiscoveryVisibility.PUBLIC,
                "Partnerwache für gemeinsame Übungen und Ausbildung",
                false);

        var partnerAccount = accountRepository.create("partner@demo.ember", "Partner", "Manager", true);
        accountRepository.setUid(partnerAccount.id(), DemoUids.account("partner@demo.ember"));
        accountRepository.createCredential(partnerAccount.id(), passwordHasher.hash("demo"));
        var partnerMember = stationMemberRepository.create(partnerStation.id(), partnerAccount.id());
        memberLookupService.setUid(partnerMember.id(), DemoUids.member("partner@demo.ember", partnerStation.id()));
        var managerRole = stationMemberRepository
                .findPermissionByName(StationPermission.STATION_ADMINISTRATOR)
                .orElseThrow();
        var loginRole = stationMemberRepository
                .findPermissionByName(StationPermission.LOGIN)
                .orElseThrow();
        stationMemberRepository.setUserType(partnerMember.id(), StationUserType.MANAGER);
        stationMemberRepository.grantPermission(partnerMember.id(), managerRole.id());
        stationMemberRepository.grantPermission(partnerMember.id(), loginRole.id());
        stationRepository.setOwner(partnerStation.id(), partnerMember.id());
        log.info("Demo: Created partner manager account partner@demo.ember");

        var memberRole = stationMemberRepository
                .findPermissionByName(StationPermission.USER)
                .orElseThrow();
        var guardianRole = stationMemberRepository
                .findPermissionByName(StationPermission.MEMBER_GUARDIAN)
                .orElseThrow();

        var team1Account = accountRepository.create("team1@partner.ember", "Lisa", "Brandmeister", true);
        accountRepository.setUid(team1Account.id(), DemoUids.account("team1@partner.ember"));
        accountRepository.createCredential(team1Account.id(), passwordHasher.hash("demo"));
        var team1 = stationMemberRepository.create(partnerStation.id(), team1Account.id());
        memberLookupService.setUid(team1.id(), DemoUids.member("team1@partner.ember", partnerStation.id()));
        stationMemberRepository.setUserType(team1.id(), StationUserType.TEAM);
        stationMemberRepository.grantPermission(team1.id(), loginRole.id());

        var team2Account = accountRepository.create("team2@partner.ember", "Jonas", "Löschzug", true);
        accountRepository.setUid(team2Account.id(), DemoUids.account("team2@partner.ember"));
        accountRepository.createCredential(team2Account.id(), passwordHasher.hash("demo"));
        var team2 = stationMemberRepository.create(partnerStation.id(), team2Account.id());
        memberLookupService.setUid(team2.id(), DemoUids.member("team2@partner.ember", partnerStation.id()));
        stationMemberRepository.setUserType(team2.id(), StationUserType.TEAM);
        stationMemberRepository.grantPermission(team2.id(), loginRole.id());

        var member1Account = accountRepository.create("member1@partner.ember", "Emma", "Schlauch", true);
        accountRepository.setUid(member1Account.id(), DemoUids.account("member1@partner.ember"));
        accountRepository.createCredential(member1Account.id(), passwordHasher.hash("demo"));
        var member1 = stationMemberRepository.create(partnerStation.id(), member1Account.id());
        memberLookupService.setUid(member1.id(), DemoUids.member("member1@partner.ember", partnerStation.id()));
        stationMemberRepository.setUserType(member1.id(), StationUserType.MEMBER);
        stationMemberRepository.grantPermission(member1.id(), loginRole.id());
        stationMemberRepository.grantPermission(member1.id(), memberRole.id());

        var member2Account = accountRepository.create("member2@partner.ember", "Felix", "Strahlrohr", true);
        accountRepository.setUid(member2Account.id(), DemoUids.account("member2@partner.ember"));
        accountRepository.createCredential(member2Account.id(), passwordHasher.hash("demo"));
        var member2 = stationMemberRepository.create(partnerStation.id(), member2Account.id());
        memberLookupService.setUid(member2.id(), DemoUids.member("member2@partner.ember", partnerStation.id()));
        stationMemberRepository.setUserType(member2.id(), StationUserType.MEMBER);
        stationMemberRepository.grantPermission(member2.id(), loginRole.id());
        stationMemberRepository.grantPermission(member2.id(), memberRole.id());

        var guardianAccount = accountRepository.create("guardian@partner.ember", "Petra", "Elternbeirat", true);
        accountRepository.setUid(guardianAccount.id(), DemoUids.account("guardian@partner.ember"));
        accountRepository.createCredential(guardianAccount.id(), passwordHasher.hash("demo"));
        var guardian = stationMemberRepository.create(partnerStation.id(), guardianAccount.id());
        memberLookupService.setUid(guardian.id(), DemoUids.member("guardian@partner.ember", partnerStation.id()));
        stationMemberRepository.setUserType(guardian.id(), StationUserType.GUARDIAN);
        stationMemberRepository.grantPermission(guardian.id(), loginRole.id());
        stationMemberRepository.grantPermission(guardian.id(), guardianRole.id());

        log.info("Demo: Created 5 additional members on partner station");

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

        String remoteHost = forcedRemoteHost();

        String initiatingPublicKey = federationService.ensureStationKey(partnerStation.id());
        var partner = federationService.acceptInvite(
                primaryStationId, partnerStation.id(), initiatingPublicKey, remoteHost, remoteHost);

        var kbFiles = kbService.findFiles(partnerStation.id(), null);
        for (var file : kbFiles) {
            federationService.createKbShare(partnerStation.id(), file.id(), null, ShareScope.ALL_PARTNERS);
        }
        var kbFolders = kbService.findFolders(partnerStation.id(), null);
        for (var folder : kbFolders) {
            federationService.createKbShare(partnerStation.id(), null, folder.id(), ShareScope.ALL_PARTNERS);
        }

        var primaryKbFiles = kbService.findFiles(primaryStationId, null);
        for (var file : primaryKbFiles) {
            federationService.createKbShare(primaryStationId, file.id(), null, ShareScope.ALL_PARTNERS);
        }

        var partnerCatalog = quizService.createCatalog(
                partnerStation.id(), "Grundwissen Feuerwehr", "Quiz zur Grundausbildung der Partnerwache", true);
        var partnerCategory = quizService.createCategory(partnerStation.id(), "Allgemein", "Allgemeine Fragen", 0);
        quizService.createQuestion(CreateQuestionCommand.builder(
                        partnerCatalog.id(), QuizQuestionType.MULTIPLE_CHOICE, "Was bedeutet RLBS?")
                .category(partnerCategory.id())
                .description("Die vier Grundaufgaben der Feuerwehr")
                .config(new QuestionConfig.MultipleChoice(
                        List.of(
                                new QuestionConfig.MultipleChoice.ChoiceOption(
                                        "Retten, Löschen, Bergen, Schützen", true),
                                new QuestionConfig.MultipleChoice.ChoiceOption(
                                        "Räumen, Löschen, Bauen, Sichern", false),
                                new QuestionConfig.MultipleChoice.ChoiceOption(
                                        "Retten, Leiten, Bergen, Senden", false)),
                        1))
                .build());
        quizService.createQuestion(CreateQuestionCommand.builder(
                        partnerCatalog.id(), QuizQuestionType.TRUE_FALSE, "Der Notruf 112 ist kostenlos")
                .category(partnerCategory.id())
                .description("Gilt in ganz Europa")
                .config(new QuestionConfig.TrueFalse(true))
                .position(1)
                .build());
        federationService.createQuizShare(partnerStation.id(), partnerCatalog.id(), ShareScope.ALL_PARTNERS);

        var partnerProtocol = protocolService.createProtocol(
                partnerStation.id(), "Grundausbildung Prüfung", "Prüfungsbogen der Partnerwache", 70);
        var protoSection = protocolService.createSection(
                partnerProtocol.id(), null, "Theorie", "Theoretische Grundlagen", 20, null, 0);
        protocolService.createItem(protoSection.id(), "Notruf absetzen", "5 W-Fragen", 5, 0, false);
        protocolService.createItem(protoSection.id(), "RLBS erklären", "Vier Grundaufgaben", 5, 1, false);
        protocolService.createItem(protoSection.id(), "Fahrzeugkunde", "Fahrzeugtypen benennen", 5, 2, false);
        protocolService.createItem(protoSection.id(), "Dienstgrade", "Dienstgrade der Feuerwehr", 5, 3, true);
        federationService.createProtocolShare(partnerStation.id(), partnerProtocol.id(), ShareScope.ALL_PARTNERS);

        enableCapabilities(partner.id());

        for (int stationId : alsoFederated) {
            var alsoPartner = federationService.acceptInvite(
                    stationId, partnerStation.id(), initiatingPublicKey, remoteHost, remoteHost);
            enableCapabilities(alsoPartner.id());
            log.info("Demo: Federated station {} with the partner station as well", stationId);
        }

        var eventCategory = categoryService.create(partnerStation.id(), "Gemeinsame Übung", 0, null, false, "#3694ff");
        var partnerDays = clock.of(partnerStation);
        LocalDate partnerToday = partnerDays.today();
        Instant nextSatStart = partnerDays.at(
                partnerToday.plusDays(14 - partnerToday.getDayOfWeek().getValue() % 7), 9, 0);
        Instant nextSatEnd = nextSatStart.plusSeconds(4 * 3600);
        var fedEvent = crudService.create(
                partnerStation.id(),
                "Gemeinsame Großübung",
                "Übergreifende Übung mit beiden Wehren - Einsatzszenarien Brand und THL",
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
                null,
                null);
        eventFederationService.setShare(fedEvent.id(), ShareScope.ALL_PARTNERS, List.of());

        var partnerMember1Uid = UUID.fromString("00000000-0000-0000-0000-000000000001");
        var partnerMember2Uid = UUID.fromString("00000000-0000-0000-0000-000000000002");

        var primaryPartner = federationService.findPartners(primaryStationId).stream()
                .filter(p -> p.partnerStationId()
                        .equals(stationRepository
                                .findById(partnerStation.id())
                                .orElseThrow()
                                .uid()))
                .findFirst()
                .orElse(null);
        for (var p : primaryPartner != null ? List.of(partner, primaryPartner) : List.of(partner)) {
            eventFederationRepository.cacheName(p.id(), partnerMember1Uid, "Max Feuermann");
            eventFederationRepository.cacheName(p.id(), partnerMember2Uid, "Sabine Lösch");
        }

        var primaryNews = newsService.findByStation(primaryStationId, 0, 10);
        var news1 = primaryNews.stream()
                .filter(n -> n.title().startsWith("Willkommen"))
                .findFirst()
                .orElse(null);
        var news2 = primaryNews.stream()
                .filter(n -> n.title().startsWith("Kreiswettbewerb"))
                .findFirst()
                .orElse(null);

        if (news1 != null) {
            newsFederationService.setShare(news1.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());
            var nc1 = newsFederationService.createRemoteComment(
                    news1.id(),
                    partner.id(),
                    partnerMember1Uid,
                    "Max Feuermann",
                    null,
                    "Toll, dass es jetzt so eine Plattform gibt! Wir nutzen das bei uns auch seit Kurzem.");
            seedComment(
                    CommentEntityType.NEWS,
                    news1.id(),
                    CommentWriter.local(memberIdentityFactory.local(primaryStationId, createdBy), "Admin"),
                    new NewComment(nc1.id(), null, "Freut uns! Vielleicht können wir mal Erfahrungen austauschen."));
        }
        if (news2 != null) {
            newsFederationService.setShare(
                    news2.id(), ShareScope.SPECIFIC, NewsVisibilityRole.TEAM, List.of(partner.id()));
            newsFederationService.createRemoteComment(
                    news2.id(),
                    partner.id(),
                    partnerMember2Uid,
                    "Sabine Lösch",
                    null,
                    "Dürfen wir auch ein Team zum Kreiswettbewerb schicken? Wäre super!");
        }
        var partnerNews = newsService.create(
                partnerStation.id(),
                "Neue Drehleiter für die Partnerwache",
                """
                        Wir haben eine neue **Drehleiter DLA(K) 23/12** erhalten! Das Fahrzeug wird in den nächsten Wochen in den Dienst gestellt.

                        ## Besichtigung

                        Alle Partnereinheiten sind herzlich eingeladen, sich das Fahrzeug bei der nächsten gemeinsamen Übung anzuschauen.

                        Wir freuen uns auf euren Besuch! 🚒
                        """,
                stationMemberRepository.resolveIdentity(partnerMember.id()),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        newsFederationService.setShare(partnerNews.id(), ShareScope.ALL_PARTNERS, NewsVisibilityRole.MEMBER, List.of());
        var reversePartner = federationService.findPartners(partnerStation.id()).stream()
                .filter(p -> p.stationId() == partnerStation.id())
                .findFirst()
                .orElse(null);
        if (reversePartner != null) {
            var primaryAdmin = stationMemberRepository.findById(createdBy).orElseThrow();
            String primaryAdminName = calledName(primaryAdmin);
            var pnc1 = newsFederationService.createRemoteComment(
                    partnerNews.id(),
                    reversePartner.id(),
                    primaryAdmin.uid(),
                    primaryAdminName,
                    null,
                    "Glückwunsch! Können wir die bei der Übung auch mal testen?");
            seedComment(
                    CommentEntityType.NEWS,
                    partnerNews.id(),
                    CommentWriter.local(
                            memberIdentityFactory.local(partnerStation.id(), partnerMember.id()), "Partner Manager"),
                    new NewComment(pnc1.id(), null, "Natürlich, das lässt sich einrichten!"));
        }

        log.info("Demo: Shared news with partner and added federated comments");

        var primaryEvents = crudService.findByStation(primaryStationId);
        primaryEvents.stream()
                .filter(e -> "Übung".equals(e.name()) && e.eventType() == StationEvent.EventType.RECURRING)
                .findFirst()
                .ifPresent(evUebung -> {
                    LocalDate nextOccurrence = nextOccurrenceOf(evUebung);
                    seedComment(
                            CommentEntityType.EVENT,
                            evUebung.id(),
                            CommentWriter.local(memberIdentityFactory.local(primaryStationId, createdBy), "Admin"),
                            new NewComment(
                                    null,
                                    nextOccurrence,
                                    "Nächste Woche üben wir den Löschangriff - bitte Sportkleidung mitbringen!"));
                });

        var primaryAdmin = stationMemberRepository.findById(createdBy).orElseThrow();
        String primaryAdminName = calledName(primaryAdmin);

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
                    "Wir kommen mit 6 Leuten! Brauchen wir eigene Schläuche?",
                    null);
            seedComment(
                    CommentEntityType.EVENT,
                    fedEvent.id(),
                    CommentWriter.local(
                            memberIdentityFactory.local(partnerStation.id(), partnerMember.id()), "Partner Manager"),
                    new NewComment(
                            fc1.id(),
                            null,
                            "Nein, wir haben genug Material da. Einfach nur Schutzkleidung mitbringen."));
            eventFederationService.createRemoteComment(
                    reversePartnerForEvents,
                    fedEvent.id(),
                    primaryAdmin.uid(),
                    primaryAdminName,
                    null,
                    "Gibt es eine Lageskizze vorab?",
                    null);
        }
        log.info("Demo: Added event comments (local + federated)");

        var primaryKbFilesForComments = kbService.findFiles(primaryStationId, null);
        if (!primaryKbFilesForComments.isEmpty()) {
            var kbFile = primaryKbFilesForComments.getFirst();
            seedComment(
                    CommentEntityType.KB,
                    kbFile.id(),
                    CommentWriter.local(memberIdentityFactory.local(primaryStationId, createdBy), "Admin"),
                    new NewComment(null, null, "Sehr hilfreich, danke!"));
        }
        var partnerKbFiles = kbService.findFiles(partnerStation.id(), null);
        if (!partnerKbFiles.isEmpty() && reversePartnerForEvents != null) {
            var sharedKbFile = partnerKbFiles.getFirst();
            var kc1 = kbFederationService.createRemoteComment(
                    sharedKbFile.id(),
                    reversePartnerForEvents.id(),
                    primaryAdmin.uid(),
                    primaryAdminName,
                    null,
                    "Können wir den Ausbildungsleitfaden auch als PDF bekommen?");
            seedComment(
                    CommentEntityType.KB,
                    sharedKbFile.id(),
                    CommentWriter.local(
                            memberIdentityFactory.local(partnerStation.id(), partnerMember.id()), "Partner Manager"),
                    new NewComment(kc1.id(), null, "Klar, ich lade diese Woche eine PDF-Version hoch."));
        }
        log.info("Demo: Added KB comments (local + federated)");

        log.info("Demo: Federated station {} with partner station {}", primaryStationId, partnerStation.id());

        var thirdStation = stationRepository.create("JF Nachbarstadt", DemoUids.station("jf-nachbarstadt"));
        stationRepository.updatePublicSlug(thirdStation.id(), "jf-nachbarstadt");
        stationRepository.markSetupComplete(thirdStation.id());
        log.info("Demo: Created third station '{}' (id={})", thirdStation.name(), thirdStation.id());
        stationRepository.updateDiscoverySettings(
                thirdStation.id(), DiscoveryVisibility.PUBLIC, "Nachbarstadt sucht Partner für Übungsaustausch", true);

        var thirdAccount = accountRepository.create("nachbar@demo.ember", "Nachbar", "Manager", true);
        accountRepository.setUid(thirdAccount.id(), DemoUids.account("nachbar@demo.ember"));
        accountRepository.createCredential(thirdAccount.id(), passwordHasher.hash("demo"));
        var thirdMember = stationMemberRepository.create(thirdStation.id(), thirdAccount.id());
        memberLookupService.setUid(thirdMember.id(), DemoUids.member("nachbar@demo.ember", thirdStation.id()));
        stationMemberRepository.setUserType(thirdMember.id(), StationUserType.MANAGER);
        stationMemberRepository.grantPermission(thirdMember.id(), managerRole.id());
        stationMemberRepository.grantPermission(thirdMember.id(), loginRole.id());
        stationRepository.setOwner(thirdStation.id(), thirdMember.id());

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

        return new SeedResult(partnerStation.id(), partnerMember.id(), thirdStation.id(), thirdMember.id());
    }

    /**
     * What the rest of the demo needs to reach the stations this seeder made.
     *
     * @param partnerStationId the station federated with the primary one
     * @param partnerMemberId  its member, who also owns it
     * @param thirdStationId   the neighbouring station, federated with nobody
     * @param thirdMemberId    its member, who also owns it
     */
    public record SeedResult(int partnerStationId, int partnerMemberId, int thirdStationId, int thirdMemberId) {}

    /**
     * The address partners are registered under when the demo forces federation over HTTP: this very instance,
     * so the remote path is exercised without a second host. Otherwise none, and partners stay local.
     */
    private @Nullable String forcedRemoteHost() {
        return demoConfig.federationForceHttp() ? "http://localhost:" + apiConfig.port() : null;
    }

    /** Everything the demo's federations may do, in both directions, because a demo showing less shows less. */
    private void enableCapabilities(int partnerId) {
        for (var cap : List.of(
                CapabilityType.EVENT_SHARE,
                CapabilityType.BOARD_SHARE,
                CapabilityType.KB_SHARE,
                CapabilityType.NEWS_SHARE,
                CapabilityType.INVENTORY_LEND)) {
            federationService.setCapability(partnerId, cap, Direction.IMPORT, true);
            federationService.setCapability(partnerId, cap, Direction.EXPORT, true);
        }
    }
}
