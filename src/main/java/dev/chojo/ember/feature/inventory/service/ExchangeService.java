/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.feature.inventory.entity.ExchangeLog;
import dev.chojo.ember.feature.inventory.entity.ExchangeRequest;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.repository.ExchangeRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class ExchangeService {
    private final ExchangeRepository exchangeRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;

    @Inject
    public ExchangeService(
            ExchangeRepository exchangeRepository,
            InventoryRepository inventoryRepository,
            InventoryService inventoryService) {
        this.exchangeRepository = exchangeRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
    }

    public ExchangeRequest create(
            int stationId,
            int memberId,
            Integer itemId,
            int inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Integer createdBy) {
        return exchangeRepository.create(
                stationId, memberId, itemId, inventoryId, oldSizeId, newSizeId, reason, createdBy);
    }

    public Optional<ExchangeRequest> findById(int id) {
        return exchangeRepository.findById(id);
    }

    public List<ExchangeRequest> findByStation(int stationId) {
        return exchangeRepository.findByStation(stationId);
    }

    public List<ExchangeRequest> findByMember(int memberId) {
        return exchangeRepository.findByMember(memberId);
    }

    public ExchangeRequest updateStatus(int id, ExchangeStatus newStatus, int changedBy, String note) {
        return updateStatus(id, newStatus, changedBy, note, null);
    }

    public ExchangeRequest updateStatus(
            int id, ExchangeStatus newStatus, int changedBy, String note, Integer exchangedItemId) {
        var request =
                exchangeRepository.findById(id).orElseThrow(() -> new BadRequestResponse("Exchange request not found"));
        var oldStatus = request.status();

        if (newStatus == ExchangeStatus.EXCHANGED) {
            completeExchange(request, exchangedItemId);
            exchangeRepository.updateStatusWithExchangedItem(id, newStatus, exchangedItemId);
        } else {
            exchangeRepository.updateStatus(id, newStatus);
        }

        exchangeRepository.createLog(id, oldStatus, newStatus, changedBy, note);
        return exchangeRepository.findById(id).orElseThrow();
    }

    private void completeExchange(ExchangeRequest request, Integer exchangedItemId) {
        var inventory = inventoryRepository.findById(request.inventoryId()).orElse(null);
        if (inventory == null) return;

        var type = inventory.inventoryType();

        // Handle old item
        if (request.itemId() != null) {
            if (type == InventoryType.EXTERNAL) {
                // External: delete old item
                inventoryRepository.deleteItem(request.itemId());
            } else {
                // Internal/Mixed: unassign old item (returns to free pool, with history)
                inventoryService.assignItem(request.itemId(), null, "");
            }
        }

        // Handle new item assignment (with history tracking)
        if (exchangedItemId != null) {
            inventoryService.assignItem(exchangedItemId, request.memberId(), "");
        }
    }

    public List<ExchangeLog> findLogs(int requestId) {
        return exchangeRepository.findLogs(requestId);
    }

    public boolean delete(int id) {
        return exchangeRepository.delete(id);
    }
}
