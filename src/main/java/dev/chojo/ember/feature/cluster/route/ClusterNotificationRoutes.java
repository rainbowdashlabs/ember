/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.route.NotificationRoutes.NotificationLinkResponse;
import dev.chojo.ember.feature.notifications.route.NotificationRoutes.NotificationResponse;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import io.javalin.http.BadRequestResponse;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * What is waiting for somebody at the cluster.
 *
 * <p>The same feed as a station member's, addressed to a cluster member instead. It needs its own routes
 * because the recipient is resolved from the cluster the request names rather than from the station, and the
 * two are separate memberships: one person can be both, and their two feeds have nothing to do with each
 * other.
 */
@Singleton
public class ClusterNotificationRoutes implements Routes {
    private final NotificationService notificationService;

    @Inject
    public ClusterNotificationRoutes(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/notifications", this::list, ClusterPermission.USER);
        routes.get(prefix + "/cluster/notifications/count", this::count, ClusterPermission.USER);
        routes.post(prefix + "/cluster/notifications/{id}/acknowledge", this::acknowledge, ClusterPermission.USER);
        routes.post(prefix + "/cluster/notifications/acknowledge-all", this::acknowledgeAll, ClusterPermission.USER);
    }

    @OpenApi(
            path = "/api/v1/cluster/notifications",
            methods = HttpMethod.GET,
            summary = "List the cluster notifications of the caller (max 50)",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = NotificationResponse[].class)))
    private void list(Context ctx) {
        ctx.json(notificationService.findAllForClusterMember(requireClusterMember(ctx)).stream()
                .map(ClusterNotificationRoutes::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/notifications/count",
            methods = HttpMethod.GET,
            summary = "Count the cluster notifications the caller has not read",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200"))
    private void count(Context ctx) {
        ctx.json(new CountResponse(notificationService.countUnacknowledgedForClusterMember(requireClusterMember(ctx))));
    }

    @OpenApi(
            path = "/api/v1/cluster/notifications/{id}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Mark one cluster notification read",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void acknowledge(Context ctx) {
        notificationService.acknowledgeForClusterMember(pathInt(ctx, "id"), requireClusterMember(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/notifications/acknowledge-all",
            methods = HttpMethod.POST,
            summary = "Mark every cluster notification read",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void acknowledgeAll(Context ctx) {
        int count = notificationService.acknowledgeAllForClusterMember(requireClusterMember(ctx));
        ctx.json(new MessageResponse(count + " notifications acknowledged"));
    }

    private static int requireClusterMember(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.clusterMember() == null) throw new BadRequestResponse("No cluster selected");
        return session.clusterMember().id();
    }

    private static NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.id(),
                n.type().name(),
                n.type().localeKey(),
                n.data().paramsAsMap(),
                n.data().link() != null
                        ? new NotificationLinkResponse(
                                n.data().link().route(), n.data().link().routeParams())
                        : null,
                n.createdAt(),
                n.acknowledgedAt());
    }

    record CountResponse(long count) {}
}
