/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Routes for member group management including CRUD operations on groups,
 * group membership, and group role assignments.
 */
@Singleton
public class MemberGroupRoutes implements Routes {
    private final MemberGroupService groupService;
    private final AccountRepository accountRepository;
    private final NotificationService notificationService;

    @Inject
    public MemberGroupRoutes(
            MemberGroupService groupService,
            AccountRepository accountRepository,
            NotificationService notificationService) {
        this.groupService = groupService;
        this.accountRepository = accountRepository;
        this.notificationService = notificationService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/groups", this::list, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/groups", this::create, Roles.MEMBER_MANAGEMENT);
        routes.get(prefix + "/groups/{id}", this::get, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/groups/{id}", this::update, Roles.MEMBER_MANAGEMENT);
        routes.delete(prefix + "/groups/{id}", this::delete, Roles.MEMBER_MANAGEMENT);

        routes.get(prefix + "/groups/{id}/members", this::getMembers, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/groups/{id}/members", this::setMembers, Roles.MEMBER_MANAGEMENT);

        routes.get(prefix + "/groups/{id}/roles", this::getGroupRoles, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/groups/{id}/roles", this::setGroupRoles, Roles.MEMBER_MANAGEMENT);

        routes.post(prefix + "/groups/{id}/convert-to-tag", this::convertToTag, Roles.MEMBER_MANAGEMENT);

        routes.get(prefix + "/station-members/{memberId}/groups", this::getMemberGroups, Roles.MEMBER_MANAGEMENT);
    }

    private MemberWithName toMemberWithName(StationMember m) {
        Account account = accountRepository.findById(m.accountId()).orElse(null);
        String name = account != null ? (account.firstName() + " " + account.lastName()).trim() : "";
        String email = account != null ? account.email() : "";
        return new MemberWithName(m.id(), m.stationId(), m.accountId(), name, email);
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(GroupRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        groupService.update(id, request.name()).ifPresentOrElse(ctx::json, () -> {
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
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
        int groupId = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(SetMembersRequest.class);
        List<Integer> memberIds = request.memberIds() != null ? request.memberIds() : List.of();

        // Determine which members are being added
        var currentMemberIds = new HashSet<>(groupService.findMembers(groupId).stream()
                .map(StationMember::id)
                .toList());
        var addedMemberIds = new ArrayList<Integer>();
        for (int id : memberIds) {
            if (!currentMemberIds.contains(id)) addedMemberIds.add(id);
        }

        var result = groupService.setMembers(groupId, memberIds);

        // Notify newly added members
        if (!addedMemberIds.isEmpty()) {
            groupService.findById(groupId).ifPresent(group -> {
                var data = NotificationData.of(
                        new NotificationParams.MemberAddedToGroup(group.name()),
                        new NotificationData.NotificationLink("dashboard-overview"));
                notificationService.notifyMembers(addedMemberIds, NotificationType.MEMBER_ADDED_TO_GROUP, data);
            });
        }

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
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        ctx.json(groupService.findGroupsForMember(memberId));
    }

    // -- Group Roles --

    @OpenApi(
            path = "/api/v1/groups/{id}/roles",
            methods = HttpMethod.GET,
            summary = "Get roles assigned to a group",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Role[].class)))
    private void getGroupRoles(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(groupService.findGroupRoles(id));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/roles",
            methods = HttpMethod.PUT,
            summary = "Set roles of a group (replaces all existing roles)",
            description =
                    "Provide the full list of role IDs. Existing roles not in the list are removed, new ones are added. "
                            + "Protected roles (MEMBER_MANAGEMENT, MANAGER) cannot be removed. "
                            + "You can only grant roles that you yourself have.",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetGroupRolesRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Role[].class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setGroupRoles(Context ctx) {
        int groupId = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SetGroupRolesRequest.class);
        List<Integer> roleIds = request.roleIds() != null ? request.roleIds() : List.of();
        ctx.json(groupService.setGroupRoles(groupId, roleIds, session.roles()));
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        groupService
                .findById(id)
                .ifPresentOrElse(
                        group -> {
                            groupService.convertToTag(id);
                            ctx.status(HttpStatus.NO_CONTENT);
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    // -- Request/Response records --

    public record MemberWithName(int id, int stationId, int accountId, String name, String email) {}

    public record GroupRequest(String name) {}

    public record GroupDetail(int id, int stationId, String name, List<StationMember> members) {}

    public record SetMembersRequest(List<Integer> memberIds) {}

    public record SetGroupRolesRequest(List<Integer> roleIds) {}
}
