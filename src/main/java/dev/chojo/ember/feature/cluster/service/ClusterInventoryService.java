/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.cluster.entity.LossReportRequirement;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.MovementFlow;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository;
import dev.chojo.ember.feature.inventory.service.MovementFlowService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

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
        var owned = inventoryRepository.findItemsOwnedByCluster(clusterId);
        Map<Integer, String> sizeLabels = inventoryRepository
                .findSizesByIds(owned.stream()
                        .map(InventoryItem::sizeId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(InventorySize::id, InventorySize::label, (first, second) -> first));

        List<ClusterItem> items = new ArrayList<>();
        for (var item : owned) {
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
                    holderName(item.assignedTo()),
                    item.sizeId(),
                    item.sizeId() == null ? null : sizeLabels.get(item.sizeId())));
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

    public List<MovementFlowStep> findSteps(int clusterId, int flowId) {
        requireOwnFlow(clusterId, flowId);
        return flowService.findAllSteps(flowId);
    }

    /**
     * Adds a chain the cluster's gear walks.
     *
     * <p>One chain per purpose, refused rather than accepted and ignored. When gear moves, the oldest
     * unarchived chain for that purpose wins and the rest are never reached, so an association that made
     * a second one because the first was wrong would have kept walking the first forever without
     * anything saying so. Archive the one in the way, then make the new one.
     *
     * @param clusterId the cluster
     * @param name      what it is called
     * @param purpose   what it is for
     * @return the flow
     */
    public MovementFlow createFlow(int clusterId, String name, MovementPurpose purpose) {
        requireCluster(clusterId);
        flowService.findClusterFlows(clusterId).stream()
                .filter(flow -> flow.purpose() == purpose)
                .findFirst()
                .ifPresent(flow -> {
                    throw new BadRequestResponse("'%s' already walks every %s. Archive it before adding another."
                            .formatted(flow.name(), purpose.name()));
                });
        return flowService.createClusterFlow(clusterId, name, purpose);
    }

    /**
     * @param clusterId the cluster
     * @param flowId    the chain
     * @param name      what it is called now
     */
    public void renameFlow(int clusterId, int flowId, String name) {
        requireOwnFlow(clusterId, flowId);
        flowService.renameFlow(flowId, name);
    }

    /**
     * Retires a chain, which is the only way out of one that turned out wrong.
     *
     * @param clusterId the cluster
     * @param flowId    the chain
     */
    public void archiveFlow(int clusterId, int flowId) {
        requireOwnFlow(clusterId, flowId);
        flowService.archiveFlow(flowId);
        log.info("Cluster {} retired movement flow {}", clusterId, flowId);
    }

    /**
     * Adds a step at the end of one of the cluster's chains.
     *
     * @param clusterId    the cluster
     * @param flowId       the chain
     * @param label        what the button says
     * @param actor        who presses it
     * @param subject      what it is about
     * @param custodyAfter where the gear is once it is pressed
     * @param picksItem    whether this is the step that names the piece
     * @return the step
     */
    public MovementFlowStep addStep(
            int clusterId,
            int flowId,
            String label,
            StepActor actor,
            StepSubject subject,
            ItemCustody custodyAfter,
            boolean picksItem) {
        requireOwnFlow(clusterId, flowId);
        return flowService.addStep(flowId, label, actor, subject, custodyAfter, picksItem);
    }

    /**
     * @param clusterId the cluster
     * @param stepId    the step
     */
    public void updateStep(
            int clusterId,
            int stepId,
            String label,
            StepActor actor,
            StepSubject subject,
            ItemCustody custodyAfter,
            boolean picksItem) {
        requireOwnStep(clusterId, stepId);
        flowService.updateStep(stepId, label, actor, subject, custodyAfter, picksItem);
    }

    /**
     * @param clusterId the cluster
     * @param stepId    the step
     */
    public void archiveStep(int clusterId, int stepId) {
        requireOwnStep(clusterId, stepId);
        flowService.archiveStep(stepId);
    }

    /** A chain of another association, or of a station, is not this one's to change. */
    private void requireOwnFlow(int clusterId, int flowId) {
        MovementFlow flow = flowService.findFlow(flowId).orElseThrow(() -> new NotFoundResponse("No such flow"));
        if (flow.clusterId() == null || flow.clusterId() != clusterId) {
            throw new NotFoundResponse("No such flow");
        }
    }

    private void requireOwnStep(int clusterId, int stepId) {
        MovementFlowStep step = flowService.findStep(stepId).orElseThrow(() -> new NotFoundResponse("No such step"));
        requireOwnFlow(clusterId, step.flowId());
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
     * Sets what a station has to bring when it reports a piece of this cluster's gear missing.
     *
     * <p>The loss is not the cluster's to accept or refuse, so nothing here is about that. It is about what
     * the cluster wants to read before it decides whether to send another one.
     *
     * @param clusterId the cluster
     * @param requires  nothing, a note, or a document as well
     */
    public void setLossReportRequires(int clusterId, LossReportRequirement requires) {
        requireCluster(clusterId);
        clusterRepository.setLossReportRequires(clusterId, requires);
        log.info("Cluster {} asks for {} with a loss report", clusterId, requires);
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
            String holderName,
            Integer sizeId,
            String sizeLabel) {}

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
