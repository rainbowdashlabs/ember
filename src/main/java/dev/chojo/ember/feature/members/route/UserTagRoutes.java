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
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.members.entity.MemberWithName;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.UserTagService;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Routes for managing user tags including CRUD operations on tag definitions
 * and assigning/removing tags to/from members.
 */
@Singleton
public class UserTagRoutes implements Routes {
    private final UserTagService tagService;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public UserTagRoutes(
            UserTagService tagService,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.tagService = tagService;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Asserts the member named in the path belongs to the caller's station. Answers 404 for a
     * member of another station, so the tags a stranger carries cannot be read or probed.
     */
    private void requireOwnedMember(Context ctx, int memberId) {
        requireOwnedOrNotFound(ctx, memberId, stationMemberRepository::findById, StationMember::stationId);
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/tags", this::list, StationPermission.USER);
        routes.post(prefix + "/tags", this::create, StationPermission.MEMBER_MANAGE_TAGS);
        routes.put(prefix + "/tags/{id}", this::update, StationPermission.MEMBER_MANAGE_TAGS);
        routes.delete(prefix + "/tags/{id}", this::delete, StationPermission.MEMBER_MANAGE_TAGS);

        routes.get(
                prefix + "/tags/{id}/members",
                this::getMembers,
                StationPermission.MEMBER_MANAGE_TAGS,
                StationPermission.INVENTORY_READ);
        routes.put(prefix + "/tags/{id}/members", this::setMembers, StationPermission.MEMBER_MANAGE_TAGS);

        routes.get(prefix + "/station-members/{memberId}/tags", this::getMemberTags, StationPermission.MEMBER_READ);

        routes.post(prefix + "/tags/{id}/convert-to-group", this::convertToGroup, StationPermission.MEMBER_MANAGE_TAGS);
    }

    // -- Tags --

    private MemberWithName toMemberWithName(StationMember m) {
        return MemberWithName.from(m, accountRepository, memberIdentityFactory);
    }

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
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, tagService::findById, UserTag::stationId);
        var request = ctx.bodyAsClass(TagRequest.class);
        if (isBlank(request.name())) {
            throw new BadRequestResponse("name is required");
        }
        if (!tagService.update(id, request.name(), request.color(), request.visible(), request.position())) {
            throw new NotFoundResponse();
        }
    }

    // -- Tag Members --

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
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, tagService::findById, UserTag::stationId);
        if (tagService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/tags/{id}/members",
            methods = HttpMethod.GET,
            summary = "Get members of a tag with name and email",
            tags = {"User Tags"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void getMembers(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, tagService::findById, UserTag::stationId);
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
        int tagId = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, tagId, tagService::findById, UserTag::stationId);
        var request = ctx.bodyAsClass(SetMembersRequest.class);
        List<Integer> memberIds = request.memberIds() != null ? request.memberIds() : List.of();
        tagService.setMembers(tagId, memberIds);
        ctx.json(tagService.findMembers(tagId).stream()
                .map(this::toMemberWithName)
                .toList());
    }

    // -- Convert to Group --

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/tags",
            methods = HttpMethod.GET,
            summary = "Get tags for a member",
            tags = {"User Tags"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = UserTag[].class)))
    private void getMemberTags(Context ctx) {
        int memberId = pathInt(ctx, "memberId");
        requireOwnedMember(ctx, memberId);
        ctx.json(tagService.findTagsForMember(memberId));
    }

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
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, tagService::findById, UserTag::stationId);
        tagService.convertToGroup(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Request/Response records --

    public record TagRequest(String name, String color, boolean visible, int position) {}

    @OpenApiName("TagSetMembersRequest")
    public record SetMembersRequest(List<Integer> memberIds) {}
}
