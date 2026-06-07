/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.api.roles.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.members.entity.MemberWithName;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.RichMember;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.FormerMemberService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * Routes for station member management including listing, creating, deleting members,
 * role assignment, display name management, and GDPR anonymization on deletion.
 */
@Singleton
public class StationMemberRoutes implements Routes {
    private final StationMemberService memberService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final FormerMemberService formerMemberService;
    private final ProfileFieldService profileFieldService;
    private final GdprDeletionService gdprDeletionService;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public StationMemberRoutes(
            StationMemberService memberService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            FormerMemberService formerMemberService,
            ProfileFieldService profileFieldService,
            GdprDeletionService gdprDeletionService,
            MemberIdentityFactory memberIdentityFactory) {
        this.memberService = memberService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.formerMemberService = formerMemberService;
        this.profileFieldService = profileFieldService;
        this.gdprDeletionService = gdprDeletionService;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/permissions", this::listAllPermissions, StationPermission.LOGIN);
        routes.get(prefix + "/station-members/completions", this::completions, StationPermission.LOGIN);
        routes.get(
                prefix + "/station-members",
                this::listByStation,
                StationPermission.MEMBER_READ,
                StationPermission.ATTENDANCE_READ,
                StationPermission.EVENT_EDIT,
                StationPermission.INVENTORY_READ,
                StationPermission.POLL_VIEW_RESULTS,
                StationPermission.PROTOCOL_TESTER,
                StationPermission.TEST_RESULT_READ);
        routes.get(
                prefix + "/station-members/former",
                this::listFormer,
                StationPermission.MEMBER_READ,
                StationPermission.ATTENDANCE_READ);
        routes.get(
                prefix + "/station-members/all-permissions",
                this::getAllMemberPermissions,
                StationPermission.MEMBER_READ,
                StationPermission.POLL_CREATE,
                StationPermission.EVENT_EDIT);
        routes.get(prefix + "/station-members/rich", this::listRichMembers, StationPermission.MEMBER_READ);
        routes.get(
                prefix + "/station-members/{id}",
                this::get,
                StationPermission.MEMBER_READ,
                StationPermission.TEST_RESULT_READ);
        routes.post(prefix + "/station-members", this::create, StationPermission.MEMBER_EDIT);
        routes.delete(prefix + "/station-members/{id}", this::delete, StationPermission.MEMBER_EDIT);
        routes.get(prefix + "/station-members/{id}/permissions", this::getPermissions, StationPermission.MEMBER_READ);
        routes.put(prefix + "/station-members/{id}/permissions", this::setPermissions, StationPermission.MEMBER_EDIT);

        routes.get(prefix + "/station-members/{id}/managed", this::getManaged, StationPermission.MEMBER_READ);
        routes.put(prefix + "/station-members/{id}/managed", this::setManaged, StationPermission.MEMBER_EDIT);
        routes.get(prefix + "/station-members/{id}/managers", this::getManagers, StationPermission.MEMBER_READ);
        routes.put(prefix + "/station-members/{id}/managers", this::setManagers, StationPermission.MEMBER_EDIT);

        routes.put(prefix + "/station-members/{id}/user-type", this::setUserType, StationPermission.MEMBER_EDIT);
        routes.post(prefix + "/station-members/{id}/mark-former", this::markFormer, StationPermission.MEMBER_EDIT);
        routes.post(prefix + "/station-members/{id}/reactivate", this::reactivate, StationPermission.MEMBER_EDIT);

        routes.get(
                prefix + "/user-type-permissions/{userType}",
                this::getUserTypePermissions,
                StationPermission.MEMBER_READ);
        routes.put(
                prefix + "/user-type-permissions/{userType}",
                this::setUserTypePermissions,
                StationPermission.MEMBER_MANAGER);
        routes.get(
                prefix + "/user-type-permissions/{userType}/effective",
                this::getEffectiveUserTypePermissions,
                StationPermission.MEMBER_READ);
    }

