/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.LendingMessageSent;
import dev.chojo.ember.event.events.LendingRequested;
import dev.chojo.ember.event.events.LendingStatusChanged;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Business logic for cross-station inventory lending.
 */
@Singleton
public class LendingService {
    private static final Logger log = LoggerFactory.getLogger(LendingService.class);

    private final LendingRepository repository;
    private final FederationHttpClient httpClient;
    private final FederationService federationService;
    private final StationRepository stationRepository;
    private final InventoryRepository inventoryRepository;
    private final DomainEventBus eventBus;

    @Inject
    public LendingService(
            LendingRepository repository,
            FederationHttpClient httpClient,
            FederationService federationService,
            StationRepository stationRepository,
            InventoryRepository inventoryRepository,
            DomainEventBus eventBus) {
        this.repository = repository;
        this.httpClient = httpClient;
        this.federationService = federationService;
        this.stationRepository = stationRepository;
        this.inventoryRepository = inventoryRepository;
        this.eventBus = eventBus;
    }

    private String stationName(int stationId) {
        return stationRepository.findById(stationId).map(Station::name).orElse("?");
    }

    private String buildItemSummary(int requestId) {
        var items = repository.findItemsByRequest(requestId);
        var parts = new ArrayList<String>();
        for (var item : items) {
            String name = item.inventoryId() != null
                    ? inventoryRepository
                            .findById(item.inventoryId())
                            .map(Inventory::name)
                            .orElse("?")
                    : "?";
            parts.add(item.quantity() + "x " + name);
        }
        return String.join(", ", parts);
    }

    private void publishStatusChange(LendingRequest request, int actingStationId, LendingStatus status) {
        int targetStationId = request.requestingStationId() == actingStationId
                ? request.owningStationId()
                : request.requestingStationId();
        eventBus.publish(new LendingStatusChanged(
                actingStationId,
                targetStationId,
                request.id(),
                NotificationType.LENDING_STATUS_CHANGE,
                stationName(actingStationId),
                status));
    }

    // -- Requests --

    public LendingRequest createRequest(
            int requestingStationId, int owningStationId, LocalDate dateFrom, LocalDate dateTo, int createdBy) {
        var request = repository.createRequest(requestingStationId, owningStationId, dateFrom, dateTo, createdBy);
        eventBus.publish(new LendingRequested(
                requestingStationId,
                owningStationId,
                request.id(),
                stationName(requestingStationId),
                buildItemSummary(request.id())));
        return request;
    }

    public Optional<LendingRequest> findRequest(int id) {
        return repository.findRequestById(id);
    }

    public List<LendingRequest> findRequestsByStation(int stationId) {
        return repository.findRequestsByStation(stationId);
    }

    public LendingRequestItem addRequestItem(int requestId, Integer inventoryId, Integer itemId, int quantity) {
        return repository.addRequestItem(requestId, inventoryId, itemId, quantity);
    }

    public List<LendingRequestItem> findRequestItems(int requestId) {
        return repository.findItemsByRequest(requestId);
    }

    public boolean assignItem(int requestItemId, int assignedItemId) {
        return repository.assignItem(requestItemId, assignedItemId);
    }

    // -- Status transitions --

