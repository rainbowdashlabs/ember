/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.MovementAdvanced;
import dev.chojo.ember.event.events.MovementDeclined;
import dev.chojo.ember.event.events.MovementStarted;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemMovementLog;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementFlowStep;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Movements between parties: starting one, acknowledging its steps, and the three ways out of one
 * that is going nowhere.
 *
 * <p>Acknowledging a step is the whole machine. The step names the party whose turn it is, the item
 * it is about and the custody that item lands in, and this service checks the turn, applies the
 * custody through {@link ItemCustodyService} and moves on. Nothing here knows what "collect", "send"
 * or "hand over" mean, because those are labels rather than behaviour.
 */
@Singleton
public class ItemMovementService {
    private static final Logger log = LoggerFactory.getLogger(ItemMovementService.class);

    private final ItemMovementRepository movementRepository;
    private final MovementFlowService flowService;
    private final InventoryRepository inventoryRepository;
    private final ItemCustodyService custodyService;
    private final ClusterRepository clusterRepository;
    private final DomainEventBus eventBus;

    @Inject
    public ItemMovementService(
            ItemMovementRepository movementRepository,
            MovementFlowService flowService,
            InventoryRepository inventoryRepository,
            ItemCustodyService custodyService,
            ClusterRepository clusterRepository,
            DomainEventBus eventBus) {
        this.movementRepository = movementRepository;
        this.flowService = flowService;
        this.inventoryRepository = inventoryRepository;
        this.custodyService = custodyService;
        this.clusterRepository = clusterRepository;
        this.eventBus = eventBus;
    }

    /**
     * Who is asking, in the terms the service needs: which member they are, and which parties they
     * may act as. Which of those matters depends on the step the movement is standing on, which is
     * why the check lives here rather than on the route.
     *
     * <p>The two rights are separate because a step can be answered by the party it belongs to or
     * merely covered by somebody standing in for them, and the record has to be able to tell those
     * apart. Nobody signs in on the owner's side yet, so {@code ownerRights} is false everywhere
     * today and the station covers every owner step.
     *
     * @param memberId      the member acting
     * @param stationRights whether they may act on the station's behalf
     * @param ownerRights   whether they may act on behalf of the body above the station
     */
    public record Actor(int memberId, boolean stationRights, boolean ownerRights) {
        /**
         * Somebody acting for the station and nobody else, which is every caller today.
         *
         * @param memberId      the member acting
         * @param stationRights whether they may act on the station's behalf
         */
        public Actor(int memberId, boolean stationRights) {
            this(memberId, stationRights, false);
        }
    }

    /**
     * Starts a movement and acknowledges its first step, because starting one is that step: a member
     * announcing an exchange has announced it, and a station starting a return has decided on it.
     *
     * @param stationId    the station running the movement
     * @param purpose      what the movement is for
     * @param memberId     the member it concerns, or {@code null} for one with no member at either end
     * @param memberName   that member's name, carried to the notification rather than looked up again
     * @param outgoingItemId the item leaving, or {@code null}
     * @param inventoryId  the inventory it is about
     * @param oldSizeId    the size being replaced, or {@code null}
     * @param newSizeId    the size asked for, or {@code null}
     * @param reason       why it was started
     * @param actor        who is starting it
     * @param pickedItemId the arriving item, when the first step is the one that names it
     * @return the movement, standing on whatever step follows the first
     */
    public ItemMovement create(
            int stationId,
            MovementPurpose purpose,
            Integer memberId,
            String memberName,
            Integer outgoingItemId,
            Integer inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Actor actor,
            Integer pickedItemId) {
        ItemOwner ownerKind = resolveOwner(outgoingItemId, inventoryId);
        int flowId = flowService.resolveFlow(
                stationId, inventoryId, ownerKind, resolveOwnerId(outgoingItemId, stationId), purpose);
        List<MovementFlowStep> steps = flowService.findActiveSteps(flowId);
        if (steps.isEmpty()) throw new BadRequestResponse("That flow has no steps to walk");

        MovementFlowStep first = steps.getFirst();
        ItemMovement movement = movementRepository.create(
                stationId,
                purpose,
                flowId,
                first.id(),
                memberId,
                outgoingItemId,
                inventoryId,
                oldSizeId,
                newSizeId,
                reason,
                actor.memberId());
        log.info(
                "Started {} movement {} on flow {} for {} gear (station={}, member={}, item={})",
                purpose,
                movement.id(),
                flowId,
                ownerKind,
                stationId,
                memberId,
                outgoingItemId);
        ItemMovement started = acknowledge(movement.id(), first.id(), actor, "", pickedItemId);
        eventBus.publish(new MovementStarted(
                stationId,
                started.id(),
                memberId,
                memberName,
                inventoryId,
                inventoryName(inventoryId),
                started.reason(),
                actor.memberId(),
                actorOfCurrentStep(started)));
        return started;
    }

