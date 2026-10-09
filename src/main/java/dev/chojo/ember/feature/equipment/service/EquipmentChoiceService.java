/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.feature.equipment.entity.EquipmentChoices;
import dev.chojo.ember.feature.equipment.entity.EquipmentChoices.ArtChoice;
import dev.chojo.ember.feature.equipment.entity.EquipmentChoices.InventoryChoice;
import dev.chojo.ember.feature.equipment.entity.EquipmentChoices.ItemChoice;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.service.InventoryArtService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What a line of an appointment can be written for, read for whoever plans the appointment.
 *
 * <p>Planning takes the right to edit the appointment and not the right to read the inventory, so
 * this answers from the inventory's own services but hands on only what the pickers of a line show.
 */
@Singleton
public class EquipmentChoiceService {

    private final InventoryService inventoryService;
    private final InventoryArtService artService;

    @Inject
    public EquipmentChoiceService(InventoryService inventoryService, InventoryArtService artService) {
        this.inventoryService = inventoryService;
        this.artService = artService;
    }

    /**
     * The station's gear as the pickers of a line offer it.
     *
     * @param stationId the station the appointment belongs to
     * @return its inventories, the kinds of its mixed inventories and its pieces
     */
    public EquipmentChoices choicesFor(int stationId) {
        List<Inventory> inventories = inventoryService.findByStation(stationId);
        Map<Integer, String> sizeLabels = inventoryService.findAllSizesByStation(stationId).stream()
                .collect(Collectors.toMap(InventorySize::id, InventorySize::label));
        return new EquipmentChoices(
                inventories.stream().map(InventoryChoice::of).toList(),
                inventories.stream()
                        .filter(inventory -> !inventory.homogeneous())
                        .flatMap(inventory -> artService.findByInventory(inventory.id()).stream())
                        .map(ArtChoice::of)
                        .toList(),
                inventoryService.findAllItemsByStation(stationId).stream()
                        .map(item -> ItemChoice.of(item, item.sizeId() == null ? null : sizeLabels.get(item.sizeId())))
                        .toList());
    }
}