    public boolean approveRequest(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.APPROVED);
        if (updated) {
            autoAssignItems(requestId);
            repository.createMessage(requestId, stationId, null, "Anfrage genehmigt", true);
            repository
                    .findRequestById(requestId)
                    .ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.APPROVED));
        }
        return updated;
    }

    private void autoAssignItems(int requestId) {
        var request = repository.findRequestById(requestId).orElse(null);
        if (request == null) return;
        var requestItems = repository.findItemsByRequest(requestId);
        for (var ri : requestItems) {
            if (ri.assignedItemId() != null) continue;
            // If a specific item was requested, assign that directly
            if (ri.itemId() != null) {
                repository.assignItem(ri.id(), ri.itemId());
                continue;
            }
            // Otherwise assign first N free items from the inventory for the requested period
            if (ri.inventoryId() == null) continue;
            var dateTo = request.requestedDateTo() != null
                    ? request.requestedDateTo()
                    : request.requestedDateFrom().plusDays(1);
            var freeItems = inventoryRepository.findFreeItems(ri.inventoryId(), request.requestedDateFrom(), dateTo);
            for (int q = 0; q < ri.quantity() && q < freeItems.size(); q++) {
                repository.assignItem(ri.id(), freeItems.get(q).id());
            }
        }
    }

    public boolean declineRequest(int requestId, int stationId, String reason) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.DECLINED);
        if (updated) {
            String msg = "Anfrage abgelehnt" + (reason != null && !reason.isBlank() ? ": " + reason : "");
            repository
                    .findRequestById(requestId)
                    .ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.DECLINED));
            repository.createMessage(requestId, stationId, null, msg, true);
        }
        return updated;
    }

    public boolean markLent(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.LENT);
        if (updated) {
            repository.createMessage(requestId, stationId, null, "Ausrüstung ausgeliehen", true);
            repository.findRequestById(requestId).ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.LENT));
        }
        return updated;
    }

    public boolean markReturned(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.RETURNED);
        if (updated) {
            repository.createMessage(requestId, stationId, null, "Ausrüstung zurückgegeben", true);
            repository
                    .findRequestById(requestId)
                    .ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.RETURNED));
        }
        return updated;
    }

    public boolean closeRequest(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.CLOSED);
        if (updated) {
            repository.createMessage(requestId, stationId, null, "Anfrage geschlossen", true);
            repository
                    .findRequestById(requestId)
                    .ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.CLOSED));
        }
        return updated;
    }

    // -- Messages --

    public LendingMessage sendMessage(
            int requestId, int senderStationId, int senderMemberId, String senderName, String message) {
        var msg = repository.createMessage(requestId, senderStationId, senderMemberId, message, false);
        repository.findRequestById(requestId).ifPresent(r -> {
            int targetStationId =
                    r.requestingStationId() == senderStationId ? r.owningStationId() : r.requestingStationId();
            eventBus.publish(new LendingMessageSent(
                    senderStationId, targetStationId, requestId, stationName(senderStationId), senderName));
        });
        return msg;
    }

    public List<LendingMessage> getLocalMessages(int requestId, int stationId) {
        return repository.findLocalMessages(requestId, stationId);
    }

    /**
     * Returns all messages for a lending request by merging local and remote messages.
     * Each station only stores messages it sent. The partner's messages are fetched either
     * via direct DB query (local partner) or HTTP (remote partner).
     */
    public List<LendingMessage> getMessages(int requestId, int localStationId) {
        var request = repository.findRequestById(requestId).orElseThrow();
        int partnerStationId = request.requestingStationId() == localStationId
                ? request.owningStationId()
                : request.requestingStationId();

        var localMessages = repository.findLocalMessages(requestId, localStationId);

        // Check if the partner is remote
        var partner = findPartnerForStation(localStationId, partnerStationId);
        List<LendingMessage> remoteMessages;
        if (partner != null && partner.isRemote()) {
            remoteMessages = fetchRemoteMessagesViaHttp(partner, requestId, localStationId);
        } else {
            // Local partner — directly query their messages from shared DB
            remoteMessages = repository.findLocalMessages(requestId, partnerStationId);
        }

        var all = new ArrayList<>(localMessages);
        all.addAll(remoteMessages);
        all.sort(Comparator.comparing(LendingMessage::createdAt));
        return all;
    }

    private List<LendingMessage> fetchRemoteMessagesViaHttp(
            FederationPartner partner, int requestId, int localStationId) {
        var station = stationRepository.findById(localStationId).orElse(null);
        if (station == null || station.federationPrivateKey() == null) {
            log.warn("No private key found for station {}, cannot fetch remote messages", localStationId);
            return List.of();
        }
        return httpClient.getList(
                partner.remoteHost(),
                "/remote/lending/messages/" + requestId,
                localStationId,
                station.federationPrivateKey(),
                LendingMessage.class);
    }

    private FederationPartner findPartnerForStation(int localStationId, int partnerStationId) {
        var partners = federationService.findPartners(localStationId);
        for (var p : partners) {
            var partnerStation =
                    stationRepository.findByUid(p.partnerStationId()).orElse(null);
            int remoteId = partnerStation != null ? partnerStation.id() : 0;
            if (remoteId == partnerStationId && p.status() == FederationPartner.FederationStatus.ACTIVE) {
                return p;
            }
        }
        return null;
    }

    // -- Blocks --

    public InventoryBlock createBlock(
            int stationId, Integer inventoryId, Integer itemId, LocalDate from, LocalDate to, String reason) {
        return repository.createBlock(stationId, inventoryId, itemId, from, to, reason);
    }

    public boolean deleteBlock(int blockId) {
        return repository.deleteBlock(blockId);
    }

    public List<InventoryBlock> findBlocks(int stationId) {
        return repository.findBlocksByStation(stationId);
    }

    public boolean isBlocked(int stationId, Integer inventoryId, Integer itemId, LocalDate dateFrom, LocalDate dateTo) {
        return repository.isBlocked(stationId, inventoryId, itemId, dateFrom, dateTo);
    }

    // -- Federated available inventory (parallel fetch from all partners) --

    /**
     * Finds available inventory across all active federation partners, with parallel fetching.
     */
    public List<AvailableInventoryEntry> findAvailableInventory(
            int stationId, String query, LocalDate dateFrom, LocalDate dateTo) {
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .toList();

        var futures = new ArrayList<CompletableFuture<List<AvailableInventoryEntry>>>();
        for (var partner : partners) {
            futures.add(CompletableFuture.supplyAsync(() -> findAvailableForPartner(partner, query, dateFrom, dateTo)));
        }

        var results = new ArrayList<AvailableInventoryEntry>();
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel available inventory fetch", e);
        }
        for (var future : futures) {
            try {
                results.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting available inventory results", e);
            }
        }
        return results;
    }

    private List<AvailableInventoryEntry> findAvailableForPartner(
            FederationPartner partner, String query, LocalDate dateFrom, LocalDate dateTo) {
        var partnerStation =
                stationRepository.findByUid(partner.partnerStationId()).orElse(null);
        if (partnerStation == null) return List.of();
        int partnerStationId = partnerStation.id();
        String name = partnerStation.name();

        if (dateFrom != null && isBlocked(partnerStationId, null, null, dateFrom, dateTo)) {
            return List.of();
        }

        var entries = new ArrayList<AvailableInventoryEntry>();
        var inventories = inventoryRepository.findByStation(partnerStationId);
        for (var inv : inventories) {
            if (query != null && !query.isBlank()) {
                if (!inv.name().toLowerCase().contains(query.toLowerCase())) {
                    continue;
                }
            }
            if (dateFrom != null && isBlocked(partnerStationId, inv.id(), null, dateFrom, dateTo)) {
                continue;
            }

            var unassigned = inventoryRepository.findUnassignedItems(inv.id());
            int availableCount;
            if (dateFrom != null) {
                availableCount = (int) unassigned.stream()
                        .filter(item -> !isBlocked(partnerStationId, null, item.id(), dateFrom, dateTo))
                        .count();
            } else {
                availableCount = unassigned.size();
            }
            if (availableCount > 0) {
                entries.add(new AvailableInventoryEntry(inv.id(), inv.name(), partnerStationId, name, availableCount));
            }
        }
        return entries;
    }

    public record AvailableInventoryEntry(
            int inventoryId, String inventoryName, int stationId, String stationName, int availableCount) {}
}
