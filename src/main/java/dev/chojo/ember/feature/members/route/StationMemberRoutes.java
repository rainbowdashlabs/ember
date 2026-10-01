/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import com.fasterxml.jackson.annotation.JsonValue;
import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.MemberWithName;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.RichMember;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.FormerMemberService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberPickerService;
import dev.chojo.ember.feature.members.service.MemberPickerService.MemberSearchResult;
import dev.chojo.ember.feature.members.service.MemberSetupMailService;
import dev.chojo.ember.feature.members.service.MemberViewService;
import dev.chojo.ember.feature.members.service.NicknameService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTypeChangeService;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Routes for station member management including listing, creating, deleting members,
 * role assignment, display name management, and GDPR anonymization on deletion.
 */
@Singleton
public class StationMemberRoutes implements Routes {
    private final StationMemberService memberService;
    private final MemberViewService memberViews;
    private final MemberPickerService pickerService;
    private final MemberSetupMailService setupMailService;
    private final FormerMemberService formerMemberService;
    private final GdprDeletionService gdprDeletionService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final RestrictionService restrictionService;
    private final NicknameService nicknameService;
    private final UserTypeChangeService userTypeChanges;

    @Inject
    public StationMemberRoutes(
            StationMemberService memberService,
            MemberViewService memberViews,
            MemberPickerService pickerService,
            MemberSetupMailService setupMailService,
            FormerMemberService formerMemberService,
            GdprDeletionService gdprDeletionService,
            MemberIdentityFactory memberIdentityFactory,
            RestrictionService restrictionService,
            NicknameService nicknameService,
            UserTypeChangeService userTypeChanges) {
        this.userTypeChanges = userTypeChanges;
        this.memberService = memberService;
        this.memberViews = memberViews;
        this.pickerService = pickerService;
        this.setupMailService = setupMailService;
        this.nicknameService = nicknameService;
        this.formerMemberService = formerMemberService;
        this.gdprDeletionService = gdprDeletionService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.restrictionService = restrictionService;
    }

    /**
     * Loads a station member and asserts they belong to the caller's station, returning them.
     * Answers 404 when the member is absent or owned by another station, so a member id from
     * another station cannot be read, modified, or probed for existence through these routes.
     */
    private StationMember requireOwnedMember(Context ctx, int memberId) {
        StationSession.from(ctx);
        return requireOwnedOrNotFound(ctx, memberId, memberService::findById, StationMember::stationId);
    }

