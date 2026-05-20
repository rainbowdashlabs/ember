/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.ExchangeLog;
import dev.chojo.ember.feature.inventory.entity.ExchangeRequest;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventorySize;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.inventory.service.ExchangeExportService;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

@Singleton
public class ExchangeRoutes implements Routes {
    private final ExchangeService exchangeService;
    private final ExchangeExportService exchangeExportService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final InventoryRepository inventoryRepository;
    private final NotificationService notificationService;

    @Inject
    public ExchangeRoutes(
            ExchangeService exchangeService,
            ExchangeExportService exchangeExportService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            InventoryRepository inventoryRepository,
            NotificationService notificationService) {
        this.exchangeService = exchangeService;
        this.exchangeExportService = exchangeExportService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.inventoryRepository = inventoryRepository;
        this.notificationService = notificationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/exchanges", this::list, Roles.LOGIN);
        routes.get(prefix + "/exchanges/{id}", this::get, Roles.LOGIN);
        routes.get(prefix + "/exchanges/{id}/logs", this::logs, Roles.LOGIN);
        routes.post(prefix + "/exchanges", this::create, Roles.LOGIN);
        routes.put(prefix + "/exchanges/{id}/status", this::updateStatus, Roles.INVENTORY_MANAGEMENT);
        routes.delete(prefix + "/exchanges/{id}", this::delete, Roles.INVENTORY_MANAGEMENT);
        routes.post(prefix + "/exchanges/export", this::exportPdf, Roles.INVENTORY_MANAGEMENT);
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
        if (session.hasRole(Roles.INVENTORY_MANAGEMENT)) {
            requests = exchangeService.findByStation(session.stationId());
        } else {
            var own = exchangeService.findByMember(session.member().id());
            if (session.hasRole(Roles.GUARDIAN)) {
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        exchangeService.findById(id).ifPresentOrElse(request -> ctx.json(toResponse(request)), () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/exchanges/{id}/logs",
            methods = HttpMethod.GET,
            summary = "Get status change logs for an exchange request",
            tags = {"Exchange"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LogResponse[].class)))
    private void logs(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
            if (!session.hasRole(Roles.INVENTORY_MANAGEMENT)) {
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
                request.itemId(),
                request.inventoryId(),
                request.oldSizeId(),
                request.newSizeId(),
                request.reason(),
                createdBy);

        // Notify managers about the new exchange request
        String memberName = session.account().fullName().trim();
        String inventoryName = inventoryRepository
                .findById(exchange.inventoryId())
                .map(Inventory::name)
                .orElse("?");
        var exchangeParams = new HashMap<String, String>();
        exchangeParams.put("memberName", memberName);
        exchangeParams.put("inventoryName", inventoryName);
        if (request.reason() != null && !request.reason().isBlank()) {
            exchangeParams.put("reason", request.reason());
        }
        var exchangeData = NotificationData.of(
                "notification.exchangeNewRequest",
                exchangeParams,
                new NotificationData.NotificationLink("inventory-exchanges"));
        var inventoryMgmtIds =
                stationMemberRepository.findMembersWithRole(session.stationId(), Roles.INVENTORY_MANAGEMENT).stream()
                        .map(StationMember::id)
                        .toList();
        notificationService.notifyMembersIfAbsent(
                inventoryMgmtIds,
                NotificationType.EXCHANGE_NEW_REQUEST,
                exchangeData,
                session.member().id());

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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(UpdateStatusRequest.class);
        if (request.status() == null || request.status().isBlank()) {
            throw new BadRequestResponse("status is required");
        }
        ExchangeStatus status;
        try {
            status = ExchangeStatus.valueOf(request.status());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Invalid status: " + request.status());
        }
        if (status == ExchangeStatus.EXCHANGED && request.exchangedItemId() == null) {
            // For EXCHANGED status, exchangedItemId is optional but recommended
        }
        var exchange = exchangeService.updateStatus(
                id, status, session.member().id(), request.note(), request.exchangedItemId());
        String inventoryName = inventoryRepository
                .findById(exchange.inventoryId())
                .map(Inventory::name)
                .orElse("?");
        var params = new HashMap<String, String>();
        params.put("status", status.name());
        params.put("inventoryName", inventoryName);
        if (exchange.reason() != null && !exchange.reason().isBlank()) {
            params.put("reason", exchange.reason());
        }
        var data = NotificationData.of(
                "notification.exchangeStatusChange",
                params,
                new NotificationData.NotificationLink("inventory-exchanges"));
        notificationService.notify(exchange.memberId(), NotificationType.EXCHANGE_STATUS_CHANGE, data);
        var invMgmtIds =
                stationMemberRepository.findMembersWithRole(session.stationId(), Roles.INVENTORY_MANAGEMENT).stream()
                        .map(StationMember::id)
                        .toList();
        notificationService.notifyMembersIfAbsent(
                invMgmtIds,
                NotificationType.EXCHANGE_STATUS_CHANGE,
                data,
                session.member().id());
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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

    public record ExportRequest(List<Integer> exchangeIds, List<Integer> extraFieldIds) {}

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

    private ExchangeResponse toResponse(ExchangeRequest exchange) {
        String memberName = stationMemberRepository
                .findById(exchange.memberId())
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
        Inventory inventory =
                inventoryRepository.findById(exchange.inventoryId()).orElse(null);
        String inventoryName = inventory != null ? inventory.name() : "";
        String inventoryType = inventory != null ? inventory.inventoryType().name() : "";
        return new ExchangeResponse(
                exchange.id(),
                exchange.memberId(),
                memberName,
                exchange.itemId(),
                exchange.inventoryId(),
                inventoryName,
                exchange.oldSizeId(),
                resolveSizeLabel(exchange.oldSizeId(), exchange.inventoryId()),
                exchange.newSizeId(),
                resolveSizeLabel(exchange.newSizeId(), exchange.inventoryId()),
                inventoryType,
                exchange.status().name(),
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
                log.id(),
                log.oldStatus().name(),
                log.newStatus().name(),
                log.changedBy(),
                changedByName,
                log.changedAt(),
                log.note());
    }

    public record ExchangeResponse(
            int id,
            int memberId,
            String memberName,
            Integer itemId,
            int inventoryId,
            String inventoryName,
            Integer oldSizeId,
            String oldSizeLabel,
            Integer newSizeId,
            String newSizeLabel,
            String inventoryType,
            String status,
            String reason,
            Instant createdAt,
            Instant updatedAt,
            String createdByName) {}

    public record LogResponse(
            int id,
            String oldStatus,
            String newStatus,
            int changedBy,
            String changedByName,
            Instant changedAt,
            String note) {}

    public record CreateExchangeRequest(
            Integer memberId, Integer itemId, int inventoryId, Integer oldSizeId, Integer newSizeId, String reason) {}

    public record UpdateStatusRequest(String status, String note, Integer exchangedItemId) {}
}