    public Optional<ItemMovement> findById(int id) {
        return movementRepository.findById(id);
    }

    public List<ItemMovement> findByStation(int stationId) {
        return movementRepository.findByStation(stationId);
    }

    public List<ItemMovement> findByMember(int memberId) {
        return movementRepository.findByMember(memberId);
    }

    public List<ItemMovementLog> findLogs(int movementId) {
        return movementRepository.findLogs(movementId);
    }

    public int countOpenByStation(int stationId) {
        return movementRepository.countOpenByStation(stationId);
    }

    /**
     * The steps a movement walks, retired ones included so a chain that passed through one still
     * renders whole.
     *
     * @param movement the movement
     * @return its steps in order, or an empty list when its flow is gone
     */
    public List<MovementFlowStep> stepsOf(ItemMovement movement) {
        return movement.flowId() == null ? List.of() : flowService.findAllSteps(movement.flowId());
    }

    /**
     * Acknowledges the step a movement is standing on and advances it.
     *
     * @param movementId   the movement
     * @param stepId       the step being acknowledged, which must be the one it stands on
     * @param actor        who is acknowledging
     * @param note         what they wrote alongside
     * @param pickedItemId the arriving item, when this is the step that names it
     * @return the movement after the step, standing on the next one or closed
     */
    public ItemMovement acknowledge(int movementId, int stepId, Actor actor, String note, Integer pickedItemId) {
        return applyStep(movementId, stepId, actor, note, pickedItemId, false);
    }

    /**
     * Acknowledges a step on behalf of a party that could have answered and has not. The note is
     * mandatory and the log says the step was forced for good, because an unresponsive counterparty
     * must not be able to freeze an item in the post forever.
     *
     * @throws BadRequestResponse when the note is missing, or when the step is the station's own and
     *                            can simply be acknowledged
     */
    public ItemMovement force(int movementId, int stepId, Actor actor, String note, Integer pickedItemId) {
        if (note == null || note.isBlank()) {
            throw new BadRequestResponse("Forcing a step needs a note saying why");
        }
        return applyStep(movementId, stepId, actor, note, pickedItemId, true);
    }

    private ItemMovement applyStep(
            int movementId, int stepId, Actor actor, String note, Integer pickedItemId, boolean forced) {
        ItemMovement movement = requireOpen(movementId);
        if (movement.currentStepId() == null || movement.currentStepId() != stepId) {
            throw new BadRequestResponse("That is not the step this movement is standing on");
        }
        MovementFlowStep step = flowService.findAllSteps(movement.flowId()).stream()
                .filter(s -> s.id() == stepId)
                .findFirst()
                .orElseThrow(() -> new BadRequestResponse("That step is gone"));

        AckKind ackKind = forced ? AckKind.FORCED : requireTurn(movement, step, actor);
        if (forced && step.actor() == StepActor.STATION) {
            throw new BadRequestResponse("This step is the station's own: acknowledge it rather than forcing it");
        }

        Integer subjectItemId = movement.itemFor(step.subject());
        if (step.picksItem()) {
            if (pickedItemId == null) throw new BadRequestResponse("This step names the arriving item, so name it");
            movementRepository.setIncomingItem(movementId, pickedItemId);
            subjectItemId = pickedItemId;
        }

        if (subjectItemId != null) {
            custodyService.applyStepCustody(
                    subjectItemId, step.custodyAfter(), movement.memberId(), movementId, movement.stationId());
        }

        movementRepository.createLog(movementId, step.id(), step.label(), ackKind, actor.memberId(), note);

        MovementFlowStep next = nextStepAfter(movement.flowId(), step.position());
        if (next == null) {
            settleInTransit(movementRepository.findById(movementId).orElseThrow());
            movementRepository.close(movementId, MovementState.DONE, null);
            log.info("Movement {} reached the end of its flow", movementId);
        } else {
            movementRepository.moveToStep(movementId, next.id());
        }
        log.info(
                "Step '{}' of movement {} acknowledged by member {} as {}",
                step.label(),
                movementId,
                actor.memberId(),
                ackKind);
        ItemMovement walked = movementRepository.findById(movementId).orElseThrow();
        eventBus.publish(new MovementAdvanced(
                walked.stationId(),
                walked.id(),
                walked.memberId(),
                walked.inventoryId(),
                inventoryName(walked.inventoryId()),
                step.label(),
                actor.memberId(),
                actorOfCurrentStep(walked)));
        return walked;
    }

