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
import dev.chojo.ember.feature.cluster.entity.ClusterProfileField;
import dev.chojo.ember.feature.cluster.entity.ClusterProfileFieldAssignment;
import dev.chojo.ember.feature.cluster.service.ClusterProfileFieldService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.FieldTypes;
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
import org.jspecify.annotations.Nullable;

import java.util.List;

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
        routes.get(
                prefix + "/cluster/fields",
                this::list,
                ClusterPermission.CLUSTER_MEMBER_READ,
                ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.post(prefix + "/cluster/fields", this::create, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.put(prefix + "/cluster/fields/order", this::reorder, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.get(
                prefix + "/cluster/fields/assignments",
                this::listAssignments,
                ClusterPermission.CLUSTER_MEMBER_READ,
                ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.put(prefix + "/cluster/fields/{fieldId}", this::update, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.delete(prefix + "/cluster/fields/{fieldId}", this::delete, ClusterPermission.CLUSTER_FIELD_EDIT);

        routes.put(
                prefix + "/cluster/fields/{fieldId}/assignments", this::assign, ClusterPermission.CLUSTER_FIELD_EDIT);
        routes.delete(
                prefix + "/cluster/fields/{fieldId}/assignments", this::unassign, ClusterPermission.CLUSTER_FIELD_EDIT);
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
                request.required() != null && request.required(),
                request.readonly() != null && request.readonly(),
                request.width(),
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
                request.required() != null && request.required(),
                request.readonly() != null && request.readonly(),
                request.width(),
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
        var role = req.role();
        if (role == null) throw ClusterRefusal.CLUSTER_FIELD_ORDER_NEEDS_AN_AUDIENCE.raise();
        fieldService.reorder(cluster.id(), role, req.fieldIds() != null ? req.fieldIds() : List.of());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The questions of one audience in the order they should stand, named the way a station's order names
     * its audience.
     */
    public record ClusterFieldOrderRequest(@Nullable ProfileFieldScope role, List<Integer> fieldIds) {}

    @OpenApi(
            path = "/api/v1/cluster/fields/assignments",
            methods = HttpMethod.GET,
            summary = "Who each of this cluster's questions is asked of",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ClusterProfileFieldAssignment[].class)))
    private void listAssignments(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(fieldService.findAssignmentsByCluster(cluster.id()));
    }

    @OpenApi(
            path = "/api/v1/cluster/fields/{fieldId}/assignments",
            methods = HttpMethod.PUT,
            summary = "Ask a kind of member this question",
            description = "Adds the assignment or updates how the question is put to that audience.",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterAssignmentRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void assign(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterAssignmentRequest.class);
        fieldService.assignToRole(
                cluster.id(),
                pathInt(ctx, "fieldId"),
                parseScope(request.role()),
                request.position(),
                request.widthOverride(),
                request.readonlyOverride(),
                request.requiredOverride());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/fields/{fieldId}/assignments",
            methods = HttpMethod.DELETE,
            summary = "Stop asking a kind of member this question",
            description = "The definition stays; only this audience stops being asked.",
            tags = {"Cluster"},
            pathParams = @OpenApiParam(name = "fieldId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterAssignmentRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void unassign(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterAssignmentRequest.class);
        fieldService.unassignRole(cluster.id(), pathInt(ctx, "fieldId"), parseScope(request.role()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * How a cluster question is put to one kind of member.
     *
     * <p>No group here, unlike a station's: a member group belongs to one station, so a cluster has no
     * way to name one. An override left {@code null} takes the question's own setting.
     */
    public record ClusterAssignmentRequest(
            String role,
            int position,
            @Nullable String widthOverride,
            @Nullable Boolean readonlyOverride,
            @Nullable Boolean requiredOverride) {}

    private Cluster requireActive(Context ctx) {
        var association = UserSession.from(ctx).association(ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_FIELDS);
        return clusterService
                .findById(association.clusterId())
                .orElseThrow(ClusterRefusal.CLUSTER_NOT_HERE_FOR_FIELDS::raise);
    }

    /**
     * The type a request names, where it is one a profile question can have at all. Whether an
     * association may ask it is the service's to say, since it names the one it may not.
     */
    private static FieldType parseType(String raw) {
        if (raw == null || raw.isBlank()) return FieldType.TEXT;
        return FieldTypes.PROFILE.stream()
                .filter(type -> type.name().equals(raw))
                .findFirst()
                .orElseThrow(() -> ClusterRefusal.CLUSTER_FIELD_TYPE_UNKNOWN.raise(raw));
    }

    /**
     * The audience a request names. Naming none is refused rather than read as the members: an assignment
     * that silently went to somebody else is worse than one that did not happen.
     */
    private static ProfileFieldScope parseScope(@Nullable String raw) {
        if (raw == null || raw.isBlank()) throw ClusterRefusal.CLUSTER_FIELD_AUDIENCE_MISSING.raise();
        try {
            return ProfileFieldScope.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ClusterRefusal.CLUSTER_FIELD_AUDIENCE_UNKNOWN.raise(raw);
        }
    }

    private static ClusterFieldResponse toResponse(ClusterProfileField field) {
        return new ClusterFieldResponse(
                field.id(),
                field.name(),
                field.fieldType(),
                field.config(),
                field.required(),
                field.readonly(),
                field.width(),
                field.stationReadonly(),
                field.keepOnArchive(),
                field.stationGroupId());
    }

    /**
     * A question a cluster asks, without reference to who is asked it.
     *
     * @param stationReadonly whether the people at the station may read the answer but not write it
     * @param width           how much of a row it takes unless an audience overrides that, or
     *                        {@code null} for the whole row
     * @param stationGroupId  the association's group of stations the question is pointed at, or
     *                        {@code null} for none
     */
    public record ClusterFieldRequest(
            String name,
            String fieldType,
            ProfileFieldConfig config,
            Boolean required,
            Boolean readonly,
            @Nullable String width,
            boolean stationReadonly,
            boolean keepOnArchive,
            @Nullable Integer stationGroupId) {}

    public record ClusterFieldResponse(
            int id,
            String name,
            FieldType fieldType,
            ProfileFieldConfig config,
            boolean required,
            boolean readonly,
            @Nullable String width,
            boolean stationReadonly,
            boolean keepOnArchive,
            @Nullable Integer stationGroupId) {}
}
