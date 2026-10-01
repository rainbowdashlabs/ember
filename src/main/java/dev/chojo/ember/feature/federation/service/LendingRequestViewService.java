/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.entity.LentOutItem;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A station's side of its lending requests: which requests it may see and act on, and each request,
 * line and message described with the names the screens show.
 *
 * <p>A request has two parties, the station asking and the station owning the gear. Either may read
 * it and talk about it; only the owner decides it and hands out the gear.
 */
@Singleton
public class LendingRequestViewService {
    private final LendingService lendingService;
    private final LendingRepository lendingRepository;
    private final StationRepository stationRepository;
    private final InventoryRepository inventoryRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final EventCrudService eventService;

    @Inject
    public LendingRequestViewService(
            LendingService lendingService,
            LendingRepository lendingRepository,
            StationRepository stationRepository,
            InventoryRepository inventoryRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            EventCrudService eventService) {
        this.lendingService = lendingService;
        this.lendingRepository = lendingRepository;
        this.stationRepository = stationRepository;
        this.inventoryRepository = inventoryRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.eventService = eventService;
    }

    /**
     * The requests a station's member reads: all of the station's for a lending manager, only those
     * the station asked for otherwise.
     *
     * @param stationId the station
     * @param manager   whether the reader manages the station's lending
     * @return the requests, described
     */
    public List<LendingRequestResponse> requestsFor(int stationId, boolean manager) {
        var stream = lendingService.findRequestsByStation(stationId).stream();
        if (!manager) {
            UUID stationUid = stationRepository.resolveUid(stationId);
            stream = stream.filter(r -> Objects.equals(r.requestingStationUid(), stationUid));
        }
        return stream.map(r -> describe(r, stationId)).toList();
    }

    /**
     * A request either party may read and act on.
     *
     * @param requestId the request
     * @param stationId the station asking
     * @return the request
     * @throws dev.chojo.ember.api.RefusalResponse when there is none or the station is neither party
     */
    public LendingRequest requireParty(int requestId, int stationId) {
        var request =
                lendingService.findRequest(requestId).orElseThrow(Refusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS::raise);
        UUID stationUid = stationRepository.resolveUid(stationId);
        if (!Objects.equals(request.requestingStationUid(), stationUid)
                && !Objects.equals(request.owningStationUid(), stationUid)) {
            throw Refusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS.raise();
        }
        return request;
    }

    /**
     * A request only the owning station may act on.
     *
     * @param requestId the request
     * @param stationId the station asking
     * @return the request
     * @throws dev.chojo.ember.api.RefusalResponse when there is none or the station does not own the gear
     */
    public LendingRequest requireOwner(int requestId, int stationId) {
        var request =
                lendingService.findRequest(requestId).orElseThrow(Refusal.LENDING_REQUEST_NOT_HERE_OR_NOT_YOURS::raise);
        if (!Objects.equals(request.owningStationUid(), stationRepository.resolveUid(stationId))) {
            throw Refusal.LENDING_NOT_THE_OWNING_STATION.raise();
        }
        return request;
    }

    /**
     * Whether the given uid is the station's own, which a station cannot borrow from.
     *
     * @param stationId  the station
     * @param stationUid the station the gear would come from
     * @return {@code true} when both are the same station
     */
    public boolean isOwnStation(int stationId, UUID stationUid) {
        return Objects.equals(stationRepository.resolveUid(stationId), stationUid);
    }

    /**
     * What the owning station is told the request is for: the appointment's name, and nothing else.
     *
     * <p>Copied here rather than resolved later, so a rename does not rewrite what was asked for and
     * nothing that is added to an appointment afterwards can travel with it.
     *
     * @param stationId the station asking
     * @param eventId   the appointment the list was collected for, or {@code null}
     * @return the name, or an empty string where there is no appointment or it is not this station's
     */
    public String occasionOf(int stationId, @Nullable Integer eventId) {
        if (eventId == null) return "";
        return eventService
                .findById(eventId)
                .filter(event -> event.stationId() == stationId)
                .map(StationEvent::name)
                .orElse("");
    }

