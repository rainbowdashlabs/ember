/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.equipment.EquipmentTestSupport;
import dev.chojo.ember.feature.equipment.repository.EquipmentRecommendationRepository;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemMetadata;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentBrowseServiceTest extends RepositoryTestBase {

    private static Station borrower;
    private static Station owner;
    private static Inventory drawer;
    private static InventoryArt blue;
    private static InventoryItem radio;
    private static InventoryItem charger;
    private static EquipmentBrowseService browse;

    @BeforeAll
    static void setup() {
        borrower = stationRepo.create("BrowseBorrower");
        owner = stationRepo.create("BrowseOwner");

        drawer = inventoryRepo.create(owner.id(), "BrowseFunk", InventoryType.INTERNAL, false, false);
        blue = artRepo.create(drawer.id(), "BrowseBlau", "", 0);
        radio = inventoryRepo.createItem(
                drawer.id(), "BR-01", "Funk blau", null, blue.id(), InventoryItemMetadata.empty(), null, null);
        inventoryRepo.createItem(
                drawer.id(), "BR-02", "Funk blau", null, blue.id(), InventoryItemMetadata.empty(), null, null);
        charger = inventoryRepo.createItem(drawer.id(), "BR-03", "Ladestation", null, InventoryItemMetadata.empty());

        var word = inventoryTagRepo.create(owner.id(), "BrowseFunkset", null);
        inventoryTagRepo.setItemTags(radio.id(), owner.id(), List.of(word.id()));
        inventoryTagRepo.setItemTags(charger.id(), owner.id(), List.of(word.id()));

        var federationService = new FederationService(new FederationRepository(), stationRepo, new Api());
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                borrower.id(), owner.id(), federationService.encodePublicKey(keyPair), null, null);
        for (var partnership : federationService.findPartners(borrower.id())) {
            federationService.setCapability(partnership.id(), CapabilityType.INVENTORY_LEND, Direction.IMPORT, true);
        }
        inventoryShareService.setInventoryShare(
                owner.id(), drawer.id(), ShareScope.ALL_PARTNERS, ShareGrant.GRANT, List.of());

        var services = newEventServices(new DomainEventBus(Set.of()));
        browse = new EquipmentBrowseService(new EquipmentRecommendationRepository(), services.lending());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(borrower.id());
        stationRepo.delete(owner.id());
    }

    @Test
    void whatGoesWithAPieceIsOffered() {
        var found = browse.recommendationsFor(owner.id(), radio.id());
        assertTrue(found.stream().anyMatch(r -> r.itemId() == charger.id() && r.byWord()));
    }

    @Test
    void aCollectedLineIsCountedAgainAgainstWhatIsOffered() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(500);
        var line = new EquipmentBrowseService.CollectedLine(owner.id(), drawer.id(), blue.id(), 2, null);
        var answer = browse.recheck(borrower.id(), day, day, List.of(line));
        assertEquals(1, answer.size());
        assertEquals(2, answer.getFirst().available());
        assertFalse(answer.getFirst().changed());
    }

    @Test
    void aLineAskingForMoreThanIsThereSaysSo() {
        LocalDate day = EquipmentTestSupport.SATURDAY.plusDays(510);
        var line = new EquipmentBrowseService.CollectedLine(owner.id(), drawer.id(), blue.id(), 9, null);
        var answer = browse.recheck(borrower.id(), day, day, List.of(line));
        assertEquals(2, answer.getFirst().available());
        assertTrue(answer.getFirst().changed());
    }

    @Test
    void theSendSaysHowManyRequestsItWillMake() {
        var lines = List.of(
                new EquipmentBrowseService.CollectedLine(owner.id(), drawer.id(), blue.id(), 1, null),
                new EquipmentBrowseService.CollectedLine(owner.id(), drawer.id(), null, 1, null),
                new EquipmentBrowseService.CollectedLine(borrower.id(), drawer.id(), null, 1, null));
        assertEquals(2, browse.requestCount(lines), "two stations, two letters");
        assertEquals(0, browse.requestCount(List.of()));
    }
}
