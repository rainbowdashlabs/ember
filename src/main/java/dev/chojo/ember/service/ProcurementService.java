/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.Procurement;
import dev.chojo.ember.repository.ProcurementRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class ProcurementService {
    private final ProcurementRepository procurementRepository;

    @Inject
    public ProcurementService(ProcurementRepository procurementRepository) {
        this.procurementRepository = procurementRepository;
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
        return procurementRepository.fulfill(id);
    }

    public boolean delete(int id) {
        return procurementRepository.delete(id);
    }
}
