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
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.service.ClusterMemberService;
import dev.chojo.ember.feature.cluster.service.ClusterService;
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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The cluster's own people: who acts for it, what they are, and the groups they sit in.
 *
 * <p>Not to be confused with {@link ClusterMemberManagementRoutes}, which is about the people at the
 * cluster's stations. These are the accounts that speak for the cluster itself.
 */
@Singleton
public class ClusterMemberRoutes implements Routes {
    private final ClusterService clusterService;
    private final ClusterMemberService memberService;
    private final AccountRepository accountRepository;

    @Inject
    public ClusterMemberRoutes(
            ClusterService clusterService, ClusterMemberService memberService, AccountRepository accountRepository) {
        this.clusterService = clusterService;
        this.memberService = memberService;
        this.accountRepository = accountRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/cluster/members", this::list, ClusterPermission.CLUSTER_MEMBER_READ);
        routes.post(prefix + "/cluster/members", this::add, ClusterPermission.CLUSTER_ADMINISTRATOR);
        routes.get(prefix + "/cluster/members/{memberId}", this::get, ClusterPermission.CLUSTER_MEMBER_READ);
        routes.delete(prefix + "/cluster/members/{memberId}", this::remove, ClusterPermission.CLUSTER_ADMINISTRATOR);
        routes.put(
                prefix + "/cluster/members/{memberId}/user-type",
                this::setUserType,
                ClusterPermission.CLUSTER_ADMINISTRATOR);
        routes.put(
                prefix + "/cluster/members/{memberId}/permissions",
                this::setPermissions,
                ClusterPermission.CLUSTER_ADMINISTRATOR);

        routes.get(prefix + "/cluster/member-groups", this::listGroups, ClusterPermission.CLUSTER_MEMBER_READ);
        routes.post(prefix + "/cluster/member-groups", this::createGroup, ClusterPermission.CLUSTER_ADMINISTRATOR);
        routes.get(prefix + "/cluster/member-groups/{groupId}", this::getGroup, ClusterPermission.CLUSTER_MEMBER_READ);
        routes.put(
                prefix + "/cluster/member-groups/{groupId}",
                this::updateGroup,
                ClusterPermission.CLUSTER_ADMINISTRATOR);
        routes.delete(
                prefix + "/cluster/member-groups/{groupId}",
                this::deleteGroup,
                ClusterPermission.CLUSTER_ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/cluster/members",
            methods = HttpMethod.GET,
            summary = "The accounts acting for this cluster",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterMemberResponse[].class)))
    private void list(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(memberService.findMembers(cluster.id()).stream()
                .map(this::toResponse)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/members",
            methods = HttpMethod.POST,
            summary = "Take an account on as a cluster member",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NewClusterMemberRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterMemberResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void add(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(NewClusterMemberRequest.class);
        var account = accountRepository
                .findByEmail(request.email())
                .orElseThrow(() -> new BadRequestResponse("No account with that email address"));
        ClusterMember member = clusterService.addMember(cluster.id(), account.id(), parseUserType(request.userType()));
        ctx.status(HttpStatus.CREATED).json(toResponse(member));
    }

    @OpenApi(
            path = "/api/v1/cluster/members/{memberId}",
            methods = HttpMethod.GET,
            summary = "What one cluster member holds, and where each part comes from",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ClusterMemberDetailResponse.class)))
    private void get(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var detail = memberService.findMemberDetail(cluster.id(), pathInt(ctx, "memberId"));
        ctx.json(new ClusterMemberDetailResponse(
                toResponse(detail.member()),
                names(detail.direct()),
                detail.groups().stream()
                        .map(group -> new ClusterGroupResponse(group.id(), group.name()))
                        .toList(),
                names(detail.resolved())));
    }

    @OpenApi(
            path = "/api/v1/cluster/members/{memberId}",
            methods = HttpMethod.DELETE,
            summary = "Release an account from the cluster",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void remove(Context ctx) {
        Cluster cluster = requireActive(ctx);
        int memberId = pathInt(ctx, "memberId");
        // Read it through the service first, so one cluster cannot remove another's people
        memberService.findMemberDetail(cluster.id(), memberId);
        clusterService.removeMember(memberId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/members/{memberId}/user-type",
            methods = HttpMethod.PUT,
            summary = "Change what a cluster member is",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterUserTypeRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setUserType(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterUserTypeRequest.class);
        memberService.setUserType(cluster.id(), pathInt(ctx, "memberId"), parseUserType(request.userType()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/members/{memberId}/permissions",
            methods = HttpMethod.PUT,
            summary = "Set what a cluster member holds in their own right",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterPermissionsRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setPermissions(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterPermissionsRequest.class);
        memberService.setPermissions(cluster.id(), pathInt(ctx, "memberId"), parsePermissions(request.permissions()));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/member-groups",
            methods = HttpMethod.GET,
            summary = "The cluster's member groups",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = ClusterGroupResponse[].class)))
    private void listGroups(Context ctx) {
        Cluster cluster = requireActive(ctx);
        ctx.json(memberService.findGroups(cluster.id()).stream()
                .map(group -> new ClusterGroupResponse(group.id(), group.name()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/cluster/member-groups",
            methods = HttpMethod.POST,
            summary = "Create a member group",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterGroupRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = ClusterGroupResponse.class)))
    private void createGroup(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var request = ctx.bodyAsClass(ClusterGroupRequest.class);
        var group = memberService.createGroup(cluster.id(), request.name());
        ctx.status(HttpStatus.CREATED).json(new ClusterGroupResponse(group.id(), group.name()));
    }

    @OpenApi(
            path = "/api/v1/cluster/member-groups/{groupId}",
            methods = HttpMethod.GET,
            summary = "What a group carries and who is in it",
            tags = {"Cluster"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = ClusterGroupDetailResponse.class)))
    private void getGroup(Context ctx) {
        Cluster cluster = requireActive(ctx);
        var detail = memberService.findGroupDetail(cluster.id(), pathInt(ctx, "groupId"));
        ctx.json(new ClusterGroupDetailResponse(
                detail.group().id(), detail.group().name(), names(detail.permissions()), detail.memberIds()));
    }

    @OpenApi(
            path = "/api/v1/cluster/member-groups/{groupId}",
            methods = HttpMethod.PUT,
            summary = "Rename a group, set what it carries and who is in it",
            tags = {"Cluster"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ClusterGroupUpdateRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void updateGroup(Context ctx) {
        Cluster cluster = requireActive(ctx);
        int groupId = pathInt(ctx, "groupId");
        var request = ctx.bodyAsClass(ClusterGroupUpdateRequest.class);

        if (request.name() != null) memberService.renameGroup(cluster.id(), groupId, request.name());
        if (request.permissions() != null) {
            memberService.setGroupPermissions(cluster.id(), groupId, parsePermissions(request.permissions()));
        }
        if (request.memberIds() != null) {
            memberService.setGroupMembers(cluster.id(), groupId, Set.copyOf(request.memberIds()));
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/cluster/member-groups/{groupId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a member group",
            tags = {"Cluster"},
            responses = @OpenApiResponse(status = "204"))
    private void deleteGroup(Context ctx) {
        Cluster cluster = requireActive(ctx);
        memberService.deleteGroup(cluster.id(), pathInt(ctx, "groupId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private Cluster requireActive(Context ctx) {
        UserSession session = UserSession.from(ctx);
        Integer clusterId = session.clusterId();
        if (clusterId == null) throw new BadRequestResponse("No cluster selected");
        return clusterService.findById(clusterId).orElseThrow(NotFoundResponse::new);
    }

    private ClusterMemberResponse toResponse(ClusterMember member) {
        var account = accountRepository.findById(member.accountId());
        return new ClusterMemberResponse(
                member.id(),
                account.map(a -> a.fullName()).orElse(null),
                account.map(a -> a.email()).orElse(null),
                member.userType().name());
    }

    private static List<String> names(Set<ClusterPermission> permissions) {
        return permissions.stream().map(Enum::name).sorted().toList();
    }

    private static ClusterUserType parseUserType(String raw) {
        if (raw == null || raw.isBlank()) return ClusterUserType.CLUSTER_USER;
        try {
            return ClusterUserType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("No such user type: " + raw);
        }
    }

    private static Set<ClusterPermission> parsePermissions(List<String> raw) {
        Set<ClusterPermission> permissions = EnumSet.noneOf(ClusterPermission.class);
        if (raw == null) return permissions;
        for (String name : raw) {
            try {
                permissions.add(ClusterPermission.valueOf(name));
            } catch (IllegalArgumentException e) {
                throw new BadRequestResponse("No such permission: " + name);
            }
        }
        return permissions;
    }

    public record NewClusterMemberRequest(String email, String userType) {}

    public record ClusterUserTypeRequest(String userType) {}

    public record ClusterPermissionsRequest(List<String> permissions) {}

    public record ClusterGroupRequest(String name) {}

    /**
     * Every field is optional: a caller renaming a group need not resend who is in it.
     */
    public record ClusterGroupUpdateRequest(String name, List<String> permissions, List<Integer> memberIds) {}

    public record ClusterMemberResponse(int id, String name, String email, String userType) {}

    public record ClusterGroupResponse(int id, String name) {}

    /**
     * @param direct   what they hold in their own right, the only part editable per member
     * @param resolved everything they hold once type, grants and groups are put together
     */
    public record ClusterMemberDetailResponse(
            ClusterMemberResponse member,
            List<String> direct,
            List<ClusterGroupResponse> groups,
            List<String> resolved) {}

    public record ClusterGroupDetailResponse(int id, String name, List<String> permissions, List<Integer> memberIds) {}
}
