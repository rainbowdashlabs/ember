/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.MemberWithName;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwned;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Routes for member group management including CRUD operations on groups,
 * group membership, and group role assignments.
 */
@Singleton
public class MemberGroupRoutes implements Routes {
    private final MemberGroupService groupService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public MemberGroupRoutes(
            MemberGroupService groupService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.groupService = groupService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Asserts the member named in the path belongs to the caller's station. Answers 404 for a
     * member of another station, so the groups a stranger is in cannot be read or probed.
     */
    private void requireOwnedMember(Context ctx, int memberId) {
        requireOwnedOrNotFound(ctx, memberId, stationMemberRepository::findById, StationMember::stationId);
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/groups", this::list, StationPermission.LOGIN);
        routes.get(prefix + "/groups/{id}", this::get, StationPermission.MEMBER_MANAGE_GROUP);
        routes.post(prefix + "/groups", this::create, StationPermission.MEMBER_MANAGE_GROUP);
        routes.put(prefix + "/groups/{id}", this::update, StationPermission.MEMBER_MANAGE_GROUP);
        routes.delete(prefix + "/groups/{id}", this::delete, StationPermission.MEMBER_MANAGE_GROUP);

        routes.get(
                prefix + "/groups/{id}/members",
                this::getMembers,
                StationPermission.MEMBER_MANAGE_GROUP,
                StationPermission.ATTENDANCE_READ,
                StationPermission.EVENT_EDIT,
                StationPermission.INVENTORY_READ);
        routes.put(prefix + "/groups/{id}/members", this::setMembers, StationPermission.MEMBER_MANAGE_GROUP);

        routes.get(
                prefix + "/groups/{id}/permissions", this::getGroupPermissions, StationPermission.MEMBER_MANAGE_GROUP);
        routes.put(
                prefix + "/groups/{id}/permissions",
                this::setGroupPermissions,
                StationPermission.MEMBER_MANAGE_GROUP,
                StepUpCategory.ROLE_CHANGE);

        routes.post(prefix + "/groups/{id}/convert-to-tag", this::convertToTag, StationPermission.MEMBER_MANAGE_GROUP);

        routes.get(prefix + "/station-members/{memberId}/groups", this::getMemberGroups, StationPermission.MEMBER_READ);
    }

    private MemberWithName toMemberWithName(StationMember m) {
        return MemberWithName.from(m, accountRepository, memberIdentityFactory);
    }

    // -- Groups --

    @OpenApi(
            path = "/api/v1/groups",
            methods = HttpMethod.GET,
            summary = "List member groups for the current station",
            tags = {"Member Groups"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberGroup[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(groupService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/groups",
            methods = HttpMethod.POST,
            summary = "Create a member group",
            tags = {"Member Groups"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = GroupRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberGroup.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(GroupRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        ctx.status(HttpStatus.CREATED).json(groupService.create(session.stationId(), request.name()));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}",
            methods = HttpMethod.GET,
            summary = "Get a member group with its members",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = GroupDetail.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwned(ctx, id, groupService::findById, MemberGroup::stationId);
        groupService
                .findById(id)
                .ifPresentOrElse(
                        group -> {
                            var members = groupService.findMembers(id);
                            ctx.json(new GroupDetail(group.id(), group.stationId(), group.name(), members));
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    @OpenApi(
            path = "/api/v1/groups/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a member group",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = GroupRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberGroup.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwned(ctx, id, groupService::findById, MemberGroup::stationId);
        var request = ctx.bodyAsClass(GroupRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        groupService
                .update(id, request.name(), request.color(), request.position())
                .ifPresentOrElse(ctx::json, () -> {
                    throw new NotFoundResponse();
                });
    }

    @OpenApi(
            path = "/api/v1/groups/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a member group",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwned(ctx, id, groupService::findById, MemberGroup::stationId);
        if (groupService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Group Members --

    @OpenApi(
            path = "/api/v1/groups/{id}/members",
            methods = HttpMethod.GET,
            summary = "Get members of a group",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationMember[].class)))
    private void getMembers(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwned(ctx, id, groupService::findById, MemberGroup::stationId);
        ctx.json(groupService.findMembers(id).stream()
                .map(this::toMemberWithName)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/members",
            methods = HttpMethod.PUT,
            summary = "Set members of a group (replaces all existing memberships)",
            description =
                    "Provide the full list of member IDs. Existing members not in the list are removed, new ones are added.",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetMembersRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void setMembers(Context ctx) {
        int groupId = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        requireOwned(ctx, groupId, groupService::findById, MemberGroup::stationId);
        var request = ctx.bodyAsClass(SetMembersRequest.class);
        List<Integer> memberIds = request.memberIds() != null ? request.memberIds() : List.of();

        var result =
                groupService.setMembers(groupId, memberIds, session.member().id());
        ctx.json(result.stream().map(this::toMemberWithName).toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/groups",
            methods = HttpMethod.GET,
            summary = "Get groups a member belongs to",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberGroup[].class)))
    private void getMemberGroups(Context ctx) {
        int memberId = pathInt(ctx, "memberId");
        requireOwnedMember(ctx, memberId);
        ctx.json(groupService.findGroupsForMember(memberId));
    }

    // -- Group Permissions --

    @OpenApi(
            path = "/api/v1/groups/{id}/permissions",
            methods = HttpMethod.GET,
            summary = "Get permissions assigned to a group",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void getGroupPermissions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwned(ctx, id, groupService::findById, MemberGroup::stationId);
        ctx.json(groupService.findGroupPermissions(id));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/permissions",
            methods = HttpMethod.PUT,
            summary = "Set permissions of a group (replaces all existing permissions)",
            description =
                    "Provide the full list of permission IDs. Existing permissions not in the list are removed, new ones are added. "
                            + "You can only grant permissions that you yourself have.",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetGroupPermissionsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setGroupPermissions(Context ctx) {
        int groupId = pathInt(ctx, "id");
        UserSession session = UserSession.from(ctx);
        requireOwned(ctx, groupId, groupService::findById, MemberGroup::stationId);
        var request = ctx.bodyAsClass(SetGroupPermissionsRequest.class);
        List<Integer> permissionIds = request.permissionIds() != null ? request.permissionIds() : List.of();
        ctx.json(groupService.setGroupPermissions(groupId, permissionIds, session.permissions()));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/convert-to-tag",
            methods = HttpMethod.POST,
            summary = "Convert a group to a tag (keeps members, deletes the group)",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void convertToTag(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwned(ctx, id, groupService::findById, MemberGroup::stationId);
        groupService.convertToTag(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Request/Response records --

    public record GroupRequest(String name, String color, int position) {}

    public record GroupDetail(int id, int stationId, String name, List<StationMember> members) {}

    public record SetMembersRequest(List<Integer> memberIds) {}

    public record SetGroupPermissionsRequest(List<Integer> permissionIds) {}
}
