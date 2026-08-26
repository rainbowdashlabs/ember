/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.AckKind;
import dev.chojo.ember.feature.inventory.entity.ExchangeLog;
import dev.chojo.ember.feature.inventory.entity.ExchangeRequest;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.entity.InventoryType;
import dev.chojo.ember.feature.inventory.entity.ItemOwner;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.ExchangeExportService;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for inventory exchange requests including creating, reviewing, shipping, completing,
 * and cancelling exchanges, plus PDF export of exchange logs.
 */
@Singleton
public class ExchangeRoutes implements Routes {
    private final ExchangeService exchangeService;
    private final ExchangeExportService exchangeExportService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final InventoryRepository inventoryRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public ExchangeRoutes(
            ExchangeService exchangeService,
            ExchangeExportService exchangeExportService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            InventoryRepository inventoryRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.exchangeService = exchangeService;
        this.exchangeExportService = exchangeExportService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.inventoryRepository = inventoryRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    /**
     * Loads an exchange request and asserts it belongs to the caller's station, returning it.
     * Answers 404 both when absent and when owned by another station.
     */
    private ExchangeRequest requireOwnedExchange(int exchangeId, UserSession session) {
        var exchange = exchangeService.findById(exchangeId).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, exchange.stationId());
        return exchange;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/exchanges", this::list, StationPermission.USER);
        routes.get(prefix + "/exchanges/{id}", this::get, StationPermission.USER);
        routes.get(prefix + "/exchanges/{id}/logs", this::logs, StationPermission.USER);
        routes.post(prefix + "/exchanges", this::create, StationPermission.USER);
        routes.put(prefix + "/exchanges/{id}/status", this::updateStatus, StationPermission.INVENTORY_EXCHANGE);
        routes.delete(prefix + "/exchanges/{id}", this::delete, StationPermission.INVENTORY_EXCHANGE);
        routes.post(prefix + "/exchanges/export", this::exportPdf, StationPermission.INVENTORY_EXCHANGE);
    }

    @OpenApi(
            path = "/api/v1/exchanges",
            methods = HttpMethod.GET,
            summary = "List exchange requests. Inventory managers see all for station, others see own.",
            tags = {"Exchange"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ExchangeResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        List<ExchangeRequest> requests;
        if (session.hasPermission(StationPermission.INVENTORY_EXCHANGE)) {
            requests = exchangeService.findByStation(session.stationId());
        } else {
            var own = exchangeService.findByMember(session.member().id());
            if (session.hasPermission(StationPermission.MEMBER_GUARDIAN)) {
                var managed =
                        stationMemberRepository.findManaged(session.member().id());
                var allIds = new HashSet<Integer>();
                allIds.add(session.member().id());
                managed.forEach(m -> allIds.add(m.id()));
                var combined = new ArrayList<ExchangeRequest>();
                for (var ex : exchangeService.findByStation(session.stationId())) {
                    if (allIds.contains(ex.memberId())) combined.add(ex);
                }
                requests = combined;
            } else {
                requests = own;
            }
        }
        ctx.json(requests.stream().map(this::toResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/exchanges/{id}",
            methods = HttpMethod.GET,
            summary = "Get a single exchange request with logs",
            tags = {"Exchange"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ExchangeResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        ctx.json(toResponse(requireOwnedExchange(id, session)));
    }

    @OpenApi(
            path = "/api/v1/exchanges/{id}/logs",
            methods = HttpMethod.GET,
            summary = "Get status change logs for an exchange request",
            tags = {"Exchange"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LogResponse[].class)))
    private void logs(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedExchange(id, session);
        var logs = exchangeService.findLogs(id);
        ctx.json(logs.stream().map(this::toLogResponse).toList());
    }

    @OpenApi(
            path = "/api/v1/exchanges",
            methods = HttpMethod.POST,
            summary = "Create an exchange request",
            tags = {"Exchange"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateExchangeRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = ExchangeResponse.class)))
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateExchangeRequest.class);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BadRequestResponse("reason is required");
        }
        int callerMemberId = session.member().id();
        int targetMemberId = callerMemberId;
        if (request.memberId() != null && request.memberId() != callerMemberId) {
            // Verify caller manages the target member or has inventory management
            if (!session.hasPermission(StationPermission.INVENTORY_EXCHANGE)) {
                boolean manages = stationMemberRepository.findManagers(request.memberId()).stream()
                        .anyMatch(m -> m.id() == callerMemberId);
                if (!manages) {
                    throw new ForbiddenResponse("You do not manage this member");
                }
            }
            targetMemberId = request.memberId();
        }
        Integer createdBy = targetMemberId != callerMemberId ? callerMemberId : null;
        var exchange = exchangeService.create(
                session.stationId(),
                targetMemberId,
                session.account().fullName().trim(),
                request.itemId(),
                request.inventoryId(),
                request.oldSizeId(),
                request.newSizeId(),
                request.reason(),
                createdBy);

