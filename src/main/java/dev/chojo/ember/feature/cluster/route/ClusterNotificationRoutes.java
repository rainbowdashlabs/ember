/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.route.NotificationRoutes.CountResponse;
import dev.chojo.ember.feature.notifications.route.NotificationRoutes.NotificationResponse;
import dev.chojo.ember.feature.notifications.service.NotificationInbox;
import dev.chojo.ember.feature.notifications.service.NotificationPreferences;
import dev.chojo.ember.feature.notifications.service.NotificationPreferences.ClusterMail;
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
    private final NotificationInbox inbox;
    private final NotificationPreferences preferences;

    @Inject
    public ClusterNotificationRoutes(NotificationInbox inbox, NotificationPreferences preferences) {
        this.inbox = inbox;
        this.preferences = preferences;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/notifications", this::list, ClusterPermission.USER);
        routes.get(prefix + "/cluster/notifications/count", this::count, ClusterPermission.USER);
        routes.get(prefix + "/cluster/notifications/settings", this::settings, ClusterPermission.USER);
        routes.put(prefix + "/cluster/notifications/settings", this::updateSettings, ClusterPermission.USER);
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
        ctx.json(inbox.recent(requireClusterMember(ctx)).stream()
                .map(NotificationResponse::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/notifications/count",
            methods = HttpMethod.GET,
            summary = "Count the cluster notifications the caller has not read",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200"))
    private void count(Context ctx) {
        ctx.json(new CountResponse(inbox.countUnread(requireClusterMember(ctx))));
    }

    @OpenApi(
            path = "/api/v1/cluster/notifications/{id}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Mark one cluster notification read",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void acknowledge(Context ctx) {
        inbox.acknowledge(requireClusterMember(ctx), pathInt(ctx, "id"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/notifications/acknowledge-all",
            methods = HttpMethod.POST,
            summary = "Mark every cluster notification read",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void acknowledgeAll(Context ctx) {
        int count = inbox.acknowledgeAll(requireClusterMember(ctx));
        ctx.json(new MessageResponse(count + " notifications acknowledged"));
    }

    @OpenApi(
            path = "/api/v1/cluster/notifications/settings",
            methods = HttpMethod.GET,
            summary = "Whether the caller gets the association's notifications by mail",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterMail.class)))
    private void settings(Context ctx) {
        ctx.json(preferences.clusterMailOf(clusterMemberId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster/notifications/settings",
            methods = HttpMethod.PUT,
            summary = "Switch the caller's mail for the association's notifications on or off",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterMailRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterMail.class)))
    private void updateSettings(Context ctx) {
        var request = ctx.bodyAsClass(ClusterMailRequest.class);
        ctx.json(preferences.setClusterMail(clusterMemberId(ctx), request.emailEnabled()));
    }

    private static Recipient requireClusterMember(Context ctx) {
        return Recipient.clusterMember(clusterMemberId(ctx));
    }

    private static int clusterMemberId(Context ctx) {
        ClusterMember member = UserSession.from(ctx).clusterMember();
        if (member == null) throw Refusal.NO_CLUSTER_CHOSEN_FOR_NOTIFICATIONS.raise();
        return member.id();
    }

    /**
     * The caller's choice.
     *
     * @param emailEnabled whether they want the association's notifications by mail
     */
    public record ClusterMailRequest(boolean emailEnabled) {}
}
