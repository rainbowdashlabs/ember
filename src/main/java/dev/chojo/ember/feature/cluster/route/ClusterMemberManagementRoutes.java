/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.service.ClusterMemberManagementService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
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

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024;

    private final ClusterService clusterService;
    private final ClusterMemberManagementService managementService;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;

    @Inject
    public ClusterMemberManagementRoutes(
            ClusterService clusterService,
            ClusterMemberManagementService managementService,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository) {
        this.clusterService = clusterService;
        this.managementService = managementService;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/members/manage/search", this::search, ClusterPermission.CLUSTER_MEMBER_MANAGER);
        routes.get(
                prefix + "/cluster/members/manage/stations",
                this::listStations,
                ClusterPermission.CLUSTER_MEMBER_MANAGER);
        // The station is in the path because a member belongs to one and the cluster is standing in for it
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
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberDocumentSummary[].class)))
    private void listDocuments(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(managementService.documentsOf(cluster.id(), pathInt(ctx, "memberId")).stream()
                .map(document -> new MemberDocumentSummary(
                        document.id(),
                        document.title(),
                        document.fileName(),
                        document.mimeType(),
                        document.sizeBytes(),
                        document.createdAt()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/members/manage/{memberId}/documents",
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            methods = HttpMethod.POST,
            summary = "File a document about one of the cluster's people",
            tags = {"Cluster"},
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberDocumentSummary.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void uploadDocument(Context ctx) {
        Cluster cluster = requireActive(ctx);
        UserSession session = UserSession.from(ctx);
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("file is required");
        if (file.size() > MAX_UPLOAD_SIZE) throw new BadRequestResponse("File too large (max 50MB)");

        String title = ctx.formParam("title");
        if (isBlank(title)) title = file.filename();

        byte[] data;
        try (var in = file.content()) {
            data = in.readAllBytes();
        } catch (IOException e) {
            throw new BadRequestResponse("That file could not be read");
        }
        var filed = managementService.fileDocument(
                cluster.id(),
                pathInt(ctx, "memberId"),
                title.strip(),
                file.filename(),
                file.contentType(),
                data,
                // The cluster member's own row on the cluster's station, which is the only one they have
                session.member() != null ? session.member().id() : null);
        ctx.status(HttpStatus.CREATED)
                .json(new MemberDocumentSummary(
                        filed.id(),
                        filed.title(),
                        filed.fileName(),
                        filed.mimeType(),
                        filed.sizeBytes(),
                        filed.createdAt()));
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
        byte[] data = managementService.readDocument(document);
        var disposition = SafeInlineMime.isInlineSafe(document.mimeType())
                ? SafeContentDisposition.Disposition.INLINE
                : SafeContentDisposition.Disposition.ATTACHMENT;
        ctx.contentType(SafeInlineMime.safeContentType(document.mimeType()));
        ctx.header("Content-Disposition", SafeContentDisposition.build(disposition, document.fileName()));
        ctx.result(data);
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
                profile.member().displayName(),
                profile.fields().stream()
                        .map(field -> new MemberProfileFieldResponse(
                                field.id(),
                                field.name(),
                                field.fieldType().name(),
                                field.config(),
                                field.position(),
                                field.scope().name(),
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

        // The change is signed by the cluster member's own row on the cluster's station, which is the only
        // member row a person acting for a cluster has.
        managementService.updateMemberProfile(
                cluster.id(),
                pathInt(ctx, "memberId"),
                entries,
                session.accountId(),
                session.member() != null ? session.member().id() : 0);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private static FieldOrigin parseOrigin(String raw) {
        if (raw == null || raw.isBlank()) return FieldOrigin.STATION;
        try {
            return FieldOrigin.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("No such field origin: " + raw);
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
        var page = managementService.search(
                cluster.id(),
                ctx.queryParam("q"),
                stationId,
                parseUserType(ctx.queryParam("userType")),
                Boolean.parseBoolean(ctx.queryParam("includeFormer")),
                intParam(ctx.queryParam("page"), 0),
                intParam(ctx.queryParam("size"), 50));

        var ids = page.members().stream()
                .map(StationMemberRepository.ClusterMemberRow::id)
                .toList();
        var colors = memberGroupRepository.findNameColors(ids);
        var tags = userTagRepository.findDisplayTags(ids);

        ctx.json(new MemberPageResponse(
                page.members().stream()
                        .map(row -> toResponse(row, colors.get(row.id()), tags.get(row.id())))
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
            throw new BadRequestResponse("firstName and lastName are required");
        }
        UUID stationUid = parseUid(ctx.pathParam("stationUid"));
        StationUserType userType = request.userType() != null ? request.userType() : StationUserType.MEMBER;

        try {
            var made = managementService.createMember(
                    cluster.id(), stationUid, request.firstName(), request.lastName(), request.email(), userType);
            ctx.status(HttpStatus.CREATED).json(new NewMemberResponse(made.memberId(), made.accountId(), made.email()));
        } catch (StationMemberInviteService.ProvisionException e) {
            throw new ConflictResponse(e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("That is not a station identity");
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
        if (userType == null) throw new BadRequestResponse("No such user type: " + request.userType());

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
                throw new BadRequestResponse("No such permission: " + name);
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

    /**
     * One row of the search, with the identity the row is drawn from.
     *
     * <p>The name travelled on the row all along and nothing read it: every list in Ember draws a person
     * through their identity, which is what carries the avatar, the colour and the display tag as well.
     * Assembling half of one in the browser would get the name back and none of the rest, so the server
     * sends the whole thing.
     */
    private static ManagedMemberResponse toResponse(
            StationMemberRepository.ClusterMemberRow row, String nameColor, UserTag tag) {
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
                row.stationOwner(),
                new MemberIdentity(
                        row.stationUid(),
                        row.uid(),
                        row.name(),
                        row.stationName(),
                        nameColor,
                        tag == null ? null : new MemberIdentity.DisplayTag(tag.name(), tag.color())),
                row.stationNames());
    }

    public record StationUserTypeRequest(String userType) {}

    public record StationPermissionsRequest(List<String> permissions) {}

    public record ManagedStationResponse(UUID uid, String name) {}

    /**
     * @param stationOwner whether they are their station's owner, which the cluster may not edit
     * @param stationNames every station of this association the person belongs to, so a row can say so
     *                     rather than naming only the membership it came from
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
            boolean stationOwner,
            MemberIdentity identity,
            String stationNames) {}

    /**
     * @param total how many the search found altogether, not how many are on this page
     */
    public record MemberPageResponse(List<ManagedMemberResponse> members, int total, int page, int size) {}

    /**
     * @param origin           which table the question lives in, so the answer goes back to the right one
     * @param readonlyAtStation whether the station may read the answer without writing it, which only a
     *                          cluster's own question can be
     */
    public record MemberProfileFieldResponse(
            int id,
            String name,
            String fieldType,
            ProfileFieldConfig config,
            int position,
            String scope,
            String origin,
            boolean readonlyAtStation) {}

    public record MemberProfileValueResponse(int fieldId, String value, String origin) {}

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

    public record NewMemberResponse(int memberId, int accountId, String email) {}

    /** One document filed about somebody, as the association's screen lists it. */
    public record MemberDocumentSummary(
            int id, String title, String fileName, String mimeType, long sizeBytes, Instant createdAt) {}
}
