/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareLevel;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryShareRepositoryTest extends RepositoryTestBase {

    private static InventoryShareRepository repository;
    private static FederationRepository federationRepo;
    private static FederationService federationService;

    private static Station owner;
    private static Station partnerStation;
    private static int inventoryId;
    private static int artId;
    private static int itemId;
    private static int partnerId;

    @BeforeAll
    static void setup() {
        repository = new InventoryShareRepository();
        federationRepo = new FederationRepository();
        federationService = new FederationService(federationRepo, stationRepo, new Api());

        owner = stationRepo.create("ShareRepoOwner");
        partnerStation = stationRepo.create("ShareRepoPartner");

        var inventory = inventoryRepo.create(owner.id(), "ShareRepoInventory", InventoryType.INTERNAL, false);
        inventoryId = inventory.id();
        artId = artRepo.create(inventoryId, "Funkgerät", "", 0).id();
        itemId = inventoryRepo
                .createItem(inventoryId, "SHR-001", "Share Repo Item", null, null)
                .id();

        var keyPair = federationService.generateKeyPair();
        partnerId = federationService
                .acceptInvite(partnerStation.id(), owner.id(), federationService.encodePublicKey(keyPair), null, null)
                .id();
    }

    @AfterAll
    static void cleanup() {
        for (var p : federationService.findPartners(owner.id())) federationRepo.deletePartner(p.id());
        for (var p : federationService.findPartners(partnerStation.id())) federationRepo.deletePartner(p.id());
        stationRepo.delete(owner.id());
        stationRepo.delete(partnerStation.id());
    }

    @Test
    void writingAnInventoryShareTwiceOverwritesTheFirstRow() {
        var first = repository.upsertInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT);
        var second = repository.upsertInventoryShare(owner.id(), inventoryId, ShareScope.SPECIFIC, ShareGrant.WITHHOLD);

        assertEquals(first.id(), second.id());
        assertEquals(ShareScope.SPECIFIC, second.shareScope());
        assertEquals(ShareGrant.WITHHOLD, second.shareGrant());
        assertEquals(1, repository.findByStation(owner.id()).size());

        assertTrue(repository.deleteInventoryShare(owner.id(), inventoryId));
        assertFalse(repository.deleteInventoryShare(owner.id(), inventoryId));
    }

    @Test
    void writingAnItemShareTwiceOverwritesTheFirstRow() {
        var first = repository.upsertItemShare(owner.id(), itemId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT);
        var second = repository.upsertItemShare(owner.id(), itemId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD);

        assertEquals(first.id(), second.id());
        assertEquals(ShareGrant.WITHHOLD, second.shareGrant());
        assertEquals(ShareLevel.ITEM, second.level());
        assertNull(second.inventoryId());

        assertTrue(repository.deleteItemShare(owner.id(), itemId));
        assertFalse(repository.deleteItemShare(owner.id(), itemId));
    }

    @Test
    void writingAKindShareTwiceOverwritesTheFirstRow() {
        var first = repository.upsertArtShare(owner.id(), artId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT);
        var second = repository.upsertArtShare(owner.id(), artId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD);

        assertEquals(first.id(), second.id());
        assertEquals(ShareLevel.ART, second.level());
        assertEquals(ShareGrant.WITHHOLD, second.shareGrant());
        assertNull(second.inventoryId());
        assertNull(second.itemId());

        var found = repository.findForArt(owner.id(), artId).orElseThrow();
        assertEquals(second.id(), found.id());
        assertTrue(repository.findForArt(partnerStation.id(), artId).isEmpty());

        assertTrue(repository.deleteArtShare(owner.id(), artId));
        assertFalse(repository.deleteArtShare(owner.id(), artId));
    }

    @Test
    void aShareIsFoundByTheGearItSpeaksAbout() {
        repository.upsertInventoryShare(owner.id(), inventoryId, ShareScope.ALL_PARTNERS, ShareGrant.GRANT);
        repository.upsertItemShare(owner.id(), itemId, ShareScope.ALL_PARTNERS, ShareGrant.WITHHOLD);

        var forInventory = repository.findForInventory(owner.id(), inventoryId).orElseThrow();
        assertEquals(ShareLevel.INVENTORY, forInventory.level());
        assertEquals(inventoryId, forInventory.inventoryId());

        var forItem = repository.findForItem(owner.id(), itemId).orElseThrow();
        assertEquals(itemId, forItem.itemId());

        assertTrue(repository.findForInventory(partnerStation.id(), inventoryId).isEmpty());
        assertTrue(repository.findForItem(partnerStation.id(), itemId).isEmpty());
        assertEquals(2, repository.findByStation(owner.id()).size());

        repository.deleteInventoryShare(owner.id(), inventoryId);
        repository.deleteItemShare(owner.id(), itemId);
    }

    @Test
    void targetsAreReplacedRatherThanAddedTo() {
        var share = repository.upsertInventoryShare(owner.id(), inventoryId, ShareScope.SPECIFIC, ShareGrant.GRANT);

        repository.setTargets(share.id(), List.of(partnerId));
        assertEquals(List.of(partnerId), repository.findTargets(share.id()));
        assertEquals(1, repository.findTargetsByStation(owner.id()).size());
        assertEquals(
                partnerId,
                repository.findTargetsByStation(owner.id()).getFirst().partnerId());

        repository.setTargets(share.id(), List.of());
        assertTrue(repository.findTargets(share.id()).isEmpty());
        assertTrue(repository.findTargetsByStation(owner.id()).isEmpty());

        repository.deleteInventoryShare(owner.id(), inventoryId);
    }
}
