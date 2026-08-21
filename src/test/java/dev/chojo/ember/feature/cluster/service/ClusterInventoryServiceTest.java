/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The seams the inventory rework left open for the cluster, now closed.
 */
class ClusterInventoryServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private Cluster freshCluster() {
        return clusterService.create("Kreisverband Gerät " + NAMES.incrementAndGet(), null);
    }

    private Station stationOf(Cluster cluster) {
        return clusterService.createStation(cluster.id(), "Wache Gerät " + NAMES.incrementAndGet());
    }

    /** A cluster-owned item sitting in an inventory at one of its stations. */
    private int clusterItemAt(Cluster cluster, Station station) {
        var inventory = inventoryRepo.create(
                station.id(), "Einsatzkleidung " + NAMES.incrementAndGet(), InventoryType.EXTERNAL, false);
        return inventoryRepo
                .createItem(
                        inventory.id(),
                        "HK-" + NAMES.incrementAndGet(),
                        "Helm",
                        null,
                        null,
                        ItemOwner.CLUSTER,
                        cluster.id())
                .id();
    }

    @Test
    void aClusterSeesEverythingItOwnsWhereverItIs() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        int itemId = clusterItemAt(cluster, station);

        var items = clusterInventoryService.findItems(cluster.id());

        assertEquals(1, items.size());
        assertEquals(itemId, items.getFirst().itemId());
        assertEquals(ItemCustody.AT_STATION, items.getFirst().custody());
        assertEquals(station.name(), items.getFirst().stationName());

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aStationCannotRenameOrDeleteGearItDoesNotOwn() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        int itemId = clusterItemAt(cluster, station);

        assertThrows(
                ForbiddenResponse.class,
                () -> inventoryService.updateItem(itemId, "HK-neu", "Anderer Helm", null, null));
        assertThrows(ForbiddenResponse.class, () -> inventoryService.deleteItem(itemId));

        // What it does own it may still change
        var own = inventoryRepo.create(station.id(), "Eigenes", InventoryType.INTERNAL, false);
        var ownItem = inventoryRepo.createItem(own.id(), "EG-1", "Eigener Helm", null, null);
        assertTrue(inventoryService
                .updateItem(ownItem.id(), "EG-1", "Umbenannt", null, null)
                .isPresent());

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aClustersOwnChainIsWalkedOnlyWhenItKeepsItsGearHere() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        var clusterFlow = clusterInventoryService.createFlow(cluster.id(), "Verbandstausch", MovementPurpose.EXCHANGE);

        // The cluster is here but does not keep its gear here, so its stations behave as if it were not
        int stationFlow = movementFlowService.resolveFlow(
                station.id(), null, ItemOwner.CLUSTER, cluster.id(), MovementPurpose.EXCHANGE);
        assertNotEquals(clusterFlow.id(), stationFlow, "an owner that cannot answer sets no terms");

        clusterInventoryService.setUsesInventory(cluster.id(), true);
        assertEquals(
                clusterFlow.id(),
                movementFlowService.resolveFlow(
                        station.id(), null, ItemOwner.CLUSTER, cluster.id(), MovementPurpose.EXCHANGE),
                "an owner that is present sets its own terms");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void gearWithNoClusterBehindItFallsThroughToTheStation() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        clusterInventoryService.createFlow(cluster.id(), "Verbandstausch", MovementPurpose.EXCHANGE);
        clusterInventoryService.setUsesInventory(cluster.id(), true);

        // Station-owned gear is the station's business whatever the cluster keeps
        int flow = movementFlowService.resolveFlow(
                station.id(), null, ItemOwner.STATION, cluster.id(), MovementPurpose.EXCHANGE);
        assertNotEquals(
                clusterInventoryService.findFlows(cluster.id()).getFirst().id(), flow);

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void theQueueHoldsWhatIsStandingOnAStepOnlyTheClusterCanAnswer() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        int itemId = clusterItemAt(cluster, station);
        int memberId = memberAt(station);
        assertTrue(clusterInventoryService.findQueue(cluster.id()).isEmpty(), "nothing waits before anything starts");

        // A chain whose second step only the owner can press
        var flow = clusterInventoryService.createFlow(cluster.id(), "Rückgabe", MovementPurpose.RETURN);
        movementFlowService.addStep(
                flow.id(), "Wache schickt", StepActor.STATION, StepSubject.OUTGOING, ItemCustody.IN_TRANSIT, false);
        movementFlowService.addStep(
                flow.id(), "Verband nimmt an", StepActor.OWNER, StepSubject.OUTGOING, ItemCustody.WITH_OWNER, false);
        clusterInventoryService.setUsesInventory(cluster.id(), true);

        itemMovementService.create(
                station.id(),
                MovementPurpose.RETURN,
                null,
                null,
                itemId,
                inventoryRepo.findItemById(itemId).orElseThrow().inventoryId(),
                null,
                null,
                "Zurück damit",
                new ItemMovementService.Actor(memberId, true),
                null);

        var queue = clusterInventoryService.findQueue(cluster.id());
        assertEquals(1, queue.size(), "the movement stopped on the cluster's own step");
        assertEquals("Verband nimmt an", queue.getFirst().stepLabel());
        assertEquals(station.name(), queue.getFirst().stationName());
        assertEquals("Helm", queue.getFirst().itemName());

        // And another cluster sees nothing of it
        assertTrue(clusterInventoryService.findQueue(freshCluster().id()).isEmpty());
    }

    @Test
    void gearAStationAlreadyKeptForTheBodyAboveItFindsItsOwnerOnJoining() {
        int n = NAMES.incrementAndGet();
        var standalone = stationRepo.create("Wache ohne Verband " + n);
        var inventory = inventoryRepo.create(standalone.id(), "Einsatzkleidung " + n, InventoryType.EXTERNAL, false);
        // Recorded as the body above the station owning it, with no body anybody could ask
        int adopted = inventoryRepo
                .createItem(inventory.id(), "ADOPT-" + n, "Helm", null, null, ItemOwner.CLUSTER, null)
                .id();
        int itsOwn = inventoryRepo
                .createItem(inventory.id(), "OWN-" + n, "Funkgerät", null, null, ItemOwner.STATION, null)
                .id();

        var cluster = freshCluster();
        clusterService.joinStation(cluster.id(), standalone.id());

        assertEquals(
                cluster.id(),
                inventoryRepo.findItemById(adopted).orElseThrow().ownerClusterId(),
                "The body the gear already belonged to can now be pointed at");
        assertNull(
                inventoryRepo.findItemById(itsOwn).orElseThrow().ownerClusterId(),
                "The station's own gear is nobody else's");
        assertEquals(
                ItemOwner.STATION,
                inventoryRepo.findItemById(itsOwn).orElseThrow().ownerKind(),
                "Joining a cluster does not hand it anything");
    }

    @Test
    void gearCannotBeRecordedAsBelongingToSomebodyElsesAssociation() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        var stranger = freshCluster();
        int n = NAMES.incrementAndGet();
        var inventory = inventoryRepo.create(station.id(), "Einsatzkleidung " + n, InventoryType.EXTERNAL, false);

        assertThrows(
                BadRequestResponse.class,
                () -> inventoryService.createItem(
                        inventory.id(), "STRANGE-" + n, "Helm", null, null, ItemOwner.CLUSTER, stranger.id()),
                "A station answers to one body, so naming another one is a mistake rather than a choice");

        // Its own is fine, and so is an owner that does not run here at all
        assertNotNull(inventoryService.createItem(
                inventory.id(), "MINE-" + n, "Helm", null, null, ItemOwner.CLUSTER, cluster.id()));
        assertNotNull(
                inventoryService.createItem(inventory.id(), "OFF-" + n, "Helm", null, null, ItemOwner.CLUSTER, null));
    }

    /** A member at the station, so a movement has somebody to have been started by. */
    private int memberAt(Station station) {
        int n = NAMES.incrementAndGet();
        var account = accountRepo.create("clustergear" + n + "@test.com", "Ger", "Aet" + n);
        return stationMemberRepo.create(station.id(), account.id()).id();
    }
}
