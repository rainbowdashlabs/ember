/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.entity.ExchangeLog;
import dev.chojo.ember.entity.ExchangeRequest;
import dev.chojo.ember.entity.ExchangeStatus;
import dev.chojo.ember.entity.Inventory;
import dev.chojo.ember.entity.InventorySize;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.InventoryRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import dev.chojo.ember.service.ExchangeService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
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
import java.util.List;

@Singleton
public class ExchangeRoutes implements Routes {
    private final ExchangeService exchangeService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final InventoryRepository inventoryRepository;
    private final dev.chojo.ember.service.NotificationService notificationService;

    @Inject
    public ExchangeRoutes(ExchangeService exchangeService, AccountRepository accountRepository,
                          StationMemberRepository stationMemberRepository, InventoryRepository inventoryRepository,
                          dev.chojo.ember.service.NotificationService notificationService) {
        this.exchangeService = exchangeService;
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
            requests = exchangeService.findByMember(session.member().id());
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
        exchangeService.findById(id).ifPresentOrElse(
                request -> ctx.json(toResponse(request)),
                () -> { throw new NotFoundResponse(); });
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
        var exchange = exchangeService.create(
                session.stationId(), session.member().id(),
                request.itemId(), request.inventoryId(), request.sizeId(), request.reason());
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
        var exchange = exchangeService.updateStatus(id, status, session.member().id(), request.note());
        notificationService.notify(exchange.memberId(),
                dev.chojo.ember.entity.NotificationType.EXCHANGE_STATUS_CHANGE, exchange.id(),
                "Tausch-Status geändert: " + status.name());
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

    private ExchangeResponse toResponse(ExchangeRequest exchange) {
        String memberName = stationMemberRepository.findById(exchange.memberId())
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
        Inventory inventory = inventoryRepository.findById(exchange.inventoryId()).orElse(null);
        String inventoryName = inventory != null ? inventory.name() : "";
        String inventoryType = inventory != null ? inventory.inventoryType() : "";
        String sizeLabel = null;
        if (exchange.sizeId() != null && inventory != null) {
            sizeLabel = inventoryRepository.findSizes(exchange.inventoryId()).stream()
                    .filter(s -> s.id() == exchange.sizeId())
                    .map(InventorySize::label)
                    .findFirst()
                    .orElse(null);
        }
        return new ExchangeResponse(exchange.id(), exchange.memberId(), memberName, exchange.itemId(),
                exchange.inventoryId(), inventoryName, exchange.sizeId(), sizeLabel, inventoryType,
                exchange.status().name(), exchange.reason(), exchange.createdAt(), exchange.updatedAt());
    }

    private LogResponse toLogResponse(ExchangeLog log) {
        String changedByName = stationMemberRepository.findById(log.changedBy())
                .flatMap(m -> accountRepository.findById(m.accountId()))
                .map(a -> (a.firstName() + " " + a.lastName()).trim())
                .orElse("");
        return new LogResponse(log.id(), log.oldStatus().name(), log.newStatus().name(),
                log.changedBy(), changedByName, log.changedAt(), log.note());
    }

    public record ExchangeResponse(int id, int memberId, String memberName, Integer itemId, int inventoryId,
                                    String inventoryName, Integer sizeId, String sizeLabel, String inventoryType,
                                    String status, String reason, Instant createdAt, Instant updatedAt) {}

    public record LogResponse(int id, String oldStatus, String newStatus, int changedBy, String changedByName,
                               Instant changedAt, String note) {}

    public record CreateExchangeRequest(Integer itemId, int inventoryId, Integer sizeId, String reason) {}

    public record UpdateStatusRequest(String status, String note) {}
}
