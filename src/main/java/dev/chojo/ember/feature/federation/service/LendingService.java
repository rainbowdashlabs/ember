/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
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
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteAvailability;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteAvailableEntry;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingAccepted;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingLine;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingNotice;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingRequest;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingStatus;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for cross-station inventory lending. Internally peer references travel as
 * {@link UUID} (the station's stable cross-instance identity); the public surface still accepts
 * local integer ids so existing routes / services / events stay unchanged. Conversion happens
 * at the edges via {@link StationRepository#resolveUid(int)}.
 */
@Singleton
public class LendingService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(LendingService.class);

    private final LendingRepository repository;
    private final FederationTransport transport;
    private final FederationService federationService;
    private final FederationFanout fanout;
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
            FederationTransport transport,
            FederationService federationService,
            FederationFanout fanout,
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
        this.fanout = fanout;
        this.artRepository = artRepository;
        this.lineTargets = lineTargets;
        this.availability = availability;
        this.repository = repository;
        this.transport = transport;
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
     * Registers what this station answers a lending partner: what it offers, requests for gear,
     * requests moving on, its half of a request's messages and word of a new one.
     */
    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteLendingRoutes.GET_AVAILABLE,
                (partner, params, body) -> serveAvailability(
                        partner, params.query("q").orElse(null), day(params, "from"), day(params, "to")));
        endpoints.<RemoteLendingRequest, RemoteLendingAccepted>serve(
                RemoteLendingRoutes.CREATE_REQUEST, (partner, params, body) -> serveRequest(partner, body));
        endpoints.<RemoteLendingStatus, Void>serve(RemoteLendingRoutes.CHANGE_STATUS, (partner, params, body) -> {
            serveStatus(partner, params.uuid("requestUid"), body);
            return null;
        });
        endpoints.serve(
                RemoteLendingRoutes.GET_MESSAGES,
                (partner, params, body) -> serveMessages(partner, params.uuid("requestUid")));
        endpoints.<RemoteLendingNotice, Void>serve(RemoteLendingRoutes.MESSAGE_NOTICE, (partner, params, body) -> {
            serveNotice(partner, params.uuid("requestUid"), body);
            return null;
        });
    }

    private static LocalDate day(PathParams params, String name) {
        try {
            return params.query(name)
                    .filter(value -> !value.isBlank())
                    .map(LocalDate::parse)
                    .orElse(null);
        } catch (DateTimeParseException e) {
            throw Refusal.LENDING_DAY_NOT_A_DAY.raise();
        }
    }

    /**
     * Opens a request for gear at a partner station, with no lines yet.
     *
     * @see #createRequest(int, UUID, LocalDate, LocalDate, int, Integer, LocalDate, String, List)
     */
    public LendingRequest createRequest(
            int requestingStationId,
            int owningStationId,
            LocalDate dateFrom,
            LocalDate dateTo,
            int createdBy,
            @Nullable Integer eventId,
            @Nullable LocalDate eventDate,
            String occasion) {
        return createRequest(
                requestingStationId,
                stationRepository.requireUid(owningStationId),
                dateFrom,
                dateTo,
                createdBy,
                eventId,
                eventDate,
                occasion,
                List.of());
    }

    /**
     * Opens a request for gear at a partner station, wherever the partner lives.
     *
     * <p>The occasion is a copy of the appointment's name rather than a link to it, deliberately: why
     * a request is being made is the question that decides a yes, and a title plus a window answers
     * it. Adding a field to an appointment must never quietly add it to a request.
     *
     * <p>The request is written down here and then handed to the lending station, which writes down
     * its own copy under the same identity. A lending station on this instance finds the request
     * already written and has nothing to add. One on another instance names the lines in its own
     * words, since its inventories are not here to name them; when it cannot be reached, the request
     * written here goes again and the refusal is passed on, so nobody is left holding a request the
     * other side never heard of.
     *
     * @param owningUid the station asked for the gear
     * @param eventId   the appointment the request was collected for, or {@code null}
     * @param eventDate the date of that appointment, or {@code null}
     * @param occasion  what to tell the owning station the request is for
     * @param lines     what is asked for
     */
    public LendingRequest createRequest(
            int requestingStationId,
            UUID owningUid,
            LocalDate dateFrom,
            LocalDate dateTo,
            int createdBy,
            @Nullable Integer eventId,
            @Nullable LocalDate eventDate,
            String occasion,
            List<RequestLine> lines) {
        UUID requestingUid = stationRepository.requireUid(requestingStationId);
        var partner = requireLendingPartner(requestingStationId, owningUid);
        Integer lendingStationHere =
                stationRepository.findByUid(owningUid).map(Station::id).orElse(null);
        var request = repository.createRequest(
                UUID.randomUUID(), requestingUid, owningUid, dateFrom, dateTo, createdBy, eventId, eventDate, occasion);
        for (var line : lines) {
            if (lendingStationHere != null) {
                repository.addRequestItem(
                        request.id(), line.inventoryId(), line.itemId(), line.artId(), line.quantity(), line.needId());
            } else {
                repository.addRequestItem(request.id(), null, null, null, line.quantity(), line.needId());
            }
        }
        var sent = new RemoteLendingRequest(
                request.uid(),
                dateFrom,
                dateTo,
                occasion,
                lines.stream()
                        .map(line ->
                                new RemoteLendingLine(line.inventoryId(), line.itemId(), line.artId(), line.quantity()))
                        .toList());
        RemoteLendingAccepted accepted;
        try {
            accepted =
                    transport.send(partner, RemoteLendingRoutes.CREATE_REQUEST.at(), sent, RemoteLendingAccepted.class);
        } catch (RuntimeException e) {
            repository.deleteRequest(request.uid());
            throw e;
        }
        if (lendingStationHere == null) repository.labelItems(request.id(), accepted.labels());
        eventBus.publish(new LendingRequested(
                requestingStationId,
                lendingStationHere == null ? 0 : lendingStationHere,
                request.id(),
                stationName(requestingStationId),
                buildItemSummary(request.id())));
        log.info(
                "Created lending request {} from station {} to station {}",
                request.id(),
                requestingStationId,
                owningUid);
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
     * @return the partnership the request travels along
     * @throws RefusalResponse when the two stations do not lend with each other
     */
    private FederationPartner requireLendingPartner(int requestingStationId, UUID owningUid) {
        var partner = findPartnerForStation(requestingStationId, owningUid);
        if (partner == null || !lendsWith(partner)) {
            throw Refusal.LENDING_PARTNER_DOES_NOT_LEND.raise();
        }
        return partner;
    }

    /**
     * Writes down a partner's request for this station's gear.
     *
     * <p>A request this instance already holds is the one row two stations of this instance share,
     * and nothing is added to it. Otherwise every line has to name gear of this station, and the
     * request is kept as this station's copy of the partner's request.
     *
     * @param partner the partnership the request arrived on
     * @param body    the request
     * @return what this station calls each line
     */
    public RemoteLendingAccepted serveRequest(ServingPartner partner, RemoteLendingRequest body) {
        UUID lendingUid = stationRepository.requireUid(partner.servingStationId());
        var known = repository.findRequestByUid(body.uid());
        if (known.isPresent()) {
            var request = known.get();
            if (!lendingUid.equals(request.owningStationUid())
                    || !partner.askingStationUid().equals(request.requestingStationUid())) {
                throw Refusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS.raise();
            }
            return new RemoteLendingAccepted(labelsOf(request.id()));
        }
        var lines = body.lines() == null ? List.<RemoteLendingLine>of() : body.lines();
        for (var line : lines) {
            var target =
                    new LendingRequestItem(0, 0, line.inventoryId(), line.itemId(), line.artId(), 0, null, "").target();
            if (target == null || !ownsTarget(partner.servingStationId(), target)) {
                throw Refusal.LENDING_LINE_NAMES_FOREIGN_GEAR.raise();
            }
        }
        var request = repository.createRequest(
                body.uid(),
                partner.askingStationUid(),
                lendingUid,
                body.dateFrom(),
                body.dateTo(),
                null,
                null,
                null,
                body.occasion());
        for (var line : lines) {
            repository.addRequestItem(
                    request.id(), line.inventoryId(), line.itemId(), line.artId(), line.quantity(), null);
        }
        eventBus.publish(new LendingRequested(
                0, partner.servingStationId(), request.id(), partnerName(partner), buildItemSummary(request.id())));
        log.info("Wrote down lending request {} of partner {}", request.id(), partner.partnerId());
        return new RemoteLendingAccepted(labelsOf(request.id()));
    }

    private List<String> labelsOf(int requestId) {
        return repository.findItemsByRequest(requestId).stream()
                .map(this::lineLabel)
                .toList();
    }

    /**
     * What a line is called: the kind of thing it asks for, the piece it names or the inventory it
     * draws from, and where none of them is on this instance, what the lending station called it.
     *
     * @param item the line
     * @return its name, {@code "?"} where nothing names it
     */
    public String lineLabel(LendingRequestItem item) {
        Integer artId = item.artId();
        if (artId != null) {
            var art = artRepository.findById(artId).map(InventoryArt::name);
            if (art.isPresent()) return art.get();
        }
        Integer itemId = item.itemId();
        if (itemId != null) {
            var piece = inventoryRepository.findItemById(itemId).map(InventoryItem::name);
            if (piece.isPresent()) return piece.get();
        }
        Integer inventoryId = item.inventoryId();
        if (inventoryId != null) {
            var inventory = inventoryRepository.findById(inventoryId).map(Inventory::name);
            if (inventory.isPresent()) return inventory.get();
        }
        return item.label() == null || item.label().isBlank() ? "?" : item.label();
    }

    private String partnerName(ServingPartner partner) {
        return FederationDisplayNames.partnerName(stationRepository, partner.row(), "?");
    }

    /**
     * The request both stations of a partnership share, as this instance holds it.
     *
     * @param partner the partnership the call arrived on
     * @param uid     the request's identity between the two stations
     * @return this instance's copy, refused alike when missing and when the two are not its parties
     */
    private LendingRequest sharedRequest(ServingPartner partner, UUID uid) {
        UUID servingUid = stationRepository.requireUid(partner.servingStationId());
        return repository
                .findRequestByUid(uid)
                .filter(request -> request.isParty(servingUid) && request.isParty(partner.askingStationUid()))
                .orElseThrow(Refusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS::raise);
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
        return repository.findRequestsByStation(stationRepository.requireUid(stationId));
    }

    public LendingRequestItem addRequestItem(
            int requestId,
            @Nullable Integer inventoryId,
            @Nullable Integer itemId,
            @Nullable Integer artId,
            int quantity,
            @Nullable Integer needId) {
        LendingRequestItem added = repository.addRequestItem(requestId, inventoryId, itemId, artId, quantity, needId);
        log.info("Lending request {} now asks for {} piece(s) more", requestId, quantity);
        return added;
    }

    /**
     * Withdraws the requests an appointment has sent that nobody has settled yet.
     *
     * <p>Cancelling an appointment is the moment a partner's shelf has to be given back: the date they
     * were holding gear for is not happening, and the partner has no other way of learning that.
     *
     * @param eventId   the appointment
     * @param stationId the station it belongs to
     * @return how many requests were withdrawn
     */
    public int withdrawForEvent(int eventId, int stationId) {
        return withdraw(repository.findOpenRequestsForEvent(eventId), stationId);
    }

    /**
     * Withdraws the requests an appointment has sent for one of its dates that nobody has settled yet.
     *
     * <p>What calling off a single date does: the requests for the other dates of the series still
     * stand, and so does one written for the appointment as a whole.
     *
     * @param eventId   the appointment
     * @param stationId the station it belongs to
     * @param date      the date called off
     * @return how many requests were withdrawn
     */
    public int withdrawForEventDate(int eventId, int stationId, LocalDate date) {
        return withdraw(
                repository.findOpenRequestsForEvent(eventId).stream()
                        .filter(request -> date.equals(request.eventDate()))
                        .toList(),
                stationId);
    }

    private int withdraw(List<LendingRequest> requests, int stationId) {
        int withdrawn = 0;
        for (var request : requests) {
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
     * @throws RefusalResponse when the piece is not this station's to lend
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
     * @throws RefusalResponse when the item is not this station's to lend
     */
    private void requireLendable(int stationId, int itemId) {
        var item = inventoryRepository.findItemById(itemId).orElse(null);
        if (item == null) return;
        if (!isLendable(lenderAt(stationId), item)) {
            throw Refusal.LENDING_GEAR_NOT_THE_STATIONS.raise();
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
        return moveOn(requestId, stationId, LendingStatus.APPROVED, "Anfrage genehmigt", null);
    }

    public boolean declineRequest(int requestId, int stationId, @Nullable String reason) {
        String msg = "Anfrage abgelehnt" + (reason != null && !reason.isBlank() ? ": " + reason : "");
        return moveOn(requestId, stationId, LendingStatus.DECLINED, msg, reason);
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
        return moveOn(requestId, stationId, LendingStatus.LENT, "Ausrüstung ausgeliehen", null);
    }

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
        return moveOn(requestId, stationId, LendingStatus.RETURNED, "Ausrüstung zurückgegeben", null);
    }

    public boolean closeRequest(int requestId, int stationId) {
        return moveOn(requestId, stationId, LendingStatus.CLOSED, "Anfrage geschlossen", null);
    }

    /**
     * Moves a request on as one of its two stations, and tells the other one.
     *
     * <p>The change is made here, with its consequences for the gear, the station's own note in the
     * thread and the notice to the other station's managers, and then handed to the other station.
     * A station on this instance reads the same row and finds the change already made; a station on
     * another instance makes it on its own copy.
     *
     * @param requestId     the request
     * @param stationId     the station moving it on
     * @param status        where it moves to
     * @param systemMessage what the station's thread says about it
     * @param reason        why, where it was declined, or {@code null}
     * @return {@code true} when the request moved
     */
    private boolean moveOn(
            int requestId, int stationId, LendingStatus status, String systemMessage, @Nullable String reason) {
        if (!repository.updateRequestStatus(requestId, status)) {
            log.warn("Moving lending request {} to {} by station {} affected no row", requestId, status, stationId);
            return false;
        }
        var request = repository.findRequestById(requestId).orElse(null);
        if (request == null) return true;
        UUID actingUid = stationRepository.requireUid(stationId);
        applyConsequences(request, status);
        repository.createMessage(requestId, actingUid, null, systemMessage, true);
        publishStatusChange(
                request, stationId, stationName(stationId), stationOf(request.otherParty(actingUid)), status);
        var partner = findPartnerForStation(stationId, request.otherParty(actingUid));
        if (partner != null) {
            transport.notify(
                    partner,
                    RemoteLendingRoutes.CHANGE_STATUS.at(request.uid()),
                    new RemoteLendingStatus(status, reason));
        }
        log.info("Lending request {} moved to {} by station {}", requestId, status, stationId);
        return true;
    }

    /**
     * A request moved on by the partner station, made on this instance's copy.
     *
     * <p>Only the lending station agrees to a request and hands the gear over; either of the two may
     * decline, give back or close it. A change this copy already shows is the one row two stations of
     * this instance share, and nothing further happens.
     *
     * @param partner the partnership the change arrived on
     * @param uid     the request's identity between the two stations
     * @param body    what it moved to
     */
    public void serveStatus(ServingPartner partner, UUID uid, RemoteLendingStatus body) {
        var request = sharedRequest(partner, uid);
        boolean lendersOnly = body.status() == LendingStatus.APPROVED || body.status() == LendingStatus.LENT;
        if (lendersOnly && !request.owningStationUid().equals(partner.askingStationUid())) {
            throw Refusal.LENDING_NOT_THE_OWNING_STATION.raise();
        }
        if (request.status() == body.status()) return;
        if (!repository.updateRequestStatus(request.id(), body.status())) return;
        applyConsequences(request, body.status());
        publishStatusChange(request, 0, partnerName(partner), partner.servingStationId(), body.status());
        log.info("Lending request {} moved to {} by partner {}", request.id(), body.status(), partner.partnerId());
    }

    /**
     * What a request moving on does to the gear. Agreeing fills the lines, handing over and giving
     * back move the pieces set aside. On a copy of a request whose gear is on another instance there
     * are no pieces here to move, so nothing happens there.
     */
    private void applyConsequences(LendingRequest request, LendingStatus status) {
        switch (status) {
            case APPROVED -> autoAssignItems(request.id());
            case LENT -> handOver(request);
            case RETURNED ->
                forEachLentItem(request.id(), (requestItemId, itemId) -> {
                    borrowedGearService.handBack(requestItemId);
                    custodyService.returnFromPartner(itemId);
                });
            default -> {}
        }
    }

    /**
     * Hands the pieces set aside over to the borrowing station. A borrower on another instance has no
     * rows here to write, so the owner's side is the whole of the handover and the borrower keeps
     * only the request.
     */
    private void handOver(LendingRequest request) {
        Integer borrower = stationRepository
                .findByUid(request.requestingStationUid())
                .map(Station::id)
                .orElse(null);
        Integer owner = stationRepository
                .findByUid(request.owningStationUid())
                .map(Station::id)
                .orElse(null);
        forEachLentItem(request.id(), (requestItemId, itemId) -> {
            custodyService.lendToPartner(itemId, borrower);
            if (borrower == null || owner == null) return;
            inventoryRepository
                    .findItemById(itemId)
                    .ifPresent(item -> borrowedGearService.handOver(item, owner, borrower, requestItemId));
        });
    }

    private int stationOf(UUID stationUid) {
        return stationRepository.findByUid(stationUid).map(Station::id).orElse(0);
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

    public LendingMessage sendMessage(
            int requestId, int senderStationId, int senderMemberId, String senderName, String message) {
        UUID senderStationUid = stationRepository.requireUid(senderStationId);
        var msg = repository.createMessage(requestId, senderStationUid, senderMemberId, message, false);
        repository.findRequestById(requestId).ifPresent(r -> {
            UUID targetStationUid = r.otherParty(senderStationUid);
            eventBus.publish(new LendingMessageSent(
                    senderStationId, stationOf(targetStationUid), requestId, stationName(senderStationId), senderName));
            var partner = findPartnerForStation(senderStationId, targetStationUid);
            if (partner != null) {
                transport.notify(
                        partner, RemoteLendingRoutes.MESSAGE_NOTICE.at(r.uid()), new RemoteLendingNotice(senderName));
            }
        });
        log.info("Lending message {} sent on request {} by station {}", msg.id(), requestId, senderStationId);
        return msg;
    }

    /**
     * Word from the partner station that it wrote on a request, passed on to this station's managers.
     *
     * <p>A partner on this instance has told them itself when it wrote the message, so only word
     * from another instance is passed on.
     *
     * @param partner the partnership the word arrived on
     * @param uid     the request's identity between the two stations
     * @param body    who wrote
     */
    public void serveNotice(ServingPartner partner, UUID uid, RemoteLendingNotice body) {
        var request = sharedRequest(partner, uid);
        if (stationRepository.findByUid(partner.askingStationUid()).isPresent()) return;
        eventBus.publish(new LendingMessageSent(
                0, partner.servingStationId(), request.id(), partnerName(partner), body.senderName()));
    }

    /**
     * The messages this station wrote on a lending request, for the partner on the other side of
     * that request.
     *
     * <p>Being a partner of this station is not the same as being a party to one of its lending
     * negotiations. Without asking whose request it is, one partner would read what this station
     * said to another: what was asked for, what was refused, and when.
     *
     * @param partner the partnership the request arrived on
     * @param uid     the request's identity between the two stations
     * @return the messages this station wrote on it
     */
    public List<LendingMessage> serveMessages(ServingPartner partner, UUID uid) {
        var request = sharedRequest(partner, uid);
        return repository.findLocalMessages(request.id(), stationRepository.requireUid(partner.servingStationId()));
    }

    /**
     * Returns all messages for a lending request, the station's own merged with its partner's.
     * Each station only stores messages it sent, so the partner's are asked of the partner through
     * the transport, which is the same question wherever the partner lives. A partner no longer
     * held as active contributes nothing.
     */
    public List<LendingMessage> getMessages(int requestId, int localStationId) {
        var request = repository.findRequestById(requestId).orElseThrow();
        UUID localStationUid = stationRepository.requireUid(localStationId);
        var all = new ArrayList<>(repository.findLocalMessages(requestId, localStationUid));
        var partner = findPartnerForStation(localStationId, request.otherParty(localStationUid));
        if (partner != null) {
            all.addAll(transport.getList(
                    partner, RemoteLendingRoutes.GET_MESSAGES.at(request.uid()), LendingMessage.class));
        }
        all.sort(Comparator.comparing(LendingMessage::createdAt));
        return all;
    }

    /**
     * What a station taking part in a request is called, as the viewing station knows it: its own
     * name where it is on this instance, the partner's name otherwise.
     *
     * @param stationUid       the station
     * @param viewingStationId the station asking
     * @return the name, {@code "Unknown"} where neither is known
     */
    public String stationName(UUID stationUid, int viewingStationId) {
        var here = stationRepository.findByUid(stationUid).map(Station::name);
        if (here.isPresent()) return here.get();
        var partner = findPartnerForStation(viewingStationId, stationUid);
        return partner == null ? "Unknown" : FederationDisplayNames.partnerName(stationRepository, partner, "Unknown");
    }

    public InventoryBlock createBlock(
            int stationId,
            @Nullable Integer inventoryId,
            @Nullable Integer itemId,
            LocalDate from,
            LocalDate to,
            String reason) {
        var block = repository.createBlock(stationId, inventoryId, itemId, from, to, reason);
        log.info("Created inventory block {} for station {}", block.id(), stationId);
        return block;
    }

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

    public boolean isBlocked(
            int stationId,
            @Nullable Integer inventoryId,
            @Nullable Integer itemId,
            LocalDate dateFrom,
            LocalDate dateTo) {
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
            int stationId, @Nullable String query, @Nullable LocalDate dateFrom, LocalDate dateTo) {
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(this::lendsWith)
                .toList();

        var answers = fanout.fanOut(partners, partner -> List.of(availabilityAt(partner, query, dateFrom, dateTo)))
                .items();

        var results = new ArrayList<AvailableInventoryEntry>();
        boolean anyOffer = false;
        for (var availability : answers) {
            results.addAll(availability.entries());
            anyOffer |= availability.offersAnything();
        }
        var entries = enrichWithDistance(stationId, results);
        if (!entries.isEmpty()) return new AvailableInventoryResult(entries, null);
        return new AvailableInventoryResult(entries, anyOffer ? EmptyReason.NOTHING_FREE : EmptyReason.NOTHING_SHARED);
    }

    private String stationName(int stationId) {
        return stationRepository.findById(stationId).map(Station::name).orElse("?");
    }

    /**
     * Builds a compact comma-separated summary of the items on a lending request, in the
     * form {@code "2x Ladder, 1x Drill"}, resolving inventory names, falling back to what the
     * lending station called a line where its inventory is on another instance, and to {@code "?"}
     * for unknown entries.
     *
     * @param requestId the lending request id
     * @return the item summary line
     */
    public String buildItemSummary(int requestId) {
        var items = repository.findItemsByRequest(requestId);
        var parts = new ArrayList<String>();
        for (var item : items) {
            parts.add(item.quantity() + "x " + inventoryName(item));
        }
        return String.join(", ", parts);
    }

    /**
     * The inventory a line draws from, by name, or what the lending station called the line where
     * that inventory is not on this instance.
     *
     * @param item the line
     * @return the name, {@code "?"} where nothing names it
     */
    public String inventoryName(LendingRequestItem item) {
        Integer inventoryId = item.inventoryId();
        if (inventoryId != null) {
            return inventoryRepository
                    .findById(inventoryId)
                    .map(Inventory::name)
                    .orElse("?");
        }
        return item.label() == null || item.label().isBlank() ? "?" : item.label();
    }

    private void publishStatusChange(
            LendingRequest request, int actingStationId, String actingName, int targetStationId, LendingStatus status) {
        eventBus.publish(new LendingStatusChanged(
                actingStationId,
                targetStationId,
                request.id(),
                NotificationType.LENDING_STATUS_CHANGE,
                actingName,
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
        LocalDate until = request.requestedDateTo();
        Instant to = until == null
                ? EquipmentAvailabilityService.openEndAfter(from)
                : until.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        for (var ri : repository.findItemsByRequest(requestId)) {
            int alreadySet = repository.findAssignedItems(ri.id()).size();
            if (alreadySet >= ri.quantity()) continue;
            Integer namedPiece = ri.itemId();
            if (namedPiece != null) {
                inventoryRepository
                        .findItemById(namedPiece)
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
     * a partner, in the post or set aside for another date is owned all the same, and promising it
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

    private @Nullable FederationPartner findPartnerForStation(int localStationId, UUID partnerStationUid) {
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
            var partnerStation = stationRepository.findByUid(entry.stationId()).orElse(null);
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
        decorated.sort(Comparator.comparing(
                AvailableInventoryEntry::distanceKm, Comparator.nullsLast(Comparator.naturalOrder())));
        return decorated;
    }

    /**
     * What one partner offers the asking station, asked of the partner wherever it lives.
     */
    private PartnerAvailability availabilityAt(
            FederationPartner partner, String query, LocalDate dateFrom, LocalDate dateTo) {
        var request = RemoteLendingRoutes.GET_AVAILABLE.at();
        if (query != null && !query.isBlank()) request = request.query("q", query);
        if (dateFrom != null) request = request.query("from", dateFrom);
        if (dateTo != null) request = request.query("to", dateTo);
        var answer = transport.get(partner, request, RemoteAvailability.class);
        String name = FederationDisplayNames.partnerName(stationRepository, partner, "?");
        var entries = answer.entries().stream()
                .map(entry -> new AvailableInventoryEntry(
                        entry.inventoryId(),
                        entry.inventoryName(),
                        entry.artId(),
                        entry.artName(),
                        partner.partnerStationId(),
                        name,
                        entry.availableCount(),
                        null))
                .toList();
        return new PartnerAvailability(entries, answer.offersAnything());
    }

    /**
     * What this station offers a partner: the inventories with something free in them, counted per
     * kind of thing, and whether it offers the partner anything at all.
     *
     * @param partner  the partnership the question arrived on
     * @param query    part of an inventory's name to narrow by, or {@code null}
     * @param dateFrom the first day the gear is wanted, or {@code null}
     * @param dateTo   the last day, or {@code null}
     * @return what is offered
     */
    public RemoteAvailability serveAvailability(
            ServingPartner partner, String query, LocalDate dateFrom, LocalDate dateTo) {
        int lendingStationId = partner.servingStationId();
        var policy = shareService.policyFor(lendingStationId, partner.askingStationUid());
        if (!policy.offersAnything()) return new RemoteAvailability(List.of(), false);
        if (dateFrom != null && isBlocked(lendingStationId, null, null, dateFrom, dateTo)) {
            return new RemoteAvailability(List.of(), true);
        }

        var lender = lenderAt(lendingStationId);
        var entries = new ArrayList<RemoteAvailableEntry>();
        var inventories = inventoryRepository.findByStation(lendingStationId);
        for (var inv : inventories) {
            if (query != null && !query.isBlank()) {
                if (!inv.name().toLowerCase().contains(query.toLowerCase())) {
                    continue;
                }
            }
            if (dateFrom != null && isBlocked(lendingStationId, inv.id(), null, dateFrom, dateTo)) {
                continue;
            }

            var offered = shareService
                    .filterShared(
                            policy,
                            inventoryRepository.findUnassignedItems(inv.id()).stream()
                                    .filter(lender::owns)
                                    .toList())
                    .stream()
                    .filter(item -> dateFrom == null || !isBlocked(lendingStationId, null, item.id(), dateFrom, dateTo))
                    .toList();

            var perArt = new LinkedHashMap<Integer, Integer>();
            for (var item : offered) {
                perArt.merge(item.artId(), 1, Integer::sum);
            }
            for (var group : perArt.entrySet()) {
                Integer artId = group.getKey();
                entries.add(new RemoteAvailableEntry(
                        inv.id(),
                        inv.name(),
                        artId,
                        artId == null
                                ? null
                                : artRepository
                                        .findById(artId)
                                        .map(InventoryArt::name)
                                        .orElse(null),
                        group.getValue()));
            }
        }
        return new RemoteAvailability(entries, true);
    }

    /**
     * What one partner offers the asking station: the inventories with something free in them, and
     * whether that partner offers anything at all. The second answer is what separates "nothing is
     * shared with you" from "nothing is free just now".
     */
    private record PartnerAvailability(List<AvailableInventoryEntry> entries, boolean offersAnything) {}

    /**
     * One line of a request as the borrowing station writes it.
     *
     * @param inventoryId the inventory the line draws from, or {@code null}
     * @param itemId      the piece the line names, or {@code null}
     * @param artId       the kind of thing the line asks for, or {@code null}
     * @param needId      the line of an appointment's needs this fills, or {@code null}
     */
    public record RequestLine(
            @Nullable Integer inventoryId,
            @Nullable Integer itemId,
            @Nullable Integer artId,
            int quantity,
            @Nullable Integer needId) {}

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
            @Nullable Integer artId,
            @Nullable String artName,
            UUID stationId,
            String stationName,
            int availableCount,
            @Nullable Double distanceKm) {}

    /**
     * The browse answer, with the reason an empty one is empty.
     *
     * @param entries     what is free at the partners right now
     * @param emptyReason why nothing came back, or {@code null} when something did
     */
    public record AvailableInventoryResult(
            List<AvailableInventoryEntry> entries, @Nullable EmptyReason emptyReason) {}

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
