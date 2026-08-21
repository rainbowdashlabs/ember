/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository;
import dev.chojo.ember.feature.inventory.service.MovementFlowService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The cluster's own view of its gear.
 *
 * <p>Nothing here stores anything new. The stock is ordinary inventory on the cluster's own station, and the
 * movements are ordinary movements at the stations running them. What the cluster needs is the same rows
 * asked a different question: not "what does this station hold" but "where is my gear" and "what is waiting
 * for me".
 */
@Singleton
public class ClusterInventoryService {
    private static final Logger log = LoggerFactory.getLogger(ClusterInventoryService.class);

    private final ClusterRepository clusterRepository;
    private final InventoryRepository inventoryRepository;
    private final ItemMovementRepository movementRepository;
    private final MovementFlowService flowService;
    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;
    private final MemberNameResolver nameResolver;

    @Inject
    public ClusterInventoryService(
            ClusterRepository clusterRepository,
            InventoryRepository inventoryRepository,
            ItemMovementRepository movementRepository,
            MovementFlowService flowService,
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            MemberNameResolver nameResolver) {
        this.clusterRepository = clusterRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.flowService = flowService;
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
        this.nameResolver = nameResolver;
    }

    /**
     * Everything the cluster owns, wherever it is.
     *
     * <p>One list rather than "in store" and "out at stations" as two, because they are the same gear and the
     * question a person actually has is where a particular jacket got to.
     *
     * @param clusterId the cluster
     * @return its items, each saying where it is
     */
    public List<ClusterItem> findItems(int clusterId) {
        List<ClusterItem> items = new ArrayList<>();
        for (var item : inventoryRepository.findItemsOwnedByCluster(clusterId)) {
            UUID stationUid = null;
            String stationName = null;
            if (item.custodyStationId() != null) {
                var station = stationRepository.findById(item.custodyStationId());
                stationUid = station.map(s -> s.uid()).orElse(null);
                stationName = station.map(s -> s.name()).orElse(null);
            }
            items.add(new ClusterItem(
                    item.id(),
                    item.internalId(),
                    item.name(),
                    item.custody(),
                    stationUid,
                    stationName,
                    holderName(item.assignedTo())));
        }
        return items;
    }

    /**
     * The movements standing on a step only the cluster can answer.
     *
     * @param clusterId the cluster
     * @return its queue, oldest first
     */
    public List<QueueEntry> findQueue(int clusterId) {
        List<QueueEntry> queue = new ArrayList<>();
        for (ItemMovement movement : movementRepository.findWaitingForCluster(clusterId)) {
            var station = stationRepository.findById(movement.stationId());
            String stepLabel = movement.currentStepId() == null
                    ? null
                    : flowService
                            .findStep(movement.currentStepId())
                            .map(step -> step.label())
                            .orElse(null);
            String itemName = movement.outgoingItemId() == null
                    ? null
                    : inventoryRepository
                            .findItemById(movement.outgoingItemId())
                            .map(item -> item.name())
                            .orElse(null);
            queue.add(new QueueEntry(
                    movement.id(),
                    movement.purpose(),
                    station.map(s -> s.uid()).orElse(null),
                    station.map(s -> s.name()).orElse(null),
                    stepLabel,
                    itemName,
                    movement.createdAt()));
        }
        return queue;
    }

    public List<MovementFlow> findFlows(int clusterId) {
        return flowService.findClusterFlows(clusterId);
    }

    /**
     * Adds a chain the cluster's gear walks.
     *
     * @param clusterId the cluster
     * @param name      what it is called
     * @param purpose   what it is for
     * @return the flow
     */
    public MovementFlow createFlow(int clusterId, String name, MovementPurpose purpose) {
        requireCluster(clusterId);
        return flowService.createClusterFlow(clusterId, name, purpose);
    }

    /**
     * Says whether the cluster keeps its gear here at all.
     *
     * <p>Switching it off leaves everything already recorded alone. What changes is what happens next: a
     * movement started afterwards falls through to the station's own chain, which carries no owner steps,
     * because a chain stopping on a button nobody will press is worse than no chain of the owner's at all.
     *
     * @param clusterId     the cluster
     * @param usesInventory whether it keeps its gear here
     */
    public void setUsesInventory(int clusterId, boolean usesInventory) {
        requireCluster(clusterId);
        clusterRepository.setUsesInventory(clusterId, usesInventory);
        log.info("Cluster {} keeps its gear here: {}", clusterId, usesInventory);
    }

    /**
     * The name of whoever is wearing it, when somebody is.
     *
     * @param memberId the member holding it, or {@code null}
     * @return their name, or {@code null} when nobody holds it
     */
    /**
     * Who is wearing it.
     *
     * <p>Through the resolver rather than off the membership row: the name cached there is the one kept
     * for somebody who has left, and a member still at their station carries no name of their own, only an
     * account that does.
     */
    private String holderName(Integer memberId) {
        if (memberId == null) return null;
        String name = nameResolver.resolveLocal(memberId);
        return name != null && !name.isBlank() ? name : null;
    }

    private void requireCluster(int clusterId) {
        if (clusterRepository.findById(clusterId).isEmpty()) throw new NotFoundResponse("No such cluster");
    }

    /**
     * @param stationUid the station holding it, or {@code null} when it rests in the cluster's own store
     * @param holderName the member wearing it, or {@code null}
     */
    public record ClusterItem(
            int itemId,
            String internalId,
            String name,
            ItemCustody custody,
            UUID stationUid,
            String stationName,
            String holderName) {}

    /**
     * @param stepLabel what the cluster is being asked to confirm
     */
    public record QueueEntry(
            int movementId,
            MovementPurpose purpose,
            UUID stationUid,
            String stationName,
            String stepLabel,
            String itemName,
            Instant createdAt) {}
}
