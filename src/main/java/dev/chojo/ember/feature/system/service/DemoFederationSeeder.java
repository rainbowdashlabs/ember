/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
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
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final PasswordHasher passwordHasher;

    @Inject
    public DemoFederationSeeder(
            StationRepository stationRepository,
            FederationService federationService,
            KnowledgeBaseService kbService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            PasswordHasher passwordHasher) {
        this.stationRepository = stationRepository;
        this.federationService = federationService;
        this.kbService = kbService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.passwordHasher = passwordHasher;
    }

    public void seed(int primaryStationId, int createdBy) {
        // Create a second station
        var partnerStation = stationRepository.create("JF Partnerwache");
        log.info("Demo: Created partner station '{}' (id={})", partnerStation.name(), partnerStation.id());

        // Create a manager account on the partner station
        var partnerAccount = accountRepository.create("partner@demo.ember", "Partner", "Manager", true);
        accountRepository.createCredential(partnerAccount.id(), passwordHasher.hash("demo"));
        var partnerMember = stationMemberRepository.create(partnerStation.id(), partnerAccount.id());
        var managerRole = stationMemberRepository
                .findRoleByName(dev.chojo.ember.api.Roles.MANAGER)
                .orElseThrow();
        var loginRole = stationMemberRepository
                .findRoleByName(dev.chojo.ember.api.Roles.LOGIN)
                .orElseThrow();
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
        var partner = federationService.acceptInvite(
                primaryStationId,
                partnerStation.id(),
                federationService.encodePublicKey(federationService.generateKeyPair()));

        // Share the partner station's KB with the primary station
        var kbFiles = kbService.findFiles(partnerStation.id(), null);
        for (var file : kbFiles) {
            federationService.createKbShare(partnerStation.id(), file.id(), null, "ALL_PARTNERS");
        }

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
    }
}