    /**
     * Asserts every given member id belongs to the caller's station. Used to validate the
     * manager/managed member ids supplied in relationship-editing request bodies so a foreign
     * member cannot be linked across the station boundary.
     */
    private void requireOwnedMembers(Context ctx, List<Integer> memberIds) {
        for (int memberId : memberIds) {
            requireOwnedMember(ctx, memberId);
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/permissions", this::listAllPermissions, StationPermission.LOGIN);
        routes.get(prefix + "/station-members/completions", this::completions, StationPermission.LOGIN);
        routes.get(
                prefix + "/members/search",
                this::searchPicker,
                StationPermission.PAGE_EDIT,
                StationPermission.INVENTORY_ASSIGN,
                StationPermission.INVENTORY_EDIT,
                StationPermission.MEMBER_READ);
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
                prefix + "/station-members/by-uid/{uid}",
                this::getByUid,
                StationPermission.PAGE_EDIT,
                StationPermission.INVENTORY_ASSIGN,
                StationPermission.INVENTORY_EDIT,
                StationPermission.MEMBER_READ);
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
        routes.put(prefix + "/station-members/{id}/nickname", this::setNickname, StationPermission.LOGIN);
        routes.post(prefix + "/station-members", this::create, StationPermission.MEMBER_EDIT);
        routes.delete(prefix + "/station-members/{id}", this::delete, StationPermission.MEMBER_EDIT);
        routes.get(prefix + "/station-members/{id}/permissions", this::getPermissions, StationPermission.MEMBER_READ);
        routes.put(
                prefix + "/station-members/{id}/permissions",
                this::setPermissions,
                StationPermission.MEMBER_EDIT,
                StepUpCategory.ROLE_CHANGE);

        routes.get(prefix + "/station-members/{id}/managed", this::getManaged, StationPermission.MEMBER_READ);
        routes.put(prefix + "/station-members/{id}/managed", this::setManaged, StationPermission.MEMBER_EDIT);
        routes.get(prefix + "/station-members/{id}/managers", this::getManagers, StationPermission.MEMBER_READ);
        routes.put(prefix + "/station-members/{id}/managers", this::setManagers, StationPermission.MEMBER_EDIT);

        routes.put(
                prefix + "/station-members/{id}/user-type",
                this::setUserType,
                StationPermission.MEMBER_EDIT,
                StepUpCategory.ROLE_CHANGE);
        routes.put(prefix + "/station-members/{id}/join-date", this::setJoinDate, StationPermission.MEMBER_EDIT);
        routes.post(prefix + "/station-members/{id}/mark-former", this::markFormer, StationPermission.MEMBER_EDIT);
        routes.post(prefix + "/station-members/{id}/reactivate", this::reactivate, StationPermission.MEMBER_EDIT);
        routes.post(
                prefix + "/station-members/{id}/resend-setup-mail",
                this::resendSetupMail,
                StationPermission.MEMBER_EDIT);

        routes.get(
                prefix + "/user-type-permissions/{userType}",
                this::getUserTypePermissions,
                StationPermission.MEMBER_READ);
        routes.put(
                prefix + "/user-type-permissions/{userType}",
                this::setUserTypePermissions,
                StationPermission.MEMBER_MANAGER,
                StepUpCategory.ROLE_CHANGE);
        routes.get(
                prefix + "/user-type-permissions/{userType}/effective",
                this::getEffectiveUserTypePermissions,
                StationPermission.MEMBER_READ);
    }

