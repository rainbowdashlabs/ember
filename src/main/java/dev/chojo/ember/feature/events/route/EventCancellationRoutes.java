/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.CancellationNotice;
import dev.chojo.ember.feature.events.entity.CancelledEventDate;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCancellationService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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
import java.time.format.DateTimeParseException;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.events.route.EventOwnership.requireOwnedEvent;

/**
 * Calling appointments off: one date at a time, a whole series in one go, and bringing a date back.
 *
 * <p>Calling off a date of a series leaves every other date of it as it was. A one-time appointment
 * is called off by its date too, since it has only the one; calling it off as a series is refused.
 *
 * <p>This class registers the literal {@code GET /events/cancellations}, so it is bound before
 * {@link EventRoutes}, whose {@code GET /events/{id}} would otherwise answer it.
 */
@Singleton
public class EventCancellationRoutes implements Routes {
    private final EventCrudService crudService;
    private final EventCancellationService cancellationService;
    private final GuardianPolicy guardianPolicy;

    @Inject
    public EventCancellationRoutes(
            EventCrudService crudService, EventCancellationService cancellationService, GuardianPolicy guardianPolicy) {
        this.crudService = crudService;
        this.cancellationService = cancellationService;
        this.guardianPolicy = guardianPolicy;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/cancellations", this::listStationCancelledDates, StationPermission.USER);
        routes.post(prefix + "/events/{id}/cancel", this::cancelSeries, StationPermission.EVENT_EDIT);
        routes.post(prefix + "/events/{id}/dates/{date}/cancel", this::cancelDate, StationPermission.EVENT_EDIT);
        routes.post(prefix + "/events/{id}/dates/{date}/restore", this::restoreDate, StationPermission.EVENT_EDIT);
        routes.get(prefix + "/events/{id}/cancellations", this::listCancelledDates, StationPermission.USER);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/cancel",
            methods = HttpMethod.POST,
            summary = "Cancel a whole series",
            description = "Every date of the series is off for good. A one-time appointment is refused: it is "
                    + "cancelled by its date.",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CancelRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void cancelSeries(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(CancelRequest.class);
        cancellationService.cancelSeries(session.stationId(), id, req.reason());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/dates/{date}/cancel",
            methods = HttpMethod.POST,
            summary = "Cancel one date of an appointment",
            description = "Only this date is off; the other dates of a series take place as before. The date "
                    + "must be one the appointment falls on and must not lie in the past.",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "date", description = "ISO yyyy-MM-dd, on the station's calendar", required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CancelRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void cancelDate(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var event = requireOwnedEvent(crudService, pathInt(ctx, "id"), session);
        var req = ctx.bodyAsClass(CancelRequest.class);
        Integer cancelledBy = session.member() != null ? session.member().id() : null;
        cancellationService.cancelDate(event, pathDate(ctx), req.reason(), cancelledBy);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/dates/{date}/restore",
            methods = HttpMethod.POST,
            summary = "Restore a cancelled date",
            description = "The date takes place again and everybody who kept a place on it is told. Dates of "
                    + "a series cancelled as a whole stay cancelled.",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "date", description = "ISO yyyy-MM-dd, on the station's calendar", required = true)
            },
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void restoreDate(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var event = requireOwnedEvent(crudService, pathInt(ctx, "id"), session);
        cancellationService.restoreDate(event, pathDate(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/cancellations",
            methods = HttpMethod.GET,
            summary = "List the cancelled dates of an appointment",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CancellationNotice[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void listCancelledDates(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var event = requireOwnedEvent(crudService, pathInt(ctx, "id"), session);
        ctx.json(cancellationService.findCancelledDates(event.id()));
    }

    @OpenApi(
            path = "/api/v1/events/cancellations",
            methods = HttpMethod.GET,
            summary = "List the cancelled dates of every appointment the reader sees",
            description = "Only dates cancelled one by one. A series cancelled as a whole says so on the "
                    + "appointment itself.",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CancelledEventDate[].class)))
    private void listStationCancelledDates(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var visible = crudService.findFilteredForMembers(
                session.stationId(), EventVisibility.memberIdsSeenBy(session, guardianPolicy), null, null);
        ctx.json(cancellationService.findCancelledDates(
                session.stationId(), visible.stream().map(StationEvent::id).toList()));
    }

    private static LocalDate pathDate(Context ctx) {
        try {
            return LocalDate.parse(ctx.pathParam("date"));
        } catch (DateTimeParseException e) {
            throw Refusal.CANCELLATION_DAY_NOT_A_DATE.raise();
        }
    }

    /**
     * Why an appointment or one of its dates is called off.
     *
     * @param reason what the members are told, or null to tell them nothing more
     */
    public record CancelRequest(String reason) {}
}
