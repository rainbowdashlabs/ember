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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sending a batch of the association's gear to one of its stations.
 */
class ClusterDispatchServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private Cluster freshCluster() {
        var cluster = clusterService.create("Kreisverband Ausgabe " + NAMES.incrementAndGet(), null);
        clusterInventoryService.setUsesInventory(cluster.id(), true);
        return cluster;
    }

    private Station stationOf(Cluster cluster) {
        return clusterService.createStation(cluster.id(), "Wache Ausgabe " + NAMES.incrementAndGet());
    }

    /** The chain that puts a consignment in the post and lets the station confirm it once. */
    private void issueFlow(Cluster cluster) {
        var flow = clusterInventoryService.createFlow(cluster.id(), "Ausgabe", MovementPurpose.ISSUE);
        movementFlowService.addStep(
                flow.id(), "Verband schickt", StepActor.OWNER, StepSubject.INCOMING, ItemCustody.IN_TRANSIT, true);
        movementFlowService.addStep(
                flow.id(), "Wache nimmt an", StepActor.STATION, StepSubject.INCOMING, ItemCustody.AT_STATION, false);
    }

    /**
     * Pieces resting in the association's own store, which is what there is to send.
     *
     * <p>New gear rests where its holder is, and a station recording gear for the body above it is holding
     * it. On the association's own station the association is both, so the stock is put back into its store
     * the same way the seeder does it.
     */
    private List<Integer> stock(Cluster cluster, int howMany) {
        var inventory = inventoryRepo.create(
                cluster.homeStationId(), "Lager " + NAMES.incrementAndGet(), InventoryType.EXTERNAL, false);
        return IntStream.range(0, howMany)
                .mapToObj(i -> {
                    int id = inventoryRepo
                            .createItem(
                                    inventory.id(),
                                    "AG-" + NAMES.incrementAndGet(),
                                    "Jacke",
                                    null,
                                    null,
                                    ItemOwner.CLUSTER,
                                    cluster.id())
                            .id();
                    itemCustodyService.returnToOwner(id);
                    return id;
                })
                .toList();
    }

    private ItemMovementService.Actor owner() {
        return new ItemMovementService.Actor(0, false, true);
    }

    @Test
    void oneMovementCarriesTheWholeConsignment() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        issueFlow(cluster);
        var items = stock(cluster, 3);

        var movement =
                clusterDispatchService.dispatch(cluster.id(), station.uid(), items, "Für die neue Gruppe", owner());

        // Everything is in the post, not just the piece the movement names
        for (int id : items) {
            assertEquals(
                    ItemCustody.IN_TRANSIT,
                    inventoryRepo.findItemById(id).orElseThrow().custody(),
                    "every piece of the consignment left together");
        }
        assertEquals(
                2,
                itemMovementService.carried(movement.id(), StepSubject.INCOMING).size());

        // The station confirms one arrival for the lot
        var steps = itemMovementService.stepsOf(movement);
        var arrival = steps.stream()
                .filter(s -> movement.currentStepId() != null && s.id() == movement.currentStepId())
                .findFirst()
                .orElseThrow();
        itemMovementService.acknowledge(movement.id(), arrival.id(), new ItemMovementService.Actor(0, true), "", null);
        for (int id : items) {
            assertEquals(
                    ItemCustody.AT_STATION,
                    inventoryRepo.findItemById(id).orElseThrow().custody(),
                    "and arrived together");
        }

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void anAssociationSendsOnlyItsOwnGearAndOnlyToItsOwnStations() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        issueFlow(cluster);
        var items = stock(cluster, 1);

        var stranger = freshCluster();
        var strangersStation = stationOf(stranger);
        assertThrows(
                BadRequestResponse.class,
                () -> clusterDispatchService.dispatch(
                        cluster.id(), strangersStation.uid(), items, "Falsche Wache", owner()),
                "a station that answers to somebody else is nobody to send to");

        var elsewhere = stock(stranger, 1);
        assertThrows(
                BadRequestResponse.class,
                () -> clusterDispatchService.dispatch(cluster.id(), station.uid(), elsewhere, "Fremd", owner()),
                "gear another body owns is not this one's to send");

        clusterService.releaseStation(stranger.id(), strangersStation.id());
        stationRepo.delete(strangersStation.id());
        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void gearThatIsNotInTheStoreCannotBeSent() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        issueFlow(cluster);
        var items = stock(cluster, 2);

        assertTrue(
                clusterDispatchService.sendable(cluster.id()).stream().anyMatch(item -> item.id() == items.getFirst()));
        clusterDispatchService.dispatch(cluster.id(), station.uid(), List.of(items.getFirst()), "Weg damit", owner());

        assertTrue(
                clusterDispatchService.sendable(cluster.id()).stream().noneMatch(item -> item.id() == items.getFirst()),
                "a piece already in the post is not in the store");
        assertThrows(
                BadRequestResponse.class,
                () -> clusterDispatchService.dispatch(
                        cluster.id(), station.uid(), List.of(items.getFirst()), "Nochmal", owner()),
                "and cannot be sent a second time");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void anAssociationWithNoChainOfItsOwnIsToldSoRatherThanRefusedAStep() {
        var cluster = freshCluster();
        var station = stationOf(cluster);
        var items = stock(cluster, 1);

        var thrown = assertThrows(
                BadRequestResponse.class,
                () -> clusterDispatchService.dispatch(cluster.id(), station.uid(), items, "Ohne Ablauf", owner()));
        assertTrue(thrown.getMessage().contains("chain"), "the message names what is missing");

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
    }
}
