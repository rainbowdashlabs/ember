/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTagRepositoryTest extends RepositoryTestBase {

    private static Station station;
    private static Station neighbour;
    private static Account account;
    private static int inventoryId;
    private static int neighbourInventoryId;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("itemtag@test.example", "Item", "Tagger");
        station = stationRepo.create("TagStation");
        neighbour = stationRepo.create("TagNeighbourStation");
        inventoryId = inventoryRepo
                .create(station.id(), "Gemeindematerial", InventoryType.INTERNAL, false)
                .id();
        neighbourInventoryId = inventoryRepo
                .create(neighbour.id(), "Sonstiges", InventoryType.INTERNAL, false)
                .id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(neighbour.id());
        accountRepo.delete(account.id());
    }

    @Test
    void oneWordHoweverItWasTyped() {
        var funk = inventoryTagRepo.create(station.id(), "Funk", "#3694FF");
        assertEquals("funk", funk.canonicalName());
        assertEquals(
                funk.id(),
                inventoryTagRepo
                        .findByName(station.id(), " FUNK ")
                        .orElseThrow()
                        .id());
        assertEquals(
                funk.id(),
                inventoryTagRepo.findByName(station.id(), "funk").orElseThrow().id());
        assertTrue(inventoryTagRepo.findByName(station.id(), "Feuerwehr").isEmpty());
        assertTrue(inventoryTagRepo.findById(funk.id()).isPresent());

        assertTrue(inventoryTagRepo.update(funk.id(), "Funkgerät", "#FF6421", 5));
        var renamed = inventoryTagRepo.findById(funk.id()).orElseThrow();
        assertEquals("funkgerät", renamed.canonicalName());
        assertEquals("#FF6421", renamed.color());
        assertEquals(5, renamed.position());

        assertTrue(inventoryTagRepo.delete(funk.id(), station.id()));
        assertFalse(inventoryTagRepo.delete(funk.id(), station.id()));
        assertFalse(inventoryTagRepo.update(funk.id(), "x", null, 0));
    }

    @Test
    void aTagOfAnotherStationIsNeitherDeletedNorPutOnAThing() {
        var mine = inventoryTagRepo.create(station.id(), "Eigen", null);
        var theirs = inventoryTagRepo.create(neighbour.id(), "Fremd", null);
        assertFalse(inventoryTagRepo.delete(theirs.id(), station.id()));

        InventoryItem item = inventoryRepo.createItem(inventoryId, "TG-100", "Kabeltrommel", null, null);
        inventoryTagRepo.setItemTags(item.id(), station.id(), List.of(mine.id(), theirs.id()));
        var worn = inventoryTagRepo.findTagsForItem(item.id());
        assertEquals(1, worn.size());
        assertEquals("Eigen", worn.getFirst().name());

        inventoryTagRepo.setItemTags(item.id(), station.id(), List.of());
        assertTrue(inventoryTagRepo.findTagsForItem(item.id()).isEmpty());
        inventoryTagRepo.setItemTags(item.id(), station.id(), null);
        assertTrue(inventoryTagRepo.findTagsForItem(item.id()).isEmpty());

        inventoryTagRepo.delete(mine.id(), station.id());
        inventoryTagRepo.delete(theirs.id(), neighbour.id());
    }

    @Test
    void theTagsOfAWholeListComeBackAtOnce() {
        var one = inventoryTagRepo.create(station.id(), "Listen-A", null);
        var two = inventoryTagRepo.create(station.id(), "Listen-B", null);
        var first = inventoryRepo.createItem(inventoryId, "TG-200", "Erster", null, null);
        var second = inventoryRepo.createItem(inventoryId, "TG-201", "Zweiter", null, null);
        var bare = inventoryRepo.createItem(inventoryId, "TG-202", "Ohne", null, null);
        inventoryTagRepo.setItemTags(first.id(), station.id(), List.of(one.id(), two.id()));
        inventoryTagRepo.setItemTags(second.id(), station.id(), List.of(two.id()));

        var tags = inventoryTagRepo.findTagsForItems(List.of(first.id(), second.id(), bare.id()));
        assertEquals(2, tags.size());
        assertEquals(2, tags.get(first.id()).size());
        assertEquals(1, tags.get(second.id()).size());
        assertTrue(inventoryTagRepo.findTagsForItems(List.of()).isEmpty());
        assertTrue(inventoryTagRepo.findTagsForItems(null).isEmpty());

        var counts = inventoryTagRepo.countItemsPerTag(station.id());
        assertEquals(1, counts.get(one.id()));
        assertEquals(2, counts.get(two.id()));

        assertEquals(2, inventoryTagRepo.findByStation(station.id()).size());

        inventoryTagRepo.delete(one.id(), station.id());
        inventoryTagRepo.delete(two.id(), station.id());
        inventoryRepo.deleteItem(first.id());
        inventoryRepo.deleteItem(second.id());
        inventoryRepo.deleteItem(bare.id());
    }

    @Test
    void twoStationsWritingOneWordAnswerOneSearch() {
        var here = inventoryTagRepo.create(station.id(), "Funk", null);
        var there = inventoryTagRepo.create(neighbour.id(), " funk ", null);
        var mine = inventoryRepo.createItem(inventoryId, "TG-300", "Funkgerät blau", null, null);
        var theirs = inventoryRepo.createItem(neighbourInventoryId, "TG-301", "Antenne", null, null);
        inventoryTagRepo.setItemTags(mine.id(), station.id(), List.of(here.id()));
        inventoryTagRepo.setItemTags(theirs.id(), neighbour.id(), List.of(there.id()));

        var mineOnly = inventoryTagRepo.findItemsByTag(List.of(station.id()), "FUNK");
        assertEquals(1, mineOnly.size());
        assertEquals("Funkgerät blau", mineOnly.getFirst().name());
        assertEquals(station.uid(), mineOnly.getFirst().stationUid());
        assertTrue(mineOnly.getFirst().available());

        var both = inventoryTagRepo.findItemsByTag(List.of(station.id(), neighbour.id()), " Funk ");
        assertEquals(2, both.size());

        assertTrue(inventoryTagRepo.findItemsByTag(List.of(), "Funk").isEmpty());
        assertTrue(inventoryTagRepo.findItemsByTag(null, "Funk").isEmpty());
        assertTrue(inventoryTagRepo
                .findItemsByTag(List.of(station.id()), "Nichtvorhanden")
                .isEmpty());

        inventoryTagRepo.delete(here.id(), station.id());
        inventoryTagRepo.delete(there.id(), neighbour.id());
        inventoryRepo.deleteItem(mine.id());
        inventoryRepo.deleteItem(theirs.id());
    }

    @Test
    void aPartnerSeesNothingUntilTheStationNamesWhatItOffers() {
        var federationRepo = new FederationRepository();
        var federationService = new FederationService(federationRepo, stationRepo, new Api());
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                neighbour.id(), station.id(), federationService.encodePublicKey(keyPair), null, null);
        int partnerId = federationService.findPartners(station.id()).stream()
                .filter(p -> neighbour.uid().equals(p.partnerStationId()))
                .findFirst()
                .orElseThrow()
                .id();

        var funk = inventoryTagRepo.create(station.id(), "Funk", null);
        var offered = inventoryRepo.createItem(inventoryId, "TG-400", "Funkgerät geteilt", null, null);
        var kept = inventoryRepo.createItem(inventoryId, "TG-401", "Funkgerät behalten", null, null);
        inventoryTagRepo.setItemTags(offered.id(), station.id(), List.of(funk.id()));
        inventoryTagRepo.setItemTags(kept.id(), station.id(), List.of(funk.id()));

        assertTrue(
                inventoryTagService
                        .findSharedItemsByTag(station.id(), partnerId, "Funk")
                        .isEmpty(),
                "a station that has offered nothing serves nothing for a word either");

        shareItem(station.id(), offered.id());
        var shared = inventoryTagService.findSharedItemsByTag(station.id(), partnerId, "funk");
        assertEquals(1, shared.size());
        assertEquals("Funkgerät geteilt", shared.getFirst().name());

        withholdItem(station.id(), kept.id());
        assertEquals(
                1,
                inventoryTagService
                        .findSharedItemsByTag(station.id(), partnerId, "funk")
                        .size(),
                "a row that expressly withholds a piece does not serve it for a word");

        clearShares(station.id());
        for (var p : federationService.findPartners(station.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(neighbour.id())) federationRepo.deletePartner(p.id());
        inventoryTagRepo.delete(funk.id(), station.id());
        inventoryRepo.deleteItem(offered.id());
        inventoryRepo.deleteItem(kept.id());
    }

    /**
     * A word reaches the station's own gear and nothing else. An inventory holding both owners can
     * be offered whole, and the pieces belonging to the body above the station stay at home anyway:
     * the offer says what a station is willing to lend, never what is the station's to lend.
     */
    @Test
    void aWordDoesNotReachGearTheStationDoesNotOwn() {
        var federationRepo = new FederationRepository();
        var federationService = new FederationService(federationRepo, stationRepo, new Api());
        var keyPair = federationService.generateKeyPair();
        federationService.acceptInvite(
                neighbour.id(), station.id(), federationService.encodePublicKey(keyPair), null, null);
        int partnerId = federationService.findPartners(station.id()).stream()
                .filter(p -> neighbour.uid().equals(p.partnerStationId()))
                .findFirst()
                .orElseThrow()
                .id();

        var funk = inventoryTagRepo.create(station.id(), "Eigenfunk", null);
        var ours = inventoryRepo.createItem(inventoryId, "TG-500", "Eigenes Funkgerät", null, null);
        var theirs = inventoryRepo.createItem(inventoryId, "TG-501", "Kreisfunkgerät", null, null);
        inventoryTagRepo.setItemTags(ours.id(), station.id(), List.of(funk.id()));
        inventoryTagRepo.setItemTags(theirs.id(), station.id(), List.of(funk.id()));
        ownedByTheBodyAbove(theirs.id());
        shareInventory(station.id(), inventoryId);

        var served = inventoryTagService.findSharedItemsByTag(station.id(), partnerId, "eigenfunk");
        assertEquals(1, served.size());
        assertEquals("Eigenes Funkgerät", served.getFirst().name());

        clearShares(station.id());
        for (var p : federationService.findPartners(station.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(neighbour.id())) federationRepo.deletePartner(p.id());
        inventoryTagRepo.delete(funk.id(), station.id());
        inventoryRepo.deleteItem(ours.id());
        inventoryRepo.deleteItem(theirs.id());
    }

    private static void ownedByTheBodyAbove(int itemId) {
        query("UPDATE inventory_item SET owner_kind = 'CLUSTER' WHERE id = :item_id;")
                .single(call().bind("item_id", itemId))
                .update();
    }

    private static void shareInventory(int stationId, int inventoryId) {
        query("""
                INSERT INTO federation_inventory_share(station_id, inventory_id, share_scope)
                VALUES (:station_id, :inventory_id, 'ALL_PARTNERS');""")
                .single(call().bind("station_id", stationId).bind("inventory_id", inventoryId))
                .insert();
    }

    private static void shareItem(int stationId, int itemId) {
        query("""
                INSERT INTO federation_inventory_share(station_id, item_id, share_scope)
                VALUES (:station_id, :item_id, 'ALL_PARTNERS');""")
                .single(call().bind("station_id", stationId).bind("item_id", itemId))
                .insert();
    }

    private static void withholdItem(int stationId, int itemId) {
        query("""
                INSERT INTO federation_inventory_share(station_id, item_id, share_scope, share_grant)
                VALUES (:station_id, :item_id, 'ALL_PARTNERS', 'WITHHOLD');""")
                .single(call().bind("station_id", stationId).bind("item_id", itemId))
                .insert();
    }

    private static void clearShares(int stationId) {
        query("DELETE FROM federation_inventory_share WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
    }
}
