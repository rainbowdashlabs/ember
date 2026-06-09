/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryItemHistory;
import dev.chojo.ember.feature.inventory.entity.InventoryRequirement;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventorySummary;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing inventories, their items, sizes, history, and requirements.
 * Provides business logic on top of the repository, including item assignment with history tracking.
 */
@Singleton
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    @Inject
    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    // -- Inventories --

    /**
     * Finds all inventories for a station.
     *
     * @param stationId the station ID
     * @return list of inventories
     */
    public List<Inventory> findByStation(int stationId) {
        return inventoryRepository.findByStation(stationId);
    }

    public List<InventorySummary> findSummaries(int stationId) {
        return inventoryRepository.findSummariesByStation(stationId);
    }

    public List<InventoryItem> findAllItemsByStation(int stationId) {
        return inventoryRepository.findItemsByStation(stationId);
    }

    public List<InventorySize> findAllSizesByStation(int stationId) {
        return inventoryRepository.findSizesByStation(stationId);
    }

    /**
     * Finds an inventory by its ID.
     *
     * @param id the inventory ID
     * @return the inventory, or empty if not found
     */
    public Optional<Inventory> findById(int id) {
        return inventoryRepository.findById(id);
    }

    /**
     * Finds all sizes for an inventory.
     *
     * @param inventoryId the inventory ID
     * @return list of sizes
     */
    public List<InventorySize> findSizes(int inventoryId) {
        return inventoryRepository.findSizes(inventoryId);
    }

    /**
     * Creates a new inventory.
     *
     * @param stationId     the station ID
     * @param name          the inventory name
     * @param inventoryType the inventory type
     * @param hasSizes      whether the inventory supports sizes
     * @return the created inventory
     */
    public Inventory create(int stationId, String name, InventoryType inventoryType, boolean hasSizes) {
        return inventoryRepository.create(stationId, name, inventoryType, hasSizes);
    }

    /**
     * Updates an existing inventory.
     *
     * @param id            the inventory ID
     * @param name          the new name
     * @param inventoryType the new type
     * @param hasSizes      whether sizes are supported
     * @return the updated inventory, or empty if not found
     */
    public Optional<Inventory> update(int id, String name, InventoryType inventoryType, boolean hasSizes) {
        if (inventoryRepository.update(id, name, inventoryType, hasSizes)) {
            return inventoryRepository.findById(id);
        }
        return Optional.empty();
    }

    /**
     * Deletes an inventory.
     *
     * @param id the inventory ID
     * @return {@code true} if deleted
     */
    public boolean delete(int id) {
        return inventoryRepository.delete(id);
    }

    // -- Sizes --

    /**
     * Creates a new size and returns all sizes for the inventory.
     *
     * @param inventoryId the inventory ID
     * @param label       the size label
     * @param position    the sort position
     * @param note        an optional note
     * @return the updated list of all sizes for the inventory
     */
    public List<InventorySize> createSize(int inventoryId, String label, int position, String note) {
        inventoryRepository.createSize(inventoryId, label, position, note);
        return inventoryRepository.findSizes(inventoryId);
    }

    /**
     * Updates a size and returns all sizes for the inventory.
     *
     * @param inventoryId the inventory ID
     * @param sizeId      the size ID
     * @param label       the new label
     * @param position    the new position
     * @param note        the new note
     * @return the updated list of sizes, or empty if the size was not found
     */
    public Optional<List<InventorySize>> updateSize(
            int inventoryId, int sizeId, String label, int position, String note) {
        if (inventoryRepository.updateSize(sizeId, label, position, note)) {
            return Optional.of(inventoryRepository.findSizes(inventoryId));
        }
        return Optional.empty();
    }

    /**
     * Deletes a size and returns the remaining sizes for the inventory.
     *
     * @param inventoryId the inventory ID
     * @param sizeId      the size ID to delete
     * @return the remaining sizes, or empty if the size was not found
     */
    public Optional<List<InventorySize>> deleteSize(int inventoryId, int sizeId) {
        if (inventoryRepository.deleteSize(sizeId)) {
            return Optional.of(inventoryRepository.findSizes(inventoryId));
        }
        return Optional.empty();
    }

    // -- Items --

    /**
     * Finds all items assigned to a member.
     *
     * @param memberId the member ID
     * @return list of assigned items
     */
    public List<InventoryItem> findItemsByMember(int memberId) {
        return inventoryRepository.findItemsByMember(memberId);
    }

    public int countItemsByMember(int memberId) {
        return inventoryRepository.countItemsByMember(memberId);
    }

    /**
     * Finds all items in an inventory.
     *
     * @param inventoryId the inventory ID
     * @return list of items
     */
    public List<InventoryItem> findItems(int inventoryId) {
        return inventoryRepository.findItems(inventoryId);
    }

    /**
     * Finds an inventory item by its ID.
     *
     * @param id the item ID
     * @return the item, or empty if not found
     */
    public Optional<InventoryItem> findItemById(int id) {
        return inventoryRepository.findItemById(id);
    }

    public Optional<InventoryItem> findByInternalId(int stationId, String internalId) {
        return inventoryRepository.findByInternalId(stationId, internalId);
    }

    /**
     * Creates a new inventory item with the default item source.
     *
     * @param inventoryId the inventory ID
     * @param internalId  the internal identifier
     * @param name        the item name
     * @param sizeId      the size ID, or {@code null}
     * @param metadata    JSON metadata
     * @return the created item
     */
    public InventoryItem createItem(int inventoryId, String internalId, String name, Integer sizeId, String metadata) {
        return inventoryRepository.createItem(inventoryId, internalId, name, sizeId, metadata);
    }

    /**
     * Creates a new inventory item with a specified item source.
     *
     * @param inventoryId the inventory ID
     * @param internalId  the internal identifier
     * @param name        the item name
     * @param sizeId      the size ID, or {@code null}
     * @param metadata    JSON metadata
     * @param itemSource  the item source
     * @return the created item
     */
    public InventoryItem createItem(
            int inventoryId,
            String internalId,
            String name,
            Integer sizeId,
            String metadata,
            InventoryItem.ItemSource itemSource) {
        return inventoryRepository.createItem(inventoryId, internalId, name, sizeId, metadata, itemSource);
    }

    /**
     * Updates an inventory item.
     *
     * @param id         the item ID
     * @param internalId the new internal identifier
     * @param name       the new name
     * @param sizeId     the new size ID, or {@code null}
     * @param metadata   the new JSON metadata
     * @return the updated item, or empty if not found
     */
    public Optional<InventoryItem> updateItem(int id, String internalId, String name, Integer sizeId, String metadata) {
        if (inventoryRepository.updateItem(id, internalId, name, sizeId, metadata)) {
            return inventoryRepository.findItemById(id);
        }
        return Optional.empty();
    }

    /**
     * Assigns an item to a member (or unassigns it) with full history tracking.
     * If the item is currently assigned, the existing history entry is closed. If a new member is specified,
     * a new history entry is created.
     *
     * @param itemId     the item ID
     * @param memberId   the member to assign to, or {@code null} to unassign
     * @param memberName the member's display name for history
     * @return the updated item, or empty if the item was not found
     */
    public Optional<InventoryItem> assignItem(int itemId, Integer memberId, String memberName) {
        // If previously assigned, close that history entry
        var item = inventoryRepository.findItemById(itemId);
        if (item.isEmpty()) return Optional.empty();

        var current = item.get();
        if (current.assignedTo() != null) {
            inventoryRepository.returnHistory(itemId, current.assignedTo());
        }

        // Assign new member
        if (inventoryRepository.assignItem(itemId, memberId)) {
            // Create history entry for new assignment
            if (memberId != null) {
                inventoryRepository.createHistory(itemId, memberId, memberName != null ? memberName : "");
            }
            return inventoryRepository.findItemById(itemId);
        }
        return Optional.empty();
    }

    /**
     * Marks an item as lost.
     *
     * @param id the item ID
     * @return the updated item, or empty if not found
     */
    public Optional<InventoryItem> markLost(int id) {
        if (inventoryRepository.markLost(id)) {
            return inventoryRepository.findItemById(id);
        }
        return Optional.empty();
    }

    /**
     * Marks a previously lost item as found.
     *
     * @param id the item ID
     * @return the updated item, or empty if not found
     */
    public Optional<InventoryItem> markFound(int id) {
        if (inventoryRepository.markFound(id)) {
            return inventoryRepository.findItemById(id);
        }
        return Optional.empty();
    }

    /**
     * Deletes an inventory item.
     *
     * @param id the item ID
     * @return {@code true} if deleted
     */
    public boolean deleteItem(int id) {
        return inventoryRepository.deleteItem(id);
    }

    // -- History --

    /**
     * Finds the assignment history for an item.
     *
     * @param itemId the item ID
     * @return list of history entries
     */
    public List<InventoryItemHistory> findHistory(int itemId) {
        return inventoryRepository.findHistory(itemId);
    }

    // -- Requirements --

    /**
     * Finds all inventory requirements for a station.
     *
     * @param stationId the station ID
     * @return list of requirements
     */
    public List<InventoryRequirement> findAllRequirementsByStation(int stationId) {
        return inventoryRepository.findAllRequirementsByStation(stationId);
    }

    /**
     * Creates a new inventory requirement.
     *
     * @param inventoryId the inventory ID
     * @param userType    the user type name, or {@code null} if not user-type-based
     * @param groupId     the group ID (0 if not group-based)
     * @param quantity    the required quantity
     * @return the created requirement
     */
    public InventoryRequirement createRequirement(int inventoryId, String userType, int groupId, int quantity) {
        return inventoryRepository.createRequirement(inventoryId, userType, groupId, quantity);
    }

    /**
     * Updates the quantity of an inventory requirement.
     *
     * @param id       the requirement ID
     * @param quantity the new quantity
     * @return {@code true} if updated
     */
    public boolean updateRequirement(int id, int quantity) {
        return inventoryRepository.updateRequirement(id, quantity);
    }

    /**
     * Updates the display position of an inventory requirement.
     *
     * @param id       the requirement ID
     * @param position the new position
     * @return {@code true} if updated
     */
    public boolean updateRequirementPosition(int id, int position) {
        return inventoryRepository.updateRequirementPosition(id, position);
    }

    /**
     * Deletes an inventory requirement.
     *
     * @param id the requirement ID
     * @return {@code true} if deleted
     */
    public boolean deleteRequirement(int id) {
        return inventoryRepository.deleteRequirement(id);
    }
}
