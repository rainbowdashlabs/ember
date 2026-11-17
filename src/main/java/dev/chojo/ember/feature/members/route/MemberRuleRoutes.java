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
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOperatorOption;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.RuleOwners;
import dev.chojo.ember.feature.members.entity.RulePreview;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberRuleEditService;
import dev.chojo.ember.feature.members.service.PrivateTags;
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

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * The rules groups and tags take their members from: reading, saving, trying out and removing one, and the
 * operators a check can use.
 */
@Singleton
public class MemberRuleRoutes implements Routes {
    private final MemberRuleEditService ruleEdits;
    private final MemberGroupService groupService;
    private final UserTagService tagService;
    private final PrivateTags privateTags;

    @Inject
    public MemberRuleRoutes(
            MemberRuleEditService ruleEdits,
            MemberGroupService groupService,
            UserTagService tagService,
            PrivateTags privateTags) {
        this.ruleEdits = ruleEdits;
        this.groupService = groupService;
        this.tagService = tagService;
        this.privateTags = privateTags;
    }

    /**
     * A rule as the builder sends it.
     *
     * @param rule the root block of the rule
     */
    public record MemberRuleRequest(RuleNode rule) {}

    /**
     * The rule of a group or tag.
     *
     * @param rule the root block of the rule, or null for a group or tag kept by hand
     */
    public record MemberRuleResponse(@Nullable RuleNode rule) {}

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/member-rules/operators",
                this::operators,
                StationPermission.MEMBER_MANAGE_GROUP,
                StationPermission.MEMBER_MANAGE_TAGS);

        routes.get(prefix + "/member-rules", this::owners, StationPermission.USER);
        routes.get(prefix + "/groups/{id}/rule", this::getGroupRule, StationPermission.MEMBER_MANAGE_GROUP);
        routes.put(prefix + "/groups/{id}/rule", this::saveGroupRule, StationPermission.MEMBER_MANAGE_GROUP);
        routes.delete(prefix + "/groups/{id}/rule", this::removeGroupRule, StationPermission.MEMBER_MANAGE_GROUP);
        routes.post(
                prefix + "/groups/{id}/rule/preview", this::previewGroupRule, StationPermission.MEMBER_MANAGE_GROUP);

        routes.get(prefix + "/tags/{id}/rule", this::getTagRule, StationPermission.MEMBER_MANAGE_TAGS);
        routes.put(prefix + "/tags/{id}/rule", this::saveTagRule, StationPermission.MEMBER_MANAGE_TAGS);
        routes.delete(prefix + "/tags/{id}/rule", this::removeTagRule, StationPermission.MEMBER_MANAGE_TAGS);
        routes.post(prefix + "/tags/{id}/rule/preview", this::previewTagRule, StationPermission.MEMBER_MANAGE_TAGS);
    }

    private RuleOwner group(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, groupService::findById, MemberGroup::stationId);
        return RuleOwner.group(id);
    }

    private RuleOwner tag(Context ctx) {
        int id = pathInt(ctx, "id");
        UserTag tag = requireOwnedOrNotFound(ctx, id, tagService::findById, UserTag::stationId);
        if (!privateTags.visibleTo(StationSession.from(ctx), tag)) throw GeneralRefusal.NOT_HERE_OR_NOT_YOURS.raise();
        return RuleOwner.tag(id);
    }

    private static RuleNode ruleOf(Context ctx) {
        var request = ctx.bodyAsClass(MemberRuleRequest.class);
        if (request == null || request.rule() == null) throw MemberRefusal.RULE_NEEDS_A_BLOCK.raise();
        return request.rule();
    }

    private void respondRule(Context ctx, RuleOwner owner) {
        ctx.json(new MemberRuleResponse(ruleEdits.find(owner).orElse(null)));
    }

    private void respondSaved(Context ctx, RuleOwner owner) {
        ctx.json(new MemberRuleResponse(ruleEdits.save(StationSession.from(ctx), owner, ruleOf(ctx))));
    }

    private void respondRemoved(Context ctx, RuleOwner owner) {
        ruleEdits.remove(owner);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void respondPreview(Context ctx, RuleOwner owner) {
        ctx.json(ruleEdits.preview(StationSession.from(ctx), owner, ruleOf(ctx)));
    }

    @OpenApi(
            path = "/api/v1/member-rules/operators",
            methods = HttpMethod.GET,
            summary = "The operators a check of a group or tag rule can use, per subject and field type",
            tags = {"Member Rules"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RuleOperatorOption[].class)))
    private void operators(Context ctx) {
        ctx.json(MemberRuleEditService.operators());
    }

    @OpenApi(
            path = "/api/v1/member-rules",
            methods = HttpMethod.GET,
            summary = "The groups and tags of the station that take their members from a rule",
            tags = {"Member Rules"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RuleOwners.class)))
    private void owners(Context ctx) {
        ctx.json(ruleEdits.owners(StationSession.from(ctx)));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/rule",
            methods = HttpMethod.GET,
            summary = "The rule a group takes its members from, if any",
            tags = {"Member Rules"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberRuleResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getGroupRule(Context ctx) {
        respondRule(ctx, group(ctx));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/rule",
            methods = HttpMethod.PUT,
            summary = "Save the rule a group takes its members from; the group is filled by it from then on",
            tags = {"Member Rules"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberRuleRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberRuleResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void saveGroupRule(Context ctx) {
        respondSaved(ctx, group(ctx));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/rule",
            methods = HttpMethod.DELETE,
            summary = "Remove a group's rule; it keeps its members and is kept by hand from then on",
            tags = {"Member Rules"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void removeGroupRule(Context ctx) {
        respondRemoved(ctx, group(ctx));
    }

    @OpenApi(
            path = "/api/v1/groups/{id}/rule/preview",
            methods = HttpMethod.POST,
            summary = "Who a rule would put into a group today, without saving it",
            tags = {"Member Rules"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberRuleRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RulePreview.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void previewGroupRule(Context ctx) {
        respondPreview(ctx, group(ctx));
    }

    @OpenApi(
            path = "/api/v1/tags/{id}/rule",
            methods = HttpMethod.GET,
            summary = "The rule a tag takes its members from, if any",
            tags = {"Member Rules"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberRuleResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getTagRule(Context ctx) {
        respondRule(ctx, tag(ctx));
    }

    @OpenApi(
            path = "/api/v1/tags/{id}/rule",
            methods = HttpMethod.PUT,
            summary = "Save the rule a tag takes its members from; the tag is filled by it from then on",
            tags = {"Member Rules"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberRuleRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberRuleResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void saveTagRule(Context ctx) {
        respondSaved(ctx, tag(ctx));
    }

    @OpenApi(
            path = "/api/v1/tags/{id}/rule",
            methods = HttpMethod.DELETE,
            summary = "Remove a tag's rule; it keeps its members and is kept by hand from then on",
            tags = {"Member Rules"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void removeTagRule(Context ctx) {
        respondRemoved(ctx, tag(ctx));
    }

    @OpenApi(
            path = "/api/v1/tags/{id}/rule/preview",
            methods = HttpMethod.POST,
            summary = "Who a rule would put into a tag today, without saving it",
            tags = {"Member Rules"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MemberRuleRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = RulePreview.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void previewTagRule(Context ctx) {
        respondPreview(ctx, tag(ctx));
    }
}
