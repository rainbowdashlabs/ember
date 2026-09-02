/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.ExchangeLog;
import dev.chojo.ember.feature.inventory.entity.ExchangeRequest;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.entity.MovementState;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The exchange screens, served from movements.
 *
 * <p>An exchange is one purpose a movement can have, and the machinery under it now records every
 * step and the party that acknowledged it. The pages that read exchanges still speak of a handful of
 * statuses, so this translates between the two until they are rewritten: the status is read off where
 * the two items actually are, and asking for a status walks the movement forward until they are
 * there.
 *
 * <p>That translation is why the derived status survives a station editing its flow. It never counts
 * steps; it looks at custody, which is the thing the steps were moving all along.
 */
@Singleton
public class ExchangeService {
    private static final Logger log = LoggerFactory.getLogger(ExchangeService.class);

    private final ItemMovementService movementService;
    private final InventoryRepository inventoryRepository;

    @Inject
    public ExchangeService(ItemMovementService movementService, InventoryRepository inventoryRepository) {
        this.movementService = movementService;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Announces an exchange, which starts a movement on whichever flow the item's owner points at.
     *
     * @param stationId   the station ID
     * @param memberId    the member the exchange is for
     * @param memberName  the member's display name, for the notification
     * @param itemId      the current item, or {@code null}
     * @param inventoryId the inventory ID
     * @param oldSizeId   the current size, or {@code null}
     * @param newSizeId   the desired size, or {@code null}
     * @param reason      the reason for the exchange
     * @param createdBy   who announced it on behalf of the member, or {@code null}
     * @return the exchange as the pages read it
     */
    public ExchangeRequest create(
            int stationId,
            int memberId,
            String memberName,
            Integer itemId,
            int inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Integer createdBy) {
        requireHomogeneous(inventoryId);
        var actor = new ItemMovementService.Actor(createdBy != null ? createdBy : memberId, true);
        ItemMovement movement = movementService.create(
                stationId,
                MovementPurpose.EXCHANGE,
                memberId,
                memberName,
                itemId,
                inventoryId,
                oldSizeId,
                newSizeId,
                reason,
                actor,
                null);
        return toRequest(movement);
    }

    public Optional<ExchangeRequest> findById(int id) {
        return movementService.findById(id).filter(this::isExchange).map(this::toRequest);
    }

    /**
     * Every movement the station has, not only the exchanges.
     *
     * <p>A station now raises returns and issues on chains of their own, and asking a member for
     * everything back raises one per piece. Listing only exchanges left all of those with nowhere to
     * be seen. Each row says which of the three it is, so the list reads as what it is: the gear that
     * is on the move.
     */
    public List<ExchangeRequest> findByStation(int stationId) {
        return movementService.findByStation(stationId).stream()
                .map(this::toRequest)
                .toList();
    }

    public List<ExchangeRequest> findByMember(int memberId) {
        return movementService.findByMember(memberId).stream()
                .map(this::toRequest)
                .toList();
    }

    public ExchangeRequest updateStatus(int id, ExchangeStatus newStatus, int changedBy, String note) {
        return updateStatus(id, newStatus, changedBy, note, null);
    }

    /**
     * Walks the movement forward until the two items are where the asked-for status says they are.
     *
     * @param id              the exchange ID
     * @param newStatus       the status the caller wants to reach
     * @param changedBy       the member acknowledging
     * @param note            an optional note, recorded against the last step walked
     * @param exchangedItemId the replacement, handed to the step that names it
     * @return the exchange as the pages read it
     * @throws BadRequestResponse if the exchange is not found, is already closed, or is asked to walk
     *                            to an end rather than to a station
     */
    public ExchangeRequest updateStatus(
            int id, ExchangeStatus newStatus, int changedBy, String note, Integer exchangedItemId) {
        if (!newStatus.walkable()) {
            throw new BadRequestResponse("An exchange is called off or refused on its chain, not walked there");
        }
        ItemMovement movement = movementService
                .findById(id)
                .filter(this::isExchange)
                .orElseThrow(() -> new BadRequestResponse("Exchange request not found"));
        var actor = new ItemMovementService.Actor(changedBy, true);

        int guard = movementService.stepsOf(movement).size() + 1;
        int walked = 0;
        while (guard-- > 0
                && movement.state() == MovementState.OPEN
                && movement.currentStepId() != null
                && !reached(deriveStatus(movement), newStatus)
                && canWalkPast(movement, exchangedItemId)) {
            movement =
                    movementService.acknowledge(movement.id(), movement.currentStepId(), actor, note, exchangedItemId);
            walked++;
        }
        requireItWentSomewhere(movement, newStatus, exchangedItemId, walked);

        // Notifying is the movement service's job, since that is where every step actually happens
        log.info("Exchange {} walked to {} by member {}", id, newStatus, changedBy);
        return toRequest(movement);
    }

    /**
     * Sets an exchange to a status somebody says it should have, forwards or backwards.
     *
     * <p>An exchange has no status of its own to write: it is read off where its two pieces are. Setting a
     * field would therefore do nothing at all, and the old value would be back the next time anybody
     * looked. Correcting one means making the world say what it should have said, so this puts the pieces
     * where the wanted status reads them, and the chain follows to whichever step that world has not
     * reached yet. What each status asks for:
     *
     * <ul>
     *   <li>Announced: the old piece is back with the member and no replacement is named, so an
     *       already-named one is unhooked from the exchange. Its own whereabouts are left alone, because
     *       the correction is about this exchange and not about that piece.
     *   <li>Received: the old piece is on the station's shelf, and again no replacement is named.
     *   <li>Shipped: the old piece is in the post. That is what shipped means for gear the station owns as
     *       well as for gear a body above it owns, which is why this one custody covers both.
     *   <li>Arrived: the replacement is at the station, which is why this status needs one to exist. Where
     *       the old piece got to no longer decides anything once the new one is here.
     *   <li>Done: the replacement is with the member and the chain is closed as finished.
     *   <li>Cancelled and Declined: the chain is closed that way and the pieces are left exactly where
     *       they are. Somebody correcting the end of an exchange is saying the record was wrong, not that
     *       the gear moved.
     * </ul>
     *
     * <p>Nothing is announced. A correction is somebody tidying a row, and the people on it did nothing
     * that deserves a notice.
     *
     * @param id        the exchange
     * @param wanted    the status it should have
     * @param changedBy who is correcting it
     * @param reason    why, which is mandatory and appears in the exchange's history
     * @return the exchange as the pages read it
     * @throws BadRequestResponse when the exchange is unknown, the reason is missing, or the wanted status
     *                            needs a replacement piece the exchange does not have
     */
    public ExchangeRequest forceStatus(int id, ExchangeStatus wanted, int changedBy, String reason) {
        ItemMovement movement = movementService
                .findById(id)
                .filter(this::isExchange)
                .orElseThrow(() -> new BadRequestResponse("Exchange request not found"));
        if (wanted == ExchangeStatus.ARRIVED && movement.incomingItemId() == null) {
            throw new BadRequestResponse("Arrived means the replacement is here, so name it before setting that");
        }
        if (wanted == ExchangeStatus.DONE && movement.incomingItemId() == null) {
            throw new BadRequestResponse("Done means the member has the replacement, so name it before setting that");
        }
        var actor = new ItemMovementService.Actor(changedBy, true);
        ItemMovement corrected = movementService.correct(id, correctionFor(wanted), actor, reason);
        log.info("Exchange {} set to {} by member {}", id, wanted, changedBy);
        return toRequest(corrected);
    }

    /** The world each status is read off, as {@link #forceStatus} describes it. */
    private ItemMovementService.Correction correctionFor(ExchangeStatus wanted) {
        return switch (wanted) {
            case ANNOUNCED -> new ItemMovementService.Correction(ItemCustody.WITH_MEMBER, null, true, null);
            case RECEIVED -> new ItemMovementService.Correction(ItemCustody.AT_STATION, null, true, null);
            case SHIPPED -> new ItemMovementService.Correction(ItemCustody.IN_TRANSIT, null, true, null);
            case ARRIVED -> new ItemMovementService.Correction(null, ItemCustody.AT_STATION, false, null);
            case DONE -> new ItemMovementService.Correction(null, ItemCustody.WITH_MEMBER, false, MovementState.DONE);
            case CANCELLED -> new ItemMovementService.Correction(null, null, false, MovementState.CANCELLED);
            case DECLINED -> new ItemMovementService.Correction(null, null, false, MovementState.DECLINED);
        };
    }

    public List<ExchangeLog> findLogs(int requestId) {
        return movementService.findLogs(requestId).stream()
                .map(entry -> new ExchangeLog(
                        entry.id(),
                        entry.movementId(),
                        entry.stepLabel(),
                        entry.ackKind(),
                        entry.changedBy() != null ? entry.changedBy() : 0,
                        entry.changedAt(),
                        entry.note()))
                .toList();
    }

    public boolean delete(int id) {
        boolean deleted = movementService.delete(id);
        if (deleted) log.info("Deleted exchange {}", id);
        else log.warn("Delete of exchange {} did not change any row", id);
        return deleted;
    }

    public int countPendingByStation(int stationId) {
        return movementService.countOpenByStation(stationId);
    }

    /**
     * Refuses a request that moved nothing, rather than answering as though it had.
     *
     * <p>The walk stops of its own accord at a step that names the arriving piece when nobody named
     * one, which is right. Reporting that as success is not: the screen redrew unchanged and the row
     * sat where it was, with nothing anywhere saying why. That is what "it cannot be moved on" looked
     * like from the outside.
     *
     * @throws BadRequestResponse when the chain did not move and the step it is standing on is the one
     *                            asking which piece arrived
     */
    private void requireItWentSomewhere(
            ItemMovement movement, ExchangeStatus wanted, Integer exchangedItemId, int walked) {
        if (walked > 0 || movement.state() != MovementState.OPEN) return;
        if (reached(deriveStatus(movement), wanted)) return;
        if (canWalkPast(movement, exchangedItemId)) return;
        throw new BadRequestResponse("This step names the piece that arrived, so name it before going on");
    }

    /**
     * Whether the movement can be walked one more step with what the caller supplied.
     *
     * <p>It stops rather than failing when the next step is the one that names the replacement and
     * nobody named it, and asking for a status the flow does not reach simply gets as far as it
     * goes: the old five statuses had a shipping leg that gear the station owns never had.
     */
    private boolean canWalkPast(ItemMovement movement, Integer exchangedItemId) {
        if (exchangedItemId != null) return true;
        return movementService.stepsOf(movement).stream()
                .filter(step -> step.id() == movement.currentStepId())
                .noneMatch(ItemMovementService::namesIncomingItem);
    }

    /**
     * Refuses an exchange on an inventory holding a drawer of different things.
     *
     * <p>An exchange swaps one size of a thing for another size of the same thing, which presupposes
     * the pieces are interchangeable. Among a laminator and a toy fire engine there is nothing to
     * swap for anything. The picker offers only the inventories where this means something, so a
     * request arriving here has gone round it.
     *
     * @throws BadRequestResponse when the inventory holds a drawer of different things
     */
    private void requireHomogeneous(int inventoryId) {
        boolean homogeneous = inventoryRepository
                .findById(inventoryId)
                .map(Inventory::homogeneous)
                .orElseThrow(() -> new BadRequestResponse("That inventory does not exist"));
        if (!homogeneous) {
            throw new BadRequestResponse("This inventory is a collection, so there is nothing to exchange in it");
        }
    }

    private boolean isExchange(ItemMovement movement) {
        return movement.purpose() == MovementPurpose.EXCHANGE;
    }

    /**
     * Whether the exchange has got at least as far as the status being asked for, so walking stops
     * once it has rather than running off the end of the flow.
     */
    private boolean reached(ExchangeStatus current, ExchangeStatus target) {
        return current.ordinal() >= target.ordinal();
    }

    /**
     * How far along an exchange is, read off where the two pieces are.
     *
     * <p>Custody is what the steps were moving all along, so this holds whatever a station has done
     * to its flow: a chain with the owner's leg collapsed into one step still reports the same status
     * at the same point as the seven-step one.
     *
     * <p>"With the owner" means two different things and has to be told apart, which is what this got
     * wrong: for the station's own gear it is the station's shelf, so the piece has come back and the
     * exchange is at received. For the body above the station it is that body's store, so the piece
     * has left for good and the exchange is past shipped. Reading both the same way made an exchange
     * of somebody else's gear jump backwards from shipped to received the moment the owner confirmed
     * it had arrived, and there was no way forward from there.
     *
     * <p>An exchange that has stopped moving is not read off custody at all, because custody no longer
     * says anything about it: calling one off puts the piece back where it started, which reads as
     * announced, and refusing one does the same. It reports the end it came to instead. Reading every
     * closed exchange as done was what made calling one off look like finishing it.
     */
    private ExchangeStatus deriveStatus(ItemMovement movement) {
        if (movement.state() == MovementState.DONE) return ExchangeStatus.DONE;
        if (movement.state() == MovementState.CANCELLED) return ExchangeStatus.CANCELLED;
        if (movement.state() == MovementState.DECLINED) return ExchangeStatus.DECLINED;
        ItemCustody incoming = custodyOf(movement.incomingItemId());
        ItemCustody outgoing = custodyOf(movement.outgoingItemId());
        if (incoming == ItemCustody.WITH_MEMBER) return ExchangeStatus.DONE;
        if (incoming == ItemCustody.AT_STATION) return ExchangeStatus.ARRIVED;
        if (incoming == ItemCustody.IN_TRANSIT || outgoing == ItemCustody.IN_TRANSIT) {
            return ExchangeStatus.SHIPPED;
        }
        if (outgoing == ItemCustody.WITH_OWNER && !ownedByTheStation(movement.outgoingItemId())) {
            return ExchangeStatus.SHIPPED;
        }
        if (outgoing == ItemCustody.AT_STATION || outgoing == ItemCustody.WITH_OWNER) {
            return ExchangeStatus.RECEIVED;
        }
        return ExchangeStatus.ANNOUNCED;
    }

    /** Whether the station itself owns this piece, which is what makes "with the owner" its own shelf. */
    private boolean ownedByTheStation(Integer itemId) {
        if (itemId == null) return true;
        return inventoryRepository
                .findItemById(itemId)
                .map(item -> item.ownerKind() == ItemOwner.STATION)
                .orElse(true);
    }

    private ItemCustody custodyOf(Integer itemId) {
        if (itemId == null) return null;
        return inventoryRepository
                .findItemById(itemId)
                .map(item -> item.custody())
                .orElse(null);
    }

    private ExchangeRequest toRequest(ItemMovement movement) {
        return new ExchangeRequest(
                movement.id(),
                movement.stationId(),
                movement.purpose(),
                movement.memberId() != null ? movement.memberId() : 0,
                movement.outgoingItemId(),
                movement.inventoryId() != null ? movement.inventoryId() : 0,
                movement.oldSizeId(),
                movement.newSizeId(),
                movement.incomingItemId(),
                deriveStatus(movement),
                movement.reason(),
                movement.createdAt(),
                movement.closedAt() != null ? movement.closedAt() : movement.createdAt(),
                movement.createdBy());
    }
}
