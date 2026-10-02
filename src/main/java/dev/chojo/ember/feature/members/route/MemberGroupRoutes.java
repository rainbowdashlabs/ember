/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.MemberWithName;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GroupMembershipService;
import dev.chojo.ember.feature.members.service.GroupRuleRefused;
import dev.chojo.ember.feature.members.service.GroupRulesService;
import dev.chojo.ember.feature.members.service.GroupRulesService.GroupRules;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberViewService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTypeChangeService;
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
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Routes for member group management including CRUD operations on groups,
 * group membership, and group role assignments.
 */
@Singleton
public class MemberGroupRoutes implements Routes {
    private final MemberGroupService groupService;
    private final GroupMembershipService groupMemberships;
    private final StationMemberService memberService;
    private final MemberViewService memberViews;
    private final UserTypeChangeService userTypeChanges;
    private final GroupRulesService groupRules;

    @Inject
    public MemberGroupRoutes(
            MemberGroupService groupService,
            GroupMembershipService groupMemberships,
            StationMemberService memberService,
            MemberViewService memberViews,
            UserTypeChangeService userTypeChanges,
            GroupRulesService groupRules) {
        this.groupService = groupService;
        this.groupMemberships = groupMemberships;
        this.memberService = memberService;
        this.memberViews = memberViews;
        this.userTypeChanges = userTypeChanges;
        this.groupRules = groupRules;
    }

    /**
     * Asserts the member named in the path belongs to the caller's station. Answers 404 for a
     * member of another station, so the groups a stranger is in cannot be read or probed.
     */
    private StationMember requireOwnedMember(Context ctx, int memberId) {
        return requireOwnedOrNotFound(ctx, memberId, memberService::findById, StationMember::stationId);
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
        routes.put(
                prefix + "/station-members/{memberId}/groups",
                this::setMemberGroups,
                StationPermission.MEMBER_EDIT,
                StationPermission.MEMBER_MANAGE_GROUP);
        routes.get(
                prefix + "/station-members/{memberId}/user-type/{userType}/consequences",
                this::getUserTypeConsequences,
                StationPermission.MEMBER_EDIT);
    }

