/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCorrection;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Putting right what a check found in somebody's hands.
 *
 * <p>What has to hold is where the piece coming off the record goes, because that is the part nobody
 * chooses: the station's own back into the station's store, the association's back to an association
 * that is here to take it, and nowhere at all when the association is not on this instance.
 */
class InventoryCheckCorrectionTest extends RepositoryTestBase {

    private static InventoryCheckService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        var containerService =
                new InventoryContainerService(containerRepo, containerKindRepo, inventoryRepo, itemCustodyService);
        service = new InventoryCheckService(
                inventoryCheckRepo,
                inventoryRepo,
                stationMemberRepo,
                memberGroupRepo,
                accountRepo,
                memberIdentityFactory,
                containerService,
                itemCustodyService,
                inventoryService,
                selfCheckRepo);
        station = stationRepo.create("CorrectionStation");
        account = accountRepo.create("correction@test.com", "Kora", "Rektur");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static int inventory(String name, InventoryType type) {
        return inventoryRepo.create(station.id(), name, type, false).id();
    }

    private static InventoryItem held(int inventoryId, String number, ItemOwner owner, Integer clusterId) {
        var item = inventoryRepo.createItem(inventoryId, number, "Jacke", null, null, owner, clusterId);
        itemCustodyService.assignToMember(item.id(), member.id(), "Kora Rektur");
        return item;
    }

    private static ItemCorrection makesANewPiece(int inventoryId, int oldItemId, ItemOwner owner) {
        return new ItemCorrection(inventoryId, oldItemId, null, null, owner, "neu", null);
    }

    private static InventoryItem reload(int itemId) {
        return inventoryRepo.findItemById(itemId).orElseThrow();
    }

    @Test
    void theStationsOwnPieceGoesBackIntoTheStationsStore() {
        int inventoryId = inventory("Helme", InventoryType.INTERNAL);
        var old = held(inventoryId, "H-1", ItemOwner.STATION, null);

        var corrected = service.correct(member.id(), makesANewPiece(inventoryId, old.id(), null));

        assertEquals(member.id(), corrected.assignedTo(), "the member holds the piece they actually have");
        assertNull(reload(old.id()).assignedTo(), "and no longer the one they never had");
        assertEquals(ItemCustody.WITH_OWNER, reload(old.id()).custody(), "which is back in the station's own store");
    }

    @Test
    void theAssociationsPieceGoesHomeWhenTheAssociationIsHere() {
        var home = stationRepo.create("Träger Korrektur");
        var cluster = clusterRepo.create("Kreisverband Korrektur", null, home.id());
        stationRepo.setCluster(station.id(), cluster.id());
        int inventoryId = inventory("Einsatzjacken", InventoryType.EXTERNAL);
        var old = held(inventoryId, "E-1", ItemOwner.CLUSTER, cluster.id());

        service.correct(member.id(), makesANewPiece(inventoryId, old.id(), null));

        var released = reload(old.id());
        assertEquals(ItemCustody.WITH_OWNER, released.custody(), "the association has it, not the station");
        assertNull(released.custodyStationId(), "so no station stands between them and it");
        assertNull(released.containerId(), "and it lies on none of the station's shelves");

        stationRepo.setCluster(station.id(), null);
        clusterRepo.delete(cluster.id());
        stationRepo.delete(home.id());
    }

    /**
     * The piece that went home stops being the station's stock, and the station's own does not.
     *
     * <p>Both rest with their owner after a correction and both keep their row, so the two look alike
     * from the row alone. Who the owner is decides which of them the station still has, and reading
     * that off the custody without the owner is what left a radio the association took back lying in
     * the station's list.
     */
    @Test
    void whatWentHomeLeavesTheStationsStockAndTheStationsOwnStaysInIt() {
        var home = stationRepo.create("Träger Bestand");
        var cluster = clusterRepo.create("Kreisverband Bestand", null, home.id());
        stationRepo.setCluster(station.id(), cluster.id());
        int inventoryId = inventory("Funkgeräte", InventoryType.MIXED);
        var theirs = held(inventoryId, "F-1", ItemOwner.CLUSTER, cluster.id());
        var ours = held(inventoryId, "F-2", ItemOwner.STATION, null);

        service.correct(member.id(), makesANewPiece(inventoryId, theirs.id(), ItemOwner.STATION));
        service.correct(member.id(), makesANewPiece(inventoryId, ours.id(), ItemOwner.STATION));

        var stock = inventoryRepo.findStock(inventoryId);
        assertTrue(
                stock.stream().noneMatch(item -> item.id() == theirs.id()),
                "what the association took back is no longer the station's to count");
        assertTrue(
                stock.stream().anyMatch(item -> item.id() == ours.id()),
                "what the station owns is in its own store and stays in the list");

        var summary = inventoryRepo.findSummariesByStation(station.id()).stream()
                .filter(entry -> entry.id() == inventoryId)
                .findFirst()
                .orElseThrow();
        assertEquals(stock.size(), summary.itemCount(), "and the figure beside the inventory says the same");

        stationRepo.setCluster(station.id(), null);
        clusterRepo.delete(cluster.id());
        stationRepo.delete(home.id());
    }