    @OpenApi(
            path = "/api/v1/station-members",
            methods = HttpMethod.GET,
            summary = "List members of a station with account info",
            tags = {"Station Members"},
            queryParams = @OpenApiParam(name = "stationId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void listAllPermissions(Context ctx) {
        ctx.json(stationMemberRepository.findAllPermissions());
    }

    private void completions(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(memberIdentityFactory.enrichCompletions(stationMemberRepository.findCompletions(session.stationId())));
    }

    private void listByStation(Context ctx) {
        var session = UserSession.from(ctx);
        int stationId = session.stationId();
        boolean includeFormer = "true".equals(ctx.queryParam("includeFormer"));
        ctx.json(memberService.findByStation(stationId, includeFormer).stream()
                .map(this::toMemberWithName)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/rich",
            methods = HttpMethod.GET,
            summary = "List all members with roles, groups, tags, and profile values in a single response",
            tags = {"Station Members"},
            queryParams = @OpenApiParam(name = "includeFormer", type = Boolean.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RichMember[].class)))
    private void listRichMembers(Context ctx) {
        var session = UserSession.from(ctx);
        boolean includeFormer = "true".equals(ctx.queryParam("includeFormer"));
        ctx.json(stationMemberRepository.findRichMembers(session.stationId(), includeFormer).stream()
                .map(m -> m.withIdentity(memberIdentityFactory.local(m.stationId(), m.id())))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}",
            methods = HttpMethod.GET,
            summary = "Get a station member by ID",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationMember.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        memberService.findById(id).map(this::toMemberWithName).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/station-members",
            methods = HttpMethod.POST,
            summary = "Add an account as member to a station",
            tags = {"Station Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateMemberRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = StationMember.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        var request = ctx.bodyAsClass(CreateMemberRequest.class);
        if (request.stationId() == null || request.accountId() == null) {
            throw new BadRequestResponse("stationId and accountId are required");
        }
        ctx.status(HttpStatus.CREATED)
                .json(toMemberWithName(memberService.create(request.stationId(), request.accountId())));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}",
            methods = HttpMethod.DELETE,
            summary = "Remove a station member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var member = stationMemberRepository.findById(id).orElseThrow(NotFoundResponse::new);
        if (member.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot delete a member from another station");
        }
        gdprDeletionService.anonymizeMember(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/permissions",
            methods = HttpMethod.GET,
            summary = "Get roles of a station member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void getAllMemberPermissions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var members = memberService.findByStation(session.stationId());
        var result = new HashMap<Integer, List<Permission>>();
        for (var member : members) {
            result.put(member.id(), memberService.findPermissions(member.id()));
        }
        ctx.json(result);
    }

    private void getPermissions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(memberService.findPermissions(id));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/permissions",
            methods = HttpMethod.PUT,
            summary = "Set roles of a station member (replaces all existing roles)",
            description =
                    "Provide the full list of role IDs. Existing roles not in the list are removed, new ones are added.",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetPermissionsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void setPermissions(Context ctx) {
        int memberId = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SetPermissionsRequest.class);
        List<Integer> permissionIds = request.permissionIds() != null ? request.permissionIds() : List.of();
        ctx.json(memberService.setPermissions(memberId, permissionIds, session.permissions()));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/managed",
            methods = HttpMethod.GET,
            summary = "Get members managed by this member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationMember[].class)))
    private void getManaged(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(memberService.findManaged(id).stream()
                .map(this::toMemberWithName)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/managers",
            methods = HttpMethod.GET,
            summary = "Get managers of this member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationMember[].class)))
    private void getManagers(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(memberService.findManagers(id).stream()
                .map(this::toMemberWithName)
                .toList());
    }

    private MemberWithName toMemberWithName(StationMember m) {
        var roles = stationMemberRepository.findPermissions(m.id()).stream()
                .map(r -> r.permission().name())
                .toList();
        boolean complete = profileFieldService.isProfileComplete(m.id(), m.stationId(), roles);
        return MemberWithName.from(m, accountRepository, memberIdentityFactory, complete);
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/managers",
            methods = HttpMethod.PUT,
            summary = "Set managers of a member (replaces all existing managers)",
            description =
                    "Provide the full list of manager member IDs. Existing managers not in the list are removed, new ones are added.",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetManagersRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationMember[].class)))
    private void setManagers(Context ctx) {
        int managedId = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(SetManagersRequest.class);
        List<Integer> managerIds = request.managerIds() != null ? request.managerIds() : List.of();
        ctx.json(memberService.setManagers(managedId, managerIds));
    }

    private void setManaged(Context ctx) {
        int managerId = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(SetManagedRequest.class);
        List<Integer> managedIds = request.managedIds() != null ? request.managedIds() : List.of();
        ctx.json(memberService.setManaged(managerId, managedIds).stream()
                .map(this::toMemberWithName)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/former",
            methods = HttpMethod.GET,
            summary = "List former members of the station",
            tags = {"Station Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void listFormer(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(stationMemberRepository.findFormerByStation(session.stationId()).stream()
                .map(this::toMemberWithName)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/mark-former",
            methods = HttpMethod.POST,
            summary = "Mark a member as former",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void markFormer(Context ctx) {
        int memberId = ctx.pathParamAsClass("id", Integer.class).get();
        String check = formerMemberService.canMarkFormer(memberId);
        if (check != null) {
            throw new BadRequestResponse(check);
        }
        formerMemberService.markFormer(memberId);
        ctx.json(new FormerCheckResponse(true, null));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/reactivate",
            methods = HttpMethod.POST,
            summary = "Reactivate a former member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reactivate(Context ctx) {
        int memberId = ctx.pathParamAsClass("id", Integer.class).get();
        formerMemberService.reactivate(memberId);
        ctx.json(new FormerCheckResponse(true, null));
    }

    // -- User Type --

    @OpenApi(
            path = "/api/v1/station-members/{id}/user-type",
            methods = HttpMethod.PUT,
            summary = "Set the user type of a station member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetUserTypeRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setUserType(Context ctx) {
        int memberId = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(SetUserTypeRequest.class);
        StationUserType userType = StationUserType.valueOf(request.userType());
        stationMemberRepository.setUserType(memberId, userType);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- User Type Permissions --

    @OpenApi(
            path = "/api/v1/user-type-permissions/{userType}",
            methods = HttpMethod.GET,
            summary = "Get station-level permissions for a user type",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "userType", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void getUserTypePermissions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        StationUserType userType = StationUserType.valueOf(ctx.pathParam("userType"));
        ctx.json(stationMemberRepository.findUserTypePermissions(session.stationId(), userType));
    }

    @OpenApi(
            path = "/api/v1/user-type-permissions/{userType}",
            methods = HttpMethod.PUT,
            summary = "Set station-level permissions for a user type",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "userType", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetUserTypePermissionsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void setUserTypePermissions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        StationUserType userType = StationUserType.valueOf(ctx.pathParam("userType"));
        var request = ctx.bodyAsClass(SetUserTypePermissionsRequest.class);
        List<Integer> permissionIds = request.permissionIds() != null ? request.permissionIds() : List.of();
        stationMemberRepository.setUserTypePermissions(session.stationId(), userType, permissionIds);
        ctx.json(stationMemberRepository.findUserTypePermissions(session.stationId(), userType));
    }

    @OpenApi(
            path = "/api/v1/user-type-permissions/{userType}/effective",
            methods = HttpMethod.GET,
            summary = "Get the expanded effective permissions granted by a user type (defaults + station overrides)",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "userType", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void getEffectiveUserTypePermissions(Context ctx) {
        UserSession session = UserSession.from(ctx);
        StationUserType userType = StationUserType.valueOf(ctx.pathParam("userType"));

        Set<StationPermission> permissions = EnumSet.noneOf(StationPermission.class);
        // Hardcoded defaults from enum
        permissions.addAll(Arrays.asList(userType.defaultPermissions()));
        // Station-level overrides
        stationMemberRepository.findUserTypePermissions(session.stationId(), userType).stream()
                .map(Permission::permission)
                .forEach(permissions::add);
        // Expand hierarchy
        Set<StationPermission> expanded = StationPermission.expand(permissions);

        ctx.json(expanded.stream().map(Enum::name).sorted().toList());
    }

    public record CreateMemberRequest(Integer stationId, Integer accountId) {}

    public record SetPermissionsRequest(List<Integer> permissionIds) {}

    public record SetManagersRequest(List<Integer> managerIds) {}

    public record SetManagedRequest(List<Integer> managedIds) {}

    public record FormerCheckResponse(boolean canMarkFormer, String reason) {}

    public record SetUserTypeRequest(String userType) {}

    public record SetUserTypePermissionsRequest(List<Integer> permissionIds) {}
}
