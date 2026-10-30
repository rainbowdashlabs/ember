/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterProfileFieldService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.members.entity.FieldSet;
import dev.chojo.ember.feature.members.route.ProfileFieldSetRoutes.FieldSetOrderRequest;
import dev.chojo.ember.feature.members.route.ProfileFieldSetRoutes.FieldSetRequest;
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

import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The bundles an association keeps its profile questions in, the same way a station keeps its own.
 */
@Singleton
public class ClusterFieldSetRoutes implements Routes {
    private final ClusterProfileFieldService fieldService;
    private final ClusterService clusterService;

    @Inject
    public ClusterFieldSetRoutes(ClusterProfileFieldService fieldService, ClusterService clusterService) {
        this.fieldService = fieldService;
        this.clusterService = clusterService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/cluster/field-sets",
                this::list,
                ClusterPermission.CLUSTER_MEMBER_READ,
                ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.post(prefix + "/cluster/field-sets", this::create, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.put(prefix + "/cluster/field-sets/{setId}", this::update, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.delete(prefix + "/cluster/field-sets/{setId}", this::dissolve, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.put(prefix + "/cluster/field-sets/{setId}/order", this::order, ClusterPermission.CLUSTER_FIELD_EDIT);
    }

    @OpenApi(
            path = "/api/v1/cluster/field-sets",
            methods = HttpMethod.GET,
            summary = "The groups the association keeps its profile fields in",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FieldSet[].class)))
    private void list(Context ctx) {
        ctx.json(fieldService.findSets(requireActive(ctx).id()));
    }

    @OpenApi(
            path = "/api/v1/cluster/field-sets",
            methods = HttpMethod.POST,
            summary = "Write down a new, empty group of profile fields",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldSetRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = FieldSet.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        var request = ctx.bodyAsClass(FieldSetRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(fieldService.createSet(requireActive(ctx).id(), request.name(), request.nameAsSection()));
    }

    @OpenApi(
            path = "/api/v1/cluster/field-sets/{setId}",
            methods = HttpMethod.PUT,
            summary = "Rename a group of profile fields, or change whether its name is a heading",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "setId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldSetRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void update(Context ctx) {
        var request = ctx.bodyAsClass(FieldSetRequest.class);
        fieldService.updateSet(requireActive(ctx).id(), pathInt(ctx, "setId"), request.name(), request.nameAsSection());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/field-sets/{setId}",
            methods = HttpMethod.DELETE,
            summary = "Dissolve a group of profile fields, keeping its fields",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "setId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void dissolve(Context ctx) {
        fieldService.dissolveSet(requireActive(ctx).id(), pathInt(ctx, "setId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/field-sets/{setId}/order",
            methods = HttpMethod.PUT,
            summary = "Put the fields of a group in order",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "setId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldSetOrderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void order(Context ctx) {
        var request = ctx.bodyAsClass(FieldSetOrderRequest.class);
        fieldService.orderSet(
                requireActive(ctx).id(),
                pathInt(ctx, "setId"),
                Objects.requireNonNullElse(request.fieldIds(), List.of()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Cluster requireActive(Context ctx) {
        var association = UserSession.from(ctx).association(ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_FIELDS);
        return clusterService
                .findById(association.clusterId())
                .orElseThrow(ClusterRefusal.CLUSTER_NOT_HERE_FOR_FIELDS::raise);
    }
}
