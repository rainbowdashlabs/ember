/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.LendingMessage;
import dev.chojo.ember.feature.federation.entity.LendingRequest;
import dev.chojo.ember.feature.federation.entity.LendingRequestItem;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.LendingService;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Routes for cross-station inventory lending, chat messages, and date blocking.
 */
@Singleton
public class LendingRoutes implements Routes {

    private final LendingService service;
    private final LendingRepository lendingRepository;
    private final StationRepository stationRepository;
    private final FederationService federationService;
    private final InventoryRepository inventoryRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;

    @Inject
    public LendingRoutes(
            LendingService service,
            LendingRepository lendingRepository,
            StationRepository stationRepository,
            FederationService federationService,
            InventoryRepository inventoryRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository) {
        this.service = service;
        this.lendingRepository = lendingRepository;
        this.stationRepository = stationRepository;
        this.federationService = federationService;
        this.inventoryRepository = inventoryRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Federated available inventory (aggregated from partners)
        routes.get(prefix + "/federated/lending/available", this::listAvailable, Roles.INVENTORY_MANAGER);

        // Remote (server-to-server, RSA signature auth)
        routes.get(prefix + "/remote/lending/messages/{requestId}", this::remoteGetMessages);

        // Lending requests
        routes.get(prefix + "/lending/requests", this::listRequests, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/lending/requests", this::createRequest, Roles.INVENTORY_MANAGER);
        routes.get(prefix + "/lending/requests/{id}", this::getRequest, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/lending/requests/{id}/approve", this::approveRequest, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/lending/requests/{id}/decline", this::declineRequest, Roles.INVENTORY_MANAGER);
        routes.get(
                prefix + "/lending/requests/{id}/available-items",
                this::availableItemsForRequest,
                Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/lending/requests/{id}/assign-items", this::assignItems, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/lending/requests/{id}/lent", this::markLent, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/lending/requests/{id}/returned", this::markReturned, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/lending/requests/{id}/close", this::closeRequest, Roles.INVENTORY_MANAGER);

        // Lent-out items by inventory
        routes.get(
                prefix + "/lending/inventory/{inventoryId}/lent-out",
                this::lentOutByInventory,
                Roles.INVENTORY_MANAGER);

        // Chat messages
        routes.get(prefix + "/lending/requests/{id}/messages", this::getMessages, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/lending/requests/{id}/messages", this::sendMessage, Roles.INVENTORY_MANAGER);

        // Inventory blocks
        routes.get(prefix + "/lending/blocks", this::listBlocks, Roles.INVENTORY_MANAGER);
        routes.post(prefix + "/lending/blocks", this::createBlock, Roles.INVENTORY_MANAGER);
        routes.delete(prefix + "/lending/blocks/{id}", this::deleteBlock, Roles.INVENTORY_MANAGER);
    }

    // -- Requests --

    private void listRequests(Context ctx) {
        var session = UserSession.from(ctx);
        var requests = service.findRequestsByStation(session.stationId());
        ctx.json(requests.stream()
                .map(r -> enrichRequest(r, session.stationId()))
                .toList());
    }

    private void createRequest(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateLendingRequest.class);
        if (req.owningStationId() == session.stationId()) {
            throw new BadRequestResponse("Cannot lend from own station");
        }
        if (req.dateFrom() == null) {
            throw new BadRequestResponse("dateFrom is required");
        }

        LocalDate dateTo = req.dateTo() != null ? req.dateTo() : req.dateFrom();
        var request = service.createRequest(
                session.stationId(),
                req.owningStationId(),
                req.dateFrom(),
                dateTo,
                session.member().id());

        // Add items
        if (req.items() != null) {
            for (var item : req.items()) {
                service.addRequestItem(request.id(), item.inventoryId(), item.itemId(), item.quantity());
            }
        }

        ctx.status(HttpStatus.CREATED).json(enrichRequest(request, session.stationId()));
    }

    private void getRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyAccess(request, session.stationId());

        var items = service.findRequestItems(id);
        ctx.json(new LendingRequestDetail(enrichRequest(request, session.stationId()), enrichItems(items)));
    }

