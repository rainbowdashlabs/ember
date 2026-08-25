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
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.cluster.service.ClusterProfileFieldService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
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

import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The questions a cluster asks of the people at its stations.
 *
 * <p>Reading the definitions is open to whoever manages the fields or the members, because somebody filling
 * an answer in has to know what is being asked. Changing them is the field manager's alone.
 */
@Singleton
public class ClusterFieldRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterProfileFieldService fieldService;

    @Inject
    public ClusterFieldRoutes(ClusterService clusterService, ClusterProfileFieldService fieldService) {
        this.clusterService = clusterService;
        this.fieldService = fieldService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/fields", this::list, ClusterPermission.CLUSTER_MEMBER_READ);
        routes.post(prefix + "/cluster/fields", this::create, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.put(prefix + "/cluster/fields/order", this::reorder, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.put(prefix + "/cluster/fields/{fieldId}", this::update, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.delete(prefix + "/cluster/fields/{fieldId}", this::delete, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.get(
                prefix + "/cluster/fields/member/{memberId}",
                this::getValues,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.put(
                prefix + "/cluster/fields/member/{memberId}",
                this::setValues,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/cluster/fields",
            methods = HttpMethod.GET,
            summary = "The questions this cluster asks",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterFieldResponse[].class)))
    private void list(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(fieldService.findByCluster(cluster.id()).stream()
                .map(ClusterFieldRoutes::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/fields",
            methods = HttpMethod.POST,
            summary = "Add a question",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterFieldRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterFieldResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterFieldRequest.class);
        ClusterProfileField field = fieldService.create(
                cluster.id(),
                request.name(),
                parseType(request.fieldType()),
                request.config() != null ? request.config() : ProfileFieldConfig.empty(),
                request.position(),
                parseScope(request.scope()),
                request.stationReadonly(),
                request.keepOnArchive(),
                request.stationGroupId());
        ctx.status(HttpStatus.CREATED).json(toResponse(field));
    }

    @OpenApi(
            path = "/api/v1/cluster/fields/{fieldId}",
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Change a question",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterFieldRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void update(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterFieldRequest.class);
        fieldService.update(
                cluster.id(),
                pathInt(ctx, "fieldId"),
                request.name(),
                parseType(request.fieldType()),
                request.config() != null ? request.config() : ProfileFieldConfig.empty(),
                request.position(),
                parseScope(request.scope()),
                request.stationReadonly(),
                request.keepOnArchive(),
                request.stationGroupId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/fields/{fieldId}",
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            methods = HttpMethod.DELETE,
            summary = "Remove a question",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        Cluster cluster = requireActive(ctx);
        fieldService.delete(cluster.id(), pathInt(ctx, "fieldId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/fields/order",
            methods = HttpMethod.PUT,
            summary = "Put this cluster's questions in a given order",
            description = "Registered before the path that takes a question id, or 'order' is read as one.",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterFieldOrderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void reorder(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var req = ctx.bodyAsClass(ClusterFieldOrderRequest.class);
        fieldService.reorder(cluster.id(), req.fieldIds() != null ? req.fieldIds() : List.of());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /** The questions in the order they should stand. */
    public record ClusterFieldOrderRequest(List<Integer> fieldIds) {}

    @OpenApi(
            path = "/api/v1/cluster/fields/member/{memberId}",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            methods = HttpMethod.GET,
            summary = "What one member answered",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FieldValuesRequest.class)))
    private void getValues(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(new FieldValuesRequest(fieldService.findValues(cluster.id(), pathInt(ctx, "memberId"))));
    }

    @OpenApi(
            path = "/api/v1/cluster/fields/member/{memberId}",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Fill in answers for one member",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldValuesRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setValues(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(FieldValuesRequest.class);
        // A cluster member is no station member, so the change is recorded against whoever the session is at
        // its station, or against the member themselves when the cluster manager has no station membership
        int changedBy = session.member() != null ? session.member().id() : pathInt(ctx, "memberId");
        fieldService.setValues(
                cluster.id(),
                pathInt(ctx, "memberId"),
                request.values() != null ? request.values() : Map.of(),
                changedBy);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    private static ProfileFieldType parseType(String raw) {
        if (raw == null || raw.isBlank()) return ProfileFieldType.TEXT;
        try {
            return ProfileFieldType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("No such field type: " + raw);
        }
    }

    private static ProfileFieldScope parseScope(String raw) {
        if (raw == null || raw.isBlank()) return ProfileFieldScope.MEMBER;
        try {
            return ProfileFieldScope.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("No such scope: " + raw);
        }
    }

    private static ClusterFieldResponse toResponse(ClusterProfileField field) {
        return new ClusterFieldResponse(
                field.id(),
                field.name(),
                field.fieldType().name(),
                field.config(),
                field.position(),
                field.scope().name(),
                field.stationReadonly(),
                field.keepOnArchive(),
                field.stationGroupId());
    }

    /**
     * @param stationReadonly whether the people at the station may read the answer but not write it
     */
    public record ClusterFieldRequest(
            String name,
            String fieldType,
            ProfileFieldConfig config,
            int position,
            String scope,
            boolean stationReadonly,
            boolean keepOnArchive,
            Integer stationGroupId) {}

    public record ClusterFieldResponse(
            int id,
            String name,
            String fieldType,
            ProfileFieldConfig config,
            int position,
            String scope,
            boolean stationReadonly,
            boolean keepOnArchive,
            Integer stationGroupId) {}

    /**
     * @param values field id to answer, in the same JSON shape a station field's answer has
     */
    public record FieldValuesRequest(Map<Integer, String> values) {}
}
