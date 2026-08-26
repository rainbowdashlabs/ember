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
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.ItemCustodyService;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLocationService;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;

/**
 * Business logic for cross-station inventory lending. Internally peer references travel as
 * {@link UUID} (the station's stable cross-instance identity); the public surface still accepts
 * local integer ids so existing routes / services / events stay unchanged. Conversion happens
 * at the edges via {@link StationRepository#resolveUid(int)}.
 */
@Singleton
public class LendingService {
    private static final Logger log = LoggerFactory.getLogger(LendingService.class);

    private final LendingRepository repository;
    private final FederationHttpClient httpClient;
    private final FederationService federationService;
    private final StationRepository stationRepository;
    private final InventoryRepository inventoryRepository;
    private final ItemCustodyService custodyService;
    private final DomainEventBus eventBus;

    @Inject
    public LendingService(
            LendingRepository repository,
            FederationHttpClient httpClient,
            FederationService federationService,
            StationRepository stationRepository,
            InventoryRepository inventoryRepository,
            ItemCustodyService custodyService,
            DomainEventBus eventBus) {
        this.repository = repository;
        this.httpClient = httpClient;
        this.federationService = federationService;
        this.stationRepository = stationRepository;
        this.inventoryRepository = inventoryRepository;
        this.custodyService = custodyService;
        this.eventBus = eventBus;
    }

    public LendingRequest createRequest(
            int requestingStationId, int owningStationId, LocalDate dateFrom, LocalDate dateTo, int createdBy) {
        UUID requestingUid = stationRepository.resolveUid(requestingStationId);
        UUID owningUid = stationRepository.resolveUid(owningStationId);
        var request = repository.createRequest(requestingUid, owningUid, dateFrom, dateTo, createdBy);
        eventBus.publish(new LendingRequested(
                requestingStationId,
                owningStationId,
                request.id(),
                stationName(requestingStationId),
                buildItemSummary(request.id())));
        log.info(
                "Created lending request {} from station {} to station {}",
                request.id(),
                requestingStationId,
                owningStationId);
        return request;
    }

    public Optional<LendingRequest> findRequest(int id) {
        return repository.findRequestById(id);
    }

    public List<LendingRequest> findRequestsByStation(int stationId) {
        return repository.findRequestsByStation(stationRepository.resolveUid(stationId));
    }

    // -- Requests --

    public LendingRequestItem addRequestItem(int requestId, Integer inventoryId, Integer itemId, int quantity) {
        LendingRequestItem added = repository.addRequestItem(requestId, inventoryId, itemId, quantity);
        log.info("Lending request {} now asks for {} piece(s) more", requestId, quantity);
        return added;
    }

    public List<LendingRequestItem> findRequestItems(int requestId) {
        return repository.findItemsByRequest(requestId);
    }

    public boolean assignItem(int requestItemId, int assignedItemId) {
        requireLendable(assignedItemId);
        boolean assigned = repository.assignItem(requestItemId, assignedItemId);
        if (assigned) log.info("Item {} was set aside for lending request item {}", assignedItemId, requestItemId);
        else log.warn("Assign of item {} to lending request item {} affected zero rows", assignedItemId, requestItemId);
        return assigned;
    }

    /**
     * Refuses to lend on gear the station does not own.
     *
     * <p>Lending is the owner's decision, and a station holding a cluster's jacket is not its owner. Passing
     * it to a third party would put it somewhere the cluster never agreed to and, worse, somewhere the
     * cluster cannot see: the partner's records are not ours to read.
     *
     * @param itemId the item somebody wants to lend out
     * @throws ForbiddenResponse when the item belongs to a cluster
     */
    private void requireLendable(int itemId) {
        inventoryRepository.findItemById(itemId).ifPresent(item -> {
            if (item.ownerKind() == ItemOwner.CLUSTER) {
                throw new ForbiddenResponse("This gear belongs to the body above the station and cannot be lent on");
            }
        });
    }

