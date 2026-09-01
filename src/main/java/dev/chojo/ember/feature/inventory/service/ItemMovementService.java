/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterItemIssued;
import dev.chojo.ember.event.events.MovementAdvanced;
import dev.chojo.ember.event.events.MovementCancelled;
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
import dev.chojo.ember.feature.inventory.entity.MovementParty;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.entity.StepActor;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementItemRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
    private final ItemMovementItemRepository carriedRepository;
    private final DomainEventBus eventBus;

    @Inject
    public ItemMovementService(
            ItemMovementRepository movementRepository,
            MovementFlowService flowService,
            InventoryRepository inventoryRepository,
            ItemCustodyService custodyService,
            ClusterRepository clusterRepository,
            ItemMovementItemRepository carriedRepository,
            DomainEventBus eventBus) {
        this.movementRepository = movementRepository;
        this.flowService = flowService;
        this.inventoryRepository = inventoryRepository;
        this.custodyService = custodyService;
        this.clusterRepository = clusterRepository;
        this.carriedRepository = carriedRepository;
        this.eventBus = eventBus;
    }

    /**
     * Records the further pieces a movement carries on one of its legs.
     *
     * <p>A dispatch sends a station twenty jackets and the station confirms one arrival. The movement names
     * one of them, the rest are recorded here, and every step that moves the named one moves these with it.
     *
     * @param movementId the movement
     * @param subject    which leg they are on
     * @param itemIds    the pieces beyond the one the movement names
     */
    public void carry(int movementId, StepSubject subject, List<Integer> itemIds) {
        carriedRepository.add(movementId, subject, itemIds);
    }

    /**
     * The further pieces a movement carries on one leg, the one it names excluded.
     *
     * @param movementId the movement
     * @param subject    which leg to read
     * @return the item ids
     */
    public List<Integer> carried(int movementId, StepSubject subject) {
        return carriedRepository.findItems(movementId, subject);
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

        /**
         * The member to write into the record, which may be nobody.
         *
         * <p>Somebody acting for a cluster need not be at any station, and the log names a station's
         * member. The entry still says what was acknowledged, when, and whether it was confirmed or
         * asserted; what it cannot say is which of the station's people did it, because none of them did.
         *
         * @return the member id, or {@code null} when the actor belongs to no station
         */
        public Integer memberIdOrNull() {
            return memberId > 0 ? memberId : null;
        }
    }

    /**
     * Starts a movement and acknowledges its first step, because starting one is that step: a member
     * announcing an exchange has announced it, and a station starting a return has decided on it.
     *
     * <p>Naming a member is what makes this a movement to or from a person rather than to or from a
     * shelf, and those are two different chains with two different sets of steps.
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
        return create(
                stationId,
                purpose,
                memberId,
                memberName,
                outgoingItemId,
                inventoryId,
                oldSizeId,
                newSizeId,
                reason,
                actor,
                pickedItemId,
                false);
    }

    /**
     * Starts a movement, saying whether it is a report that the outgoing item is gone.
     *
     * @param lostReport whether the item being replaced is missing rather than coming back
     * @see #create(int, MovementPurpose, Integer, String, Integer, Integer, Integer, Integer, String, Actor, Integer)
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
            Integer pickedItemId,
            boolean lostReport) {
        return create(
                stationId,
                purpose,
                memberId,
                memberName,
                outgoingItemId,
                inventoryId,
                oldSizeId,
                newSizeId,
                reason,
                actor,
                pickedItemId,
                lostReport,
                List.of());
    }

    /**
     * Asks a member for everything they hold back, each piece on the chain that fits it.
     *
     * <p>One movement per piece rather than one for the lot, because the pieces go different ways:
     * what the station owns goes back on its shelf and what the body above it owns goes into the
     * post. A single movement would have to be in two places at the end.
     *
     * @param stationId  the station asking
     * @param memberId   the member holding the gear
     * @param memberName their name, for what the member is told
     * @param actor      who is asking
     * @return the movements that were started, one per piece
     */
    public List<ItemMovement> requestEverythingBack(int stationId, int memberId, String memberName, Actor actor) {
        var held = inventoryRepository.findItemsByMember(memberId).stream()
                .filter(item -> item.custody() == ItemCustody.WITH_MEMBER)
                .toList();

        var started = new ArrayList<ItemMovement>();
        for (InventoryItem item : held) {
            started.add(create(
                    stationId,
                    MovementPurpose.RETURN,
                    memberId,
                    memberName,
                    item.id(),
                    item.inventoryId(),
                    item.sizeId(),
                    null,
                    "",
                    actor,
                    null));
        }
        log.info("Station {} asked member {} for {} piece(s) back", stationId, memberId, started.size());
        return started;
    }

    /**
     * Starts a movement carrying more than the one piece it names.
     *
     * <p>The rest of the load is recorded before the first step is walked, because that step moves the whole
     * consignment: recorded afterwards, the pieces would still be sitting in the store while the movement
     * said it had sent them.
     *
     * @param carriedIncoming the further arriving pieces this movement carries
     * @see #create(int, MovementPurpose, Integer, String, Integer, Integer, Integer, Integer, String, Actor, Integer)
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
            Integer pickedItemId,
            boolean lostReport,
            List<Integer> carriedIncoming) {
        requireItIsNotAlreadyOnItsWay(outgoingItemId);
        ItemOwner ownerKind = resolveOwner(outgoingItemId, inventoryId);
        Integer ownerClusterId = resolveOwnerId(outgoingItemId, stationId);
        MovementParty party = memberId != null ? MovementParty.MEMBER : MovementParty.STORE;
        int flowId = flowService.resolveFlow(stationId, inventoryId, ownerKind, ownerClusterId, purpose, party);
        List<MovementFlowStep> steps = walkable(flowService.findActiveSteps(flowId), lostReport);
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
                // Somebody acting for a body above the station belongs to no station, so there is no member
                // to name. The record still says what was started and when; what it cannot say is which of
                // the station's people did it, because none of them did.
                actor.memberIdOrNull(),
                lostReport);
        carry(movement.id(), StepSubject.INCOMING, carriedIncoming);
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
                actorOfCurrentStep(started),
                ownerKind == ItemOwner.CLUSTER ? ownerClusterId : null));
        announceIssue(purpose, ownerKind, ownerClusterId, stationId, started);
        return started;
    }

    /**
     * Refuses to send a piece out on a second chain while it is still walking the first.
     *
     * <p>A piece can only be in one place, and every chain says where it is by moving it. Two chains on
     * one piece therefore read each other's work: a step walked on one shifts custody, and the other
     * reports itself further along without anybody having touched it. Stations really did end up with
     * two exchanges raised for the same jacket, and both of them then drifted.
     *
     * @param outgoingItemId the piece that would be setting out, or {@code null} when nothing does
     * @throws BadRequestResponse naming the movement that already has it
     */
    private void requireItIsNotAlreadyOnItsWay(Integer outgoingItemId) {
        if (outgoingItemId == null) return;
        movementRepository.findOpenByOutgoingItem(outgoingItemId).ifPresent(open -> {
            throw new BadRequestResponse(
                    "This piece is already on movement %d, so finish or call that one off first".formatted(open.id()));
        });
    }

    /**
     * Tells a station that gear is coming, when the cluster above it is what started the movement.
     *
     * <p>Only an issue is announced this way. A return or an exchange is something the station is part of
     * already, and would be told twice.
     *
     * @param purpose        what the movement is for
     * @param ownerKind      who owns the gear
     * @param ownerClusterId the owning cluster, when one on this instance owns it
     * @param stationId      the station receiving it
     * @param movement       the movement carrying it
     */
    private void announceIssue(
            MovementPurpose purpose,
            ItemOwner ownerKind,
            Integer ownerClusterId,
            int stationId,
            ItemMovement movement) {
        if (purpose != MovementPurpose.ISSUE || ownerKind != ItemOwner.CLUSTER || ownerClusterId == null) return;
        String clusterName =
                clusterRepository.findById(ownerClusterId).map(Cluster::name).orElse("");
        String itemName = movement.outgoingItemId() == null
                ? ""
                : inventoryRepository
                        .findItemById(movement.outgoingItemId())
                        .map(InventoryItem::name)
                        .orElse("");
        eventBus.publish(new ClusterItemIssued(stationId, movement.id(), clusterName, itemName));
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
        if (movement.flowId() == null) return List.of();
        return walkable(flowService.findAllSteps(movement.flowId()), movement.lostReport());
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
        // A missing item is not somewhere else because a step was walked. It is missing, and the step that
        // reports it says so about the request rather than about where the thing is.
        if (movement.lostReport() && step.subject() == StepSubject.OUTGOING) subjectItemId = null;
        if (step.picksItem()) {
            if (pickedItemId == null) throw new BadRequestResponse("This step names the arriving item, so name it");
            movementRepository.setIncomingItem(movementId, pickedItemId);
            subjectItemId = pickedItemId;
        }

        if (subjectItemId != null) {
            custodyService.applyStepCustody(
                    subjectItemId, step.custodyAfter(), movement.memberId(), movementId, movement.stationId());
            // Everything else the movement carries on this leg goes where the named piece goes. A batch
            // arrives once or not at all, so a step that moved one of twenty jackets moved all twenty.
            for (int carriedId : carried(movementId, step.subject())) {
                custodyService.applyStepCustody(
                        carriedId, step.custodyAfter(), movement.memberId(), movementId, movement.stationId());
            }
        }

        movementRepository.createLog(movementId, step.id(), step.label(), ackKind, actor.memberIdOrNull(), note);

        MovementFlowStep next = nextStepAfter(movement, step.position());
        if (next == null) {
            ItemMovement finished = movementRepository.findById(movementId).orElseThrow();
            settleInTransit(finished);
            dropGearGoneForGood(finished);
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
                actorOfCurrentStep(walked),
                owningCluster(walked)));
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
     *
     * <p>Whoever is at the wheel may call it off, and so may a member who is still holding the piece
     * the movement is about. Somebody who asks for a bigger jacket and finds the next morning that it
     * fits after all should be able to take that back themselves rather than ask the station to do it
     * for them. The line is the handover: what is in the member's hands is their business, and from
     * the moment the station has taken it, it is the station's.
     *
     * @param movementId the movement
     * @param actor      who is calling it off
     * @param reason     what to record, for whoever reads it later
     * @return the closed movement
     */
    public ItemMovement cancel(int movementId, Actor actor, String reason) {
        ItemMovement movement = requireOpen(movementId);
        MovementFlowStep step = currentStep(movement);
        if (step != null && !mayAct(movement, step, actor) && !stillHoldsIt(movement, actor)) {
            throw new ForbiddenResponse("This movement is not on your side any more");
        }
        String itemName = itemName(movement.outgoingItemId());
        boolean away = hasLeftTheStation(movement.outgoingItemId());
        ItemMovement cancelled = close(movement, MovementState.CANCELLED, reason, false);
        eventBus.publish(new MovementCancelled(
                cancelled.stationId(),
                cancelled.id(),
                cancelled.memberId(),
                cancelled.inventoryId(),
                inventoryName(cancelled.inventoryId()),
                itemName,
                reason,
                away,
                actor.memberId()));
        return cancelled;
    }

    /**
     * Whether the piece a movement is about is still on the member it concerns.
     *
     * <p>Which is what decides whether they may call it off themselves, and therefore whether their
     * own pages offer them the button.
     *
     * @param movement the movement
     * @return true while the member holds it
     */
    public boolean stillHeldBy(ItemMovement movement) {
        return movement.memberId() != null && stillHoldsIt(movement, new Actor(movement.memberId(), false));
    }

    /**
     * Whether the member calling this off is the one the movement is for and still has the piece.
     *
     * <p>Read off the item rather than off the step: the member walked their own step when they asked
     * for the exchange, so by the letter of the chain it is no longer their turn, while the jacket is
     * demonstrably still on them.
     */
    private boolean stillHoldsIt(ItemMovement movement, Actor actor) {
        if (movement.memberId() == null || movement.memberId() != actor.memberId()) return false;
        Integer itemId = movement.outgoingItemId();
        if (itemId == null) return false;
        return inventoryRepository
                .findItemById(itemId)
                .filter(item -> item.custody() == ItemCustody.WITH_MEMBER)
                .filter(item -> item.assignedTo() != null && item.assignedTo() == actor.memberId())
                .isPresent();
    }

    /** Whether the piece is past the station, which is what decides that calling off cannot fetch it back. */
    private boolean hasLeftTheStation(Integer itemId) {
        if (itemId == null) return false;
        return inventoryRepository
                .findItemById(itemId)
                .filter(item -> item.custody() == ItemCustody.IN_TRANSIT || item.custody() == ItemCustody.WITH_OWNER)
                .isPresent();
    }

    private String itemName(Integer itemId) {
        if (itemId == null) return "";
        return inventoryRepository.findItemById(itemId).map(InventoryItem::name).orElse("");
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
        boolean deleted = movementRepository.delete(movementId);
        if (deleted) log.info("Deleted movement {}", movementId);
        else log.warn("Delete for movement {} affected zero rows", movementId);
        return deleted;
    }

    private ItemMovement close(ItemMovement movement, MovementState state, String reason) {
        return close(movement, state, reason, true);
    }

    /**
     * @param fetchBack whether the piece that set out is to be put back with whoever sent it even if it
     *                  has already left the station. A refusal comes from the far end and settles the
     *                  whole journey, so it does. Calling off does not: it ends the plan, not the post.
     */
    private ItemMovement close(ItemMovement movement, MovementState state, String reason, boolean fetchBack) {
        if (fetchBack || !hasLeftTheStation(movement.outgoingItemId())) {
            restoreOutgoingItem(movement);
        } else {
            log.info(
                    "Movement {} was called off while item {} was already away, so it stays with its owner",
                    movement.id(),
                    movement.outgoingItemId());
        }
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
     *
     * <p>A report that the item is gone has nowhere to put it back: it was missing before the report and it
     * is missing after, whether the owner sent a replacement or refused one. The loss is recorded either way.
     *
     */
    private void restoreOutgoingItem(ItemMovement movement) {
        if (movement.outgoingItemId() == null || movement.lostReport()) return;
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
    /**
     * Removes the piece that has gone back to a body Ember cannot see.
     *
     * <p>Gear that left for an owner on this instance stays on the books: the owner still has a row
     * for it and may send it here again. Gear that went back to a body outside Ember is gone in
     * every sense that matters here. Nobody can identify it again, nothing will name it, and it
     * will not come back. Keeping the row would fill the inventory with pieces that concern nobody,
     * and every list, count and check would have to learn to ignore them.
     *
     * <p>Only the piece that left, and only where something arrived to replace it. A return leaves
     * the row alone, because a return is the whole point of the movement and the station may want to
     * read afterwards what it sent away.
     */
    private void dropGearGoneForGood(ItemMovement movement) {
        if (movement.purpose() != MovementPurpose.EXCHANGE) return;
        Integer gone = movement.outgoingItemId();
        if (gone == null || owningCluster(movement) != null) return;

        inventoryRepository
                .findItemById(gone)
                .filter(item -> item.ownerKind() == ItemOwner.CLUSTER)
                .ifPresent(item -> {
                    inventoryRepository.deleteItem(item.id());
                    log.info(
                            "Item {} left movement {} for an owner outside Ember and was removed from the inventory",
                            item.id(),
                            movement.id());
                });
    }

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

    private MovementFlowStep nextStepAfter(ItemMovement movement, int position) {
        return walkable(flowService.findActiveSteps(movement.flowId()), movement.lostReport()).stream()
                .filter(s -> s.position() > position)
                .findFirst()
                .orElse(null);
    }

    /**
     * The steps this movement actually walks.
     *
     * <p>An exchange normally walks two legs: the old item back to the owner, the new one out. A report that
     * the old item is gone has nothing to walk back, so the steps about the outgoing item are not steps
     * somebody skipped, they are steps that could never happen. They are left out of the chain entirely
     * rather than waiting for an acknowledgement nobody can honestly give.
     *
     * <p>The first step is the exception, because it is the announcement rather than part of either leg: it
     * is the act of raising the movement, and raising it is exactly what the station has just done.
     *
     * @param steps      the flow's active steps in order
     * @param lostReport whether the outgoing item is missing rather than coming back
     * @return the steps to walk, in order
     */
    private List<MovementFlowStep> walkable(List<MovementFlowStep> steps, boolean lostReport) {
        if (!lostReport || steps.isEmpty()) return steps;
        MovementFlowStep announcement = steps.getFirst();
        return steps.stream()
                .filter(s -> s.id() == announcement.id() || s.subject() != StepSubject.OUTGOING)
                .toList();
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

    /**
     * Whether this caller may press this step.
     *
     * <p>The one place that decides it, so the button the screen draws and the answer the service gives
     * cannot disagree.
     *
     * <p>An owner's step is the owner's. The station covers it only where the owner cannot answer at all,
     * which is when the gear belongs to a body that does not run here: then the station stands in and the
     * record says it asserted rather than confirmed. When the owner is a cluster on this instance it can
     * answer for itself, and the station waiting is the whole point of the step.
     *
     * @param movement the movement
     * @param step     the step in question
     * @param actor    who is asking
     * @return whether they may press it
     */
    public boolean mayAct(ItemMovement movement, MovementFlowStep step, Actor actor) {
        return switch (step.actor()) {
            case MEMBER ->
                movement.memberId() != null
                        && (movement.memberId() == actor.memberId() || (actor.stationRights() && opensTheChain(step)));
            case STATION -> actor.stationRights();
            case OWNER -> actor.ownerRights() || (actor.stationRights() && owningCluster(movement) == null);
        };
    }

    /**
     * Whether this is the step a chain opens with.
     *
     * <p>What it decides is the one thing a station may do in a member's name. Raising the movement is
     * routine and often literal: somebody says at the station that their jacket no longer fits, and
     * the manager writes it down. Saying that they have received something is not that. It is the
     * confirmation the whole chain exists to collect, and a station ticking it for them turns a
     * receipt into a claim.
     *
     * <p>A member who will not answer does not freeze the chain: the step can be forced, with a note,
     * and the record then says forced rather than confirmed.
     */
    private boolean opensTheChain(MovementFlowStep step) {
        return flowService.findActiveSteps(step.flowId()).stream()
                .findFirst()
                .filter(first -> first.id() == step.id())
                .isPresent();
    }

    /**
     * Whether the body that owns this movement's gear can answer for itself here.
     *
     * <p>What it decides is who names an arriving piece. An owner on this instance names what it
     * sends, and a second row written by the station for the same piece would be one thing with two
     * records. An owner outside Ember names nothing, so the station writes down what turned up.
     *
     * @param movement the movement
     * @return whether the owner is reachable on this instance
     */
    public boolean ownerAnswersHere(ItemMovement movement) {
        return owningCluster(movement) != null;
    }

    /**
     * Who owns the gear this movement is about, for a replacement that has to belong to the same
     * body as the piece it replaces.
     *
     * @param movement the movement
     * @return the owner of its gear
     */
    public ItemOwner ownerOf(ItemMovement movement) {
        return resolveOwner(movement.outgoingItemId(), movement.inventoryId());
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
     * The cluster a movement's gear belongs to, when that cluster is in a position to answer for it.
     *
     * <p>Read off the item rather than off the movement, because ownership lives on the item.
     *
     * <p>Two things have to hold, and leaving either out strands the movement. A body that does not run
     * on this instance has nobody to press its steps, which has always been true. A body that runs here
     * but keeps no gear here has nobody either: it has no store, no queue and no reason to look. In both
     * cases the station stands in and the record says asserted. Answering with the cluster whenever one
     * merely exists locks the station out of a step that will then never be pressed by anybody.
     *
     * @param movement the movement
     * @return the owning cluster, or {@code null} when nobody on its side can answer here
     */
    private Integer owningCluster(ItemMovement movement) {
        if (movement.outgoingItemId() == null) return null;
        return inventoryRepository
                .findItemById(movement.outgoingItemId())
                .filter(item -> item.ownerKind() == ItemOwner.CLUSTER)
                .map(InventoryItem::ownerClusterId)
                .flatMap(clusterRepository::findById)
                .filter(Cluster::usesInventory)
                .map(Cluster::id)
                .orElse(null);
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