    @OpenApi(
            path = "/api/v1/groups",
            methods = HttpMethod.GET,
            summary = "List member groups for the current station",
            tags = {"Member Groups"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberGroup[].class)))
    private void list(Context ctx) {
        StationSession session = StationSession.from(ctx);
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
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(GroupRequest.class);
        ctx.status(HttpStatus.CREATED)
                .json(groupRules.create(session.stationId(), request.name(), request.groupRules(), session.user()));
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
        requireOwnedOrNotFound(ctx, id, groupService::findById, MemberGroup::stationId);
        groupService
                .findById(id)
                .ifPresentOrElse(
                        group -> {
                            var members = groupService.findMembers(id);
                            ctx.json(new GroupDetail(group.id(), group.stationId(), group.name(), members));
                        },
                        () -> {
                            throw MemberRefusal.GROUP_NOT_HERE_ON_READ.raise();
                        });
    }

    @OpenApi(
            path = "/api/v1/groups/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a member group",
            description = "Rules sent with the group replace its binding and set. Members the new binding does not "
                    + "take are refused and listed, unless removeNonMatching takes them out of the group. Members "
                    + "who would be in two groups of the set are always refused and listed.",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = GroupRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberGroup.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(
                        status = "409",
                        content = @OpenApiContent(from = GroupRuleRefused.GroupRuleRefusedBody.class))
            })
    private void update(Context ctx) {
        int id = pathInt(ctx, "id");
        var group = requireOwnedOrNotFound(ctx, id, groupService::findById, MemberGroup::stationId);
        var request = ctx.bodyAsClass(GroupRequest.class);
        ctx.json(groupRules.update(
                group,
                request.name(),
                request.color(),
                request.position(),
                request.groupRules(),
                request.removeNonMatching(),
                UserSession.from(ctx)));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a member group",
            description = "Refused while content is limited to the group. A group that grants permissions can only "
                    + "be deleted by somebody holding all of them, and asks for a fresh proof.",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        var group = requireOwnedOrNotFound(ctx, id, groupService::findById, MemberGroup::stationId);
        if (groupService.delete(group, UserSession.from(ctx))) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw MemberRefusal.GROUP_NOT_HERE_ON_DELETE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/members",
            methods = HttpMethod.GET,
            summary = "Get members of a group",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void getMembers(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, groupService::findById, MemberGroup::stationId);
        ctx.json(groupService.findMembers(id).stream().map(memberViews::named).toList());
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
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)),
                @OpenApiResponse(
                        status = "400",
                        content = @OpenApiContent(from = GroupRuleRefused.GroupRuleRefusedBody.class)),
                @OpenApiResponse(
                        status = "409",
                        content = @OpenApiContent(from = GroupRuleRefused.GroupRuleRefusedBody.class))
            })
    private void setMembers(Context ctx) {
        int groupId = pathInt(ctx, "id");
        StationSession session = StationSession.from(ctx);
        var group = requireOwnedOrNotFound(ctx, groupId, groupService::findById, MemberGroup::stationId);
        var request = ctx.bodyAsClass(SetMembersRequest.class);
        List<Integer> memberIds = request.memberIds() != null ? request.memberIds() : List.of();

        var result = groupMemberships.setMembers(group, memberIds, request.move(), session);
        ctx.json(result.stream().map(memberViews::named).toList());
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

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/groups",
            methods = HttpMethod.PUT,
            summary = "Replace the groups a member is in",
            description = "Provide every group the member should be in. Choosing another group of a set moves the "
                    + "member; two groups of one set, a group of another station and a group bound to other user "
                    + "types are refused. Joining or leaving a group that grants permissions asks for a fresh "
                    + "proof, and only groups granting nothing the caller lacks can be joined.",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberGroupsRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberGroup[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setMemberGroups(Context ctx) {
        var member = requireOwnedMember(ctx, pathInt(ctx, "memberId"));
        var request = ctx.bodyAsClass(MemberGroupsRequest.class);
        List<Integer> groupIds = request.groupIds() != null ? request.groupIds() : List.of();
        ctx.json(groupMemberships.replaceGroupsOfMember(member, groupIds, StationSession.from(ctx)));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/user-type/{userType}/consequences",
            methods = HttpMethod.GET,
            summary = "Get the groups a member would leave on becoming another user type",
            tags = {"Member Groups"},
            pathParams = {
                @OpenApiParam(name = "memberId", type = Integer.class, required = true),
                @OpenApiParam(name = "userType", type = StationUserType.class, required = true)
            },
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberGroup[].class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getUserTypeConsequences(Context ctx) {
        int memberId = pathInt(ctx, "memberId");
        requireOwnedMember(ctx, memberId);
        ctx.json(userTypeChanges.consequences(memberId, ctx.pathParam("userType")));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/permissions",
            methods = HttpMethod.GET,
            summary = "Get permissions assigned to a group",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void getGroupPermissions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, groupService::findById, MemberGroup::stationId);
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
        requireOwnedOrNotFound(ctx, groupId, groupService::findById, MemberGroup::stationId);
        var request = ctx.bodyAsClass(SetGroupPermissionsRequest.class);
        List<Integer> permissionIds = request.permissionIds() != null ? request.permissionIds() : List.of();
        ctx.json(groupService.setGroupPermissions(groupId, permissionIds, session.permissions()));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/convert-to-tag",
            methods = HttpMethod.POST,
            summary = "Convert a group to a tag (keeps members, deletes the group)",
            description = "Held to the same rules as deleting the group.",
            tags = {"Member Groups"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void convertToTag(Context ctx) {
        int id = pathInt(ctx, "id");
        var group = requireOwnedOrNotFound(ctx, id, groupService::findById, MemberGroup::stationId);
        groupService.convertToTag(group, UserSession.from(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * A group as it is created or changed.
     *
     * @param name              its name
     * @param color             its colour, or {@code null} for none
     * @param position          its place in the order, or {@code null} to keep the one it has
     * @param rules             its binding and set, or {@code null} to leave them as they are
     * @param removeNonMatching whether members a new binding does not take are taken out of the group,
     *                          rather than refused
     */
    public record GroupRequest(
            @Nullable String name,
            @Nullable String color,
            @Nullable Integer position,
            @Nullable GroupRulesRequest rules,
            boolean removeNonMatching) {
        @Nullable
        GroupRules groupRules() {
            return rules == null ? null : new GroupRules(rules.groupSetId(), Set.copyOf(rules.userTypes()));
        }
    }

    /**
     * The rules of a group.
     *
     * @param groupSetId the set it belongs to, or {@code null} for none
     * @param userTypes  the member types it takes, empty for every type
     */
    public record GroupRulesRequest(Integer groupSetId, List<StationUserType> userTypes) {
        public GroupRulesRequest {
            userTypes = userTypes == null ? List.of() : userTypes;
        }
    }

    /**
     * Every group one member should be in.
     *
     * @param groupIds the groups
     */
    public record MemberGroupsRequest(List<Integer> groupIds) {}

    public record GroupDetail(int id, int stationId, String name, List<StationMember> members) {}

    /**
     * The members a group should hold.
     *
     * @param memberIds every member it should hold afterwards
     * @param move      whether members already in another group of the same set are moved out of it,
     *                  rather than refused
     */
    public record SetMembersRequest(List<Integer> memberIds, boolean move) {}

    public record SetGroupPermissionsRequest(List<Integer> permissionIds) {}
}
