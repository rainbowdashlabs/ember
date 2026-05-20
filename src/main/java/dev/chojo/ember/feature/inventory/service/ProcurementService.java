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

/**
 * Service for managing equipment procurement requests.
 * Handles creating, fulfilling, and deleting procurement requests, including automatic item creation on fulfillment.
 */
@Singleton
public class ProcurementService {
    private final ProcurementRepository procurementRepository;
    private final InventoryService inventoryService;

    @Inject
    public ProcurementService(ProcurementRepository procurementRepository, InventoryService inventoryService) {
        this.procurementRepository = procurementRepository;
        this.inventoryService = inventoryService;
    }

    /**
     * Creates a new procurement request.
     *
     * @param stationId   the station ID
     * @param inventoryId the inventory the equipment is from
     * @param memberId    the member the equipment is for
     * @param sizeId      the requested size, or {@code null}
     * @param notes       additional notes
     * @return the created procurement
     */
    public Procurement create(int stationId, int inventoryId, int memberId, Integer sizeId, String notes) {
        return procurementRepository.create(stationId, inventoryId, memberId, sizeId, notes);
    }

    /**
     * Finds a procurement request by its ID.
     *
     * @param id the procurement ID
     * @return the procurement, or empty if not found
     */
    public Optional<Procurement> findById(int id) {
        return procurementRepository.findById(id);
    }

    /**
     * Finds all procurement requests for a station.
     *
     * @param stationId the station ID
     * @return list of procurements
     */
    public List<Procurement> findByStation(int stationId) {
        return procurementRepository.findByStation(stationId);
    }

    /**
     * Finds open (unfulfilled) procurement requests for a station.
     *
     * @param stationId the station ID
     * @return list of open procurements
     */
    public List<Procurement> findOpen(int stationId) {
        return procurementRepository.findOpen(stationId);
    }

    /**
     * Fulfills a procurement request by creating a new inventory item and assigning it to the member.
     *
     * @param id the procurement ID
     * @return {@code true} if the procurement was fulfilled
     */
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

    /**
     * Deletes a procurement request.
     *
     * @param id the procurement ID
     * @return {@code true} if the procurement was deleted
     */
    public boolean delete(int id) {
        return procurementRepository.delete(id);
    }
}
