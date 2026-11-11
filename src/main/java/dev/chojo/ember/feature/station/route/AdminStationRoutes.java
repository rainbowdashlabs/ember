/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.refusal.StationRefusal;
import dev.chojo.ember.feature.station.entity.ReminderAnswer;
import dev.chojo.ember.feature.station.entity.ReminderResult;
import dev.chojo.ember.feature.station.entity.ReminderTrigger;
import dev.chojo.ember.feature.station.entity.StationOverview;
import dev.chojo.ember.feature.station.entity.StationState;
import dev.chojo.ember.feature.station.service.ManagerReminderService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The station list of the instance administration, with each station's state and manager, and the reminder
 * an administrator sends a manager whose station still waits.
 */
@Singleton
public class AdminStationRoutes implements Routes {
    private final ManagerReminderService reminderService;

    @Inject
    public AdminStationRoutes(ManagerReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/stations", this::list, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/stations/{uid}", this::get, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/stations/{uid}/remind-manager", this::remindManager, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/stations",
            methods = HttpMethod.GET,
            summary = "List every station with its state, manager, member count and last reminder",
            tags = {"Admin Stations"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AdminStationSummary[].class)))
    private void list(Context ctx) {
        ctx.json(reminderService.list().stream().map(AdminStationSummary::of).toList());
    }

    @OpenApi(
            path = "/api/v1/admin/stations/{uid}",
            methods = HttpMethod.GET,
            summary = "One station with its state, manager, member count and last reminder",
            tags = {"Admin Stations"},
            pathParams = @OpenApiParam(name = "uid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AdminStationSummary.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        ctx.json(AdminStationSummary.of(resolve(ctx)));
    }

    @OpenApi(
            path = "/api/v1/admin/stations/{uid}/remind-manager",
            methods = HttpMethod.POST,
            summary = "Remind the manager of a station that waits",
            description =
                    "A manager who cannot sign in yet gets a fresh link, one who can is told the station waits to be set up. Refused for a station that moved away, is set up or has no manager, within ten minutes of the last reminder by hand, and where nobody can be written to.",
            tags = {"Admin Stations"},
            pathParams = @OpenApiParam(name = "uid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagerReminderAnswer.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void remindManager(Context ctx) {
        StationOverview station = resolve(ctx);
        UserSession session = UserSession.from(ctx);
        ReminderAnswer answer = reminderService.remindByHand(station.id(), session.accountId());
        ctx.json(new ManagerReminderAnswer(AdminStationSummary.of(answer.station()), answer.result()));
    }

    private StationOverview resolve(Context ctx) {
        return reminderService.find(pathUuid(ctx, "uid")).orElseThrow(StationRefusal.STATION_NOT_HERE_BY_NAME::raise);
    }

    /**
     * A station as the instance administration lists it.
     *
     * @param id              the station's identifier
     * @param name            the station's name
     * @param state           how far the station has come
     * @param memberCount     its members, without the archived ones
     * @param managerName     the manager's name, or {@code null} without a manager or where it is blank
     * @param managerEmail    the manager's address, or {@code null} without a manager
     * @param waitingSince    since when the station waits for its manager, or {@code null}
     * @param createdAt       when the station was made, or {@code null} for one made before this was kept
     * @param associationName the association the station belongs to, or {@code null}
     * @param lastReminder    the newest reminder its manager was sent in its current wait, or {@code null}
     * @param movedAway       whether this is only the copy the station left here when it moved away
     */
    public record AdminStationSummary(
            UUID id,
            String name,
            StationState state,
            int memberCount,
            @Nullable String managerName,
            @Nullable String managerEmail,
            @Nullable Instant waitingSince,
            @Nullable Instant createdAt,
            @Nullable String associationName,
            @Nullable ManagerReminderSummary lastReminder,
            boolean movedAway) {

        static AdminStationSummary of(StationOverview station) {
            Instant remindedAt = station.lastReminderAt();
            ReminderTrigger trigger = station.lastReminderTrigger();
            return new AdminStationSummary(
                    station.uid(),
                    station.name(),
                    station.state(),
                    station.memberCount(),
                    nameOf(station),
                    station.managerEmail(),
                    station.waitingSince(),
                    station.createdAt(),
                    station.associationName(),
                    remindedAt != null && trigger != null ? new ManagerReminderSummary(remindedAt, trigger) : null,
                    station.movedAway());
        }

        private static @Nullable String nameOf(StationOverview station) {
            String first = station.managerFirstName();
            String last = station.managerLastName();
            String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            return name.isEmpty() ? null : name;
        }
    }

    /**
     * The newest reminder a station's manager was sent.
     *
     * @param sentAt  when it went out
     * @param trigger whether an administrator sent it by hand or it went out automatically
     */
    public record ManagerReminderSummary(Instant sentAt, ReminderTrigger trigger) {}

    /**
     * The answer to a reminder sent by hand.
     *
     * @param station the station as it stands after the reminder
     * @param result  whether a mail went out, or the address was confirmed without one
     */
    public record ManagerReminderAnswer(AdminStationSummary station, ReminderResult result) {}
}