    /**
     * Refuses the step whose turn it is. The movement closes and the outgoing item goes back to
     * whoever had it before, which an owner with no replacement in stock needs.
     */
    public ItemMovement decline(int movementId, Actor actor, String reason) {
        ItemMovement movement = requireOpen(movementId);
        MovementFlowStep step = currentStep(movement);
        if (step != null) requireTurn(movement, step, actor);
        ItemMovement declined = close(movement, MovementState.DECLINED, reason);
        eventBus.publish(new MovementDeclined(
                declined.stationId(),
                declined.id(),
                declined.memberId(),
                declined.inventoryId(),
                inventoryName(declined.inventoryId()),
                reason,
                actor.memberId()));
        return declined;
    }

    /**
     * Calls off a movement that is still on the caller's side of the chain.
     */
    public ItemMovement cancel(int movementId, Actor actor, String reason) {
        ItemMovement movement = requireOpen(movementId);
        MovementFlowStep step = currentStep(movement);
        if (step != null && !mayAct(movement, step, actor)) {
            throw new ForbiddenResponse("This movement is not on your side any more");
        }
        return close(movement, MovementState.CANCELLED, reason);
    }

    /**
     * Calls off a movement because the ground it stood on is gone.
     *
     * <p>Unlike {@link #cancel(int, Actor, String)} there is nobody whose turn it is to check: a station that
     * has left its cluster cannot finish a chain the cluster was one end of, and leaving the movement open
     * would leave the item in transit to a party that is no longer there.
     *
     * @param movementId the movement
     * @param reason     what to record, for whoever reads it later
     * @return the closed movement
     */
    public ItemMovement abandon(int movementId, String reason) {
        return close(requireOpen(movementId), MovementState.CANCELLED, reason);
    }

    /**
     * Deletes a movement outright, which is what the old exchange list called cancelling.
     */
    public boolean delete(int movementId) {
        return movementRepository.delete(movementId);
    }

    private ItemMovement close(ItemMovement movement, MovementState state, String reason) {
        restoreOutgoingItem(movement);
        settleInTransit(movementRepository.findById(movement.id()).orElseThrow());
        movementRepository.close(movement.id(), state, reason);
        log.info("Movement {} closed as {} ({})", movement.id(), state, reason);
        return movementRepository.findById(movement.id()).orElseThrow();
    }

    /**
     * Puts the outgoing item back where it was before the movement started.
     *
     * <p>That place follows from the purpose rather than needing to be remembered: a movement with a
     * member is one the member's own gear set out on, so it goes back to them, and one without a
     * member set out from a store, so it goes back to the store it rests in.
     */
    private void restoreOutgoingItem(ItemMovement movement) {
        if (movement.outgoingItemId() == null) return;
        if (movement.memberId() != null) {
            custodyService.applyStepCustody(
                    movement.outgoingItemId(), ItemCustody.WITH_MEMBER, movement.memberId(), null);
        } else {
            custodyService.takeBack(movement.outgoingItemId());
        }
    }

    /**
     * Settles anything still in the post when a movement ends.
     *
     * <p>An item pointing at a finished movement while claiming to be between two parties is a state
     * nobody can act on, and it is the state a chain without the owner's steps would otherwise leave
     * behind. It settles with its owner, which is the last thing anybody honestly knows about it: the
     * station posted it, never got it back, and is not the one holding it.
     *
     * @param movement the movement as it stands at the moment it ends
     */
    private void settleInTransit(ItemMovement movement) {
        for (StepSubject subject : StepSubject.values()) {
            Integer itemId = movement.itemFor(subject);
            if (itemId == null) continue;
            inventoryRepository
                    .findItemById(itemId)
                    .filter(item -> item.custody() == ItemCustody.IN_TRANSIT)
                    .ifPresent(item -> custodyService.applyStepCustody(item.id(), ItemCustody.WITH_OWNER, null, null));
        }
    }

    /**
     * The party a movement is now waiting on, or {@code null} once the chain has ended. It is what
     * decides who hears about the step that was just walked.
     */
    private StepActor actorOfCurrentStep(ItemMovement movement) {
        MovementFlowStep step = currentStep(movement);
        return step != null ? step.actor() : null;
    }

    private String inventoryName(Integer inventoryId) {
        if (inventoryId == null) return "";
        return inventoryRepository.findById(inventoryId).map(Inventory::name).orElse("");
    }

