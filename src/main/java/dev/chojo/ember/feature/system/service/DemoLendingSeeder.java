/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

/**
 * Seeds demo lending requests, chat messages, and inventory blocks between stations.
 */
@Singleton
public class DemoLendingSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoLendingSeeder.class);

    private final LendingService lendingService;
    private final LendingRepository lendingRepository;
    private final InventoryRepository inventoryRepository;

    @Inject
    public DemoLendingSeeder(
            LendingService lendingService,
            LendingRepository lendingRepository,
            InventoryRepository inventoryRepository) {
        this.lendingService = lendingService;
        this.lendingRepository = lendingRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Seeds lending demo data between two federated stations.
     *
     * @param stationId        the primary station ID
     * @param partnerStationId the partner station ID
     * @param createdBy        the member ID who creates the requests
     */
    public void seed(int stationId, int partnerStationId, int createdBy) {
        // Create inventory on the partner station for lending purposes
        var partnerFeuerloescher =
                inventoryRepository.create(partnerStationId, "Feuerlöscher", InventoryType.INTERNAL, false);
        inventoryRepository.createItem(partnerFeuerloescher.id(), "FL-001", "Feuerlöscher ABC 6kg", null, "{}");
        inventoryRepository.createItem(partnerFeuerloescher.id(), "FL-002", "Feuerlöscher ABC 6kg", null, "{}");
        inventoryRepository.createItem(partnerFeuerloescher.id(), "FL-003", "Feuerlöscher CO2 5kg", null, "{}");

        var partnerSchlaeuche =
                inventoryRepository.create(partnerStationId, "Schläuche", InventoryType.INTERNAL, false);
        inventoryRepository.createItem(partnerSchlaeuche.id(), "S-001", "B-Schlauch 20m", null, "{}");
        inventoryRepository.createItem(partnerSchlaeuche.id(), "S-002", "B-Schlauch 20m", null, "{}");
        inventoryRepository.createItem(partnerSchlaeuche.id(), "S-003", "C-Schlauch 15m", null, "{}");
        inventoryRepository.createItem(partnerSchlaeuche.id(), "S-004", "C-Schlauch 15m", null, "{}");

        var partnerZelte = inventoryRepository.create(partnerStationId, "Zelte", InventoryType.INTERNAL, false);
        inventoryRepository.createItem(partnerZelte.id(), "Z-001", "Mannschaftszelt 6x4m", null, "{}");
        inventoryRepository.createItem(partnerZelte.id(), "Z-002", "Faltzelt 3x3m", null, "{}");

        // -- Request 1: APPROVED — partner lends Feuerlöscher to main station --
        var approvedRequest = lendingService.createRequest(
                stationId,
                partnerStationId,
                LocalDate.now().plusDays(7),
                LocalDate.now().plusDays(14),
                createdBy);
        lendingService.addRequestItem(approvedRequest.id(), partnerFeuerloescher.id(), null, 2);
        lendingService.approveRequest(approvedRequest.id(), partnerStationId);

        // Chat messages
        lendingRepository.createMessage(
                approvedRequest.id(),
                stationId,
                createdBy,
                "Wir benötigen 2 Feuerlöscher für unsere Übung nächste Woche.",
                false);
        lendingRepository.createMessage(
                approvedRequest.id(),
                partnerStationId,
                null,
                "Kein Problem, die stehen bereit. Können ab Montag abgeholt werden.",
                false);

        // -- Request 2: REQUESTED — main station wants Schläuche from partner --
        var requestedRequest = lendingService.createRequest(
                stationId,
                partnerStationId,
                LocalDate.now().plusDays(21),
                LocalDate.now().plusDays(28),
                createdBy);
        lendingService.addRequestItem(requestedRequest.id(), partnerSchlaeuche.id(), null, 3);

        // Chat message
        lendingRepository.createMessage(
                requestedRequest.id(),
                stationId,
                createdBy,
                "Für den Kreiswettbewerb bräuchten wir 3 zusätzliche Schläuche.",
                false);

        // -- Request 3: RETURNED — completed lending from last month --
        var returnedRequest = lendingService.createRequest(
                stationId,
                partnerStationId,
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(23),
                createdBy);
        lendingService.addRequestItem(returnedRequest.id(), partnerZelte.id(), null, 1);
        lendingService.approveRequest(returnedRequest.id(), partnerStationId);
        lendingService.markLent(returnedRequest.id(), partnerStationId);
        lendingService.markReturned(returnedRequest.id(), stationId);

        // Chat messages
        lendingRepository.createMessage(
                returnedRequest.id(),
                stationId,
                createdBy,
                "Danke für das Zelt! War super für unser Zeltlager.",
                false);
        lendingRepository.createMessage(
                returnedRequest.id(), partnerStationId, null, "Gerne, jederzeit wieder!", false);

        // -- Walky Talkies on the base station (some lent out to partner) --
        var walkieTalkies = inventoryRepository.create(stationId, "Funkgeräte", InventoryType.INTERNAL, false);
        var wt1 = inventoryRepository.createItem(walkieTalkies.id(), "FG-001", "Motorola DP1400", null, "{}");
        var wt2 = inventoryRepository.createItem(walkieTalkies.id(), "FG-002", "Motorola DP1400", null, "{}");
        inventoryRepository.createItem(walkieTalkies.id(), "FG-003", "Motorola DP1400", null, "{}");
        inventoryRepository.createItem(walkieTalkies.id(), "FG-004", "Motorola DP1400", null, "{}");
        inventoryRepository.createItem(walkieTalkies.id(), "FG-005", "Motorola DP3441e", null, "{}");
        inventoryRepository.createItem(walkieTalkies.id(), "FG-006", "Motorola DP3441e", null, "{}");

        // -- Request 4 (INCOMING): partner requests Funkgeräte from main station (LENT — currently out) --
        var lentRequest = lendingService.createRequest(
                partnerStationId,
                stationId,
                LocalDate.now().minusDays(3),
                LocalDate.now().plusDays(4),
                createdBy);
        lendingService.addRequestItem(lentRequest.id(), walkieTalkies.id(), wt1.id(), 1);
        lendingService.addRequestItem(lentRequest.id(), walkieTalkies.id(), wt2.id(), 1);
        lendingService.approveRequest(lentRequest.id(), stationId);
        lendingService.markLent(lentRequest.id(), stationId);

        lendingRepository.createMessage(
                lentRequest.id(),
                partnerStationId,
                null,
                "Könnten wir uns 2 Funkgeräte für unser Wochenende ausleihen?",
                false);
        lendingRepository.createMessage(
                lentRequest.id(), stationId, createdBy, "Klar, kommt sie einfach abholen.", false);

        // -- Request 5 (INCOMING): partner requests Funkgeräte from main station (REQUESTED — pending) --
        var incomingPending = lendingService.createRequest(
                partnerStationId,
                stationId,
                LocalDate.now().plusDays(14),
                LocalDate.now().plusDays(16),
                createdBy);
        lendingService.addRequestItem(incomingPending.id(), walkieTalkies.id(), null, 3);

        lendingRepository.createMessage(
                incomingPending.id(),
                partnerStationId,
                null,
                "Für unsere Übung am übernächsten Wochenende bräuchten wir 3 Funkgeräte.",
                false);

        // -- Request 6 (INCOMING): partner wants Zelte — APPROVED, about to be picked up --
        var aboutToLend = lendingService.createRequest(
                partnerStationId,
                stationId,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(5),
                createdBy);
        lendingService.addRequestItem(aboutToLend.id(), walkieTalkies.id(), null, 2);
        lendingService.approveRequest(aboutToLend.id(), stationId);

        lendingRepository.createMessage(
                aboutToLend.id(),
                partnerStationId,
                null,
                "Wir bräuchten 2 Funkgeräte für die Übung am Wochenende.",
                false);
        lendingRepository.createMessage(
                aboutToLend.id(), stationId, createdBy, "Geht klar, könnt ihr morgen abholen.", false);

        // -- Request 7 (INCOMING): OVERDUE — partner has items past return date --
        var overdueRequest = lendingService.createRequest(
                partnerStationId,
                stationId,
                LocalDate.now().minusDays(14),
                LocalDate.now().minusDays(7),
                createdBy);
        lendingService.addRequestItem(overdueRequest.id(), walkieTalkies.id(), null, 1);
        lendingService.approveRequest(overdueRequest.id(), stationId);
        lendingService.markLent(overdueRequest.id(), stationId);

        lendingRepository.createMessage(
                overdueRequest.id(),
                partnerStationId,
                null,
                "Können wir ein Funkgerät für die Übung letzte Woche ausleihen?",
                false);
        lendingRepository.createMessage(overdueRequest.id(), stationId, createdBy, "Klar, nehmt eins mit.", false);

        // -- Inventory block on partner station --
        lendingService.createBlock(
                partnerStationId,
                null,
                null,
                LocalDate.now().plusMonths(1).withDayOfMonth(1),
                LocalDate.now().plusMonths(1).withDayOfMonth(3),
                "Kreisfeuerwehrtag");

        log.info("Demo: Created lending requests, messages, and blocks");
    }
}
