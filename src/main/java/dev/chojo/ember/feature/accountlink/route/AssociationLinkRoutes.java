/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.accountlink.entity.AssociationLinkState;
import dev.chojo.ember.feature.accountlink.service.AssociationLinkService;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The association's side of the requests it sent to existing accounts to take a role there: where each
 * stands, and sending one again.
 */
@Singleton
public class AssociationLinkRoutes implements Routes {
    private final AssociationLinkService links;
    private final ClusterService clusterService;

    @Inject
    public AssociationLinkRoutes(AssociationLinkService links, ClusterService clusterService) {
        this.links = links;
        this.clusterService = clusterService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/link-requests", this::list, ClusterPermission.CLUSTER_MEMBER_READ);
        routes.post(
                prefix + "/cluster/link-requests/{uid}/send-again",
                this::sendAgain,
                ClusterPermission.CLUSTER_ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/cluster/link-requests",
            methods = HttpMethod.GET,
            summary = "The association's requests that wait, were declined or ran out",
            tags = {"Account links"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AssociationLinkState[].class)))
    private void list(Context ctx) {
        ctx.json(links.statesAt(requireActive(ctx).id()));
    }

    @OpenApi(
            path = "/api/v1/cluster/link-requests/{uid}/send-again",
            methods = HttpMethod.POST,
            summary = "Send one of the association's requests again",
            description =
                    "Sends a waiting request again with a fresh deadline, or asks anew where it ran out. Refused where the person declined, and within a day of the last time it was sent.",
            tags = {"Account links"},
            pathParams = @OpenApiParam(name = "uid", type = UUID.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AssociationLinkState.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "429", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void sendAgain(Context ctx) {
        ctx.json(links.sendAgain(requireActive(ctx).id(), pathUuid(ctx, "uid")));
    }

    private Cluster requireActive(Context ctx) {
        Integer clusterId = UserSession.from(ctx).clusterId();
        if (clusterId == null) throw ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_LINK_REQUESTS.raise();
        return clusterService.findById(clusterId).orElseThrow(ClusterRefusal.CLUSTER_NOT_HERE_FOR_LINK_REQUESTS::raise);
    }
}