    private ItemMovement requireOpen(int movementId) {
        ItemMovement movement =
                movementRepository.findById(movementId).orElseThrow(() -> new BadRequestResponse("No such movement"));
        if (movement.state().closed()) {
            throw new BadRequestResponse("This movement is already %s".formatted(movement.state()));
        }
        if (movement.flowId() == null) {
            throw new BadRequestResponse("The flow this movement walked is gone");
        }
        return movement;
    }

    private MovementFlowStep currentStep(ItemMovement movement) {
        if (movement.currentStepId() == null || movement.flowId() == null) return null;
        return flowService.findAllSteps(movement.flowId()).stream()
                .filter(s -> s.id() == movement.currentStepId())
                .findFirst()
                .orElse(null);
    }

    private MovementFlowStep nextStepAfter(int flowId, int position) {
        return flowService.findActiveSteps(flowId).stream()
                .filter(s -> s.position() > position)
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks that it is the caller's turn and reports how the acknowledgement should be recorded.
     *
     * <p>Confirmed means the party the step belongs to said so itself, so it follows from who
     * pressed the button and never from whether the item names a body above the station. Reading it
     * off the item would stamp an owner's confirmation on a click the station made, which is the one
     * thing this distinction exists to prevent.
     *
     * <p>Nobody signs in on the owner's side yet, so every owner step is covered by the station and
     * asserted. The day a cluster's people can act, they arrive here carrying owner rights and the
     * same steps start reading as confirmed with nothing else changing.
     */
    private AckKind requireTurn(ItemMovement movement, MovementFlowStep step, Actor actor) {
        if (!mayAct(movement, step, actor)) {
            throw new ForbiddenResponse("This step belongs to the %s".formatted(step.actor()));
        }
        return step.actor() == StepActor.OWNER && !actor.ownerRights() ? AckKind.ASSERTED : AckKind.CONFIRMED;
    }

    private boolean mayAct(ItemMovement movement, MovementFlowStep step, Actor actor) {
        return switch (step.actor()) {
            case MEMBER ->
                movement.memberId() != null && (movement.memberId() == actor.memberId() || actor.stationRights());
            case STATION -> actor.stationRights();
            // The owner answers for itself where it can, and the station covers it where it cannot
            case OWNER -> actor.ownerRights() || actor.stationRights();
        };
    }

    /**
     * Who owns the gear a movement is about. The item says so when there is one; an issue that has
     * not named its item yet falls back to what the inventory may hold.
     */
    private ItemOwner resolveOwner(Integer itemId, Integer inventoryId) {
        if (itemId != null) {
            Optional<InventoryItem> item = inventoryRepository.findItemById(itemId);
            if (item.isPresent()) return item.get().ownerKind();
        }
        if (inventoryId == null) return ItemOwner.STATION;
        return inventoryRepository
                .findById(inventoryId)
                .map(inv -> inv.inventoryType() == InventoryType.INTERNAL ? ItemOwner.STATION : ItemOwner.CLUSTER)
                .orElse(ItemOwner.STATION);
    }

    /**
     * Which cluster owns the gear, when a cluster does.
     *
     * <p>Read off the item when there is one, because that is where ownership actually lives. An issue that
     * has not named its item yet falls back to the cluster the station answers to, which is the only cluster
     * whose gear could be arriving.
     *
     * @param itemId    the item, when the movement has one
     * @param stationId the station running the movement
     * @return the owning cluster, or {@code null} when no cluster owns it
     */
    private Integer resolveOwnerId(Integer itemId, int stationId) {
        if (itemId != null) {
            Optional<InventoryItem> item = inventoryRepository.findItemById(itemId);
            if (item.isPresent()) return item.get().ownerClusterId();
        }
        return clusterRepository.findByStation(stationId).map(Cluster::id).orElse(null);
    }

    /**
     * The custody values a flow step may name. A step moves gear between parties, so it can put an
     * item with its owner, at a station, with a member or in the post, and nothing else: lending is
     * its own flow and losing something is not a step anybody takes.
     *
     * @param custody the custody a step wants to name
     * @return whether a step may name it
     */
    public static boolean legalStepCustody(ItemCustody custody) {
        return custody == ItemCustody.WITH_OWNER
                || custody == ItemCustody.AT_STATION
                || custody == ItemCustody.WITH_MEMBER
                || custody == ItemCustody.IN_TRANSIT;
    }

    /**
     * Whether a step is about the item that has not been named yet, which is how the caller knows to
     * ask for one.
     */
    public static boolean namesIncomingItem(MovementFlowStep step) {
        return step.subject() == StepSubject.INCOMING && step.picksItem();
    }
}
