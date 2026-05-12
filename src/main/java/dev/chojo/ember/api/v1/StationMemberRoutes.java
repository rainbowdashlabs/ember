/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.entity.Account;
import dev.chojo.ember.entity.Role;
import dev.chojo.ember.entity.StationMember;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import dev.chojo.ember.service.StationMemberService;
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

@Singleton
public class StationMemberRoutes implements Routes {
    private final StationMemberService memberService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public StationMemberRoutes(
            StationMemberService memberService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository) {
        this.memberService = memberService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/roles", this::listAllRoles, Roles.MEMBER_MANAGEMENT);
        routes.get(prefix + "/station-members", this::listByStation, Roles.MEMBER_MANAGEMENT);
        routes.get(prefix + "/station-members/{id}", this::get, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/station-members", this::create, Roles.MEMBER_MANAGEMENT);
        routes.delete(prefix + "/station-members/{id}", this::delete, Roles.MEMBER_MANAGEMENT);

        routes.get(prefix + "/station-members/{id}/roles", this::getRoles, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/station-members/{id}/roles", this::setRoles, Roles.MEMBER_MANAGEMENT);

        routes.get(prefix + "/station-members/{id}/managed", this::getManaged, Roles.MEMBER_MANAGEMENT);
        routes.get(prefix + "/station-members/{id}/managers", this::getManagers, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/station-members/{id}/managers", this::setManagers, Roles.MEMBER_MANAGEMENT);
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
        List<MemberWithName> result = memberService.findByStation(stationId).stream()
                .map(m -> {
                    Account account = accountRepository.findById(m.accountId()).orElse(null);
                    String name = account != null ? (account.firstName() + " " + account.lastName()).trim() : "";
                    String email = account != null ? account.email() : "";
                    return new MemberWithName(m.id(), m.stationId(), m.accountId(), name, email);
                })
                .toList();
        ctx.json(result);
    }

    @io.javalin.openapi.OpenApiName("StationMemberWithName")
    public record MemberWithName(int id, int stationId, int accountId, String name, String email) {}

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
        memberService.findById(id).ifPresentOrElse(member -> ctx.json(member), () -> {
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
        if (memberService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
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

    private MemberWithName toMemberWithName(dev.chojo.ember.entity.StationMember m) {
        Account account = accountRepository.findById(m.accountId()).orElse(null);
        String name = account != null ? (account.firstName() + " " + account.lastName()).trim() : "";
        String email = account != null ? account.email() : "";
        return new MemberWithName(m.id(), m.stationId(), m.accountId(), name, email);
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

    public record CreateMemberRequest(Integer stationId, Integer accountId) {}

    public record SetRolesRequest(List<Integer> roleIds) {}

    public record SetManagersRequest(List<Integer> managerIds) {}
}