    public boolean approveRequest(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.APPROVED);
        if (updated) {
            autoAssignItems(requestId);
            repository.createMessage(
                    requestId, stationRepository.resolveUid(stationId), null, "Anfrage genehmigt", true);
            repository
                    .findRequestById(requestId)
                    .ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.APPROVED));
            log.info("Lending request {} approved by station {}", requestId, stationId);
        } else {
            log.warn("Approve for lending request {} by station {} affected no row", requestId, stationId);
        }
        return updated;
    }

    public boolean declineRequest(int requestId, int stationId, String reason) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.DECLINED);
        if (updated) {
            String msg = "Anfrage abgelehnt" + (reason != null && !reason.isBlank() ? ": " + reason : "");
            repository
                    .findRequestById(requestId)
                    .ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.DECLINED));
            repository.createMessage(requestId, stationRepository.resolveUid(stationId), null, msg, true);
            log.info("Lending request {} declined by station {}", requestId, stationId);
        } else {
            log.warn("Decline for lending request {} by station {} affected no row", requestId, stationId);
        }
        return updated;
    }

    public boolean markLent(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.LENT);
        if (updated) {
            forEachLentItem(requestId, custodyService::lendToPartner);
            repository.createMessage(
                    requestId, stationRepository.resolveUid(stationId), null, "Ausrüstung ausgeliehen", true);
            repository.findRequestById(requestId).ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.LENT));
            log.info("Lending request {} marked lent by station {}", requestId, stationId);
        } else {
            log.warn("Mark-lent for lending request {} by station {} affected no row", requestId, stationId);
        }
        return updated;
    }

    // -- Status transitions --

    public boolean markReturned(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.RETURNED);
        if (updated) {
            forEachLentItem(requestId, custodyService::returnFromPartner);
            repository.createMessage(
                    requestId, stationRepository.resolveUid(stationId), null, "Ausrüstung zurückgegeben", true);
            repository
                    .findRequestById(requestId)
                    .ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.RETURNED));
            log.info("Lending request {} marked returned by station {}", requestId, stationId);
        } else {
            log.warn("Mark-returned for lending request {} by station {} affected no row", requestId, stationId);
        }
        return updated;
    }

    /**
     * Runs an action over every item actually assigned to a lending request, which is what changes
     * hands when the request is marked lent or returned. Request lines that never got an item
     * assigned carry nothing to move.
     *
     * @param requestId the lending request
     * @param action    what to do with each assigned item
     */
    private void forEachLentItem(int requestId, IntConsumer action) {
        for (var requestItem : repository.findItemsByRequest(requestId)) {
            if (requestItem.assignedItemId() != null) action.accept(requestItem.assignedItemId());
        }
    }

    public boolean closeRequest(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.CLOSED);
        if (updated) {
            repository.createMessage(
                    requestId, stationRepository.resolveUid(stationId), null, "Anfrage geschlossen", true);
            repository
                    .findRequestById(requestId)
                    .ifPresent(r -> publishStatusChange(r, stationId, LendingStatus.CLOSED));
            log.info("Lending request {} closed by station {}", requestId, stationId);
        } else {
            log.warn("Close for lending request {} by station {} affected no row", requestId, stationId);
        }
        return updated;
    }

    public LendingMessage sendMessage(
            int requestId, int senderStationId, int senderMemberId, String senderName, String message) {
        UUID senderStationUid = stationRepository.resolveUid(senderStationId);
        var msg = repository.createMessage(requestId, senderStationUid, senderMemberId, message, false);
        repository.findRequestById(requestId).ifPresent(r -> {
            UUID targetStationUid = Objects.equals(r.requestingStationUid(), senderStationUid)
                    ? r.owningStationUid()
                    : r.requestingStationUid();
            int targetStationId = stationRepository
                    .findByUid(targetStationUid)
                    .map(Station::id)
                    .orElse(0);
            eventBus.publish(new LendingMessageSent(
                    senderStationId, targetStationId, requestId, stationName(senderStationId), senderName));
        });
        log.info("Lending message {} sent on request {} by station {}", msg.id(), requestId, senderStationId);
        return msg;
    }

    /**
     * The messages this station wrote on a lending request, for the partner on the other side of
     * that request.
     *
     * <p>Being a partner of this station is not the same as being a party to one of its lending
     * negotiations. Request ids run in sequence, so without asking whose request it is, one partner
     * reads what this station said to another: what was asked for, what was refused, and when.
     *
     * @param requestId         the lending request being read
     * @param stationId         this station, whose messages are stored here
     * @param partnerStationUid the station asking, which has to be the other side of the request
     */
    public List<LendingMessage> getLocalMessages(int requestId, int stationId, UUID partnerStationUid) {
        var request = repository.findRequestById(requestId).orElseThrow(NotFoundResponse::new);
        UUID localStationUid = stationRepository.resolveUid(stationId);
        if (!isParty(request, localStationUid) || !isParty(request, partnerStationUid)) {
            throw new NotFoundResponse();
        }
        return repository.findLocalMessages(requestId, localStationUid);
    }

    private static boolean isParty(LendingRequest request, UUID stationUid) {
        return Objects.equals(request.requestingStationUid(), stationUid)
                || Objects.equals(request.owningStationUid(), stationUid);
    }

    /**
     * Returns all messages for a lending request by merging local and remote messages.
     * Each station only stores messages it sent. The partner's messages are fetched either
     * via direct DB query (local partner) or HTTP (remote partner).
     */
    public List<LendingMessage> getMessages(int requestId, int localStationId) {
        var request = repository.findRequestById(requestId).orElseThrow();
        UUID localStationUid = stationRepository.resolveUid(localStationId);
        UUID partnerStationUid = Objects.equals(request.requestingStationUid(), localStationUid)
                ? request.owningStationUid()
                : request.requestingStationUid();

        var localMessages = repository.findLocalMessages(requestId, localStationUid);

        // Check if the partner is remote
        var partner = findPartnerForStation(localStationId, partnerStationUid);
        List<LendingMessage> remoteMessages;
        if (partner != null && partner.isRemote()) {
            remoteMessages = fetchRemoteMessagesViaHttp(partner, requestId, localStationId);
        } else {
            // Local partner - directly query their messages from shared DB
            remoteMessages = repository.findLocalMessages(requestId, partnerStationUid);
        }

        var all = new ArrayList<>(localMessages);
        all.addAll(remoteMessages);
        all.sort(Comparator.comparing(LendingMessage::createdAt));
        return all;
    }

    public InventoryBlock createBlock(
            int stationId, Integer inventoryId, Integer itemId, LocalDate from, LocalDate to, String reason) {
        var block = repository.createBlock(stationId, inventoryId, itemId, from, to, reason);
        log.info("Created inventory block {} for station {}", block.id(), stationId);
        return block;
    }

    // -- Messages --

    public boolean deleteBlock(int blockId, int stationId) {
        boolean deleted = repository.deleteBlock(blockId, stationId);
        if (deleted) {
            log.info("Deleted inventory block {}", blockId);
        } else {
            log.warn("Delete of inventory block {} affected no row", blockId);
        }
        return deleted;
    }

    public List<InventoryBlock> findBlocks(int stationId) {
        return repository.findBlocksByStation(stationId);
    }

    public boolean isBlocked(int stationId, Integer inventoryId, Integer itemId, LocalDate dateFrom, LocalDate dateTo) {
        return repository.isBlocked(stationId, inventoryId, itemId, dateFrom, dateTo);
    }

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
        return enrichWithDistance(stationId, results);
    }

    private String stationName(int stationId) {
        return stationRepository.findById(stationId).map(Station::name).orElse("?");
    }

    // -- Blocks --

    /**
     * Builds a compact comma-separated summary of the items on a lending request, in the
     * form {@code "2x Ladder, 1x Drill"}, resolving inventory names and falling back to
     * {@code "?"} for unknown entries.
     *
     * @param requestId the lending request id
     * @return the item summary line
     */
    public String buildItemSummary(int requestId) {
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
        UUID actingStationUid = stationRepository.resolveUid(actingStationId);
        UUID targetStationUid = Objects.equals(request.requestingStationUid(), actingStationUid)
                ? request.owningStationUid()
                : request.requestingStationUid();
        int targetStationId =
                stationRepository.findByUid(targetStationUid).map(Station::id).orElse(0);
        eventBus.publish(new LendingStatusChanged(
                actingStationId,
                targetStationId,
                request.id(),
                NotificationType.LENDING_STATUS_CHANGE,
                stationName(actingStationId),
                status));
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

    private List<LendingMessage> fetchRemoteMessagesViaHttp(
            FederationPartner partner, int requestId, int localStationId) {
        var station = stationRepository.findById(localStationId).orElse(null);
        if (station == null || station.federationPrivateKey() == null) {
            log.warn("No private key found for station {}, cannot fetch remote messages", localStationId);
            return List.of();
        }
        return httpClient.getList(
                partner.remoteHost(),
                RemoteLendingRoutes.GET_MESSAGES.at(requestId),
                partner.partnerStationId(),
                localStationId,
                station.federationPrivateKey(),
                LendingMessage.class);
    }

    // -- Federated available inventory (parallel fetch from all partners) --

    private FederationPartner findPartnerForStation(int localStationId, UUID partnerStationUid) {
        var partners = federationService.findPartners(localStationId);
        for (var p : partners) {
            if (Objects.equals(p.partnerStationId(), partnerStationUid)
                    && p.status() == FederationPartner.FederationStatus.ACTIVE) {
                return p;
            }
        }
        return null;
    }

    /**
     * Decorates each result with the great-circle distance from the local station's
     * coordinates (if both ends have them) and sorts entries by distance ascending, with
     * {@code null} distances pushed to the end. The original collection order is preserved
     * among entries that share a null distance.
     */
    private List<AvailableInventoryEntry> enrichWithDistance(int stationId, List<AvailableInventoryEntry> results) {
        var local = stationRepository.findById(stationId).orElse(null);
        if (local == null || local.latitude() == null || local.longitude() == null) {
            return results;
        }
        double localLat = local.latitude().doubleValue();
        double localLon = local.longitude().doubleValue();

        var decorated = new ArrayList<AvailableInventoryEntry>(results.size());
        for (var entry : results) {
            Double distance = null;
            var partnerStation = stationRepository.findById(entry.stationId()).orElse(null);
            if (partnerStation != null && partnerStation.latitude() != null && partnerStation.longitude() != null) {
                distance = StationLocationService.distanceKm(
                        localLat,
                        localLon,
                        partnerStation.latitude().doubleValue(),
                        partnerStation.longitude().doubleValue());
            }
            decorated.add(new AvailableInventoryEntry(
                    entry.inventoryId(),
                    entry.inventoryName(),
                    entry.stationId(),
                    entry.stationName(),
                    entry.availableCount(),
                    distance));
        }
        decorated.sort((a, b) -> {
            if (a.distanceKm() == null && b.distanceKm() == null) return 0;
            if (a.distanceKm() == null) return 1;
            if (b.distanceKm() == null) return -1;
            return Double.compare(a.distanceKm(), b.distanceKm());
        });
        return decorated;
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
                entries.add(new AvailableInventoryEntry(
                        inv.id(), inv.name(), partnerStationId, name, availableCount, null));
            }
        }
        return entries;
    }

    /**
     * Federated lending inventory entry.
     *
     * @param distanceKm great-circle distance from the searching station to the offering
     *                   station, or {@code null} when either side hasn't published
     *                   coordinates. Distance is computed locally; the partner is never
     *                   asked to reveal exact coordinates over the wire.
     */
    public record AvailableInventoryEntry(
            int inventoryId,
            String inventoryName,
            int stationId,
            String stationName,
            int availableCount,
            Double distanceKm) {}
}
