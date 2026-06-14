/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ProcurementCreated;
import dev.chojo.ember.event.events.ProcurementFulfilled;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.Procurement;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.repository.ProcurementRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class ProcurementService {
    private final ProcurementRepository procurementRepository;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final DomainEventBus eventBus;

    @Inject
    public ProcurementService(
            ProcurementRepository procurementRepository,
            InventoryService inventoryService,
            InventoryRepository inventoryRepository,
            DomainEventBus eventBus) {
        this.procurementRepository = procurementRepository;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.eventBus = eventBus;
    }

    public Procurement create(int stationId, int inventoryId, int memberId, Integer sizeId, String notes) {
        var procurement = procurementRepository.create(stationId, inventoryId, memberId, sizeId, notes);
        String inventoryName =
                inventoryRepository.findById(inventoryId).map(Inventory::name).orElse("?");
        eventBus.publish(new ProcurementCreated(stationId, memberId, inventoryId, inventoryName));
        return procurement;
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

        var inv = inventoryService.findById(proc.inventoryId());
        if (inv.isPresent()) {
            var item = inventoryService.createItem(
                    proc.inventoryId(), "", inv.get().name(), proc.sizeId(), null);
            inventoryService.assignItem(item.id(), proc.memberId(), "");
        }

        if (procurementRepository.fulfill(id)) {
            String inventoryName = inventoryRepository
                    .findById(proc.inventoryId())
                    .map(Inventory::name)
                    .orElse("?");
            eventBus.publish(
                    new ProcurementFulfilled(proc.stationId(), proc.memberId(), proc.inventoryId(), inventoryName));
            return true;
        }
        return false;
    }

    public boolean delete(int id) {
        return procurementRepository.delete(id);
    }
}