        ctx.status(HttpStatus.CREATED).json(toResponse(exchange));
    }

    @OpenApi(
            path = "/api/v1/exchanges/{id}/status",
            methods = HttpMethod.PUT,
            summary = "Update the status of an exchange request",
            tags = {"Exchange"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateStatusRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ExchangeResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateStatus(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        requireOwnedExchange(id, session);
        var request = ctx.bodyAsClass(UpdateStatusRequest.class);
        if (request.status() == null) {
            throw new BadRequestResponse("status is required");
        }
        ExchangeStatus status = request.status();
        if (status == ExchangeStatus.DONE && request.exchangedItemId() == null) {
            // For DONE status, exchangedItemId is optional but recommended
        }
        var exchange = exchangeService.updateStatus(
                id, status, session.member().id(), request.note(), request.exchangedItemId());
        ctx.json(toResponse(exchange));
    }

    @OpenApi(
            path = "/api/v1/exchanges/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete an exchange request",
            tags = {"Exchange"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedExchange(id, session);
        if (exchangeService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/exchanges/export",
            methods = HttpMethod.POST,
            summary = "Export selected exchange requests as PDF",
            tags = {"Exchange"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ExportRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void exportPdf(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ExportRequest.class);
        var generatedBy = stationMemberRepository
                .findById(session.member().id())
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> a.firstName() + " " + a.lastName())
                .orElse("?");
        var pdf = exchangeExportService.exportPdf(
                session.stationId(),
                request.exchangeIds() != null ? request.exchangeIds() : List.of(),
                request.extraFieldIds() != null ? request.extraFieldIds() : List.of(),
                generatedBy);
        if (pdf.isEmpty()) {
            throw new NotFoundResponse("No data to export");
        }
        ctx.contentType("application/pdf");
        ctx.header("Content-Disposition", "attachment; filename=\"exchange-requests.pdf\"");
        ctx.result(pdf.get());
    }

    private String resolveSizeLabel(Integer sizeId, int inventoryId) {
        if (sizeId == null) return null;
        return inventoryRepository.findSizes(inventoryId).stream()
                .filter(s -> s.id() == sizeId)
                .map(InventorySize::label)
                .findFirst()
                .orElse(null);
    }

    private String resolveCreatedByName(Integer createdBy) {
        if (createdBy == null) return null;
        return stationMemberRepository
                .findById(createdBy)
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse(null);
    }

    /**
     * One row of the list, as the screens read it.
     *
     * <p>Who owns the piece is read off the piece rather than off the inventory: the inventory only
     * says which owners it may hold, and in a mixed one that is "both", which answers nothing about
     * the row in front of you.
     *
     * <p>A movement between the store and the body above the station concerns nobody in particular,
     * so there is no member to name and none is asked for.
     */
    private ExchangeResponse toResponse(ExchangeRequest exchange) {
        String memberName = stationMemberRepository
                .findById(exchange.memberId())
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
        MemberIdentity memberIdentity =
                exchange.memberId() > 0 ? memberIdentityFactory.local(exchange.stationId(), exchange.memberId()) : null;
        Inventory inventory =
                inventoryRepository.findById(exchange.inventoryId()).orElse(null);
        String inventoryName = inventory != null ? inventory.name() : "";
        InventoryType inventoryType = inventory != null ? inventory.inventoryType() : null;
        Optional<InventoryItem> item =
                exchange.itemId() == null ? Optional.empty() : inventoryRepository.findItemById(exchange.itemId());
        ItemOwner ownerKind = item.map(InventoryItem::ownerKind).orElse(null);

        return new ExchangeResponse(
                exchange.id(),
                exchange.memberId(),
                memberName,
                memberIdentity,
                exchange.itemId(),
                exchange.inventoryId(),
                inventoryName,
                exchange.oldSizeId(),
                resolveSizeLabel(exchange.oldSizeId(), exchange.inventoryId()),
                exchange.newSizeId(),
                resolveSizeLabel(exchange.newSizeId(), exchange.inventoryId()),
                inventoryType,
                ownerKind,
                item.map(InventoryItem::name).orElse(null),
                exchange.purpose(),
                exchange.status(),
                exchange.reason(),
                exchange.createdAt(),
                exchange.updatedAt(),
                resolveCreatedByName(exchange.createdBy()));
    }

    private LogResponse toLogResponse(ExchangeLog log) {
        String changedByName = stationMemberRepository
                .findById(log.changedBy())
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
        return new LogResponse(
                log.id(), log.stepLabel(), log.ackKind(), log.changedBy(), changedByName, log.changedAt(), log.note());
    }

    public record ExportRequest(List<Integer> exchangeIds, List<Integer> extraFieldIds) {}

    public record ExchangeResponse(
            int id,
            int memberId,
            String memberName,
            MemberIdentity memberIdentity,
            Integer itemId,
            int inventoryId,
            String inventoryName,
            Integer oldSizeId,
            String oldSizeLabel,
            Integer newSizeId,
            String newSizeLabel,
            InventoryType inventoryType,
            /** Who owns the piece itself, which is the interesting half in an inventory holding both. */
            ItemOwner ownerKind,
            /**
             * What the piece is called. The overview is the only place a piece in the post still appears,
             * so naming the inventory it came from is not enough to know which one is meant.
             */
            String itemName,
            /** Whether this is an issue, a return or an exchange. */
            MovementPurpose purpose,
            ExchangeStatus status,
            String reason,
            Instant createdAt,
            Instant updatedAt,
            String createdByName) {}

    public record LogResponse(
            int id,
            String stepLabel,
            AckKind ackKind,
            int changedBy,
            String changedByName,
            Instant changedAt,
            String note) {}

    public record CreateExchangeRequest(
            Integer memberId, Integer itemId, int inventoryId, Integer oldSizeId, Integer newSizeId, String reason) {}

    public record UpdateStatusRequest(ExchangeStatus status, String note, Integer exchangedItemId) {}
}