    @OpenApi(
            path = "/api/v1/permissions",
            methods = HttpMethod.GET,
            summary = "List every permission a member, a group or a user type can be granted",
            tags = {"Station Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void listAllPermissions(Context ctx) {
        ctx.json(memberService.findAllPermissions());
    }

    @OpenApi(
            path = "/api/v1/members/search",
            methods = HttpMethod.GET,
            summary = "Search active members of the caller's station for the page-editor pickers",
            description = "Lightweight result shape (memberUid, displayName, userType, nameColor, avatarUrl)"
                    + " scoped to the caller's own station. Empty query returns the 20 most"
                    + " recently joined active members.",
            tags = {"Station Members"},
            queryParams = {
                @OpenApiParam(name = "q"),
                @OpenApiParam(name = "uid"),
                @OpenApiParam(name = "limit", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberSearchResult[].class)))
    private void searchPicker(Context ctx) {
        var session = StationSession.from(ctx);
        int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(20);
        ctx.json(pickerService.search(session.stationId(), ctx.queryParam("q"), ctx.queryParam("uid"), limit));
    }

    @OpenApi(
            path = "/api/v1/station-members/completions",
            methods = HttpMethod.GET,
            summary = "List the active members of the caller's station for autocomplete",
            tags = {"Station Members"},
            queryParams = {
                @OpenApiParam(name = "restrictionType", type = RestrictionType.class),
                @OpenApiParam(name = "entityId", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberCompletion[].class)))
    private void completions(Context ctx) {
        var session = StationSession.from(ctx);
        var completions = memberService.findCompletions(session.stationId());

        String rtParam = ctx.queryParam("restrictionType");
        String entityIdParam = ctx.queryParam("entityId");
        if (rtParam != null && entityIdParam != null) {
            try {
                var rType = RestrictionType.valueOf(rtParam);
                int entityId = Integer.parseInt(entityIdParam);
                var allowedIds = restrictionService.findMembersPassingRestriction(rType, entityId, session.stationId());
                if (allowedIds.isPresent()) {
                    completions = completions.stream()
                            .filter(c -> allowedIds.get().contains(c.id()))
                            .toList();
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        ctx.json(memberIdentityFactory.enrichCompletions(completions));
    }

    @OpenApi(
            path = "/api/v1/station-members",
            methods = HttpMethod.GET,
            summary = "List members of the caller's station with account info",
            tags = {"Station Members"},
            queryParams = @OpenApiParam(name = "includeFormer", type = Boolean.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void listByStation(Context ctx) {
        var session = StationSession.from(ctx);
        int stationId = session.stationId();
        boolean includeFormer = "true".equals(ctx.queryParam("includeFormer"));
        ctx.json(memberViews.withCompleteness(memberService.findByStation(stationId, includeFormer)));
    }

    /**
     * Everybody on the register, with the answers this reader may read and no others.
     */
    @OpenApi(
            path = "/api/v1/station-members/rich",
            methods = HttpMethod.GET,
            summary = "List all members with roles, groups, tags, and profile values in a single response",
            tags = {"Station Members"},
            queryParams = @OpenApiParam(name = "includeFormer", type = Boolean.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RichMember[].class)))
    private void listRichMembers(Context ctx) {
        var session = StationSession.from(ctx);
        boolean includeFormer = "true".equals(ctx.queryParam("includeFormer"));
        ctx.json(memberViews.richMembers(
                session.stationId(), includeFormer, session.user().permissions()));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}",
            methods = HttpMethod.GET,
            summary = "Get a station member by ID",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        int id = pathInt(ctx, "id");
        var member = requireOwnedMember(ctx, id);
        ctx.json(memberViews.withCompleteness(member));
    }

    /**
     * One member of this station, named by the UUID a member menu hands over.
     *
     * <p>The menus identify a person by their UUID and everything below them works from the row id,
     * and a screen holding a list of members could only translate the one into the other for as long
     * as its list was current. A menu asks the server as it is typed in, so it offers people a list
     * loaded when the page opened does not hold, and the screen was left unable to act on a person it
     * was showing as chosen. This answers for one person, whenever they are asked about.
     *
     * <p>Somebody marked former is answered as not found, which is what the menus already do: a page
     * left open while somebody leaves the station must not be able to act on them afterwards.
     */
    @OpenApi(
            path = "/api/v1/station-members/by-uid/{uid}",
            methods = HttpMethod.GET,
            summary = "Get a station member of the caller's station by member UUID",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "uid", required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getByUid(Context ctx) {
        var session = StationSession.from(ctx);
        var member = memberService
                .resolveId(session.stationId(), pathUuid(ctx, "uid"))
                .flatMap(memberService::findById)
                .filter(found -> found.stationId() == session.stationId())
                .filter(found -> !found.former())
                .orElseThrow(MemberRefusal.MEMBER_NOT_HERE_BY_UID::raise);
        ctx.json(memberViews.withCompleteness(member));
    }

    @OpenApi(
            path = "/api/v1/station-members",
            methods = HttpMethod.POST,
            summary = "Add an account as member to a station",
            tags = {"Station Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateMemberRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberWithName.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(CreateMemberRequest.class);
        if (request.accountId() == null) {
            throw MemberRefusal.MEMBER_ACCOUNT_NOT_NAMED.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(memberViews.withCompleteness(memberService.create(session.stationId(), request.accountId())));
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
        int id = pathInt(ctx, "id");
        requireOwnedMember(ctx, id);
        gdprDeletionService.anonymizeMember(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Sets the name a member is called by.
     *
     * <p>Behind {@code LOGIN} rather than behind a right over members, because what somebody is
     * called is theirs to decide: the service allows the member themselves and whoever looks after
     * them, and refuses everybody else however senior.
     */
    @OpenApi(
            path = "/api/v1/station-members/{id}/nickname",
            methods = HttpMethod.PUT,
            summary = "Set the name a member is called by",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = NicknameRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setNickname(Context ctx) {
        int id = pathInt(ctx, "id");
        StationSession session = StationSession.from(ctx);
        requireOwnedMember(ctx, id);
        var request = ctx.bodyAsClass(NicknameRequest.class);
        nicknameService.set(
                id, request.nickname(), session.member().id(), session.user().permissions());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/station-members/all-permissions",
            methods = HttpMethod.GET,
            summary = "Get the permissions of every member of the caller's station, by member id",
            tags = {"Station Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PermissionsByMember.class)))
    private void getAllMemberPermissions(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var members = memberService.findByStation(session.stationId());
        var result = new HashMap<Integer, List<Permission>>();
        for (var member : members) {
            result.put(member.id(), memberService.findPermissions(member.id()));
        }
        ctx.json(new PermissionsByMember(result));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/permissions",
            methods = HttpMethod.GET,
            summary = "Get roles of a station member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void getPermissions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedMember(ctx, id);
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
        int memberId = pathInt(ctx, "id");
        StationSession session = StationSession.from(ctx);
        requireOwnedMember(ctx, memberId);
        var request = ctx.bodyAsClass(SetPermissionsRequest.class);
        List<Integer> permissionIds = request.permissionIds() != null ? request.permissionIds() : List.of();
        ctx.json(memberService.setPermissions(
                memberId,
                permissionIds,
                session.user().permissions(),
                session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/managed",
            methods = HttpMethod.GET,
            summary = "Get members managed by this member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void getManaged(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedMember(ctx, id);
        ctx.json(memberViews.withCompleteness(memberService.findManaged(id)));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/managers",
            methods = HttpMethod.GET,
            summary = "Get managers of this member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void getManagers(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedMember(ctx, id);
        ctx.json(memberViews.withCompleteness(memberService.findManagers(id)));
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
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void setManagers(Context ctx) {
        int managedId = pathInt(ctx, "id");
        requireOwnedMember(ctx, managedId);
        var request = ctx.bodyAsClass(SetManagersRequest.class);
        List<Integer> managerIds = request.managerIds() != null ? request.managerIds() : List.of();
        requireOwnedMembers(ctx, managerIds);
        ctx.json(memberViews.withCompleteness(memberService.setManagers(managedId, managerIds)));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/managed",
            methods = HttpMethod.PUT,
            summary = "Set the members this member manages (replaces all existing ones)",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetManagedRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void setManaged(Context ctx) {
        int managerId = pathInt(ctx, "id");
        requireOwnedMember(ctx, managerId);
        var request = ctx.bodyAsClass(SetManagedRequest.class);
        List<Integer> managedIds = request.managedIds() != null ? request.managedIds() : List.of();
        requireOwnedMembers(ctx, managedIds);
        ctx.json(memberViews.withCompleteness(memberService.setManaged(managerId, managedIds)));
    }

    @OpenApi(
            path = "/api/v1/station-members/former",
            methods = HttpMethod.GET,
            summary = "List former members of the station",
            tags = {"Station Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void listFormer(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(memberViews.withCompleteness(memberService.findFormerByStation(session.stationId())));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/mark-former",
            methods = HttpMethod.POST,
            summary = "Mark a member as former",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormerCheckResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void markFormer(Context ctx) {
        int memberId = pathInt(ctx, "id");
        requireOwnedMember(ctx, memberId);
        if (formerMemberService.canMarkFormer(memberId) != null) {
            throw MemberRefusal.MEMBER_NOT_MARKED_FORMER.raise();
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
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FormerCheckResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void reactivate(Context ctx) {
        int memberId = pathInt(ctx, "id");
        requireOwnedMember(ctx, memberId);
        formerMemberService.reactivate(memberId);
        ctx.json(new FormerCheckResponse(true, null));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/resend-setup-mail",
            methods = HttpMethod.POST,
            summary = "Resend the password-setup mail to a member whose account has not been setup yet",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404")
            })
    private void resendSetupMail(Context ctx) {
        int memberId = pathInt(ctx, "id");
        setupMailService.resend(requireOwnedMember(ctx, memberId));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/user-type",
            methods = HttpMethod.PUT,
            summary = "Set the user type of a station member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            description = "The member leaves every group bound to user types that do not take the new one.",
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetUserTypeRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = UserTypeChangeResponse.class)))
    private void setUserType(Context ctx) {
        int memberId = pathInt(ctx, "id");
        requireOwnedMember(ctx, memberId);
        var request = ctx.bodyAsClass(SetUserTypeRequest.class);
        if (request.userType() == null) {
            throw MemberRefusal.MEMBER_USER_TYPE_NOT_NAMED.raise();
        }
        ctx.json(new UserTypeChangeResponse(userTypeChanges.change(memberId, request.userType())));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/join-date",
            methods = HttpMethod.PUT,
            summary = "Set the join date of a station member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetJoinDateRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setJoinDate(Context ctx) {
        int memberId = pathInt(ctx, "id");
        requireOwnedMember(ctx, memberId);
        var request = ctx.bodyAsClass(SetJoinDateRequest.class);
        if (request.joinDate() == null) {
            throw MemberRefusal.MEMBER_JOIN_DATE_NOT_NAMED.raise();
        }
        memberService.setJoinDate(memberId, request.joinDate());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/user-type-permissions/{userType}",
            methods = HttpMethod.GET,
            summary = "Get station-level permissions for a user type",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "userType", required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void getUserTypePermissions(Context ctx) {
        StationSession session = StationSession.from(ctx);
        StationUserType userType = StationUserType.valueOf(ctx.pathParam("userType"));
        ctx.json(memberService.findUserTypePermissions(session.stationId(), userType));
    }

    @OpenApi(
            path = "/api/v1/user-type-permissions/{userType}",
            methods = HttpMethod.PUT,
            summary = "Set station-level permissions for a user type",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "userType", required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetUserTypePermissionsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Permission[].class)))
    private void setUserTypePermissions(Context ctx) {
        StationSession session = StationSession.from(ctx);
        StationUserType userType = StationUserType.valueOf(ctx.pathParam("userType"));
        var request = ctx.bodyAsClass(SetUserTypePermissionsRequest.class);
        List<Integer> permissionIds = request.permissionIds() != null ? request.permissionIds() : List.of();
        ctx.json(memberService.setUserTypePermissions(session.stationId(), userType, permissionIds));
    }

    @OpenApi(
            path = "/api/v1/user-type-permissions/{userType}/effective",
            methods = HttpMethod.GET,
            summary = "Get the expanded effective permissions granted by a user type (defaults + station overrides)",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "userType", required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void getEffectiveUserTypePermissions(Context ctx) {
        StationSession session = StationSession.from(ctx);
        StationUserType userType = StationUserType.valueOf(ctx.pathParam("userType"));
        ctx.json(memberService.effectiveUserTypePermissions(session.stationId(), userType));
    }

    public record CreateMemberRequest(Integer stationId, Integer accountId) {}

    public record SetPermissionsRequest(List<Integer> permissionIds) {}

    public record SetManagersRequest(List<Integer> managerIds) {}

    public record SetManagedRequest(List<Integer> managedIds) {}

    /**
     * Whether a member may be marked former.
     *
     * @param canMarkFormer whether they may
     * @param reason        why not, or null when they may
     */
    public record FormerCheckResponse(
            boolean canMarkFormer, @Nullable String reason) {}

    /**
     * The permissions of every member of a station, keyed by member id.
     *
     * @param byMember the permissions, by member id
     */
    public record PermissionsByMember(@JsonValue Map<Integer, List<Permission>> byMember) {}

    public record SetUserTypeRequest(StationUserType userType) {}

    /**
     * What changing a member's type did beyond the type.
     *
     * @param leftGroups the groups they left because those do not take the new type
     */
    public record UserTypeChangeResponse(List<MemberGroup> leftGroups) {}

    public record SetJoinDateRequest(LocalDate joinDate) {}

    public record SetUserTypePermissionsRequest(List<Integer> permissionIds) {}

    /**
     * @param nickname the name to be called by, or null and blank alike to go back to the register
     */
    public record NicknameRequest(String nickname) {}
}
