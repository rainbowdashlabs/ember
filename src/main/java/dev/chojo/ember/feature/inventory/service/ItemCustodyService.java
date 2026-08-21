/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Inject
    public ItemCustodyService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Hands an item to a member, closing the spell it was on and opening a new one.
     *
     * @param itemId     the item ID
     * @param memberId   the member receiving it
     * @param memberName the member's display name for the history
     * @return the updated item, or empty if the item was not found
     * @throws BadRequestResponse if the item is in a custody it cannot be handed out of
     */
    public Optional<InventoryItem> assignToMember(int itemId, int memberId, String memberName) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Assign skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        if (!item.custody().assignable()) {
            throw new BadRequestResponse("Item %d cannot be handed out: it is %s"
                    .formatted(itemId, item.custody().name()));
        }

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
     */
    public Optional<InventoryItem> takeBack(int itemId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Take-back skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
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
    public Optional<InventoryItem> placeInContainer(int itemId, Integer containerId) {
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
     * @param itemId the item ID
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> markLost(int itemId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Mark-lost skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        Integer holder = item.custodyStationId() != null ? item.custodyStationId() : stationOf(item);
        inventoryRepository.updateCustody(itemId, ItemCustody.LOST, holder, item.assignedTo(), null);
        log.info("Item {} marked lost (was {})", itemId, item.custody());
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Records that a missing item has turned up again. It goes back to whoever it was still on the
     * record of, or to its store when nobody had it.
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
        if (item.assignedTo() != null) {
            inventoryRepository.updateCustody(
                    itemId, ItemCustody.WITH_MEMBER, stationOf(item), item.assignedTo(), null);
        } else {
            writeResting(item);
        }
        log.info("Item {} marked found", itemId);
        return inventoryRepository.findItemById(itemId);
    }

    /**
     * Records that a federation partner has the item. The lending flow keeps its own status; this
     * is only where the item is while that status stands.
     *
     * @param itemId the item ID
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> lendToPartner(int itemId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Lend skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        closeCurrentSpell(item);
        inventoryRepository.updateCustody(itemId, ItemCustody.WITH_PARTNER, stationOf(item), null, null);
        log.info("Item {} lent to a federation partner", itemId);
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
     *                      it at a station
     * @return the updated item, or empty if the item was not found
     * @throws BadRequestResponse if the step hands an item to a member the movement does not name
     */
    public Optional<InventoryItem> applyStepCustody(
            int itemId, ItemCustody custody, Integer memberId, Integer movementId, Integer stepStationId) {
        var found = inventoryRepository.findItemById(itemId);
        if (found.isEmpty()) {
            log.warn("Step custody skipped: item {} not found", itemId);
            return Optional.empty();
        }
        var item = found.get();
        if (custody == ItemCustody.WITH_MEMBER) {
            if (memberId == null) throw new BadRequestResponse("This step hands the item to a member, but names none");
            return writeAssignment(item, memberId, "");
        }

        closeCurrentSpell(item);
        // The station running the movement, not the one whose inventory the row sits in. For a station's own
        // gear those are the same; for a cluster's they are not, and it is the movement that says where the
        // item has actually got to.
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
     * owner, so there is no store of its at which the item could rest.
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
        inventoryRepository.updateCustody(itemId, ItemCustody.WITH_OWNER, null, null, null);
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
            int itemId, ItemCustody custody, Integer memberId, Integer movementId) {
        return applyStepCustody(itemId, custody, memberId, movementId, null);
    }

    /**
     * The custody an item falls back to when nobody in particular has it: with the owner when the
     * station owns it, and at the station when the body above it does.
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
        if (item.assignedTo() != null) {
            inventoryRepository.returnHistory(item.id(), item.assignedTo());
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
