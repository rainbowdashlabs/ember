/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.InventoryShareService;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;

/**
 * Seeds demo lending requests, chat messages, and inventory blocks between stations.
 */
@Singleton
public class DemoLendingSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoLendingSeeder.class);

    private final LendingService lendingService;
    private final InventoryShareService shareService;
    private final InventoryRepository inventoryRepository;
    private final InventoryArtRepository artRepository;

    @Inject
    public DemoLendingSeeder(
            LendingService lendingService,
            InventoryShareService shareService,
            InventoryRepository inventoryRepository,
            InventoryArtRepository artRepository) {
        this.lendingService = lendingService;
        this.shareService = shareService;
        this.inventoryRepository = inventoryRepository;
        this.artRepository = artRepository;
    }

    /**
     * Runs after the federation band because the requests are exchanged with the partner station.
     */
    @Override
    public int order() {
        return FEDERATED_MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var federation = run.federation();
        seed(
                station.stationId(),
                federation.partnerStationId(),
                station.adminMember().id(),
                federation.partnerMemberId());
        log.info("Demo: Created lending data");
    }

    /**
     * Seeds lending demo data between two federated stations.
     *
     * @param stationId        the primary station ID
     * @param partnerStationId the partner station ID
     * @param createdBy        the member ID who creates the requests
     */
    public void seed(int stationId, int partnerStationId, int createdBy, int partnerMemberId) {
        // The partner's shelf, stocked once however many stations borrow from it
        var partnerFeuerloescher = partnerStock(
                partnerStationId,
                "Feuerlöscher",
                List.of(
                        new Stock("FL-001", "Feuerlöscher ABC 6kg"),
                        new Stock("FL-002", "Feuerlöscher ABC 6kg"),
                        new Stock("FL-003", "Feuerlöscher CO2 5kg")));

        var partnerSchlaeuche = partnerStock(
                partnerStationId,
                "Schläuche",
                List.of(
                        new Stock("S-001", "B-Schlauch 20m"),
                        new Stock("S-002", "B-Schlauch 20m"),
                        new Stock("S-003", "C-Schlauch 15m"),
                        new Stock("S-004", "C-Schlauch 15m")));

        var partnerZelte = partnerStock(
                partnerStationId,
                "Zelte",
                List.of(new Stock("Z-001", "Mannschaftszelt 6x4m"), new Stock("Z-002", "Faltzelt 3x3m")));

        offerToPartners(partnerStationId, partnerFeuerloescher.id());
        offerToPartners(partnerStationId, partnerSchlaeuche.id());
        offerToPartners(partnerStationId, partnerZelte.id());

        // -- Request 1: APPROVED - partner lends Feuerlöscher to main station --
        var approvedRequest = lendingService.createRequest(
                stationId,
                partnerStationId,
                LocalDate.now().plusDays(7),
                LocalDate.now().plusDays(14),
                createdBy,
                null,
                null,
                "");
        lendingService.addRequestItem(approvedRequest.id(), partnerFeuerloescher.id(), null, null, 2, null);
        lendingService.approveRequest(approvedRequest.id(), partnerStationId);

        // Chat messages
        lendingService.sendMessage(
                approvedRequest.id(),
                stationId,
                createdBy,
                "Admin",
                "Wir benötigen 2 Feuerlöscher für unsere Übung nächste Woche.");
        lendingService.sendMessage(
                approvedRequest.id(),
                partnerStationId,
                partnerMemberId,
                "Partner Manager",
                "Kein Problem, die stehen bereit. Können ab Montag abgeholt werden.");

        // -- Request 2: REQUESTED - main station wants Schläuche from partner --
        var requestedRequest = lendingService.createRequest(
                stationId,
                partnerStationId,
                LocalDate.now().plusDays(21),
                LocalDate.now().plusDays(28),
                createdBy,
                null,
                null,
                "");
        lendingService.addRequestItem(requestedRequest.id(), partnerSchlaeuche.id(), null, null, 3, null);

        // Chat message
        lendingService.sendMessage(
                requestedRequest.id(),
                stationId,
                createdBy,
                "Admin",
                "Für den Kreiswettbewerb bräuchten wir 3 zusätzliche Schläuche.");

        // -- Request 3: RETURNED - completed lending from last month --
        var returnedRequest = lendingService.createRequest(
                stationId,
                partnerStationId,
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(23),
                createdBy,
                null,
                null,
                "");
        lendingService.addRequestItem(returnedRequest.id(), partnerZelte.id(), null, null, 1, null);
        lendingService.approveRequest(returnedRequest.id(), partnerStationId);
        lendingService.markLent(returnedRequest.id(), partnerStationId);
        lendingService.markReturned(returnedRequest.id(), stationId);

        // Chat messages
        lendingService.sendMessage(
                returnedRequest.id(),
                stationId,
                createdBy,
                "Admin",
                "Danke für das Zelt! War super für unser Zeltlager.");
        lendingService.sendMessage(
                returnedRequest.id(), partnerStationId, partnerMemberId, "Partner Manager", "Gerne, jederzeit wieder!");

        // -- Walky Talkies on the base station (some lent out to partner) --
        var walkieTalkies = inventoryRepository.create(stationId, "Funkgeräte", InventoryType.INTERNAL, false);
        var wt1 = inventoryRepository.createItem(walkieTalkies.id(), "FG-001", "Motorola DP1400", null, null);
        var wt2 = inventoryRepository.createItem(walkieTalkies.id(), "FG-002", "Motorola DP1400", null, null);
        inventoryRepository.createItem(walkieTalkies.id(), "FG-003", "Motorola DP1400", null, null);
        inventoryRepository.createItem(walkieTalkies.id(), "FG-004", "Motorola DP1400", null, null);
        var goodRadio = inventoryRepository.createItem(walkieTalkies.id(), "FG-005", "Motorola DP3441e", null, null);
        var otherGoodRadio =
                inventoryRepository.createItem(walkieTalkies.id(), "FG-006", "Motorola DP3441e", null, null);

        var goodRadios = artRepository.create(walkieTalkies.id(), "Motorola DP3441e", "Die guten Geräte", 10);
        artRepository.setArt(goodRadios.id(), List.of(goodRadio.id(), otherGoodRadio.id()));

        offerToPartners(stationId, walkieTalkies.id());
        shareService.setArtShare(stationId, goodRadios.id(), ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD, List.of());
        shareService.setItemShare(stationId, goodRadio.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        // -- Request 4 (INCOMING): partner requests Funkgeräte from main station (LENT - currently out) --
        var lentRequest = lendingService.createRequest(
                partnerStationId,
                stationId,
                LocalDate.now().minusDays(3),
                LocalDate.now().plusDays(4),
                createdBy,
                null,
                null,
                "");
        lendingService.addRequestItem(lentRequest.id(), walkieTalkies.id(), wt1.id(), null, 1, null);
        lendingService.addRequestItem(lentRequest.id(), walkieTalkies.id(), wt2.id(), null, 1, null);
        lendingService.approveRequest(lentRequest.id(), stationId);
        lendingService.markLent(lentRequest.id(), stationId);

        lendingService.sendMessage(
                lentRequest.id(),
                partnerStationId,
                partnerMemberId,
                "Partner Manager",
                "Könnten wir uns 2 Funkgeräte für unser Wochenende ausleihen?");
        lendingService.sendMessage(lentRequest.id(), stationId, createdBy, "Admin", "Klar, kommt sie einfach abholen.");

        // -- Request 5 (INCOMING): partner requests Funkgeräte from main station (REQUESTED - pending) --
        var incomingPending = lendingService.createRequest(
                partnerStationId,
                stationId,
                LocalDate.now().plusDays(14),
                LocalDate.now().plusDays(16),
                createdBy,
                null,
                null,
                "");
        lendingService.addRequestItem(incomingPending.id(), walkieTalkies.id(), null, null, 3, null);

        lendingService.sendMessage(
                incomingPending.id(),
                partnerStationId,
                partnerMemberId,
                "Partner Manager",
                "Für unsere Übung am übernächsten Wochenende bräuchten wir 3 Funkgeräte.");

        // -- Request 6 (INCOMING): partner wants Zelte - APPROVED, about to be picked up --
        var aboutToLend = lendingService.createRequest(
                partnerStationId,
                stationId,
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(5),
                createdBy,
                null,
                null,
                "");
        lendingService.addRequestItem(aboutToLend.id(), walkieTalkies.id(), null, null, 2, null);
        lendingService.approveRequest(aboutToLend.id(), stationId);

        lendingService.sendMessage(
                aboutToLend.id(),
                partnerStationId,
                partnerMemberId,
                "Partner Manager",
                "Wir bräuchten 2 Funkgeräte für die Übung am Wochenende.");
        lendingService.sendMessage(
                aboutToLend.id(), stationId, createdBy, "Admin", "Geht klar, könnt ihr morgen abholen.");

        // -- Request 7 (INCOMING): OVERDUE - partner has items past return date --
        var overdueRequest = lendingService.createRequest(
                partnerStationId,
                stationId,
                LocalDate.now().minusDays(14),
                LocalDate.now().minusDays(7),
                createdBy,
                null,
                null,
                "");
        lendingService.addRequestItem(overdueRequest.id(), walkieTalkies.id(), null, null, 1, null);
        lendingService.approveRequest(overdueRequest.id(), stationId);
        lendingService.markLent(overdueRequest.id(), stationId);

        lendingService.sendMessage(
                overdueRequest.id(),
                partnerStationId,
                partnerMemberId,
                "Partner Manager",
                "Können wir ein Funkgerät für die Übung letzte Woche ausleihen?");
        lendingService.sendMessage(overdueRequest.id(), stationId, createdBy, "Admin", "Klar, nehmt eins mit.");

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

    /**
     * Puts a whole inventory on offer to every partner, which is what a demo station has to say
     * before anything of it can be found: sharing is opt-in and an unshared shelf is invisible.
     */
    private void offerToPartners(int stationId, int inventoryId) {
        shareService.setInventoryShare(stationId, inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());
    }

    /** One piece on the partner's shelf: what it is called on the label, and what it is. */
    private record Stock(String internalId, String description) {}

    /**
     * An inventory at the partner station, made once.
     *
     * <p>Every full station borrows from the same partner, and what the partner keeps is the partner's: made
     * again for the second station it would be a second shelf with the same name, which the database refuses
     * and nobody meant.
     */
    private Inventory partnerStock(int partnerStationId, String name, List<Stock> items) {
        var existing = inventoryRepository.findByStation(partnerStationId).stream()
                .filter(inventory -> name.equals(inventory.name()))
                .findFirst();
        if (existing.isPresent()) return existing.get();

        var inventory = inventoryRepository.create(partnerStationId, name, InventoryType.INTERNAL, false);
        for (Stock item : items) {
            inventoryRepository.createItem(inventory.id(), item.internalId(), item.description(), null, null);
        }
        return inventory;
    }
}
