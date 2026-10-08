/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.MemberWithName;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberViewService;
import dev.chojo.ember.feature.members.service.PrivateTags;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
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
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Routes for managing user tags including CRUD operations on tag definitions
 * and assigning/removing tags to/from members.
 */
@Singleton
public class UserTagRoutes implements Routes {
    private final UserTagService tagService;
    private final StationMemberService memberService;
    private final MemberViewService memberViews;
    private final PrivateTags privateTags;

    @Inject
    public UserTagRoutes(
            UserTagService tagService,
            StationMemberService memberService,
            MemberViewService memberViews,
            PrivateTags privateTags) {
        this.tagService = tagService;
        this.memberService = memberService;
        this.memberViews = memberViews;
        this.privateTags = privateTags;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Asserts the member named in the path belongs to the caller's station. Answers 404 for a
     * member of another station, so the tags a stranger carries cannot be read or probed.
     */
    private void requireOwnedMember(Context ctx, int memberId) {
        requireOwnedOrNotFound(ctx, memberId, memberService::findById, StationMember::stationId);
    }

    /**
     * Asserts the tag named in the path belongs to the caller's station and is one the caller may
     * see. A private tag answers 404 to a caller who may not view members, the same as a tag of
     * another station, so its existence cannot be probed.
     */
    private void requireVisibleTag(Context ctx, int tagId) {
        UserTag tag = requireOwnedOrNotFound(ctx, tagId, tagService::findById, UserTag::stationId);
        if (!privateTags.visibleTo(StationSession.from(ctx), tag)) {
            throw GeneralRefusal.NOT_HERE_OR_NOT_YOURS.raise();
        }
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

    @OpenApi(
            path = "/api/v1/tags",
            methods = HttpMethod.GET,
            summary = "List tags for the current station",
            tags = {"User Tags"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = UserTag[].class)))
    private void list(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(privateTags.visibleTo(session, tagService.findByStation(session.stationId())));
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
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(TagRequest.class);
        if (isBlank(request.name())) {
            throw MemberRefusal.TAG_NAME_MISSING_ON_CREATE.raise();
        }
        requireMayChooseVisibility(session, request);
        ctx.status(HttpStatus.CREATED)
                .json(tagService.create(session.stationId(), request.name(), request.color(), request.visibility()));
    }

    /**
     * Refuses a private tag asked for by somebody who may not view members, who would lose sight of
     * the tag the moment it was saved.
     */
    private void requireMayChooseVisibility(StationSession session, TagRequest request) {
        if (request.visibility().restricted() && !privateTags.seenBy(session)) {
            throw GeneralRefusal.ROUTE_PERMISSION_MISSING.raise();
        }
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
        requireVisibleTag(ctx, id);
        var request = ctx.bodyAsClass(TagRequest.class);
        if (isBlank(request.name())) {
            throw MemberRefusal.TAG_NAME_MISSING_ON_CHANGE.raise();
        }
        requireMayChooseVisibility(StationSession.from(ctx), request);
        if (!tagService.update(id, request.name(), request.color(), request.visibility(), request.position())) {
            throw MemberRefusal.MEMBER_TAG_NOT_HERE_ON_CHANGE.raise();
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
        int id = pathInt(ctx, "id");
        requireVisibleTag(ctx, id);
        if (tagService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw MemberRefusal.MEMBER_TAG_NOT_HERE_ON_DELETE.raise();
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
        requireVisibleTag(ctx, id);
        ctx.json(tagService.findMembers(id).stream().map(memberViews::named).toList());
    }

    @OpenApi(
            path = "/api/v1/tags/{id}/members",
            methods = HttpMethod.PUT,
            summary = "Set members of a tag (replaces all existing memberships)",
            description =
                    "Provide the full list of member IDs. Existing members not in the list are removed, new ones are added.",
            tags = {"User Tags"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TagSetMembersRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberWithName[].class)))
    private void setMembers(Context ctx) {
        int tagId = pathInt(ctx, "id");
        requireVisibleTag(ctx, tagId);
        var request = ctx.bodyAsClass(TagSetMembersRequest.class);
        List<Integer> memberIds = request.memberIds() != null ? request.memberIds() : List.of();
        tagService.setMembers(tagId, memberIds);
        ctx.json(tagService.findMembers(tagId).stream().map(memberViews::named).toList());
    }

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
        requireVisibleTag(ctx, id);
        tagService.convertToGroup(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * @param color      the tag's colour, or {@code null} for none
     * @param visibility who sees the tag; {@link TagVisibility#PLAIN} where the request names none
     */
    public record TagRequest(
            String name, @Nullable String color, @Nullable TagVisibility visibility, int position) {
        public TagRequest {
            visibility = visibility != null ? visibility : TagVisibility.PLAIN;
        }

        /** Who sees the tag, {@link TagVisibility#PLAIN} where the request named none. */
        @Override
        public TagVisibility visibility() {
            return Objects.requireNonNull(visibility, "the constructor fills in a missing visibility");
        }
    }

    public record TagSetMembersRequest(List<Integer> memberIds) {}
}
