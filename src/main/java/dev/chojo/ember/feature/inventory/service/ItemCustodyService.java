/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ItemMovementRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * Owns every change of custody. Nothing else writes an item's custody, the station that custody
 * runs through, the member holding it or the movement carrying it.
 *
 * <p>That single-writer rule is the whole point of the service. Custody used to be spread across an
 * assignment, a lost timestamp and a lending row that each moved on their own, and the model went
 * wrong wherever two of them disagreed. Here every move is one call, and the database refuses any
 * combination this service would not write.
 */
@Singleton
public class ItemCustodyService {
    private static final Logger log = LoggerFactory.getLogger(ItemCustodyService.class);
    private final InventoryRepository inventoryRepository;
    private final ItemMovementRepository movementRepository;

    @Inject
    public ItemCustodyService(InventoryRepository inventoryRepository, ItemMovementRepository movementRepository) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
    }

    /**
     * What a write may find on a piece that would otherwise make it not free to act on.
     *
     * <p>Most writes may find nothing: a piece reported missing, carried by an open movement or set aside
     * for a loan is somebody else's business. Two writes are about the loss itself, reporting it and
     * finding the piece again, and a movement walking its own step may of course find its own piece on
     * it.
     *
     * @param mayBeLost  whether the piece may be reported missing, which only the loss and its finding allow
     * @param movementId the movement walking a step on the piece, which does not count as carrying it
     *                   against itself, or {@code null}
     */
    public record Allowance(boolean mayBeLost, @Nullable Integer movementId) {
        /** An ordinary write, which finds nothing standing in its way. */
        public static final Allowance NONE = new Allowance(false, null);

        /** Reporting a piece missing, or reporting it found again. */
        public static final Allowance THE_LOSS_ITSELF = new Allowance(true, null);

        /**
         * A movement walking a step on its own piece.
         *
         * @param movementId the movement
         * @param lossReport whether the movement reports the piece missing
         * @return the allowance
         */
        public static Allowance walking(int movementId, boolean lossReport) {
            return new Allowance(lossReport, movementId);
        }
    }

    /**
     * Refuses a piece that is not free to act on: reported missing, carried by an open movement, or set
     * aside for a loan to a partner station.
     *
     * <p>Custody used to be written without reading it first. A loss was undone by the next write
     * nobody meant as a finding, a piece promised to one member was handed to another, and the further
     * pieces of a dispatch were invisible to every check because the movement names only one. This is
     * the one place that says whether a piece is free, so every writer asks the same question.
     *
     * <p>A promised piece is refused with its own words, since the remedy differs: it is handed out on
     * the movement that promised it. A piece that does not exist is left to the caller, which knows
     * whether a missing row is a refusal or nothing to do.
     *
     * @param itemId    the piece
     * @param allowance what the write may find on it
     * @throws RefusalResponse when the piece is missing, carried by another open movement, or set aside
     *                         for a loan
     */
    public void requireFreeToActOn(int itemId, Allowance allowance) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) return;
        InventoryItem item = found.get();
        if (!allowance.mayBeLost() && (item.custody() == ItemCustody.LOST || item.lostAt() != null)) {
            throw InventoryRefusal.CUSTODY_PIECE_LOST.raise();
        }
        Integer own = allowance.movementId();
        movementRepository
                .findOpenByIncomingItem(itemId)
                .filter(open -> !Objects.equals(open.id(), own))
                .ifPresent(open -> {
                    throw InventoryRefusal.CUSTODY_PIECE_PROMISED.raise();
                });
        if (movementRepository.findOpenCarrying(itemId).stream().anyMatch(id -> !id.equals(own))) {
            throw InventoryRefusal.MOVEMENT_PIECE_ALREADY_ON_A_MOVEMENT.raise();
        }
        if (inventoryRepository.isSetAsideForALoan(itemId)) {
            throw InventoryRefusal.CUSTODY_PIECE_SET_ASIDE_FOR_A_LOAN.raise();
        }
    }

    /**
     * Refuses to write anything but the loss itself onto a piece reported missing.
     *
     * <p>The row keeps a loss until it is found again, and every other write leaves such a row as it was.
     * Refusing here says so, where the write would otherwise report success and change nothing.
     *
     * @param item the piece
     * @throws RefusalResponse when it is reported missing
     */
    private static void requireNotLost(InventoryItem item) {
        if (item.custody() == ItemCustody.LOST || item.lostAt() != null) {
            throw InventoryRefusal.CUSTODY_PIECE_LOST.raise();
        }
    }

    /**
     * Hands an item to a member, closing the spell it was on and opening a new one.
     *
     * @param itemId     the item ID
     * @param memberId   the member receiving it
     * @param memberName the member's display name for the history
     * @return the updated item, or empty if the item was not found
     * @throws RefusalResponse if the item is in a custody it cannot be handed out of
     */
    public Optional<InventoryItem> assignToMember(int itemId, int memberId, String memberName) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Assign skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        if (!item.custody().assignable()) {
            throw InventoryRefusal.CUSTODY_NOT_HANDED_OUT_FROM_HERE.raise();
        }
        requireFreeToActOn(itemId, Allowance.NONE);

        return writeAssignment(item, memberId, memberName);
    }

    /**
     * Hands an item to a member without asking whether it was available.
     *
     * <p>The availability check on {@link #assignToMember} is there to stop somebody handing out
     * gear that is in the post or with a partner. A movement putting its own item back is the thing
     * that ends "in the post", so it is not the check's business.
     */
    private Optional<InventoryItem> writeAssignment(InventoryItem item, int memberId, String memberName) {
        closeCurrentSpell(item);
        inventoryRepository.updateCustody(item.id(), ItemCustody.WITH_MEMBER, stationOf(item), memberId, null);
        inventoryRepository.createHistory(item.id(), memberId, memberName != null ? memberName : "");
        log.info("Item {} handed to member {} ('{}')", item.id(), memberId, memberName);
        return inventoryRepository.findItemById(item.id());
    }

    /**
     * Takes an item back from whoever holds it and puts it in the store it rests in: the owner's
     * when the station owns it, the station's own when it does not.
     *
     * @param itemId the item ID
     * @return the updated item, or empty if the item was not found
     * @throws RefusalResponse if the item is reported missing
     */
    public Optional<InventoryItem> takeBack(int itemId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Take-back skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        requireNotLost(item);
        closeCurrentSpell(item);
        writeResting(item);
        log.info("Item {} taken back into the store (was {})", itemId, item.custody());
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Places an item in a container, or takes it out of one. A container says where in the store an
     * item is, not who has it, so the custody only moves when the item was with a member: somebody
     * putting gear on a shelf is handing it back at the same time.
     *
     * @param itemId      the item ID
     * @param containerId the container, or {@code null} to clear the location
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> placeInContainer(int itemId, @Nullable Integer containerId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Container placement skipped: item {} not found", itemId);
            return Optional.empty();
        }
        if (containerId != null && found.get().custody() == ItemCustody.WITH_MEMBER) {
            takeBack(itemId);
        }
        inventoryRepository.setItemContainer(itemId, containerId);
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Records that the item has gone missing.
     *
     * <p>It stays on the record of whoever had it. Gear a member cannot find is still gear that
     * member is short of, and it stays theirs until something replaces it: taking it off them the
     * moment it is reported would hide exactly the fact worth seeing. It does leave the free stock,
     * because nobody can hand out what nobody can find.
     *
     * <p>Nobody above the station is told. Losing track of a jacket is the station's own business
     * until it wants something done about it, and wanting something done is a separate act: the
     * station raises a report, and that is what reaches the owner.
     *
     * <p>Gear borrowed from a partner is refused. The borrower's row is a copy that disappears when
     * the loan ends, so a loss written on it would be gone by the time it mattered, and the owner's
     * row would go on saying the partner has the piece. The fact belongs to the owner, and it travels
     * on the loan the gear came in on, which is the one channel both stations already read.
     *
     * @param itemId the item ID
     * @param note   what whoever reported it wrote, or {@code null} when they wrote nothing
     * @param noteBy who wrote that note, which is the guardian when one acted for a member
     * @return the updated item, or empty if the item was not found
     * @throws RefusalResponse if the item is borrowed from a federation partner
     */
    public Optional<InventoryItem> markLost(int itemId, @Nullable String note, @Nullable Integer noteBy) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Mark-lost skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        if (item.borrowed()) {
            throw InventoryRefusal.CUSTODY_BORROWED_NOT_MARKED_LOST.raise();
        }
        Integer custodian = item.custodyStationId();
        Integer holder = custodian != null ? custodian : stationOf(item);
        inventoryRepository.updateCustody(itemId, ItemCustody.LOST, holder, item.assignedTo(), null);
        inventoryRepository.setLostNote(itemId, note, noteBy);
        log.info("Item {} marked lost (was {})", itemId, item.custody());
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Takes a missing item off the record of whoever it was on, and leaves it missing.
     *
     * <p>Putting a record right settles whose the piece was, and settles nothing whatever about
     * where the piece is. Sending it to a store the way an ordinary correction does would grow the
     * shelf by a thing nobody can find, and the station would go looking for it on the day it is
     * handed out. The loss outlives the record it was written against, the reporter's note with it,
     * and the piece stays missing with nobody named against it until it turns up.
     *
     * @param itemId the item ID
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> releaseLost(int itemId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Lost-release skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        closeCurrentSpell(item);
        Integer custodian = item.custodyStationId();
        Integer holder = custodian != null ? custodian : stationOf(item);
        inventoryRepository.updateCustody(itemId, ItemCustody.LOST, holder, null, null);
        log.info("Item {} came off member {}'s record and stays missing", itemId, item.assignedTo());
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Records that a missing item has turned up again. It goes back to whoever it was still on the
     * record of, or to its store when nobody had it. The loss note goes with the loss.
     *
     * @param itemId the item ID
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> markFound(int itemId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Mark-found skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        Integer holder = item.assignedTo();
        if (holder != null) {
            inventoryRepository.updateCustodyFoundAgain(itemId, ItemCustody.WITH_MEMBER, stationOf(item), holder);
        } else {
            ItemCustody resting = restingCustody(item);
            inventoryRepository.updateCustodyFoundAgain(
                    itemId, resting, resting == ItemCustody.AT_STATION ? stationOf(item) : null, null);
        }
        inventoryRepository.setLostNote(itemId, null, null);
        log.info("Item {} marked found", itemId);
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Records that a federation partner has the item. The lending flow keeps its own status; this
     * is only where the item is while that status stands.
     *
     * <p>Two stations are named and they are different questions. The custody station stays the
     * lender, because that is what keeps the piece in the lender's own lists while it is away. The
     * partner station is who actually has it, which until now lived in the lending request, one join
     * away from anything asking where a radio is.
     *
     * @param itemId           the item ID
     * @param partnerStationId the partner taking it, or {@code null} when the partner is on another
     *                         instance and has no row here
     * @return the updated item, or empty if the item was not found
     * @throws RefusalResponse if the item is reported missing
     */
    public Optional<InventoryItem> lendToPartner(int itemId, Integer partnerStationId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Lend skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        requireNotLost(item);
        closeCurrentSpell(item);
        inventoryRepository.updateCustody(
                itemId, ItemCustody.WITH_PARTNER, stationOf(item), null, null, partnerStationId);
        log.info("Item {} lent to federation partner {}", itemId, partnerStationId);
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Records that a federation partner has given the item back.
     *
     * @param itemId the item ID
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> returnFromPartner(int itemId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty() || found.get().custody() != ItemCustody.WITH_PARTNER) return Optional.empty();
        writeResting(found.get());
        log.info("Item {} returned from a federation partner", itemId);
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Puts an item into the custody a movement step names.
     *
     * <p>The step says what custody its subject item is in once it is acknowledged, and this is
     * where that sentence becomes a row. Which custody is legal for a step is settled when the step
     * is written, so anything arriving here is a custody a flow may ask for.
     *
     * @param itemId        the item the step is about
     * @param custody       the custody the step names
     * @param memberId      the movement's member, needed only when the step hands the item to them
     * @param movementId    the movement, needed only when the step puts the item in the post
     * @param stepStationId the station running the movement, which is where the item is once a step leaves
     *                      it at a station; for a cluster's gear it differs from the inventory's station
     * @return the updated item, or empty if the item was not found
     * @throws RefusalResponse if the item is reported missing, or the step hands it to a member the
     *                         movement does not name
     */
    public Optional<InventoryItem> applyStepCustody(
            int itemId,
            ItemCustody custody,
            @Nullable Integer memberId,
            @Nullable Integer movementId,
            @Nullable Integer stepStationId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Step custody skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        requireNotLost(item);
        if (custody == ItemCustody.WITH_MEMBER) {
            if (memberId == null) throw InventoryRefusal.CUSTODY_STEP_NAMES_NO_MEMBER.raise();
            return writeAssignment(item, memberId, "");
        }

        closeCurrentSpell(item);
        Integer stationId =
                custody == ItemCustody.AT_STATION ? (stepStationId != null ? stepStationId : stationOf(item)) : null;
        Integer movement = custody == ItemCustody.IN_TRANSIT ? movementId : null;
        inventoryRepository.updateCustody(itemId, custody, stationId, null, movement);
        log.info("Item {} moved to {} by a movement step (was {})", itemId, custody, item.custody());
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Puts an item back in its owner's own store, whoever was holding it and wherever it sat.
     *
     * <p>Not the same as taking it back, which returns gear to the store of the station that holds it. This
     * one is for when the holding stops entirely: the station that had it is no longer connected to the
     * owner, so there is no store of its at which the item could rest. A piece reported missing stays
     * missing, now on nobody's record but its owner's.
     *
     * @param itemId the item
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> returnToOwner(int itemId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Return to owner skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        closeCurrentSpell(item);
        boolean missing = item.custody() == ItemCustody.LOST || item.lostAt() != null;
        inventoryRepository.updateCustody(
                itemId, missing ? ItemCustody.LOST : ItemCustody.WITH_OWNER, null, null, null);
        inventoryRepository.setItemContainer(itemId, null);
        log.info("Item {} returned to its owner (was {})", itemId, item.custody());
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * The same, for a caller with no movement behind it, which falls back to the item's own station.
     *
     * @param itemId     the item
     * @param custody    where it lands
     * @param memberId   the member receiving it, when the step hands it to one
     * @param movementId the movement, when it is going into the post
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> applyStepCustody(
            int itemId, ItemCustody custody, @Nullable Integer memberId, @Nullable Integer movementId) {
        return applyStepCustody(itemId, custody, memberId, movementId, null);
    }

    /**
     * The custody an item falls back to when nobody in particular has it: with the owner when the
     * station owns it, and at the station when anybody else does. A borrowed piece rests at the
     * station that borrowed it for the same reason a cluster's jacket does, which is that the store
     * it is lying in is that station's own.
     *
     * @param item the item
     * @return the resting custody
     */
    public static ItemCustody restingCustody(InventoryItem item) {
        return item.ownedByStation() ? ItemCustody.WITH_OWNER : ItemCustody.AT_STATION;
    }

    private void writeResting(InventoryItem item) {
        ItemCustody resting = restingCustody(item);
        Integer stationId = resting == ItemCustody.AT_STATION ? stationOf(item) : null;
        inventoryRepository.updateCustody(item.id(), resting, stationId, null, null);
    }

    private void closeCurrentSpell(InventoryItem item) {
        Integer holder = item.assignedTo();
        if (holder != null) {
            inventoryRepository.returnHistory(item.id(), holder);
        }
    }

    /**
     * The station an item's custody runs through. An item that exists always sits in an inventory
     * and an inventory always belongs to a station, so there is no absent case to handle here.
     */
    private int stationOf(InventoryItem item) {
        return inventoryRepository
                .findStationIdByItem(item.id())
                .orElseThrow(() -> new IllegalStateException("Item " + item.id() + " sits in no inventory"));
    }
}