    /**
     * Gear kept for a body that does not use Ember has no store to go back to. Leaving the row behind
     * would leave a piece nobody owns and nobody can ever tidy up, and the correction says exactly that
     * the member never held it.
     */
    @Test
    void theAssociationsPieceDisappearsWhenTheAssociationIsNotHere() {
        int inventoryId = inventory("Fremde Jacken", InventoryType.EXTERNAL);
        var old = held(inventoryId, "F-1", ItemOwner.CLUSTER, null);

        service.correct(member.id(), makesANewPiece(inventoryId, old.id(), null));

        assertTrue(inventoryRepo.findItemById(old.id()).isEmpty(), "the piece is gone rather than lying nowhere");
    }

    @Test
    void aMixedInventoryHasToBeToldWhoOwnsTheNewPiece() {
        int inventoryId = inventory("Gemischtes", InventoryType.MIXED);
        var old = held(inventoryId, "G-1", ItemOwner.STATION, null);

        assertThrows(
                BadRequestResponse.class,
                () -> service.correct(member.id(), makesANewPiece(inventoryId, old.id(), null)));

        var corrected = service.correct(member.id(), makesANewPiece(inventoryId, old.id(), ItemOwner.STATION));
        assertEquals(ItemOwner.STATION, corrected.ownerKind(), "and takes the owner it was told");
    }

    @Test
    void aPieceFromTheFreeStockCanBeTheCorrection() {
        int inventoryId = inventory("Stiefel", InventoryType.INTERNAL);
        var old = held(inventoryId, "S-1", ItemOwner.STATION, null);
        var spare = inventoryRepo.createItem(inventoryId, "S-2", "Stiefel", null, null);

        var corrected = service.correct(
                member.id(), new ItemCorrection(inventoryId, old.id(), spare.id(), null, null, null, null));

        assertEquals(spare.id(), corrected.id(), "the piece off the shelf is the one they hold");
        assertEquals(2, inventoryRepo.findItems(inventoryId).size(), "and nothing new was written down");
    }

    @Test
    void aPieceAlreadyWithSomebodyIsNotFreeToCorrectTo() {
        int inventoryId = inventory("Handschuhe", InventoryType.INTERNAL);
        var old = held(inventoryId, "Ha-1", ItemOwner.STATION, null);
        var theirs = held(inventoryId, "Ha-2", ItemOwner.STATION, null);

        assertThrows(
                BadRequestResponse.class,
                () -> service.correct(
                        member.id(), new ItemCorrection(inventoryId, old.id(), theirs.id(), null, null, null, null)));
    }

    @Test
    void theHistorySaysTheSpellEndedInACorrection() {
        int inventoryId = inventory("Hosen", InventoryType.INTERNAL);
        var old = held(inventoryId, "Ho-1", ItemOwner.STATION, null);

        service.correct(member.id(), makesANewPiece(inventoryId, old.id(), null));

        var spell = inventoryRepo.findHistory(old.id()).getFirst();
        assertTrue(spell.corrected(), "the spell reads as a correction");
        assertTrue(spell.returned() != null, "and as one that has ended");
    }

    @Test
    void aPieceOnNobodyElsesRecordCannotBeCorrectedAway() {
        int inventoryId = inventory("Gurte", InventoryType.INTERNAL);
        var loose = inventoryRepo.createItem(inventoryId, "Gu-1", "Gurt", null, null);

        assertThrows(
                BadRequestResponse.class,
                () -> service.correct(member.id(), makesANewPiece(inventoryId, loose.id(), null)));
    }
}
