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
import dev.chojo.ember.entity.StationMember;
import dev.chojo.ember.entity.UserTag;
import dev.chojo.ember.repository.AccountRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import dev.chojo.ember.service.UserTagService;
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
public class UserTagRoutes implements Routes {
    private final UserTagService tagService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public UserTagRoutes(
            UserTagService tagService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository) {
        this.tagService = tagService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    private MemberWithName toMemberWithName(StationMember m) {
        Account account = accountRepository.findById(m.accountId()).orElse(null);
        String name = account != null ? (account.firstName() + " " + account.lastName()).trim() : "";
        String email = account != null ? account.email() : "";
        return new MemberWithName(m.id(), m.stationId(), m.accountId(), name, email);
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/tags", this::list, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/tags", this::create, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/tags/{id}", this::update, Roles.MEMBER_MANAGEMENT);
        routes.delete(prefix + "/tags/{id}", this::delete, Roles.MEMBER_MANAGEMENT);

        routes.get(prefix + "/tags/{id}/members", this::getMembers, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/tags/{id}/members", this::setMembers, Roles.MEMBER_MANAGEMENT);

        routes.get(prefix + "/station-members/{memberId}/tags", this::getMemberTags, Roles.MEMBER_MANAGEMENT);

        routes.post(prefix + "/tags/{id}/convert-to-group", this::convertToGroup, Roles.MEMBER_MANAGEMENT);
    }

    // -- Tags --

    @OpenApi(
            path = "/api/v1/tags",
            methods = HttpMethod.GET,
            summary = "List tags for the current station",
            tags = {"User Tags"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = UserTag[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(tagService.findByStation(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/tags",
            methods = HttpMethod.POST,
            summary = "Create a tag",
            tags = {"User Tags"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TagRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = UserTag.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(TagRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        ctx.status(HttpStatus.CREATED).json(tagService.create(session.stationId(), request.name()));
    }

    @OpenApi(
            path = "/api/v1/tags/{id}",
            methods = HttpMethod.PUT,
            summary = "Update a tag name",
            tags = {"User Tags"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TagRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(TagRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        if (!tagService.update(id, request.name())) {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/tags/{id}",
            methods = HttpMethod.DELETE,
            summary = "Delete a tag",
            tags = {"User Tags"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        if (tagService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Tag Members --

    @OpenApi(
            path = "/api/v1/tags/{id}/members",
            methods = HttpMethod.GET,
            summary = "Get members of a tag with name and email",
            tags = {"User Tags"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void getMembers(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(tagService.findMembers(id).stream().map(this::toMemberWithName).toList());
    }

    @OpenApi(
            path = "/api/v1/tags/{id}/members",
            methods = HttpMethod.PUT,
            summary = "Set members of a tag (replaces all existing memberships)",
            description =
                    "Provide the full list of member IDs. Existing members not in the list are removed, new ones are added.",
            tags = {"User Tags"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetMembersRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void setMembers(Context ctx) {
        int tagId = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(SetMembersRequest.class);
        List<Integer> memberIds = request.memberIds() != null ? request.memberIds() : List.of();
        tagService.setMembers(tagId, memberIds);
        ctx.json(tagService.findMembers(tagId).stream()
                .map(this::toMemberWithName)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/tags",
            methods = HttpMethod.GET,
            summary = "Get tags for a member",
            tags = {"User Tags"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = UserTag[].class)))
    private void getMemberTags(Context ctx) {
        int memberId = ctx.pathParamAsClass("memberId", Integer.class).get();
        ctx.json(tagService.findTagsForMember(memberId));
    }

    // -- Convert to Group --

    @OpenApi(
            path = "/api/v1/tags/{id}/convert-to-group",
            methods = HttpMethod.POST,
            summary = "Convert a tag to a member group",
            description = "Creates a new member group with the same name and members, then deletes the tag.",
            tags = {"User Tags"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void convertToGroup(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        tagService
                .findById(id)
                .ifPresentOrElse(
                        tag -> {
                            tagService.convertToGroup(id);
                            ctx.status(HttpStatus.NO_CONTENT);
                        },
                        () -> {
                            throw new NotFoundResponse();
                        });
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // -- Request/Response records --

    public record TagRequest(String name) {}

    @OpenApiName("TagSetMembersRequest")
    public record SetMembersRequest(List<Integer> memberIds) {}

    @OpenApiName("TagMemberWithName")
    public record MemberWithName(int id, int stationId, int accountId, String name, String email) {}
}
