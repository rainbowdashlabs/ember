/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.equipment.entity.EquipmentHandover;
import dev.chojo.ember.feature.equipment.entity.EquipmentNeed;
import dev.chojo.ember.feature.equipment.entity.NeedCoverage;
import dev.chojo.ember.feature.equipment.service.EquipmentNeedService;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
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

import java.time.LocalDate;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * What an appointment needs, and what actually went out for one evening.
 *
 * <p>Two permissions meet here and neither was written for this before. Reading a line takes
 * {@code INVENTORY_READ}, because it is a statement about the station's gear. Writing one takes
 * {@code EVENT_EDIT}, because it is part of planning the evening: nothing here reserves, holds or
 * moves anything, so no third permission is minted. Borrowing what a line still misses takes
 * {@code INVENTORY_LENDING_REQUEST}, where lending already asks for it.
 */
@Singleton
public class EquipmentNeedRoutes implements Routes {

    private final EquipmentNeedService needService;
    private final EventCrudService eventService;

    @Inject
    public EquipmentNeedRoutes(EquipmentNeedService needService, EventCrudService eventService) {
        this.needService = needService;
        this.eventService = eventService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/{eventId}/equipment", this::list, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/events/{eventId}/equipment/coverage", this::coverage, StationPermission.INVENTORY_READ);
        routes.get(prefix + "/events/{eventId}/equipment/handovers", this::handovers, StationPermission.INVENTORY_READ);
        routes.post(prefix + "/events/{eventId}/equipment", this::add, StationPermission.EVENT_EDIT);
        routes.put(prefix + "/events/{eventId}/equipment/order", this::reorder, StationPermission.EVENT_EDIT);
        routes.put(prefix + "/events/{eventId}/equipment/{needId}", this::update, StationPermission.EVENT_EDIT);
        routes.delete(prefix + "/events/{eventId}/equipment/{needId}", this::delete, StationPermission.EVENT_EDIT);
        routes.post(
                prefix + "/events/{eventId}/equipment/{needId}/handovers",
                this::handOver,
                StationPermission.INVENTORY_ASSIGN);
        routes.delete(
                prefix + "/events/{eventId}/equipment/handovers/{handoverId}",
                this::handBack,
                StationPermission.INVENTORY_ASSIGN);
    }

    private StationEvent ownEvent(Context ctx) {
        UserSession session = UserSession.from(ctx);
        StationEvent event = eventService.findById(pathInt(ctx, "eventId")).orElseThrow(NotFoundResponse::new);
        RouteSupport.requireSameStation(session, event.stationId());
        return event;
    }

    private EquipmentNeed ownNeed(Context ctx) {
        StationEvent event = ownEvent(ctx);
        EquipmentNeed need = needService.findById(pathInt(ctx, "needId")).orElseThrow(NotFoundResponse::new);
        if (need.eventId() != event.id()) throw new NotFoundResponse();
        return need;
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/equipment",
            methods = HttpMethod.GET,
            summary = "List what an appointment needs",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EquipmentNeed[].class)))
    private void list(Context ctx) {
        ctx.json(needService.findByEvent(ownEvent(ctx).id()));
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/equipment/coverage",
            methods = HttpMethod.GET,
            summary = "Read what one evening needs against what is there",
            description =
                    "Answers per line how much of it the station has free, how much is here on loan and how much has been asked for and not arrived. An over-claim is reported with the appointments involved rather than being prevented.",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "date", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = NeedCoverage[].class)))
    private void coverage(Context ctx) {
        StationEvent event = ownEvent(ctx);
        try {
            ctx.json(needService.coverage(event.id(), requiredDate(ctx)));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/equipment/handovers",
            methods = HttpMethod.GET,
            summary = "List the pieces that went out for one evening",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "date", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EquipmentHandover[].class)))
    private void handovers(Context ctx) {
        ctx.json(needService.handovers(ownEvent(ctx).id(), requiredDate(ctx)));
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/equipment",
            methods = HttpMethod.POST,
            summary = "Write a line onto an appointment",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NeedRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EquipmentNeed.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void add(Context ctx) {
        StationEvent event = ownEvent(ctx);
        var body = ctx.bodyAsClass(NeedRequest.class);
        try {
            LineTarget target = LineTarget.of(body.itemId(), body.artId(), body.inventoryId());
            ctx.status(HttpStatus.CREATED)
                    .json(needService.add(
                            event.id(),
                            event.stationId(),
                            body.eventDate(),
                            target,
                            body.quantity() == null ? 1 : body.quantity(),
                            body.leadMinutes() == null ? EquipmentNeed.DEFAULT_LEAD_MINUTES : body.leadMinutes(),
                            body.trailMinutes() == null ? EquipmentNeed.DEFAULT_LEAD_MINUTES : body.trailMinutes()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/equipment/{needId}",
            methods = HttpMethod.PUT,
            summary = "Change what a line asks for",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventId", type = Integer.class, required = true),
                @OpenApiParam(name = "needId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NeedUpdate.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        EquipmentNeed need = ownNeed(ctx);
        var body = ctx.bodyAsClass(NeedUpdate.class);
        try {
            needService.update(
                    need.id(),
                    body.quantity() == null ? need.quantity() : body.quantity(),
                    body.leadMinutes() == null ? need.leadMinutes() : body.leadMinutes(),
                    body.trailMinutes() == null ? need.trailMinutes() : body.trailMinutes());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/equipment/order",
            methods = HttpMethod.PUT,
            summary = "Reorder the lines of an appointment",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = OrderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reorder(Context ctx) {
        StationEvent event = ownEvent(ctx);
        var body = ctx.bodyAsClass(OrderRequest.class);
        needService.reorder(event.id(), body.needIds() == null ? List.of() : body.needIds());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/equipment/{needId}",
            methods = HttpMethod.DELETE,
            summary = "Take a line off an appointment",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventId", type = Integer.class, required = true),
                @OpenApiParam(name = "needId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        needService.delete(ownNeed(ctx).id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/equipment/{needId}/handovers",
            methods = HttpMethod.POST,
            summary = "Record that a piece went out for one evening",
            description = "Where a loose claim becomes a firm one: the line gains the pieces that actually went.",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventId", type = Integer.class, required = true),
                @OpenApiParam(name = "needId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = HandoverRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = EquipmentHandover[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void handOver(Context ctx) {
        UserSession session = UserSession.from(ctx);
        EquipmentNeed need = ownNeed(ctx);
        var body = ctx.bodyAsClass(HandoverRequest.class);
        if (body.itemIds() == null || body.itemIds().isEmpty()) {
            throw new BadRequestResponse("A handover names at least one piece");
        }
        try {
            ctx.status(HttpStatus.CREATED)
                    .json(body.itemIds().stream()
                            .map(itemId -> needService.handOver(
                                    need.id(),
                                    requiredDate(ctx),
                                    itemId,
                                    session.member().id()))
                            .toList());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    @OpenApi(
            path = "/api/v1/events/{eventId}/equipment/handovers/{handoverId}",
            methods = HttpMethod.DELETE,
            summary = "Record that a piece came back",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventId", type = Integer.class, required = true),
                @OpenApiParam(name = "handoverId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void handBack(Context ctx) {
        StationEvent event = ownEvent(ctx);
        if (!needService.handBack(pathInt(ctx, "handoverId"), event.id())) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private static LocalDate requiredDate(Context ctx) {
        String raw = ctx.queryParam("date");
        if (raw == null || raw.isBlank()) throw new BadRequestResponse("A date is required");
        try {
            return LocalDate.parse(raw);
        } catch (RuntimeException e) {
            throw new BadRequestResponse("The date is not a date");
        }
    }

    /**
     * @param eventDate the one evening the line speaks for, or {@code null} for the whole series
     */
    public record NeedRequest(
            Integer itemId,
            Integer artId,
            Integer inventoryId,
            Integer quantity,
            Integer leadMinutes,
            Integer trailMinutes,
            LocalDate eventDate) {}

    public record NeedUpdate(Integer quantity, Integer leadMinutes, Integer trailMinutes) {}

    public record OrderRequest(List<Integer> needIds) {}

    public record HandoverRequest(List<Integer> itemIds) {}
}
