/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.InventoryRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
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
import dev.chojo.ember.feature.federation.entity.SharePolicy;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository.OfferedIds;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteAvailability;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteAvailableEntry;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingAccepted;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingLine;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingNotice;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingPiece;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingRequest;
import dev.chojo.ember.feature.federation.route.RemoteLendingRoutes.RemoteLendingStatus;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.inventory.entity.BorrowedPiece;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryArt;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventoryMatch;
import dev.chojo.ember.feature.inventory.entity.ItemCustody;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.repository.InventoryArtRepository;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.BorrowedGearService;
import dev.chojo.ember.feature.inventory.service.InventorySearchService;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final InventorySearchService searchService;

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
            DomainEventBus eventBus,
            InventorySearchService searchService) {
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
        this.searchService = searchService;
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

    static LocalDate day(PathParams params, String name) {
        try {
            return params.query(name)
                    .filter(value -> !value.isBlank())
                    .map(LocalDate::parse)
                    .orElse(null);
        } catch (DateTimeParseException e) {
            throw FederationRefusal.LENDING_DAY_NOT_A_DAY.raise();
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
     * @param occasion  what to show the owning station the request is for
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
        Integer lendingStationHere = stationHere(owningUid);
        var request = repository.createRequest(
                UUID.randomUUID(), requestingUid, owningUid, dateFrom, dateTo, createdBy, eventId, eventDate, occasion);
        for (var line : lines) {
            if (lendingStationHere != null) {
                repository.addRequestItem(
                        request.id(), line.inventoryId(), line.itemId(), line.artId(), line.quantity(), line.needId());
            } else {
                repository.addRequestItem(
                        request.id(),
                        null,
                        null,
                        null,
                        line.quantity(),
                        line.needId(),
                        new OfferedIds(line.inventoryId(), line.artId(), line.itemId()));
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
            throw FederationRefusal.LENDING_PARTNER_DOES_NOT_LEND.raise();
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
                throw FederationRefusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS.raise();
            }
            return new RemoteLendingAccepted(labelsOf(request.id()));
        }
        var lines = body.lines() == null ? List.<RemoteLendingLine>of() : body.lines();
        for (var line : lines) {
            var target =
                    new LendingRequestItem(0, 0, line.inventoryId(), line.itemId(), line.artId(), 0, null, "").target();
            if (target == null || !ownsTarget(partner.servingStationId(), target)) {
                throw FederationRefusal.LENDING_LINE_NAMES_FOREIGN_GEAR.raise();
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
                .orElseThrow(FederationRefusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS::raise);
    }

    /**
     * Whether lending is switched on for a partnership. This is the coarsest of the questions asked
     * before anything is offered or requested, and it is the one every other federated feature
     * already asks of its own capability.
     *
     * @param partner the partnership seen from the asking station
     * @return {@code true} when gear may travel along it
     */
    boolean lendsWith(FederationPartner partner) {
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
     * <p>This is the path a person drives, so it refuses out loud. The automatic path cannot, and
     * quietly passes over what it may not lend instead.
     *
     * @param requestItemId  the line of the request being filled
     * @param assignedItemId the piece being set aside
     * @param stationId      the station doing the lending, whose gear it has to be
     * @throws RefusalResponse when the piece is not this station's to lend, is reported missing, an open
     *                         movement carries it, or another loan holds it
     */
    public boolean assignItem(int requestItemId, int assignedItemId, int stationId) {
        requireLendable(stationId, assignedItemId);
        repository
                .findRequestIdOfLine(requestItemId)
                .ifPresent(requestId -> custodyService.requireFreeToActOn(
                        assignedItemId, ItemCustodyService.Allowance.lending(requestId)));
        boolean assigned = repository.assignItem(requestItemId, assignedItemId);
        if (assigned) log.info("Item {} was set aside for lending request item {}", assignedItemId, requestItemId);
        else log.warn("Assign of item {} to lending request item {} affected zero rows", assignedItemId, requestItemId);
        return assigned;
    }

    /**
     * The gear of one inventory a station may offer a partner, which is what the picker behind a
     * manual assignment shows. A piece an open movement carries or a loan already holds is left out, as
     * filling a loan by itself leaves it out.
     *
     * @param stationId   the station doing the lending
     * @param inventoryId the inventory being picked from
     * @return the free pieces that are this station's to lend
     */
    public List<InventoryItem> findAssignableItems(int stationId, int inventoryId) {
        var lender = lenderAt(stationId);
        if (!ownsInventory(stationId, inventoryId)) return List.of();
        return inventoryRepository.findFreeToHandOut(inventoryId).stream()
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
            throw FederationRefusal.LENDING_GEAR_NOT_THE_STATIONS.raise();
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
     * truth about the thing and now shows which partner has it; the borrower's row is the truth about
     * where it is this fortnight, and it goes away again when the gear does.
     *
     * @param requestId the lending request
     * @param stationId the station handing the gear over
     * @return {@code true} when the request moved to lent
     */
    public boolean markLent(int requestId, int stationId) {
        requireReadyToHandOver(requestId);
        return moveOn(requestId, stationId, LendingStatus.LENT, "Ausrüstung ausgeliehen", null);
    }

    /**
     * Refuses to hand over a loan whose pieces have changed since it was agreed.
     *
     * <p>A loan is agreed days before the gear leaves, and in between a piece may have gone missing, been
     * handed to a member or taken up by a movement. Handing it over regardless wrote the partner as
     * having it while somebody else did. Every piece is asked before anything moves, so a loan is handed
     * over whole or not at all.
     *
     * @param requestId the lending request about to be handed over
     * @throws RefusalResponse when a piece set aside for it is reported missing, carried by an open
     *                         movement, held by another loan, or no longer resting at the station
     */
    private void requireReadyToHandOver(int requestId) {
        for (var line : repository.findItemsByRequest(requestId)) {
            for (int itemId : repository.findAssignedItems(line.id())) {
                custodyService.requireFreeToActOn(itemId, ItemCustodyService.Allowance.lending(requestId));
                inventoryRepository
                        .findItemById(itemId)
                        .filter(item ->
                                item.custody() != ItemCustody.WITH_OWNER && item.custody() != ItemCustody.AT_STATION)
                        .ifPresent(item -> {
                            throw InventoryRefusal.CUSTODY_NOT_HANDED_OUT_FROM_HERE.raise();
                        });
            }
        }
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
     * Moves a request on as one of its two stations, and notifies the other one.
     *
     * <p>The change is made here, with its consequences for the gear, the station's own note in the
     * thread and the notice to the other station's managers, and then handed to the other station.
     * A station on this instance reads the same row and finds the change already made; a station on
     * another instance makes it on its own copy.
     *
     * @param requestId     the request
     * @param stationId     the station moving it on
     * @param status        where it moves to
     * @param systemMessage what the station's thread shows about it
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
        var handedOver = applyConsequences(request, status);
        repository.createMessage(requestId, actingUid, null, systemMessage, true);
        publishStatusChange(
                request, stationId, stationName(stationId), stationOf(request.otherParty(actingUid)), status);
        var partner = findPartnerForStation(stationId, request.otherParty(actingUid));
        if (partner != null) {
            transport.notify(
                    partner,
                    RemoteLendingRoutes.CHANGE_STATUS.at(request.uid()),
                    new RemoteLendingStatus(status, reason, handedOver));
        }
        log.info("Lending request {} moved to {} by station {}", requestId, status, stationId);
        return true;
    }

    /**
     * A request moved on by the partner station, made on this instance's copy.
     *
     * <p>Only the lending station agrees to a request and hands the gear over; either of the two may
     * decline, give back or close it. A change this copy already shows is the one row two stations of
     * this instance share, and nothing further happens. Gear handed over by a lending station on
     * another instance is written down here from the pieces it lists.
     *
     * @param partner the partnership the change arrived on
     * @param uid     the request's identity between the two stations
     * @param body    what it moved to
     */
    public void serveStatus(ServingPartner partner, UUID uid, RemoteLendingStatus body) {
        var request = sharedRequest(partner, uid);
        boolean lendersOnly = body.status() == LendingStatus.APPROVED || body.status() == LendingStatus.LENT;
        if (lendersOnly && !request.owningStationUid().equals(partner.askingStationUid())) {
            throw FederationRefusal.LENDING_NOT_THE_OWNING_STATION.raise();
        }
        if (request.status() == body.status()) return;
        if (!repository.updateRequestStatus(request.id(), body.status())) return;
        applyConsequences(request, body.status());
        if (body.status() == LendingStatus.LENT) receiveHandedOver(request, body.handedOver());
        publishStatusChange(request, 0, partnerName(partner), partner.servingStationId(), body.status());
        log.info("Lending request {} moved to {} by partner {}", request.id(), body.status(), partner.partnerId());
    }

    /**
     * What a request moving on does to the gear. Agreeing fills the lines, handing over and giving
     * back move the pieces set aside. On a copy of a request whose gear is on another instance there
     * are no pieces here to move, so only the borrowed copies come and go there.
     *
     * @return what was handed over, for a borrowing station on another instance to write down; empty
     * unless the gear was handed over here
     */
    private List<RemoteLendingPiece> applyConsequences(LendingRequest request, LendingStatus status) {
        switch (status) {
            case APPROVED -> autoAssignItems(request.id());
            case LENT -> {
                return handOver(request);
            }
            case RETURNED -> takeBack(request);
            default -> {}
        }
        return List.of();
    }

    /**
     * Hands the pieces set aside over to the borrowing station, line by line. The owner's rows show
     * which partner has them; a borrower on this instance gets its copies written here, and one on
     * another instance writes them itself from the pieces listed in the answer.
     *
     * @return the pieces handed over, each with the position of its line
     */
    private List<RemoteLendingPiece> handOver(LendingRequest request) {
        Integer borrower = stationHere(request.requestingStationUid());
        Integer owner = stationHere(request.owningStationUid());
        var lines = repository.findItemsByRequest(request.id());
        var handedOver = new ArrayList<RemoteLendingPiece>();
        for (int position = 0; position < lines.size(); position++) {
            int lineId = lines.get(position).id();
            var pieces = new ArrayList<BorrowedPiece>();
            for (int itemId : repository.findAssignedItems(lineId)) {
                custodyService.lendToPartner(itemId, borrower);
                inventoryRepository.findItemById(itemId).map(BorrowedPiece::of).ifPresent(pieces::add);
            }
            if (borrower != null && owner != null) {
                borrowedGearService.handOver(pieces, request.owningStationUid(), owner, borrower, lineId);
            }
            for (var piece : pieces) {
                handedOver.add(new RemoteLendingPiece(position, piece.internalId(), piece.name()));
            }
        }
        return handedOver;
    }

    /**
     * Writes down the gear a lending station on another instance handed over, at the borrowing
     * station of this instance. The pieces name their line by its position, which both copies of
     * a request share. A piece the lending station listed without a name is written down under the
     * label of its line, since every piece here has a name and the request has already moved on.
     *
     * @param request the borrowing station's copy of the request
     * @param pieces  what the lending station listed
     */
    private void receiveHandedOver(LendingRequest request, List<RemoteLendingPiece> pieces) {
        Integer borrower = stationHere(request.requestingStationUid());
        if (borrower == null || pieces.isEmpty() || stationHere(request.owningStationUid()) != null) return;
        var lines = repository.findItemsByRequest(request.id());
        var byLine = new LinkedHashMap<Integer, List<BorrowedPiece>>();
        for (var piece : pieces) {
            if (piece.line() < 0 || piece.line() >= lines.size()) continue;
            String sent = piece.name();
            String name =
                    sent == null || sent.isBlank() ? lines.get(piece.line()).label() : sent;
            byLine.computeIfAbsent(piece.line(), line -> new ArrayList<>())
                    .add(BorrowedPiece.named(piece.internalId(), name));
        }
        byLine.forEach((position, linePieces) -> borrowedGearService.handOver(
                linePieces,
                request.owningStationUid(),
                null,
                borrower,
                lines.get(position).id()));
    }

    /**
     * Takes the gear back: the borrowed copies of every line go, wherever they were written, and the
     * owner's pieces set aside on them are home again.
     */
    private void takeBack(LendingRequest request) {
        for (var line : repository.findItemsByRequest(request.id())) {
            borrowedGearService.handBack(line.id());
            for (int itemId : repository.findAssignedItems(line.id())) {
                custodyService.returnFromPartner(itemId);
            }
        }
    }

    private int stationOf(UUID stationUid) {
        return Objects.requireNonNullElse(stationHere(stationUid), 0);
    }

    /**
     * The station running on this instance under a uid. A station that moved to another instance
     * left a copy here under the same uid, and that copy is not where the station is.
     *
     * @param stationUid the station
     * @return its id here, or {@code null} where it runs on another instance
     */
    private @Nullable Integer stationHere(UUID stationUid) {
        return stationRepository.findHereByUid(stationUid).map(Station::id).orElse(null);
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
     * <p>A partner on this instance has notified them itself when it wrote the message, so only word
     * from another instance is passed on.
     *
     * @param partner the partnership the word arrived on
     * @param uid     the request's identity between the two stations
     * @param body    who wrote
     */
    public void serveNotice(ServingPartner partner, UUID uid, RemoteLendingNotice body) {
        var request = sharedRequest(partner, uid);
        if (stationRepository.findHereByUid(partner.askingStationUid()).isPresent()) return;
        eventBus.publish(new LendingMessageSent(
                0, partner.servingStationId(), request.id(), partnerName(partner), body.senderName()));
    }

    /**
     * The messages this station wrote on a lending request, for the partner on the other side of
     * that request.
     *
     * <p>Being a partner of this station is not the same as being a party to one of its lending
     * negotiations. Without asking whose request it is, one partner would read what this station
     * sent to another: what was asked for, what was refused, and when.
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
        var here = stationRepository.findHereByUid(stationUid).map(Station::name);
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
     * <p>An empty answer shows which of two situations it is, and no more than that. Listing the
     * inventories that were held back would be the more helpful search and the wrong product: it
     * would reveal to another station what you own and which of it you are deliberately keeping.
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
     * partner never notified. A line that cannot be filled is left empty for somebody to fill by hand.
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
                stationRepository.findHereByUid(request.owningStationUid()).orElse(null);
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
                        .filter(item -> custodyService.isFreeToActOn(
                                item.id(), ItemCustodyService.Allowance.lending(requestId)))
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
                    .filter(item ->
                            custodyService.isFreeToActOn(item.id(), ItemCustodyService.Allowance.lending(requestId)))
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
     * <p>Ownership means a station may lend a piece; it does not mean the piece is here. Gear already at
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

    @Nullable
    FederationPartner findPartnerForStation(int localStationId, UUID partnerStationUid) {
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
        var decorated = new ArrayList<AvailableInventoryEntry>(results.size());
        for (var entry : results) {
            decorated.add(new AvailableInventoryEntry(
                    entry.inventoryId(),
                    entry.inventoryName(),
                    entry.artId(),
                    entry.artName(),
                    entry.stationId(),
                    entry.stationName(),
                    entry.availableCount(),
                    distanceKm(local, entry.stationId()),
                    entry.pictureKey(),
                    entry.icon(),
                    entry.color(),
                    entry.homogeneous()));
        }
        decorated.sort(Comparator.comparing(
                AvailableInventoryEntry::distanceKm, Comparator.nullsLast(Comparator.naturalOrder())));
        return decorated;
    }

    /**
     * The great-circle distance from a station to a partner station on this instance, computed here so
     * the partner never has to give its coordinates away.
     *
     * @param local             the asking station, or {@code null}
     * @param partnerStationUid the partner station
     * @return the distance in kilometres, or {@code null} where either station gives no location
     */
    @Nullable
    Double distanceKm(@Nullable Station local, UUID partnerStationUid) {
        if (local == null || local.latitude() == null || local.longitude() == null) return null;
        var partner = stationRepository.findHereByUid(partnerStationUid).orElse(null);
        if (partner == null || partner.latitude() == null || partner.longitude() == null) return null;
        return StationLocationService.distanceKm(
                local.latitude().doubleValue(),
                local.longitude().doubleValue(),
                partner.latitude().doubleValue(),
                partner.longitude().doubleValue());
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
                        null,
                        entry.pictureKey(),
                        entry.icon(),
                        entry.color(),
                        entry.homogeneous()))
                .toList();
        return new PartnerAvailability(entries, answer.offersAnything());
    }

    /**
     * What this station offers a partner: the inventories with something free in them, counted per
     * kind of thing, and whether it offers the partner anything at all. Free is what lending accepts:
     * on the shelf, carried by no open movement and held by no loan, so a partner is not offered a piece
     * it could only be refused.
     *
     * @param partner  the partnership the question arrived on
     * @param query    a search over the names and descriptions of the inventories, their kinds and their
     *                 pieces, or {@code null}
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
        var entries = offeredGroups(lendingStationId, policy, null, query, dateFrom, dateTo).stream()
                .map(group -> new RemoteAvailableEntry(
                        group.inventory().id(),
                        group.inventory().name(),
                        group.artId(),
                        group.artName(),
                        group.count(),
                        group.pictureKey(),
                        group.icon(),
                        group.color(),
                        group.inventory().homogeneous()))
                .toList();
        return new RemoteAvailability(entries, true);
    }

    /**
     * Whether a partner may see a photo: it belongs to an inventory with something on offer to the
     * partner right now, whatever the day, and is one the lending shop shows that offer with: the
     * inventory's own, a kind's of an offered piece, or an offered piece's own. Only the inventory
     * holding the key is looked at.
     *
     * @param partner    the partnership the question arrived on
     * @param pictureKey the key asked for
     * @return whether the photo may be sent
     */
    public boolean isPictureOffered(ServingPartner partner, String pictureKey) {
        int lendingStationId = partner.servingStationId();
        var inventoryId = artRepository.inventoryOfPicture(lendingStationId, pictureKey);
        if (inventoryId.isEmpty()) return false;
        var policy = shareService.policyFor(lendingStationId, partner.askingStationUid());
        if (!policy.offersAnything()) return false;
        for (var stock : offeredStock(lendingStationId, policy, inventoryId.get(), null, null, null)) {
            if (pictureKey.equals(stock.inventory().pictureKey())) return true;
            var offeredArts = stock.pieces().stream()
                    .map(InventoryItem::artId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            for (var art : artRepository.findByInventory(stock.inventory().id())) {
                if (offeredArts.contains(art.id()) && pictureKey.equals(art.pictureKey())) return true;
            }
            var offeredPieces = stock.pieces().stream().map(InventoryItem::id).collect(Collectors.toSet());
            for (var presentation :
                    inventoryRepository.findItemPresentations(stock.inventory().id())) {
                if (offeredPieces.contains(presentation.itemId()) && pictureKey.equals(presentation.pictureKey())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * What a station has free to offer under a policy, counted per inventory and kind, of one
     * inventory where {@code onlyInventory} is given.
     */
    private List<OfferedGroup> offeredGroups(
            int lendingStationId,
            SharePolicy policy,
            @Nullable Integer onlyInventory,
            @Nullable String query,
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo) {
        var groups = new ArrayList<OfferedGroup>();
        for (var stock : offeredStock(lendingStationId, policy, onlyInventory, query, dateFrom, dateTo)) {
            var perArt = new LinkedHashMap<Integer, Integer>();
            for (var item : stock.pieces()) {
                perArt.merge(item.artId(), 1, Integer::sum);
            }
            for (var group : perArt.entrySet()) {
                Integer artId = group.getKey();
                InventoryArt art =
                        artId == null ? null : artRepository.findById(artId).orElse(null);
                groups.add(new OfferedGroup(stock.inventory(), artId, art, group.getValue()));
            }
        }
        return groups;
    }

    /**
     * What a station has free to offer under a policy, per inventory: the pieces on the shelf, carried by
     * no open movement, held by no loan, owned by the station, shared with the partner, and not blocked
     * over the days asked for. An inventory with nothing on offer is left out, and so is one the search
     * does not reach through something on offer.
     *
     * @param lendingStationId the station doing the lending
     * @param policy           what the station shares with the asking partner
     * @param onlyInventory    one inventory to look at, or {@code null} for every one
     * @param query            a search over names and descriptions, or {@code null}
     * @param dateFrom         the first day the gear is wanted, or {@code null}
     * @param dateTo           the last day, or {@code null}
     * @return one entry per inventory with something on offer, in the station's order
     */
    List<OfferedStock> offeredStock(
            int lendingStationId,
            SharePolicy policy,
            @Nullable Integer onlyInventory,
            @Nullable String query,
            @Nullable LocalDate dateFrom,
            @Nullable LocalDate dateTo) {
        var lender = lenderAt(lendingStationId);
        var result = new ArrayList<OfferedStock>();
        Map<Integer, InventoryMatch> matches =
                query == null || query.isBlank() ? null : searchService.match(lendingStationId, query);
        List<InventoryBlock> blocks = dateFrom == null
                ? List.of()
                : repository.findActiveBlocks(lendingStationId, dateFrom, dateTo == null ? dateFrom : dateTo);
        for (var inv : inventoryRepository.findByStation(lendingStationId)) {
            if (onlyInventory != null && inv.id() != onlyInventory) continue;
            InventoryMatch match = matches == null ? null : matches.get(inv.id());
            if (matches != null && match == null) continue;
            if (blocks(blocks, inv.id(), null)) continue;

            var offered = shareService
                    .filterShared(
                            policy,
                            inventoryRepository.findFreeToHandOut(inv.id()).stream()
                                    .filter(lender::owns)
                                    .toList())
                    .stream()
                    .filter(item -> !blocks(blocks, null, item.id()))
                    .toList();
            if (offered.isEmpty()) continue;
            if (match != null && !matchesAnyOffered(match, offered)) continue;
            result.add(new OfferedStock(inv, offered, match));
        }
        return result;
    }

    /**
     * Whether any of the blocks covers the station, the inventory or the piece, by
     * {@link InventoryBlock#covers}, for a caller that read the blocks once.
     *
     * @param blocks      the blocks over the days in question
     * @param inventoryId the inventory, or {@code null}
     * @param itemId      the piece, or {@code null}
     * @return {@code true} when one of them covers it
     */
    static boolean blocks(List<InventoryBlock> blocks, @Nullable Integer inventoryId, @Nullable Integer itemId) {
        return blocks.stream().anyMatch(block -> block.covers(inventoryId, itemId));
    }

    /**
     * The blocks of a station that touch any of the days, read once.
     *
     * @param stationId the station
     * @param first     the first day
     * @param last      the last day, inclusive
     * @return the blocks
     */
    List<InventoryBlock> blocksBetween(int stationId, LocalDate first, LocalDate last) {
        return repository.findActiveBlocks(stationId, first, last);
    }

    /**
     * One inventory with the pieces of it on offer to a partner, and how a search reached it.
     *
     * @param inventory the inventory
     * @param pieces    the pieces on offer, never empty
     * @param match     how the search reached it, or {@code null} where nothing was searched for
     */
    record OfferedStock(
            Inventory inventory,
            List<InventoryItem> pieces,
            @Nullable InventoryMatch match) {}

    /**
     * Whether a search reaches the inventory through something actually on offer: the inventory
     * itself, or a kind or a piece among the offered pieces. A match on a piece held back must not
     * bring the inventory into the answer, or the search would reveal to the partner that it exists.
     */
    private static boolean matchesAnyOffered(InventoryMatch match, List<InventoryItem> offered) {
        if (match.itself()) return true;
        var kinds = match.kinds().stream().map(InventoryMatch.Hit::id).collect(Collectors.toSet());
        var pieces = match.pieces().stream().map(InventoryMatch.Hit::id).collect(Collectors.toSet());
        return offered.stream()
                .anyMatch(item -> pieces.contains(item.id()) || (item.artId() != null && kinds.contains(item.artId())));
    }

    /**
     * One inventory, or one kind of it, with how many of its pieces are on offer. It is shown with the
     * kind's photo and icon where the kind has them, otherwise the inventory's. The kind's id stays
     * even where the kind itself could not be read, as it did before.
     */
    private record OfferedGroup(
            Inventory inventory,
            @Nullable Integer artId,
            @Nullable InventoryArt art,
            int count) {
        @Nullable
        String artName() {
            return art == null ? null : art.name();
        }

        @Nullable
        String pictureKey() {
            return firstNamed(art == null ? null : art.pictureKey(), inventory.pictureKey());
        }

        @Nullable
        String icon() {
            return firstNamed(art == null ? null : art.icon(), inventory.icon());
        }

        @Nullable
        String color() {
            return firstNamed(art == null ? null : art.color(), inventory.color());
        }

        private static @Nullable String firstNamed(@Nullable String first, @Nullable String second) {
            return first != null && !first.isBlank() ? first : second;
        }
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
     * @param pictureKey  the key of the photo it is shown with at the partner, to ask for through this
     *                    instance, or {@code null} for none
     * @param icon        the icon it is drawn with, or {@code null} for the plain shape
     * @param color       the colour of that icon, or {@code null}
     * @param homogeneous whether the partner's inventory holds one thing in many copies
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
            @Nullable Double distanceKm,
            @Nullable String pictureKey,
            @Nullable String icon,
            @Nullable String color,
            boolean homogeneous) {}

    /**
     * The browse answer, with the reason an empty one is empty.
     *
     * @param entries     what is free at the partners right now
     * @param emptyReason why nothing came back, or {@code null} when something did
     */
    public record AvailableInventoryResult(
            List<AvailableInventoryEntry> entries, @Nullable EmptyReason emptyReason) {}

    /**
     * The two situations an empty browse answer can mean. It shows nothing finer on purpose: which
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
