/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Map;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for notification management including listing, acknowledging,
 * and bulk-acknowledging notifications for the current user.
 */
@Singleton
public class NotificationRoutes implements Routes {
    private final NotificationService notificationService;

    @Inject
    public NotificationRoutes(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/notifications", this::list, StationPermission.USER);
        routes.get(prefix + "/notifications/unacknowledged", this::listUnacknowledged, StationPermission.USER);
        routes.get(prefix + "/notifications/count", this::count, StationPermission.USER);
        routes.post(prefix + "/notifications/{id}/acknowledge", this::acknowledge, StationPermission.USER);
        routes.post(prefix + "/notifications/acknowledge-all", this::acknowledgeAll, StationPermission.USER);
    }

    @OpenApi(
            path = "/api/v1/notifications",
            methods = HttpMethod.GET,
            summary = "List all notifications (max 50)",
            tags = {"Notifications"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = NotificationResponse[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(notificationService.findAll(session.member().id()).stream()
                .map(this::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/notifications/unacknowledged",
            methods = HttpMethod.GET,
            summary = "List unacknowledged notifications",
            tags = {"Notifications"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = NotificationResponse[].class)))
    private void listUnacknowledged(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(notificationService.findUnacknowledged(session.member().id()).stream()
                .map(this::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/notifications/count",
            methods = HttpMethod.GET,
            summary = "Count unacknowledged notifications",
            tags = {"Notifications"},
            responses = @OpenApiResponse(status = "200"))
    private void count(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(new CountResponse(
                notificationService.countUnacknowledged(session.member().id())));
    }

    @OpenApi(
            path = "/api/v1/notifications/{id}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Acknowledge a notification",
            tags = {"Notifications"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void acknowledge(Context ctx) {
        int id = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        notificationService.acknowledge(id, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/notifications/acknowledge-all",
            methods = HttpMethod.POST,
            summary = "Acknowledge all notifications",
            tags = {"Notifications"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void acknowledgeAll(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int count = notificationService.acknowledgeAll(session.member().id());
        ctx.json(new MessageResponse(count + " notifications acknowledged"));
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.id(),
                n.type().name(),
                n.type().localeKey(),
                n.data().paramsAsMap(),
                n.data().link() != null
                        ? new NotificationLinkResponse(
                                n.data().link().route(),
                                n.data().link().routeParams(),
                                n.data().link().query())
                        : null,
                n.createdAt(),
                n.acknowledgedAt());
    }

    record CountResponse(long count) {}

    /**
     * The link a notification carries. The query names a place inside the page the route opens,
     * which is how a notification about a comment reaches that comment.
     */
    public record NotificationLinkResponse(String route, Map<String, Object> routeParams, Map<String, Object> query) {}

    public record NotificationResponse(
            int id,
            String type,
            String localeKey,
            Map<String, String> params,
            NotificationLinkResponse link,
            Instant createdAt,
            Instant acknowledgedAt) {}
}
