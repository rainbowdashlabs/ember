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
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.equipment.service.EquipmentAvailabilityService;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.InventoryBlock;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.BorrowedGearService;
import dev.chojo.ember.feature.inventory.service.ItemCustodyService;
import dev.chojo.ember.feature.inventory.service.LineTargetService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    private final ClusterRepository clusterRepository;
    private final ItemCustodyService custodyService;
    private final BorrowedGearService borrowedGearService;
    private final InventoryShareService shareService;
    private final InventoryArtRepository artRepository;
    private final LineTargetService lineTargets;
    private final EquipmentAvailabilityService availability;
    private final DomainEventBus eventBus;

    @Inject
    public LendingService(
            LendingRepository repository,
            FederationHttpClient httpClient,
            FederationService federationService,
            StationRepository stationRepository,
            InventoryRepository inventoryRepository,
            ClusterRepository clusterRepository,
            ItemCustodyService custodyService,
            BorrowedGearService borrowedGearService,
            InventoryShareService shareService,
            InventoryArtRepository artRepository,
            LineTargetService lineTargets,
            EquipmentAvailabilityService availability,
            DomainEventBus eventBus) {
        this.artRepository = artRepository;
        this.lineTargets = lineTargets;
        this.availability = availability;
        this.repository = repository;
        this.httpClient = httpClient;
        this.federationService = federationService;
        this.stationRepository = stationRepository;
        this.inventoryRepository = inventoryRepository;
        this.clusterRepository = clusterRepository;
        this.custodyService = custodyService;
        this.borrowedGearService = borrowedGearService;
        this.shareService = shareService;
        this.eventBus = eventBus;
    }

    /**
     * Opens a request for gear at a partner station.
     *
     * <p>The occasion is a copy of the appointment's name rather than a link to it, deliberately: why
     * a request is being made is the question that decides a yes, and a title plus a window answers
     * it. Adding a field to an appointment must never quietly add it to a request.
     *
     * @param eventId   the appointment the request was collected for, or {@code null}
     * @param eventDate the evening of that appointment, or {@code null}
     * @param occasion  what to tell the owning station the request is for
     */
    public LendingRequest createRequest(
            int requestingStationId,
            int owningStationId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int createdBy,
            Integer eventId,
            LocalDate eventDate,
            String occasion) {
        UUID requestingUid = stationRepository.resolveUid(requestingStationId);
        UUID owningUid = stationRepository.resolveUid(owningStationId);
        requireLendingPartner(requestingStationId, owningUid);
        var request = repository.createRequest(
                requestingUid, owningUid, dateFrom, dateTo, createdBy, eventId, eventDate, occasion);
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

    /**
     * Refuses a request aimed at a station this one does not lend with.
     *
     * <p>Two things are asked here, and neither was asked before: whether the stations are federated
     * at all, and whether lending is switched on for that partnership. Somebody who turns lending off
     * for a partner meant it, so that partner stops asking as well as stops browsing.
     *
     * @param requestingStationId the station asking for gear
     * @param owningUid           the station it wants the gear from
     * @throws ForbiddenResponse when the two stations do not lend with each other
     */
    private void requireLendingPartner(int requestingStationId, UUID owningUid) {
        var partner = findPartnerForStation(requestingStationId, owningUid);
        if (partner == null || !lendsWith(partner)) {
            throw new ForbiddenResponse("This station does not lend gear to yours");
        }
    }

    /**
     * Whether lending is switched on for a partnership. This is the coarsest of the questions asked
     * before anything is offered or requested, and it is the one every other federated feature
     * already asks of its own capability.
     *
     * @param partner the partnership seen from the asking station
     * @return {@code true} when gear may travel along it
     */
    private boolean lendsWith(FederationPartner partner) {
        return federationService.hasCapability(partner, CapabilityType.INVENTORY_LEND, Direction.IMPORT);
    }

    public Optional<LendingRequest> findRequest(int id) {
        return repository.findRequestById(id);
    }

    public List<LendingRequest> findRequestsByStation(int stationId) {
        return repository.findRequestsByStation(stationRepository.resolveUid(stationId));
    }

    // -- Requests --

    public LendingRequestItem addRequestItem(
            int requestId, Integer inventoryId, Integer itemId, Integer artId, int quantity, Integer needId) {
        LendingRequestItem added = repository.addRequestItem(requestId, inventoryId, itemId, artId, quantity, needId);
        log.info("Lending request {} now asks for {} piece(s) more", requestId, quantity);
        return added;
    }

    /**
     * Withdraws the requests an appointment has sent that nobody has settled yet.
     *
     * <p>Cancelling an appointment is the moment a partner's shelf has to be given back: the evening
     * they were holding gear for is not happening, and the partner has no other way of learning that.
     *
     * @param eventId   the appointment
     * @param stationId the station it belongs to
     * @return how many requests were withdrawn
     */
    public int withdrawForEvent(int eventId, int stationId) {
        int withdrawn = 0;
        for (var request : repository.findOpenRequestsForEvent(eventId)) {
            if (declineRequest(request.id(), stationId, "Der Termin wurde abgesagt")) withdrawn++;
        }
        return withdrawn;
    }

    public List<LendingRequestItem> findRequestItems(int requestId) {
        return repository.findItemsByRequest(requestId);
    }

    /**
     * Sets one piece of gear aside for a lending request, by hand.
     *
     * <p>This is the path a person drives, so it says no out loud. The automatic path cannot, and
     * quietly passes over what it may not lend instead.
     *
     * @param requestItemId  the line of the request being filled
     * @param assignedItemId the piece being set aside
     * @param stationId      the station doing the lending, whose gear it has to be
     * @throws ForbiddenResponse when the piece is not this station's to lend
     */
    public boolean assignItem(int requestItemId, int assignedItemId, int stationId) {
        requireLendable(stationId, assignedItemId);
        boolean assigned = repository.assignItem(requestItemId, assignedItemId);
        if (assigned) log.info("Item {} was set aside for lending request item {}", assignedItemId, requestItemId);
        else log.warn("Assign of item {} to lending request item {} affected zero rows", assignedItemId, requestItemId);
        return assigned;
    }

    /**
     * The gear of one inventory a station may offer a partner, which is what the picker behind a
     * manual assignment shows.
     *
     * @param stationId   the station doing the lending
     * @param inventoryId the inventory being picked from
     * @return the free pieces that are this station's to lend
     */
    public List<InventoryItem> findAssignableItems(int stationId, int inventoryId) {
        var lender = lenderAt(stationId);
        if (!ownsInventory(stationId, inventoryId)) return List.of();
        return inventoryRepository.findUnassignedItems(inventoryId).stream()
                .filter(lender::owns)
                .toList();
    }

    /**
     * Refuses to lend on gear that is not the station's to lend.
     *
     * <p>Lending is the owner's decision, and a station holding a cluster's jacket is not its owner.
     * Passing it to a third party would put it somewhere the cluster never agreed to and, worse,
     * somewhere the cluster cannot see: the partner's records are not ours to read.
     *
     * @param stationId the station doing the lending
     * @param itemId    the item somebody wants to lend out
     * @throws ForbiddenResponse when the item is not this station's to lend
     */
    private void requireLendable(int stationId, int itemId) {
        var item = inventoryRepository.findItemById(itemId).orElse(null);
        if (item == null) return;
        if (!isLendable(lenderAt(stationId), item)) {
            throw new ForbiddenResponse("This gear is not this station's to lend");
        }
    }

    /**
     * Whether a piece is this station's to lend: it sits in one of the station's own inventories, and
     * the station owns it rather than merely holding it.
     *
     * @param lender the station doing the lending, with what it owns already resolved
     * @param item   the piece in question
     * @return {@code true} when the piece may travel to a partner
     */
    private boolean isLendable(Lender lender, InventoryItem item) {
        return lender.owns(item) && ownsInventory(lender.stationId(), item.inventoryId());
    }

    /**
     * Whether an inventory is run by the given station. The requesting side names the inventory it
     * wants gear from, and nothing until now checked that the naming was honest.
     *
     * @param stationId   the station doing the lending
     * @param inventoryId the inventory named on the request
     * @return {@code true} when the inventory is that station's own
     */
    private boolean ownsInventory(int stationId, int inventoryId) {
        return inventoryRepository
                .findById(inventoryId)
                .map(inventory -> inventory.stationId() == stationId)
                .orElse(false);
    }

    /**
     * Resolves once, per station, what that station counts as its own.
     *
     * @param stationId the station doing the lending
     * @return the station together with the cluster whose shell it is, if it is one
     */
    private Lender lenderAt(int stationId) {
        return new Lender(
                stationId,
                clusterRepository.findByHomeStation(stationId).map(Cluster::id).orElse(null));
    }

    /**
     * A station in its role as lender, and the one question that role has to answer.
     *
     * <p>The owner may lend, a holder may not lend on. A cluster owns what sits on the station shell
     * it owns, so the shell lending the cluster's gear is the owner acting and is not refused here;
     * a member station holding the same gear is refused.
     *
     * <p>Gear borrowed from another partner is refused for the same reason and more plainly: lending
     * it on would put a third station's radio somewhere its owner never agreed to and cannot see.
     *
     * @param stationId     the station doing the lending
     * @param homeClusterId the cluster this station is the shell of, or {@code null} when it is not one
     */
    private record Lender(int stationId, Integer homeClusterId) {
        boolean owns(InventoryItem item) {
            if (item.ownerKind() == ItemOwner.PARTNER_STATION) return false;
            if (item.ownerKind() != ItemOwner.CLUSTER) return true;
            return homeClusterId != null && homeClusterId.equals(item.ownerClusterId());
        }
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

    /**
     * Hands the gear over, which is where a borrowed piece becomes a thing at the borrower.
     *
     * <p>Two rows come out of one radio and they are different sentences. The owner's row is the
     * truth about the thing and now says which partner has it; the borrower's row is the truth about
     * where it is this fortnight, and it goes away again when the gear does.
     *
     * @param requestId the lending request
     * @param stationId the station handing the gear over
     * @return {@code true} when the request moved to lent
     */
    public boolean markLent(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.LENT);
        if (updated) {
            var request = repository.findRequestById(requestId);
            Integer borrower = request.flatMap(r -> stationRepository.findByUid(r.requestingStationUid()))
                    .map(Station::id)
                    .orElse(null);
            Integer owner = request.flatMap(r -> stationRepository.findByUid(r.owningStationUid()))
                    .map(Station::id)
                    .orElse(null);
            forEachLentItem(requestId, (requestItemId, itemId) -> {
                custodyService.lendToPartner(itemId, borrower);
                // A partner on another instance has no rows here to write, so the owner's side is the
                // whole of the handover and the borrower keeps only the request, as it always did.
                if (borrower == null || owner == null) return;
                inventoryRepository
                        .findItemById(itemId)
                        .ifPresent(item -> borrowedGearService.handOver(item, owner, borrower, requestItemId));
            });
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

    /**
     * Takes the gear back, which is where the borrower's row goes away.
     *
     * <p>The row goes rather than being marked returned: it was a copy of somebody else's gear taken
     * for the length of one loan, and a snapshot that outlives its loan is only a way of being wrong
     * later. The loan stays at both ends, which is the history worth having.
     *
     * @param requestId the lending request
     * @param stationId the station taking the gear back
     * @return {@code true} when the request moved to returned
     */
    public boolean markReturned(int requestId, int stationId) {
        boolean updated = repository.updateRequestStatus(requestId, LendingStatus.RETURNED);
        if (updated) {
            forEachLentItem(requestId, (requestItemId, itemId) -> {
                borrowedGearService.handBack(requestItemId);
                custodyService.returnFromPartner(itemId);
            });
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
     * @param action    what to do with each assigned item, given the line it was set aside on and the
     *                  item itself
     */
    private void forEachLentItem(int requestId, LentItemAction action) {
        for (var requestItem : repository.findItemsByRequest(requestId)) {
            for (int itemId : repository.findAssignedItems(requestItem.id())) {
                action.accept(requestItem.id(), itemId);
            }
        }
    }

    /**
     * What to do with one piece of gear that is actually changing hands, told both which line of the
     * request it is on and which item it is. The line is what pairs the two stations' rows.
     */
    @FunctionalInterface
    private interface LentItemAction {
        void accept(int requestItemId, int itemId);
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
     *
     * <p>An empty answer says which of two situations it is, and no more than that. Listing the
     * inventories that were held back would be the more helpful search and the wrong product: it
     * would tell another station what you own and which of it you are deliberately keeping.
     */
    public AvailableInventoryResult findAvailableInventory(
            int stationId, String query, LocalDate dateFrom, LocalDate dateTo) {
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(this::lendsWith)
                .toList();
        UUID askingStationUid = stationRepository.resolveUid(stationId);

        var futures = new ArrayList<CompletableFuture<PartnerAvailability>>();
        for (var partner : partners) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> findAvailableForPartner(partner, askingStationUid, query, dateFrom, dateTo)));
        }

        var results = new ArrayList<AvailableInventoryEntry>();
        boolean anyOffer = false;
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel available inventory fetch", e);
        }
        for (var future : futures) {
            try {
                var availability = future.get();
                results.addAll(availability.entries());
                anyOffer |= availability.offersAnything();
            } catch (Exception e) {
                log.error("Error collecting available inventory results", e);
            }
        }
        var entries = enrichWithDistance(stationId, results);
        if (!entries.isEmpty()) return new AvailableInventoryResult(entries, null);
        return new AvailableInventoryResult(entries, anyOffer ? EmptyReason.NOTHING_FREE : EmptyReason.NOTHING_SHARED);
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

    /**
     * Fills the lines of an approved request with gear.
     *
     * <p><b>This filters, it never throws.</b> The status change is already committed by the time it
     * runs and there is nothing holding the two together, so a refusal here would turn one piece the
     * station may not lend into a rejected call on an approval that has already happened, with the
     * partner never told. A line that cannot be filled is left empty for somebody to fill by hand.
     *
     * <p>What may be filled in is the owning station's own gear, whichever way the line names it: the
     * requesting side chooses both the inventory and, where it wants one piece in particular, the
     * piece, and neither naming was checked before.
     *
     * <p>How much may be filled in is what is free over the window, which is more than which pieces
     * nobody has named: an appointment of the station's own asking for four radios that weekend names
     * no piece and still takes four. So the count is bounded by the whole reckoning of free and the
     * choice of pieces by the ones nobody has spoken for, and the request being filled is left out of
     * both, because it is approved by now and would otherwise read as its own competition.
     *
     * @param requestId the request that was just approved
     */
    private void autoAssignItems(int requestId) {
        var request = repository.findRequestById(requestId).orElse(null);
        if (request == null) return;
        var owningStation =
                stationRepository.findByUid(request.owningStationUid()).orElse(null);
        if (owningStation == null) return;
        var lender = lenderAt(owningStation.id());
        Instant from = request.requestedDateFrom().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = request.requestedDateTo() == null
                ? EquipmentAvailabilityService.openEndAfter(from)
                : request.requestedDateTo()
                        .plusDays(1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();
        for (var ri : repository.findItemsByRequest(requestId)) {
            int alreadySet = repository.findAssignedItems(ri.id()).size();
            if (alreadySet >= ri.quantity()) continue;
            if (ri.itemId() != null) {
                inventoryRepository
                        .findItemById(ri.itemId())
                        .filter(item -> isLendable(lender, item))
                        .filter(item -> isFree(owningStation.id(), LineTarget.item(item.id()), from, to))
                        .ifPresent(item -> repository.assignItem(ri.id(), item.id()));
                continue;
            }
            LineTarget target = ri.target();
            if (target == null || !ownsTarget(lender.stationId(), target)) continue;
            var free = availability.freePieces(owningStation.id(), target, from, to).stream()
                    .map(inventoryRepository::findItemById)
                    .flatMap(Optional::stream)
                    .filter(lender::owns)
                    .toList();
            int room = availability
                            .availability(owningStation.id(), target, from, to, null, requestId)
                            .free()
                    - alreadySet;
            for (int q = 0; q < ri.quantity() - alreadySet && q < free.size() && q < room; q++) {
                repository.assignItem(ri.id(), free.get(q).id());
            }
        }
    }

    /**
     * Whether one named piece is still there to be promised over a window.
     *
     * <p>Ownership says a station may lend a piece; it does not say the piece is here. Gear already at
     * a partner, in the post or set aside for another evening is owned all the same, and promising it
     * a second time is how one radio is lent twice.
     *
     * @param stationId the station doing the lending
     * @param target    the piece
     * @param from      the first moment of the window
     * @param to        the last moment of the window
     * @return {@code true} when nobody has it and nobody has spoken for it
     */
    private boolean isFree(int stationId, LineTarget target, Instant from, Instant to) {
        return !availability.freePieces(stationId, target, from, to).isEmpty();
    }

    /**
     * Whether what a line names is the lending station's to offer at all.
     *
     * @param stationId the station being asked
     * @param target    what the line names
     * @return {@code true} when it belongs to that station
     */
    private boolean ownsTarget(int stationId, LineTarget target) {
        try {
            return lineTargets.stationOf(target) == stationId;
        } catch (IllegalArgumentException e) {
            return false;
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
                    entry.artId(),
                    entry.artName(),
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

    private PartnerAvailability findAvailableForPartner(
            FederationPartner partner, UUID askingStationUid, String query, LocalDate dateFrom, LocalDate dateTo) {
        var partnerStation =
                stationRepository.findByUid(partner.partnerStationId()).orElse(null);
        if (partnerStation == null) return PartnerAvailability.nothing();
        int partnerStationId = partnerStation.id();
        String name = partnerStation.name();

        var policy = shareService.policyFor(partnerStationId, askingStationUid);
        if (!policy.offersAnything()) return PartnerAvailability.nothing();
        if (dateFrom != null && isBlocked(partnerStationId, null, null, dateFrom, dateTo)) {
            return new PartnerAvailability(List.of(), true);
        }

        var lender = lenderAt(partnerStationId);
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

            var offered = shareService
                    .filterShared(
                            policy,
                            inventoryRepository.findUnassignedItems(inv.id()).stream()
                                    .filter(lender::owns)
                                    .toList())
                    .stream()
                    .filter(item -> dateFrom == null || !isBlocked(partnerStationId, null, item.id(), dateFrom, dateTo))
                    .toList();

            var perArt = new LinkedHashMap<Integer, Integer>();
            for (var item : offered) {
                perArt.merge(item.artId(), 1, Integer::sum);
            }
            for (var group : perArt.entrySet()) {
                Integer artId = group.getKey();
                entries.add(new AvailableInventoryEntry(
                        inv.id(),
                        inv.name(),
                        artId,
                        artId == null
                                ? null
                                : artRepository
                                        .findById(artId)
                                        .map(InventoryArt::name)
                                        .orElse(null),
                        partnerStationId,
                        name,
                        group.getValue(),
                        null));
            }
        }
        return new PartnerAvailability(entries, true);
    }

    /**
     * What one partner offers the asking station: the inventories with something free in them, and
     * whether that partner offers anything at all. The second answer is what separates "nothing is
     * shared with you" from "nothing is free just now".
     */
    private record PartnerAvailability(List<AvailableInventoryEntry> entries, boolean offersAnything) {
        static PartnerAvailability nothing() {
            return new PartnerAvailability(List.of(), false);
        }
    }

    /**
     * One thing a partner offers, counted.
     *
     * <p>A row per kind of thing rather than per drawer, because a count out of the radio drawer is
     * the granularity that fails: asking for four out of it may be answered with the charging station
     * and the case. Where a piece carries no kind the row is the drawer, as it always was.
     *
     * @param artId   the kind counted, or {@code null} where the row counts a whole inventory
     * @param artName what that kind is called, or {@code null}
     *
     * @param distanceKm great-circle distance from the searching station to the offering
     *                   station, or {@code null} when either side hasn't published
     *                   coordinates. Distance is computed locally; the partner is never
     *                   asked to reveal exact coordinates over the wire.
     */
    public record AvailableInventoryEntry(
            int inventoryId,
            String inventoryName,
            Integer artId,
            String artName,
            int stationId,
            String stationName,
            int availableCount,
            Double distanceKm) {}

    /**
     * The browse answer, with the reason an empty one is empty.
     *
     * @param entries     what is free at the partners right now
     * @param emptyReason why nothing came back, or {@code null} when something did
     */
    public record AvailableInventoryResult(List<AvailableInventoryEntry> entries, EmptyReason emptyReason) {}

    /**
     * The two situations an empty browse answer can mean. It says nothing finer on purpose: which
     * inventories a station holds back is its own business, and naming them would defeat the point
     * of holding them back.
     */
    public enum EmptyReason {
        /** No partner offers this station anything at all. */
        NOTHING_SHARED,
        /** Something is offered, but nothing of it is free in the window that was asked for. */
        NOTHING_FREE
    }
}
