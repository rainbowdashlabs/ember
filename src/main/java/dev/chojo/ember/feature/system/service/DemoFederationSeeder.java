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
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.protocol.service.TestProtocolService;
import dev.chojo.ember.feature.quiz.entity.QuestionType;
import dev.chojo.ember.feature.quiz.service.QuizService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final PasswordHasher passwordHasher;
    private final Demo demoConfig;
    private final Api apiConfig;

    @Inject
    public DemoFederationSeeder(
            StationRepository stationRepository,
            FederationService federationService,
            KnowledgeBaseService kbService,
            QuizService quizService,
            TestProtocolService protocolService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            PasswordHasher passwordHasher,
            Demo demoConfig,
            Api apiConfig) {
        this.stationRepository = stationRepository;
        this.federationService = federationService;
        this.kbService = kbService;
        this.quizService = quizService;
        this.protocolService = protocolService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.passwordHasher = passwordHasher;
        this.demoConfig = demoConfig;
        this.apiConfig = apiConfig;
    }

    /**
     * Seeds a partner station, federates it with the primary station, and shares content.
     *
     * @param primaryStationId the primary station ID
     * @param createdBy        the member ID creating the data
     * @return the partner station ID
     */
    public int seed(int primaryStationId, int createdBy) {
        // Create a second station
        var partnerStation = stationRepository.create("JF Partnerwache");
        log.info("Demo: Created partner station '{}' (id={})", partnerStation.name(), partnerStation.id());

        // Create a manager account on the partner station
        var partnerAccount = accountRepository.create("partner@demo.ember", "Partner", "Manager", true);
        accountRepository.createCredential(partnerAccount.id(), passwordHasher.hash("demo"));
        var partnerMember = stationMemberRepository.create(partnerStation.id(), partnerAccount.id());
        var managerRole = stationMemberRepository.findRoleByName(Roles.MANAGER).orElseThrow();
        var loginRole = stationMemberRepository.findRoleByName(Roles.LOGIN).orElseThrow();
        stationMemberRepository.addRole(partnerMember.id(), managerRole.id());
        stationMemberRepository.addRole(partnerMember.id(), loginRole.id());
        log.info("Demo: Created partner manager account partner@demo.ember");

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

        // Share the partner station's KB with the primary station
        var kbFiles = kbService.findFiles(partnerStation.id(), null);
        for (var file : kbFiles) {
            federationService.createKbShare(partnerStation.id(), file.id(), null, "ALL_PARTNERS");
        }

        // Share the primary station's KB with the partner station
        var primaryKbFiles = kbService.findFiles(primaryStationId, null);
        for (var file : primaryKbFiles) {
            federationService.createKbShare(primaryStationId, file.id(), null, "ALL_PARTNERS");
        }

        // Create and share a quiz catalog on the partner station
        var partnerCatalog = quizService.createCatalog(
                partnerStation.id(), "Grundwissen Feuerwehr", "Quiz zur Grundausbildung der Partnerwache", true);
        var partnerCategory = quizService.createCategory(partnerStation.id(), "Allgemein", "Allgemeine Fragen", 0);
        quizService.createQuestion(
                partnerCatalog.id(),
                partnerCategory.id(),
                QuestionType.MULTIPLE_CHOICE,
                "Was bedeutet RLBS?",
                "Die vier Grundaufgaben der Feuerwehr",
                null,
                1,
                true,
                "{\"options\":[\"Retten, Löschen, Bergen, Schützen\",\"Räumen, Löschen, Bauen, Sichern\",\"Retten, Leiten, Bergen, Senden\"],\"correctIndices\":[0]}",
                0);
        quizService.createQuestion(
                partnerCatalog.id(),
                partnerCategory.id(),
                QuestionType.TRUE_FALSE,
                "Der Notruf 112 ist kostenlos",
                "Gilt in ganz Europa",
                null,
                1,
                true,
                "{\"correctAnswer\":true}",
                1);
        federationService.createQuizShare(partnerStation.id(), partnerCatalog.id(), "ALL_PARTNERS");

        // Create and share a test protocol on the partner station
        var partnerProtocol = protocolService.createProtocol(
                partnerStation.id(), "Grundausbildung Prüfung", "Prüfungsbogen der Partnerwache", 70);
        var protoSection = protocolService.createSection(
                partnerProtocol.id(), null, "Theorie", "Theoretische Grundlagen", 20, null, 0);
        protocolService.createItem(protoSection.id(), "Notruf absetzen", "5 W-Fragen", 5, 0);
        protocolService.createItem(protoSection.id(), "RLBS erklären", "Vier Grundaufgaben", 5, 1);
        protocolService.createItem(protoSection.id(), "Fahrzeugkunde", "Fahrzeugtypen benennen", 5, 2);
        protocolService.createItem(protoSection.id(), "Dienstgrade", "Dienstgrade der Feuerwehr", 5, 3);
        federationService.createProtocolShare(partnerStation.id(), partnerProtocol.id(), "ALL_PARTNERS");

        log.info("Demo: Federated station {} with partner station {}", primaryStationId, partnerStation.id());

        // === Third station (not federated) ===
        var thirdStation = stationRepository.create("JF Nachbarstadt");
        log.info("Demo: Created third station '{}' (id={})", thirdStation.name(), thirdStation.id());

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

        return partnerStation.id();
    }
}
