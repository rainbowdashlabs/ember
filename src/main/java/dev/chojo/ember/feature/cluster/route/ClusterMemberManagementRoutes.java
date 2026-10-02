/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FileResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterMemberManagementService;
import dev.chojo.ember.feature.cluster.service.ClusterMemberSearchService;
import dev.chojo.ember.feature.cluster.service.ClusterMemberSearchService.MemberPageResponse;
import dev.chojo.ember.feature.cluster.service.ClusterMemberSearchService.Search;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.MemberDocumentResponse;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.station.entity.Station;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The people at the cluster's stations, seen and edited from the cluster.
 *
 * <p>Everything here is guarded by {@code CLUSTER_MEMBER_MANAGER} on the way in and by the service's two
 * refusals on the way through: nobody edits their own membership from here, and nobody edits a station's
 * owner from here.
 *
 * <p>A change is signed by the acting person's own member row on the cluster's station, the only member
 * row a person acting for a cluster has. Creating a member takes the station in the path, because a member
 * belongs to one and the cluster is standing in for it.
 */
@Singleton
public class ClusterMemberManagementRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(ClusterMemberManagementRoutes.class);

    private final ClusterService clusterService;
    private final ClusterMemberManagementService managementService;
    private final ClusterMemberSearchService memberSearch;

    @Inject
    public ClusterMemberManagementRoutes(
            ClusterService clusterService,
            ClusterMemberManagementService managementService,
            ClusterMemberSearchService memberSearch) {
        this.clusterService = clusterService;
        this.managementService = managementService;
        this.memberSearch = memberSearch;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/members/manage/search", this::search, ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.get(
                prefix + "/cluster/members/manage/stations",
                this::listStations,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.post(
                prefix + "/cluster/members/manage/stations/{stationUid}/members",
                this::createMember,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.put(
                prefix + "/cluster/members/manage/{memberId}/user-type",
                this::setUserType,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.put(
                prefix + "/cluster/members/manage/{memberId}/permissions",
                this::setPermissions,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.get(
                prefix + "/cluster/members/manage/{memberId}/profile",
                this::getProfile,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.put(
                prefix + "/cluster/members/manage/{memberId}/profile",
                this::updateProfile,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.get(
                prefix + "/cluster/members/manage/{memberId}/documents",
                this::listDocuments,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.post(
                prefix + "/cluster/members/manage/{memberId}/documents",
                this::uploadDocument,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.get(
                prefix + "/cluster/members/manage/documents/{documentId}/content",
                this::documentContent,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.delete(
                prefix + "/cluster/members/manage/{memberId}", this::archive, ClusterPermission.CLUSTER_MEMBER_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}/documents",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            methods = HttpMethod.GET,
            summary = "What is filed about one of the cluster's people",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberDocumentResponse[].class)))
    private void listDocuments(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(managementService.documentsOf(cluster.id(), pathInt(ctx, "memberId")));
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}/documents",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            methods = HttpMethod.POST,
            summary = "File a document about one of the cluster's people",
            tags = {"Cluster"},
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberDocumentResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void uploadDocument(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        var filed = managementService.fileDocument(
                cluster.id(),
                pathInt(ctx, "memberId"),
                ctx.formParam("title"),
                ctx.uploadedFile("file"),
                session.accountId());
        ctx.status(HttpStatus.CREATED).json(managementService.view(filed));
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/documents/{documentId}/content",
            pathParams = @OpenApiParam(name = "documentId", type = Integer.class, required = true),
            methods = HttpMethod.GET,
            summary = "The bytes of a document filed about one of the cluster's people",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200"))
    private void documentContent(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var document = managementService.requireDocumentOfCluster(cluster.id(), pathInt(ctx, "documentId"));
        FileResponse.send(ctx, document.mimeType(), document.fileName(), managementService.readDocument(document));
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}/profile",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            methods = HttpMethod.GET,
            summary = "What is asked of one person, and what they have answered",
            description = "The station's own questions and the cluster's, merged, each naming which it is.",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberProfileResponse.class)))
    private void getProfile(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var profile = managementService.getMemberProfile(cluster.id(), pathInt(ctx, "memberId"));
        ctx.json(new MemberProfileResponse(
                profile.member().id(),
                profile.name(),
                profile.fields().stream()
                        .map(field -> new MemberProfileFieldResponse(
                                field.id(),
                                field.name(),
                                field.fieldType().name(),
                                field.config(),
                                field.required(),
                                field.position(),
                                field.width(),
                                field.readonly(),
                                Optional.ofNullable(field.role())
                                        .map(Enum::name)
                                        .orElse(null),
                                field.origin().name(),
                                field.readonlyAtStation()))
                        .toList(),
                profile.values().stream()
                        .map(value -> new MemberProfileValueResponse(
                                value.fieldId(), value.value(), value.origin().name()))
                        .toList()));
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}/profile",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            methods = HttpMethod.PUT,
            summary = "Answer what is asked of one person",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberProfileRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateProfile(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(MemberProfileRequest.class);

        List<FieldValueEntry> entries = new ArrayList<>();
        for (var value : request.values() != null ? request.values() : List.<MemberProfileValueRequest>of()) {
            entries.add(new FieldValueEntry(value.fieldId(), value.value(), parseOrigin(value.origin())));
        }

        managementService.updateMemberProfile(cluster.id(), pathInt(ctx, "memberId"), entries, session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private static FieldOrigin parseOrigin(String raw) {
        if (raw == null || raw.isBlank()) return FieldOrigin.STATION;
        try {
            return FieldOrigin.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw ClusterRefusal.CLUSTER_FIELD_ORIGIN_UNKNOWN.raise(raw);
        }
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
        ctx.json(memberSearch.search(
                cluster.id(),
                new Search(
                        ctx.queryParam("q"),
                        stationId,
                        parseUserType(ctx.queryParam("userType")),
                        Boolean.parseBoolean(ctx.queryParam("includeFormer")),
                        intParam(ctx.queryParam("page"), 0),
                        intParam(ctx.queryParam("size"), 50))));
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
            path = "/api/v1/cluster/members/manage/stations/{stationUid}/members",
            pathParams = @OpenApiParam(name = "stationUid", type = String.class, required = true),
            methods = HttpMethod.POST,
            summary = "Take somebody on at one of the cluster's stations",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewMemberRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = NewMemberResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createMember(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(NewMemberRequest.class);
        if (isBlank(request.firstName()) || isBlank(request.lastName())) {
            throw ClusterRefusal.CLUSTER_NEW_MEMBER_NEEDS_A_NAME.raise();
        }
        UUID stationUid = parseUid(ctx.pathParam("stationUid"));
        StationUserType userType = request.userType() != null ? request.userType() : StationUserType.MEMBER;

        try {
            var made = managementService.createMember(
                    cluster.id(), stationUid, request.firstName(), request.lastName(), request.email(), userType);
            ctx.status(HttpStatus.CREATED).json(new NewMemberResponse(made.memberId(), made.accountId(), made.email()));
        } catch (StationMemberInviteService.ProvisionException e) {
            log.warn("A member could not be taken on at a station of a cluster", e);
            throw ClusterRefusal.CLUSTER_MEMBER_ALREADY_TAKEN_ON.raise();
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ClusterRefusal.CLUSTER_NEW_MEMBER_STATION_NOT_AN_IDENTITY.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}/user-type",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
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
        if (userType == null) throw ClusterRefusal.STATION_USER_TYPE_UNKNOWN_FROM_CLUSTER.raise(request.userType());

        managementService.setUserType(cluster.id(), pathInt(ctx, "memberId"), userType, session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}/permissions",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
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
                throw ClusterRefusal.STATION_PERMISSION_UNKNOWN_FROM_CLUSTER.raise(name);
            }
        }
        managementService.setPermissions(cluster.id(), pathInt(ctx, "memberId"), permissions, session.accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
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
        if (clusterId == null) throw ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_MEMBER_MANAGEMENT.raise();
        return clusterService
                .findById(clusterId)
                .orElseThrow(ClusterRefusal.CLUSTER_NOT_HERE_FOR_MEMBER_MANAGEMENT::raise);
    }

    /**
     * Turns the station identity on the wire into the internal id, checked against this cluster so the
     * filter cannot be used to peer into somebody else's station.
     */
    private @Nullable Integer resolveStationFilter(Cluster cluster, @Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        UUID uid;
        try {
            uid = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ClusterRefusal.CLUSTER_MEMBER_FILTER_STATION_NOT_AN_IDENTITY.raise(raw);
        }
        return managementService.reachableStations(cluster.id()).stream()
                .filter(station -> station.uid().equals(uid))
                .map(Station::id)
                .findFirst()
                .orElseThrow(ClusterRefusal.STATION_NOT_IN_THIS_CLUSTER::raise);
    }

    private static int intParam(@Nullable String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static @Nullable StationUserType parseUserType(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return StationUserType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public record StationUserTypeRequest(String userType) {}

    public record StationPermissionsRequest(List<String> permissions) {}

    public record ManagedStationResponse(UUID uid, String name) {}

    /**
     * @param origin           which table the question lives in, so the answer goes back to the right one
     * @param readonlyAtStation whether the station may read the answer without writing it, which only a
     *                          cluster's own question can be
     */
    /**
     * @param required whether this audience must answer
     * @param width    how much of a row the question takes, null meaning the whole row
     * @param readonly whether this audience may read the answer but not write it
     * @param role     the kind of member this form was built for, null where a group is asked
     */
    public record MemberProfileFieldResponse(
            int id,
            String name,
            String fieldType,
            @Nullable ProfileFieldConfig config,
            boolean required,
            int position,
            @Nullable String width,
            boolean readonly,
            @Nullable String role,
            String origin,
            boolean readonlyAtStation) {}

    public record MemberProfileValueResponse(
            int fieldId, @Nullable String value, String origin) {}

    public record MemberProfileResponse(
            int memberId,
            String name,
            List<MemberProfileFieldResponse> fields,
            List<MemberProfileValueResponse> values) {}

    public record MemberProfileValueRequest(int fieldId, String value, String origin) {}

    public record MemberProfileRequest(List<MemberProfileValueRequest> values) {}

    /**
     * Somebody being taken on at a station of the cluster.
     *
     * @param email leave it out for somebody who is not meant to sign in
     */
    public record NewMemberRequest(String firstName, String lastName, String email, StationUserType userType) {}

    public record NewMemberResponse(
            int memberId, int accountId, @Nullable String email) {}
}
