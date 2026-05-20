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
import dev.chojo.ember.feature.legal.service.GdprDeletionService;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.FormerMemberService;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class StationMemberRoutes implements Routes {
    private final StationMemberService memberService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;
    private final FormerMemberService formerMemberService;
    private final ProfileFieldService profileFieldService;
    private final GdprDeletionService gdprDeletionService;

    @Inject
    public StationMemberRoutes(
            StationMemberService memberService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            FormerMemberService formerMemberService,
            ProfileFieldService profileFieldService,
            GdprDeletionService gdprDeletionService) {
        this.memberService = memberService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.formerMemberService = formerMemberService;
        this.profileFieldService = profileFieldService;
        this.gdprDeletionService = gdprDeletionService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/roles", this::listAllRoles, Roles.LOGIN);
        routes.get(prefix + "/station-members", this::listByStation, Roles.MEMBER_MANAGEMENT);
        routes.get(prefix + "/station-members/former", this::listFormer, Roles.MEMBER_MANAGEMENT);
        routes.get(prefix + "/station-members/{id}", this::get, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/station-members", this::create, Roles.MEMBER_MANAGEMENT);
        routes.delete(prefix + "/station-members/{id}", this::delete, Roles.MEMBER_MANAGEMENT);

        routes.get(prefix + "/station-members/{id}/roles", this::getRoles, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/station-members/{id}/roles", this::setRoles, Roles.MEMBER_MANAGEMENT);

        routes.get(prefix + "/station-members/{id}/managed", this::getManaged, Roles.MEMBER_MANAGEMENT);
        routes.get(prefix + "/station-members/{id}/managers", this::getManagers, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/station-members/{id}/managers", this::setManagers, Roles.MEMBER_MANAGEMENT);

        routes.post(prefix + "/station-members/{id}/mark-former", this::markFormer, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/station-members/{id}/reactivate", this::reactivate, Roles.MEMBER_MANAGEMENT);
    }

    @OpenApi(
            path = "/api/v1/station-members",
            methods = HttpMethod.GET,
            summary = "List members of a station with account info",
            tags = {"Station Members"},
            queryParams = @OpenApiParam(name = "stationId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void listAllRoles(Context ctx) {
        ctx.json(stationMemberRepository.findAllRoles());
    }

    private void listByStation(Context ctx) {
        int stationId = ctx.queryParamAsClass("stationId", Integer.class).get();
        boolean includeFormer = "true".equals(ctx.queryParam("includeFormer"));
        ctx.json(memberService.findByStation(stationId, includeFormer).stream()
                .map(this::toMemberWithName)
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
        memberService.findById(id).ifPresentOrElse(ctx::json, () -> {
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
        ctx.status(HttpStatus.CREATED).json(memberService.create(request.stationId(), request.accountId()));
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
        var member = stationMemberRepository.findById(id).orElseThrow(NotFoundResponse::new);
        // Check that the member is not a station owner
        var station = member.stationId() > 0
                ? stationMemberRepository.findByStation(member.stationId()).stream()
                        .filter(m -> m.id() == id)
                        .findFirst()
                        .orElse(null)
                : null;
        gdprDeletionService.anonymizeMember(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/roles",
            methods = HttpMethod.GET,
            summary = "Get roles of a station member",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Role[].class)))
    private void getRoles(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(memberService.findRoles(id));
    }

    @OpenApi(
            path = "/api/v1/station-members/{id}/roles",
            methods = HttpMethod.PUT,
            summary = "Set roles of a station member (replaces all existing roles)",
            description =
                    "Provide the full list of role IDs. Existing roles not in the list are removed, new ones are added.",
            tags = {"Station Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetRolesRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Role[].class)))
    private void setRoles(Context ctx) {
        int memberId = ctx.pathParamAsClass("id", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SetRolesRequest.class);
        List<Integer> roleIds = request.roleIds() != null ? request.roleIds() : List.of();
        ctx.json(memberService.setRoles(memberId, roleIds, session.roles()));
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
        if (m.accountId() == null) {
            return new MemberWithName(m.id(), m.stationId(), 0, m.displayName(), "", true);
        }
        Account account = accountRepository.findById(m.accountId()).orElse(null);
        String name = account != null ? (account.firstName() + " " + account.lastName()).trim() : "";
        String email = account != null ? account.email() : "";
        var roles = stationMemberRepository.findRoles(m.id()).stream()
                .map(r -> r.role().name())
                .toList();
        boolean complete = profileFieldService.isProfileComplete(m.id(), m.stationId(), roles);
        return new MemberWithName(m.id(), m.stationId(), m.accountId(), name, email, complete);
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

    @OpenApiName("StationMemberWithName")
    public record MemberWithName(
            int id, int stationId, int accountId, String name, String email, boolean profileComplete) {}

    public record CreateMemberRequest(Integer stationId, Integer accountId) {}

    public record SetRolesRequest(List<Integer> roleIds) {}

    public record SetManagersRequest(List<Integer> managerIds) {}

    public record FormerCheckResponse(boolean canMarkFormer, String reason) {}

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
}
