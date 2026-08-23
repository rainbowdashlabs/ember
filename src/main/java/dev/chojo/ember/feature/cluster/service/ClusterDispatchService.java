/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.inventory.service.MovementFlowService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Sending gear from the association's own store out to one of its stations.
 *
 * <p>The everyday case is many pieces at once, so one movement carries the lot and the station confirms one
 * arrival rather than twenty. That is the only way gear leaves the store: an item's own page carries no
 * issue action, so there is one thing to learn and one place a movement starts.
 */
@Singleton
public class ClusterDispatchService {
    private static final Logger log = LoggerFactory.getLogger(ClusterDispatchService.class);

    private final ClusterRepository clusterRepository;
    private final StationRepository stationRepository;
    private final InventoryRepository inventoryRepository;
    private final ItemMovementService movementService;
    private final MovementFlowService flowService;

    @Inject
    public ClusterDispatchService(
            ClusterRepository clusterRepository,
            StationRepository stationRepository,
            InventoryRepository inventoryRepository,
            ItemMovementService movementService,
            MovementFlowService flowService) {
        this.clusterRepository = clusterRepository;
        this.stationRepository = stationRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementService = movementService;
        this.flowService = flowService;
    }

    /**
     * The gear resting in the association's own store, which is what there is to send.
     *
     * <p>Anything already out at a station, on its way somewhere or missing is not in the store, however
     * much the association owns it.
     *
     * @param clusterId the association
     * @return its free stock
     */
    public List<InventoryItem> sendable(int clusterId) {
        clusterRepository.findById(clusterId).orElseThrow(() -> new BadRequestResponse("No such body"));
        return inventoryRepository.findItemsOwnedByCluster(clusterId).stream()
                .filter(item -> item.custody() == ItemCustody.WITH_OWNER)
                .toList();
    }

    /**
     * Refuses a consignment the association has no chain to walk.
     *
     * <p>Sending gear out starts on the association's own step: it posts, and the station confirms what
     * arrived. A station's own preset for gear from a body above it begins at the station instead, which
     * would have the consignment arrive the moment it was sent and never be in the post at all. So the
     * association needs a chain of its own before it can send anything, and it says so plainly rather than
     * refusing the first step for a reason nobody could act on.
     *
     * @param clusterId the association
     * @throws BadRequestResponse when it has defined no chain for sending gear out
     */
    private void requireOwnChain(int clusterId) {
        boolean hasIssueFlow = flowService.findClusterFlows(clusterId).stream()
                .anyMatch(flow -> flow.purpose() == MovementPurpose.ISSUE);
        if (!hasIssueFlow) {
            throw new BadRequestResponse(
                    "This body has no chain for sending gear out yet. Add one under its inventory settings.");
        }
    }

    /**
     * What an inventory is called, so a piece on the dispatch screen says which one it comes out of.
     *
     * @param inventoryId the inventory
     * @return its name, or an empty string when it is gone
     */
    public String inventoryName(int inventoryId) {
        return inventoryRepository.findById(inventoryId).map(Inventory::name).orElse("");
    }

    /**
     * Sends a batch of the association's gear to one of its stations.
     *
     * @param clusterId  the association sending
     * @param stationUid the station receiving, which has to be one of its own
     * @param itemIds    the pieces to send, all resting in the association's store
     * @param reason     what the association wrote about the consignment
     * @param actor      who is sending, acting for the owner
     * @return the movement carrying the lot
     * @throws BadRequestResponse when the station is not the association's, when a piece is not its to send,
     *                            or when it has no chain for sending gear out
     */
    public ItemMovement dispatch(
            int clusterId, UUID stationUid, List<Integer> itemIds, String reason, ItemMovementService.Actor actor) {
        if (itemIds.isEmpty()) throw new BadRequestResponse("Pick at least one piece to send");
        Station station =
                stationRepository.findByUid(stationUid).orElseThrow(() -> new BadRequestResponse("No such station"));
        if (station.clusterId() == null || station.clusterId() != clusterId) {
            throw new BadRequestResponse("That station does not answer to this body");
        }

        List<InventoryItem> sending = sendable(clusterId).stream()
                .filter(item -> itemIds.contains(item.id()))
                .toList();
        if (sending.size() != itemIds.size()) {
            throw new BadRequestResponse("Some of that gear is not resting in the store and cannot be sent");
        }
        requireOwnChain(clusterId);

        InventoryItem named = sending.getFirst();
        ItemMovement movement = movementService.create(
                station.id(),
                MovementPurpose.ISSUE,
                null,
                null,
                null,
                named.inventoryId(),
                null,
                null,
                reason != null ? reason : "",
                actor,
                named.id(),
                false,
                sending.stream().skip(1).map(InventoryItem::id).toList());
        log.info(
                "Cluster {} dispatched {} piece(s) to station {} as movement {}",
                clusterId,
                sending.size(),
                station.id(),
                movement.id());
        return movement;
    }
}