    /**
     * A request with the names of both parties, whether the viewer owns the gear, a summary of the
     * lines, and whether it has run past its last day.
     *
     * @param request          the request
     * @param currentStationId the station viewing it
     * @return the description
     */
    public LendingRequestResponse describe(LendingRequest request, int currentStationId) {
        String requestingName = lendingService.stationName(request.requestingStationUid(), currentStationId);
        String owningName = lendingService.stationName(request.owningStationUid(), currentStationId);
        UUID currentStationUid = stationRepository.resolveUid(currentStationId);
        boolean isOwner = Objects.equals(request.owningStationUid(), currentStationUid);

        String itemSummary = lendingService.buildItemSummary(request.id());

        LocalDate dueBack = request.requestedDateTo();
        boolean overdue = (request.status() == LendingStatus.LENT || request.status() == LendingStatus.APPROVED)
                && dueBack != null
                && dueBack.isBefore(LocalDate.now());

        return new LendingRequestResponse(request, requestingName, owningName, isOwner, itemSummary, overdue);
    }

    /**
     * The lines of a request, each with the name of the inventory it asks from.
     *
     * @param requestId the request
     * @return the lines, described
     */
    public List<EnrichedItem> describeItems(int requestId) {
        return lendingService.findRequestItems(requestId).stream()
                .map(item -> new EnrichedItem(item, lendingService.inventoryName(item)))
                .toList();
    }

    /**
     * The pieces the owning station could hand out for each line of a request, the one a line
     * already names marked as preselected.
     *
     * @param requestId the request
     * @param stationId the owning station
     * @return one entry per assignable piece and line
     */
    public List<AvailableItemDetail> availableItems(int requestId, int stationId) {
        var result = new ArrayList<AvailableItemDetail>();
        for (var ri : lendingService.findRequestItems(requestId)) {
            Integer inventoryId = ri.inventoryId();
            if (inventoryId == null) continue;
            var inv = inventoryRepository.findById(inventoryId).orElse(null);
            if (inv == null) continue;
            Integer preselected = ri.itemId();
            for (var item : lendingService.findAssignableItems(stationId, inventoryId)) {
                String sizeName = null;
                Integer sizeId = item.sizeId();
                if (sizeId != null) {
                    sizeName = inventoryRepository.findSizes(inventoryId).stream()
                            .filter(s -> s.id() == sizeId)
                            .map(InventorySize::label)
                            .findFirst()
                            .orElse(null);
                }
                result.add(new AvailableItemDetail(
                        item.id(),
                        inventoryId,
                        inv.name(),
                        item.internalId(),
                        item.name(),
                        sizeName,
                        ri.id(),
                        preselected != null && preselected == item.id()));
            }
        }
        return result;
    }

    /**
     * A message with who wrote it. The member is looked up only where the station that wrote it is on
     * this instance: a member number written on another instance names somebody else here.
     *
     * @param msg              the message
     * @param viewingStationId the station reading it
     * @return the message, described
     */
    public EnrichedMessage describe(LendingMessage msg, int viewingStationId) {
        String senderName = null;
        boolean writtenHere =
                stationRepository.findByUid(msg.senderStationUid()).isPresent();
        Integer senderMemberId = msg.senderMemberId();
        if (!msg.isSystem() && senderMemberId != null && writtenHere) {
            senderName = stationMemberRepository
                    .findById(senderMemberId)
                    .map(m -> {
                        if (m.displayName() != null && !m.displayName().isBlank()) return m.displayName();
                        Integer accountId = m.accountId();
                        if (accountId != null) {
                            return accountRepository
                                    .findById(accountId)
                                    .map(a -> NameParts.of(a).called())
                                    .orElse(null);
                        }
                        return null;
                    })
                    .orElse(null);
        }
        return new EnrichedMessage(
                msg, senderName, lendingService.stationName(msg.senderStationUid(), viewingStationId));
    }

    /**
     * What of one of the station's inventories is out on loan right now.
     *
     * @param inventoryId the inventory
     * @param stationId   the owning station
     * @return the lines lent out from it
     */
    public List<LentOutItem> lentOut(int inventoryId, int stationId) {
        UUID stationUid = stationRepository.requireUid(stationId);
        return lendingRepository.findLentOutByInventory(inventoryId, stationUid);
    }

    public record LendingRequestResponse(
            LendingRequest request,
            String requestingStationName,
            String owningStationName,
            boolean isOwner,
            String itemSummary,
            boolean overdue) {}

    public record EnrichedMessage(
            LendingMessage message, @Nullable String senderName, String senderStationName) {}

    public record EnrichedItem(LendingRequestItem item, String inventoryName) {}

    public record AvailableItemDetail(
            int itemId,
            int inventoryId,
            String inventoryName,
            @Nullable String internalId,
            String itemName,
            @Nullable String sizeName,
            int requestItemId,
            boolean preselected) {}
}
