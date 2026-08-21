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
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterMemberManagementService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The people at the cluster's stations, seen and edited from the cluster.
 *
 * <p>Everything here is guarded by {@code CLUSTER_MEMBER_MANAGER} on the way in and by the service's two
 * refusals on the way through: nobody edits their own membership from here, and nobody edits a station's
 * owner from here.
 */
@Singleton
public class ClusterMemberManagementRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterMemberManagementService managementService;

    @Inject
    public ClusterMemberManagementRoutes(
            ClusterService clusterService, ClusterMemberManagementService managementService) {
        this.clusterService = clusterService;
        this.managementService = managementService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/members/manage/search", this::search, ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.get(
                prefix + "/cluster/members/manage/stations",
                this::listStations,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.put(
                prefix + "/cluster/members/manage/{memberId}/user-type",
                this::setUserType,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.put(
                prefix + "/cluster/members/manage/{memberId}/permissions",
                this::setPermissions,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.delete(
                prefix + "/cluster/members/manage/{memberId}", this::archive, ClusterPermission.CLUSTER_MEMBER_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/search",
            methods = HttpMethod.GET,
            summary = "Search the people at every station of this cluster",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberPageResponse.class)))
    private void search(Context ctx) {
        Cluster cluster = requireActive(ctx);
        Integer stationId = resolveStationFilter(cluster, ctx.queryParam("stationUid"));
        var page = managementService.search(
                cluster.id(),
                ctx.queryParam("q"),
                stationId,
                parseUserType(ctx.queryParam("userType")),
                Boolean.parseBoolean(ctx.queryParam("includeFormer")),
                intParam(ctx.queryParam("page"), 0),
                intParam(ctx.queryParam("size"), 50));

        ctx.json(new MemberPageResponse(
                page.members().stream()
                        .map(ClusterMemberManagementRoutes::toResponse)
                        .toList(),
                page.total(),
                page.page(),
                page.size()));
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/stations",
            methods = HttpMethod.GET,
            summary = "The stations a cluster member manager may act in",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ManagedStationResponse[].class)))
    private void listStations(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(managementService.reachableStations(cluster.id()).stream()
                .map(station -> new ManagedStationResponse(station.uid(), station.name()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}/user-type",
            methods = HttpMethod.PUT,
            summary = "Change what somebody is at their station",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationUserTypeRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setUserType(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(StationUserTypeRequest.class);
        StationUserType userType = parseUserType(request.userType());
        if (userType == null) throw new BadRequestResponse("No such user type: " + request.userType());

        managementService.setUserType(cluster.id(), pathInt(ctx, "memberId"), userType, session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}/permissions",
            methods = HttpMethod.PUT,
            summary = "Set what somebody may do at their station",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StationPermissionsRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setPermissions(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(StationPermissionsRequest.class);

        Set<StationPermission> permissions = EnumSet.noneOf(StationPermission.class);
        for (String name : request.permissions() != null ? request.permissions() : List.<String>of()) {
            try {
                permissions.add(StationPermission.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new BadRequestResponse("No such permission: " + name);
            }
        }
        managementService.setPermissions(cluster.id(), pathInt(ctx, "memberId"), permissions, session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}",
            methods = HttpMethod.DELETE,
            summary = "Mark somebody as having left their station",
            tags = {"Cluster"},
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void archive(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        managementService.archive(cluster.id(), pathInt(ctx, "memberId"), session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    /**
     * Turns the station identity on the wire into the internal id, checked against this cluster so the
     * filter cannot be used to peer into somebody else's station.
     */
    private Integer resolveStationFilter(Cluster cluster, String raw) {
        if (raw == null || raw.isBlank()) return null;
        UUID uid;
        try {
            uid = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Not a station identity: " + raw);
        }
        return managementService.reachableStations(cluster.id()).stream()
                .filter(station -> station.uid().equals(uid))
                .map(Station::id)
                .findFirst()
                .orElseThrow(() -> new NotFoundResponse("No such station in this cluster"));
    }

    private static int intParam(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static StationUserType parseUserType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return StationUserType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ManagedMemberResponse toResponse(StationMemberRepository.ClusterMemberRow row) {
        return new ManagedMemberResponse(
                row.id(),
                row.uid(),
                row.stationUid(),
                row.stationName(),
                row.name(),
                row.email(),
                row.userType().name(),
                row.joinDate(),
                row.former(),
                row.stationOwner());
    }

    public record StationUserTypeRequest(String userType) {}

    public record StationPermissionsRequest(List<String> permissions) {}

    public record ManagedStationResponse(UUID uid, String name) {}

    /**
     * @param stationOwner whether they are their station's owner, which the cluster may not edit
     */
    public record ManagedMemberResponse(
            int id,
            UUID uid,
            UUID stationUid,
            String stationName,
            String name,
            String email,
            String userType,
            LocalDate joinDate,
            boolean former,
            boolean stationOwner) {}

    /**
     * @param total how many the search found altogether, not how many are on this page
     */
    public record MemberPageResponse(List<ManagedMemberResponse> members, int total, int page, int size) {}
}
