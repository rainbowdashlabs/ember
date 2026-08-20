/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.AckKind;
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

    @Inject
    public ItemMovementService(
            ItemMovementRepository movementRepository,
            MovementFlowService flowService,
            InventoryRepository inventoryRepository,
            ItemCustodyService custodyService) {
        this.movementRepository = movementRepository;
        this.flowService = flowService;
        this.inventoryRepository = inventoryRepository;
        this.custodyService = custodyService;
    }

    /**
     * Who is asking, in the only two terms the service needs: which member they are, and whether
     * they may act for the station. Which of those matters depends on the step the movement is
     * standing on, which is why the check lives here rather than on the route.
     *
     * @param memberId      the member acting
     * @param stationRights whether they may act on the station's behalf
     */
    public record Actor(int memberId, boolean stationRights) {}

    /**
     * Starts a movement and acknowledges its first step, because starting one is that step: a member
     * announcing an exchange has announced it, and a station starting a return has decided on it.
     *
     * @param stationId    the station running the movement
     * @param purpose      what the movement is for
     * @param memberId     the member it concerns, or {@code null} for one with no member at either end
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
            Integer outgoingItemId,
            Integer inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Actor actor,
            Integer pickedItemId) {
        ItemOwner ownerKind = resolveOwner(outgoingItemId, inventoryId);
        int flowId = flowService.resolveFlow(stationId, inventoryId, ownerKind, purpose);
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
        return acknowledge(movement.id(), first.id(), actor, "", pickedItemId);
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
            custodyService.applyStepCustody(subjectItemId, step.custodyAfter(), movement.memberId(), movementId);
        }

        movementRepository.createLog(movementId, step.id(), step.label(), ackKind, actor.memberId(), note);

        MovementFlowStep next = nextStepAfter(movement.flowId(), step.position());
        if (next == null) {
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
        return movementRepository.findById(movementId).orElseThrow();
    }

    /**
     * Refuses the step whose turn it is. The movement closes and the outgoing item goes back to
     * whoever had it before, which an owner with no replacement in stock needs.
     */
    public ItemMovement decline(int movementId, Actor actor, String reason) {
        ItemMovement movement = requireOpen(movementId);
        MovementFlowStep step = currentStep(movement);
        if (step != null) requireTurn(movement, step, actor);
        return close(movement, MovementState.DECLINED, reason);
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
     * Deletes a movement outright, which is what the old exchange list called cancelling.
     */
    public boolean delete(int movementId) {
        return movementRepository.delete(movementId);
    }

    private ItemMovement close(ItemMovement movement, MovementState state, String reason) {
        restoreOutgoingItem(movement);
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
     * <p>An owner's step is confirmed when that owner runs on this instance and can press the button
     * itself. When it does not, the station stands in and the log says the step was asserted rather
     * than confirmed, which is what makes the gap in the chain visible as a gap.
     */
    private AckKind requireTurn(ItemMovement movement, MovementFlowStep step, Actor actor) {
        if (!mayAct(movement, step, actor)) {
            throw new ForbiddenResponse("This step belongs to the %s".formatted(step.actor()));
        }
        return step.actor() == StepActor.OWNER && !ownerIsOnThisInstance(movement)
                ? AckKind.ASSERTED
                : AckKind.CONFIRMED;
    }

    private boolean mayAct(ItemMovement movement, MovementFlowStep step, Actor actor) {
        return switch (step.actor()) {
            case MEMBER ->
                movement.memberId() != null && (movement.memberId() == actor.memberId() || actor.stationRights());
            case STATION -> actor.stationRights();
            // Nobody from the owner's side can sign in yet, so its steps are the station's to assert
            case OWNER -> actor.stationRights();
        };
    }

    /**
     * Whether the body above the station runs on this instance, which decides whether its steps are
     * confirmed by its own people or asserted by the station. No such body exists yet, so this is
     * always false and every owner step is asserted.
     */
    private boolean ownerIsOnThisInstance(ItemMovement movement) {
        if (movement.outgoingItemId() == null && movement.incomingItemId() == null) return false;
        Integer itemId = movement.outgoingItemId() != null ? movement.outgoingItemId() : movement.incomingItemId();
        return inventoryRepository
                .findItemById(itemId)
                .map(item -> item.ownerClusterId() != null)
                .orElse(false);
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
