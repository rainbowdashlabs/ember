/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.Procurement;
import dev.chojo.ember.feature.inventory.repository.ProcurementRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class ProcurementService {
    private final ProcurementRepository procurementRepository;
    private final InventoryService inventoryService;

    @Inject
    public ProcurementService(ProcurementRepository procurementRepository, InventoryService inventoryService) {
        this.procurementRepository = procurementRepository;
        this.inventoryService = inventoryService;
    }

    public Procurement create(int stationId, int inventoryId, int memberId, Integer sizeId, String notes) {
        return procurementRepository.create(stationId, inventoryId, memberId, sizeId, notes);
    }

    public Optional<Procurement> findById(int id) {
        return procurementRepository.findById(id);
    }

    public List<Procurement> findByStation(int stationId) {
        return procurementRepository.findByStation(stationId);
    }

    public List<Procurement> findOpen(int stationId) {
        return procurementRepository.findOpen(stationId);
    }

    public boolean fulfill(int id) {
        var procurement = procurementRepository.findById(id);
        if (procurement.isEmpty()) return false;
        var proc = procurement.get();

        // Create item and assign to member
        var inv = inventoryService.findById(proc.inventoryId());
        if (inv.isPresent()) {
            var item = inventoryService.createItem(
                    proc.inventoryId(), "", inv.get().name(), proc.sizeId(), "{}");
            inventoryService.assignItem(item.id(), proc.memberId(), "");
        }

        return procurementRepository.fulfill(id);
    }

    public boolean delete(int id) {
        return procurementRepository.delete(id);
    }
}
