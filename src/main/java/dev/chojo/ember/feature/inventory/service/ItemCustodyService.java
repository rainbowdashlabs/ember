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

        closeCurrentSpell(item);
        inventoryRepository.updateCustody(itemId, ItemCustody.WITH_MEMBER, stationOf(item), memberId, null);
        inventoryRepository.createHistory(itemId, memberId, memberName != null ? memberName : "");
        log.info("Item {} handed to member {} ('{}')", itemId, memberId, memberName);
        return inventoryRepository.findItemById(itemId);
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
