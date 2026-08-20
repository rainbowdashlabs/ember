/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ExchangeRequested;
import dev.chojo.ember.event.events.ExchangeStatusChanged;
import dev.chojo.ember.feature.inventory.entity.ExchangeLog;
import dev.chojo.ember.feature.inventory.entity.ExchangeRequest;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.repository.ExchangeRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing equipment exchange requests.
 * Handles the full exchange lifecycle including item reassignment when exchanges are completed.
 */
@Singleton
public class ExchangeService {
    private static final Logger log = LoggerFactory.getLogger(ExchangeService.class);
    private final ExchangeRepository exchangeRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryService inventoryService;
    private final DomainEventBus eventBus;

    @Inject
    public ExchangeService(
            ExchangeRepository exchangeRepository,
            InventoryRepository inventoryRepository,
            InventoryService inventoryService,
            DomainEventBus eventBus) {
        this.exchangeRepository = exchangeRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryService = inventoryService;
        this.eventBus = eventBus;
    }

    /**
     * Creates a new exchange request.
     *
     * @param stationId   the station ID
     * @param memberId    the member requesting the exchange
     * @param itemId      the current item, or {@code null}
     * @param inventoryId the inventory ID
     * @param oldSizeId   the current size, or {@code null}
     * @param newSizeId   the desired size, or {@code null}
     * @param reason      the reason for the exchange
     * @param createdBy   who created the request on behalf of the member, or {@code null}
     * @return the created exchange request
     */
    public ExchangeRequest create(
            int stationId,
            int memberId,
            String memberName,
            Integer itemId,
            int inventoryId,
            Integer oldSizeId,
            Integer newSizeId,
            String reason,
            Integer createdBy) {
        var exchange = exchangeRepository.create(
                stationId, memberId, itemId, inventoryId, oldSizeId, newSizeId, reason, createdBy);
        String inventoryName =
                inventoryRepository.findById(inventoryId).map(Inventory::name).orElse("?");
        eventBus.publish(new ExchangeRequested(
                stationId, exchange.id(), memberId, memberName, inventoryId, inventoryName, reason));
        log.info(
                "Created exchange {} for member {} on inventory {} (itemId={}, oldSizeId={}, newSizeId={}, station={})",
                exchange.id(),
                memberId,
                inventoryId,
                itemId,
                oldSizeId,
                newSizeId,
                stationId);
        return exchange;
    }

    /**
     * Finds an exchange request by its ID.
     *
     * @param id the exchange request ID
     * @return the exchange request, or empty if not found
     */
    public Optional<ExchangeRequest> findById(int id) {
        return exchangeRepository.findById(id);
    }

    /**
     * Finds all exchange requests for a station.
     *
     * @param stationId the station ID
     * @return list of exchange requests
     */
    public List<ExchangeRequest> findByStation(int stationId) {
        return exchangeRepository.findByStation(stationId);
    }

    /**
     * Finds all exchange requests for a specific member.
     *
     * @param memberId the member ID
     * @return list of exchange requests
     */
    public List<ExchangeRequest> findByMember(int memberId) {
        return exchangeRepository.findByMember(memberId);
    }

    /**
     * Updates the status of an exchange request without specifying a replacement item.
     *
     * @param id        the exchange request ID
     * @param newStatus the new status
     * @param changedBy the member making the change
     * @param note      an optional note
     * @return the updated exchange request
     */
    public ExchangeRequest updateStatus(int id, ExchangeStatus newStatus, int changedBy, String note) {
        return updateStatus(id, newStatus, changedBy, note, null);
    }

    /**
     * Updates the status of an exchange request. When transitioning to {@link ExchangeStatus#DONE},
     * completes the exchange by handling old and new item assignments.
     *
     * @param id              the exchange request ID
     * @param newStatus       the new status
     * @param changedBy       the member making the change
     * @param note            an optional note
     * @param exchangedItemId the replacement item ID, or {@code null}
     * @return the updated exchange request
     * @throws BadRequestResponse if the exchange request is not found
     */
    public ExchangeRequest updateStatus(
            int id, ExchangeStatus newStatus, int changedBy, String note, Integer exchangedItemId) {
        var request =
                exchangeRepository.findById(id).orElseThrow(() -> new BadRequestResponse("Exchange request not found"));
        var oldStatus = request.status();

        if (newStatus == ExchangeStatus.DONE) {
            completeExchange(request, exchangedItemId);
            exchangeRepository.updateStatusWithExchangedItem(id, newStatus, exchangedItemId);
        } else {
            exchangeRepository.updateStatus(id, newStatus);
        }

        exchangeRepository.createLog(id, oldStatus, newStatus, changedBy, note);
        var updated = exchangeRepository.findById(id).orElseThrow();
        String inventoryName = inventoryRepository
                .findById(updated.inventoryId())
                .map(Inventory::name)
                .orElse("?");
        eventBus.publish(new ExchangeStatusChanged(
                updated.stationId(),
                updated.id(),
                updated.memberId(),
                null,
                updated.inventoryId(),
                inventoryName,
                newStatus));
        log.info(
                "Updated exchange {} status {} -> {} by member {} (exchangedItemId={}, station={})",
                id,
                oldStatus,
                newStatus,
                changedBy,
                exchangedItemId,
                updated.stationId());
        return updated;
    }

    /**
     * Finds the status change logs for an exchange request.
     *
     * @param requestId the exchange request ID
     * @return list of log entries
     */
    public List<ExchangeLog> findLogs(int requestId) {
        return exchangeRepository.findLogs(requestId);
    }

    /**
     * Deletes an exchange request.
     *
     * @param id the exchange request ID
     * @return {@code true} if the request was deleted
     */
    public boolean delete(int id) {
        boolean deleted = exchangeRepository.delete(id);
        if (deleted) log.info("Deleted exchange request {}", id);
        else log.warn("Delete of exchange request {} did not change any row", id);
        return deleted;
    }

    public int countPendingByStation(int stationId) {
        return exchangeRepository.countPendingByStation(stationId);
    }

    /**
     * Completes an exchange by disposing of the old item and assigning the replacement to the member.
     *
     * <p>What happens to the old item follows from that item's own owner, never from the type of the
     * inventory it sits in. An inventory may hold both owners at once, and reading the inventory
     * instead of the item is how gear the station does not own used to end up in the station's free
     * pool.
     *
     * @param request         the exchange request being completed
     * @param exchangedItemId the replacement item ID, or {@code null}
     */
    private void completeExchange(ExchangeRequest request, Integer exchangedItemId) {
        if (request.itemId() != null) {
            var item = inventoryRepository.findItemById(request.itemId()).orElse(null);
            if (item != null && !item.ownedByStation()) {
                // Owned by the body above the station: it leaves this station's stock entirely
                inventoryRepository.deleteItem(item.id());
            } else if (item != null) {
                // Station-owned: unassign the old item, which returns it to the free pool with history
                inventoryService.assignItem(item.id(), null, "");
            }
        }

        // Handle new item assignment (with history tracking)
        if (exchangedItemId != null) {
            inventoryService.assignItem(exchangedItemId, request.memberId(), "");
        }
    }
}
