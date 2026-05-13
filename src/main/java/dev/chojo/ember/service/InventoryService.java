/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.Inventory;
import dev.chojo.ember.entity.InventoryItem;
import dev.chojo.ember.entity.InventoryItemHistory;
import dev.chojo.ember.entity.InventoryRequirement;
import dev.chojo.ember.entity.InventorySize;
import dev.chojo.ember.entity.InventoryType;
import dev.chojo.ember.repository.InventoryRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    @Inject
    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    // -- Inventories --

    public List<Inventory> findByStation(int stationId) {
        return inventoryRepository.findByStation(stationId);
    }

    public Optional<Inventory> findById(int id) {
        return inventoryRepository.findById(id);
    }

    public List<InventorySize> findSizes(int inventoryId) {
        return inventoryRepository.findSizes(inventoryId);
    }

    public Inventory create(int stationId, String name, InventoryType inventoryType, boolean hasSizes) {
        return inventoryRepository.create(stationId, name, inventoryType, hasSizes);
    }

    public Optional<Inventory> update(int id, String name, InventoryType inventoryType, boolean hasSizes) {
        if (inventoryRepository.update(id, name, inventoryType, hasSizes)) {
            return inventoryRepository.findById(id);
        }
        return Optional.empty();
    }

    public boolean delete(int id) {
        return inventoryRepository.delete(id);
    }

    // -- Sizes --

    public List<InventorySize> createSize(int inventoryId, String label, int position, String note) {
        inventoryRepository.createSize(inventoryId, label, position, note);
        return inventoryRepository.findSizes(inventoryId);
    }

    public Optional<List<InventorySize>> updateSize(
            int inventoryId, int sizeId, String label, int position, String note) {
        if (inventoryRepository.updateSize(sizeId, label, position, note)) {
            return Optional.of(inventoryRepository.findSizes(inventoryId));
        }
        return Optional.empty();
    }

    public Optional<List<InventorySize>> deleteSize(int inventoryId, int sizeId) {
        if (inventoryRepository.deleteSize(sizeId)) {
            return Optional.of(inventoryRepository.findSizes(inventoryId));
        }
        return Optional.empty();
    }

    // -- Items --

    public List<InventoryItem> findItemsByMember(int memberId) {
        return inventoryRepository.findItemsByMember(memberId);
    }

    public List<InventoryItem> findItems(int inventoryId) {
        return inventoryRepository.findItems(inventoryId);
    }

    public Optional<InventoryItem> findItemById(int id) {
        return inventoryRepository.findItemById(id);
    }

    public InventoryItem createItem(int inventoryId, String internalId, String name, Integer sizeId, String metadata) {
        return inventoryRepository.createItem(inventoryId, internalId, name, sizeId, metadata);
    }

    public InventoryItem createItem(
            int inventoryId, String internalId, String name, Integer sizeId, String metadata, String itemSource) {
        return inventoryRepository.createItem(inventoryId, internalId, name, sizeId, metadata, itemSource);
    }

    public Optional<InventoryItem> updateItem(int id, String internalId, String name, Integer sizeId, String metadata) {
        if (inventoryRepository.updateItem(id, internalId, name, sizeId, metadata)) {
            return inventoryRepository.findItemById(id);
        }
        return Optional.empty();
    }

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

    public Optional<InventoryItem> markLost(int id) {
        if (inventoryRepository.markLost(id)) {
            return inventoryRepository.findItemById(id);
        }
        return Optional.empty();
    }

    public Optional<InventoryItem> markFound(int id) {
        if (inventoryRepository.markFound(id)) {
            return inventoryRepository.findItemById(id);
        }
        return Optional.empty();
    }

    public boolean deleteItem(int id) {
        return inventoryRepository.deleteItem(id);
    }

    // -- History --

    public List<InventoryItemHistory> findHistory(int itemId) {
        return inventoryRepository.findHistory(itemId);
    }

    // -- Requirements --

    public List<InventoryRequirement> findAllRequirementsByStation(int stationId) {
        return inventoryRepository.findAllRequirementsByStation(stationId);
    }

    public InventoryRequirement createRequirement(int inventoryId, int roleId, int groupId, int quantity) {
        return inventoryRepository.createRequirement(inventoryId, roleId, groupId, quantity);
    }

    public boolean updateRequirement(int id, int quantity) {
        return inventoryRepository.updateRequirement(id, quantity);
    }

    public boolean updateRequirementPosition(int id, int position) {
        return inventoryRepository.updateRequirementPosition(id, position);
    }

    public boolean deleteRequirement(int id) {
        return inventoryRepository.deleteRequirement(id);
    }
}