    private void approveRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyOwner(request, session.stationId());
        service.approveRequest(id, session.stationId());
        ctx.json(enrichRequest(service.findRequest(id).orElseThrow(), session.stationId()));
    }

    private void declineRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyOwner(request, session.stationId());
        var body = ctx.bodyAsClass(DeclineBody.class);
        service.declineRequest(id, session.stationId(), body.reason());
        ctx.json(enrichRequest(service.findRequest(id).orElseThrow(), session.stationId()));
    }

    private void availableItemsForRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyOwner(request, session.stationId());

        // Collect all inventory IDs from request items
        var requestItems = service.findRequestItems(id);
        var result = new ArrayList<AvailableItemDetail>();
        for (var ri : requestItems) {
            if (ri.inventoryId() == null) continue;
            var inv = inventoryRepository.findById(ri.inventoryId()).orElse(null);
            if (inv == null) continue;
            var unassigned = inventoryRepository.findUnassignedItems(ri.inventoryId());
            for (var item : unassigned) {
                String sizeName = null;
                if (item.sizeId() != null) {
                    sizeName = inventoryRepository.findSizes(ri.inventoryId()).stream()
                            .filter(s -> s.id() == item.sizeId())
                            .map(InventorySize::label)
                            .findFirst()
                            .orElse(null);
                }
                result.add(new AvailableItemDetail(
                        item.id(),
                        ri.inventoryId(),
                        inv.name(),
                        item.internalId(),
                        item.name(),
                        sizeName,
                        ri.id(),
                        ri.itemId() != null && ri.itemId() == item.id()));
            }
        }
        ctx.json(result);
    }

    private void assignItems(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyOwner(request, session.stationId());
        if (request.status() != LendingStatus.APPROVED) {
            throw new BadRequestResponse("Can only assign items to approved requests");
        }

        var assignments = ctx.bodyAsClass(AssignItemsRequest.class);
        if (assignments.items() != null) {
            for (var a : assignments.items()) {
                service.assignItem(a.requestItemId(), a.itemId());
            }
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void markLent(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyOwner(request, session.stationId());
        service.markLent(id, session.stationId());
        ctx.json(enrichRequest(service.findRequest(id).orElseThrow(), session.stationId()));
    }

    private void markReturned(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyAccess(request, session.stationId());
        service.markReturned(id, session.stationId());
        ctx.json(enrichRequest(service.findRequest(id).orElseThrow(), session.stationId()));
    }

    private void closeRequest(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyAccess(request, session.stationId());
        service.closeRequest(id, session.stationId());
        ctx.json(enrichRequest(service.findRequest(id).orElseThrow(), session.stationId()));
    }

    // -- Messages --

    private void getMessages(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyAccess(request, session.stationId());
        var messages = service.getMessages(id, session.stationId());
        ctx.json(messages.stream().map(this::enrichMessage).toList());
    }

    private EnrichedMessage enrichMessage(LendingMessage msg) {
        String senderName = null;
        if (!msg.isSystem() && msg.senderMemberId() != null) {
            senderName = stationMemberRepository
                    .findById(msg.senderMemberId())
                    .map(m -> {
                        if (m.displayName() != null && !m.displayName().isBlank()) return m.displayName();
                        if (m.accountId() != null) {
                            return accountRepository
                                    .findById(m.accountId())
                                    .map(a -> (a.firstName() + " " + a.lastName()).trim())
                                    .orElse(null);
                        }
                        return null;
                    })
                    .orElse(null);
        }
        String stationName = stationRepository
                .findById(msg.senderStationId())
                .map(Station::name)
                .orElse("Unknown");
        return new EnrichedMessage(msg, senderName, stationName);
    }

    private void sendMessage(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = service.findRequest(id).orElseThrow(NotFoundResponse::new);
        verifyAccess(request, session.stationId());
        var body = ctx.bodyAsClass(MessageBody.class);
        if (body.message() == null || body.message().isBlank()) {
            throw new BadRequestResponse("message is required");
        }
        String senderName = session.account().fullName().trim();
        var msg = service.sendMessage(id, session.stationId(), session.member().id(), senderName, body.message());
        ctx.status(HttpStatus.CREATED).json(msg);
    }

    // -- Blocks --

    private void listBlocks(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findBlocks(session.stationId()));
    }

    private void createBlock(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateBlockRequest.class);
        if (req.blockFrom() == null || req.blockTo() == null) {
            throw new BadRequestResponse("blockFrom and blockTo are required");
        }
        var block = service.createBlock(
                session.stationId(),
                req.inventoryId(),
                req.itemId(),
                req.blockFrom(),
                req.blockTo(),
                req.reason() != null ? req.reason() : "");
        ctx.status(HttpStatus.CREATED).json(block);
    }

    private void deleteBlock(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.deleteBlock(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Available inventory --

    private void listAvailable(Context ctx) {
        var session = UserSession.from(ctx);
        String query = ctx.queryParam("q");
        String fromParam = ctx.queryParam("from");
        String toParam = ctx.queryParam("to");
        LocalDate dateFrom = fromParam != null && !fromParam.isBlank() ? LocalDate.parse(fromParam) : null;
        LocalDate dateTo = toParam != null && !toParam.isBlank() ? LocalDate.parse(toParam) : dateFrom;

        var partners = federationService.findPartners(session.stationId()).stream()
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
        } catch (Exception ignored) {
        }
        for (var future : futures) {
            try {
                results.addAll(future.get());
            } catch (Exception ignored) {
            }
        }
        ctx.json(results);
    }

    private List<AvailableInventoryEntry> findAvailableForPartner(
            FederationPartner partner, String query, LocalDate dateFrom, LocalDate dateTo) {
        var partnerStation =
                stationRepository.findByUid(partner.partnerStationId()).orElse(null);
        if (partnerStation == null) return List.of();
        int partnerStationId = partnerStation.id();
        String stationName = partnerStation.name();

        if (dateFrom != null && service.isBlocked(partnerStationId, null, null, dateFrom, dateTo)) {
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
            if (dateFrom != null && service.isBlocked(partnerStationId, inv.id(), null, dateFrom, dateTo)) {
                continue;
            }

            var unassigned = inventoryRepository.findUnassignedItems(inv.id());
            int availableCount;
            if (dateFrom != null) {
                availableCount = (int) unassigned.stream()
                        .filter(item -> !service.isBlocked(partnerStationId, null, item.id(), dateFrom, dateTo))
                        .count();
            } else {
                availableCount = unassigned.size();
            }
            if (availableCount > 0) {
                entries.add(new AvailableInventoryEntry(
                        inv.id(), inv.name(), partnerStationId, stationName, availableCount));
            }
        }
        return entries;
    }

    // -- Helpers --

    private void verifyAccess(LendingRequest request, int stationId) {
        if (request.requestingStationId() != stationId && request.owningStationId() != stationId) {
            throw new NotFoundResponse();
        }
    }

    private void verifyOwner(LendingRequest request, int stationId) {
        if (request.owningStationId() != stationId) {
            throw new BadRequestResponse("Only the owning station can perform this action");
        }
    }

    private LendingRequestResponse enrichRequest(LendingRequest request, int currentStationId) {
        String requestingName = stationRepository
                .findById(request.requestingStationId())
                .map(Station::name)
                .orElse("Unknown");
        String owningName = stationRepository
                .findById(request.owningStationId())
                .map(Station::name)
                .orElse("Unknown");
        boolean isOwner = request.owningStationId() == currentStationId;

        // Build item summary for list display
        var items = service.findRequestItems(request.id());
        var summaryParts = new ArrayList<String>();
        for (var item : items) {
            String name = item.inventoryId() != null
                    ? inventoryRepository
                            .findById(item.inventoryId())
                            .map(Inventory::name)
                            .orElse("?")
                    : "?";
            summaryParts.add(item.quantity() + "x " + name);
        }
        String itemSummary = String.join(", ", summaryParts);

        boolean overdue = (request.status() == LendingStatus.LENT || request.status() == LendingStatus.APPROVED)
                && request.requestedDateTo() != null
                && request.requestedDateTo().isBefore(LocalDate.now());

        return new LendingRequestResponse(request, requestingName, owningName, isOwner, itemSummary, overdue);
    }

    private List<EnrichedItem> enrichItems(List<LendingRequestItem> items) {
        return items.stream()
                .map(item -> {
                    String name = item.inventoryId() != null
                            ? inventoryRepository
                                    .findById(item.inventoryId())
                                    .map(Inventory::name)
                                    .orElse("Unbekannt")
                            : "Unbekannt";
                    return new EnrichedItem(item, name);
                })
                .toList();
    }

    // -- Lent-out items by inventory --

    private void lentOutByInventory(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int inventoryId = ctx.pathParamAsClass("inventoryId", Integer.class).get();
        var lentItems = lendingRepository.findLentOutByInventory(inventoryId, session.stationId());
        ctx.json(lentItems);
    }

    // -- Records --

    public record CreateLendingRequest(
            int owningStationId, LocalDate dateFrom, LocalDate dateTo, List<ItemRequest> items) {}

    public record ItemRequest(Integer inventoryId, Integer itemId, int quantity) {}

    public record DeclineBody(String reason) {}

    public record MessageBody(String message) {}

    public record CreateBlockRequest(
            Integer inventoryId, Integer itemId, LocalDate blockFrom, LocalDate blockTo, String reason) {}

    public record LendingRequestResponse(
            LendingRequest request,
            String requestingStationName,
            String owningStationName,
            boolean isOwner,
            String itemSummary,
            boolean overdue) {}

    public record EnrichedMessage(LendingMessage message, String senderName, String senderStationName) {}

    public record EnrichedItem(LendingRequestItem item, String inventoryName) {}

    public record LendingRequestDetail(LendingRequestResponse request, List<EnrichedItem> items) {}

    public record AvailableInventoryEntry(
            int inventoryId, String inventoryName, int stationId, String stationName, int availableCount) {}

    public record AvailableItemDetail(
            int itemId,
            int inventoryId,
            String inventoryName,
            String internalId,
            String itemName,
            String sizeName,
            int requestItemId,
            boolean preselected) {}

    public record AssignItemsRequest(List<ItemAssignment> items) {}

    public record ItemAssignment(int requestItemId, int itemId) {}

    // -- Remote endpoints (server-to-server, RSA signature auth) --

    private void remoteGetMessages(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int requestId = ctx.pathParamAsClass("requestId", Integer.class).get();
        var messages = service.getLocalMessages(requestId, partner.stationId());
        ctx.json(messages);
    }

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }
}
