/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.StationFree;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.events.route.EventVisibility;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.feature.generator.service.TemplatePictureService;
import dev.chojo.ember.feature.media.entity.MediaContent;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Optional;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The picture of a document template, for every place a template is chosen: the template list, the
 * documents an appointment asks for, and generating documents. A picture holds no member data, so
 * whoever may choose a template there may see it, and whoever sees an appointment may see the
 * pictures of the documents it asks for.
 */
@Singleton
public class TemplatePictureRoutes implements Routes {
    private static final int DEFAULT_SIZE = 1024;

    private final TemplatePictureService pictures;
    private final EventVisibility visibility;
    private final EventRequirementService requirements;

    @Inject
    public TemplatePictureRoutes(
            TemplatePictureService pictures, EventVisibility visibility, EventRequirementService requirements) {
        this.pictures = pictures;
        this.visibility = visibility;
        this.requirements = requirements;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/document-templates/{id}/picture",
                this::stationPicture,
                StationPermission.DOCUMENT_TEMPLATE_EDIT,
                StationPermission.DOCUMENT_EDIT_MEMBER,
                StationPermission.EVENT_EDIT,
                StationPermission.EVENT_MANAGE_TEMPLATE);
        routes.get(
                prefix + "/events/{id}/documents-to-bring/{templateId}/picture",
                this::appointmentPicture,
                StationPermission.USER);
        routes.get(
                prefix + "/cluster/document-templates/{id}/picture",
                this::associationPicture,
                ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT);
    }

    @OpenApi(
            path = "/api/v1/document-templates/{id}/picture",
            methods = HttpMethod.GET,
            summary = "The first page of a template the station uses, drawn without a member",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "size", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void stationPicture(Context ctx) {
        var session = StationSession.from(ctx);
        send(ctx, pictures.forStation(session.stationId(), pathInt(ctx, "id"), sizeOf(ctx)));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/documents-to-bring/{templateId}/picture",
            methods = HttpMethod.GET,
            summary = "The first page of a document an appointment asks for, drawn without a member",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "id", type = Integer.class, required = true),
                @OpenApiParam(name = "templateId", type = Integer.class, required = true)
            },
            queryParams = @OpenApiParam(name = "size", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void appointmentPicture(Context ctx) {
        var session = StationSession.from(ctx);
        var event = visibility.requireVisibleEvent(session, pathInt(ctx, "id"));
        int templateId = pathInt(ctx, "templateId");
        boolean asked = requirements.forEvent(event.id()).stream()
                .anyMatch(template -> template.templateId() == templateId && !template.archived());
        if (!asked) throw DocumentRefusal.DOCUMENT_REQUIREMENT_NOT_REQUIRED.raise();
        send(ctx, pictures.forStation(session.stationId(), templateId, sizeOf(ctx)));
    }

    @OpenApi(
            path = "/api/v1/cluster/document-templates/{id}/picture",
            methods = HttpMethod.GET,
            summary = "The first page of an association's template, drawn without a member",
            tags = {"Documents"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            queryParams = @OpenApiParam(name = "size", type = Integer.class),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    @StationFree("a template of the association is looked up among the association's own, which the session names")
    private void associationPicture(Context ctx) {
        var association = UserSession.from(ctx).association(ClusterRefusal.NO_CLUSTER_CHOSEN_FOR_DOCUMENT_TEMPLATES);
        send(ctx, pictures.forOwner(association, pathInt(ctx, "id"), sizeOf(ctx)));
    }

    private static int sizeOf(Context ctx) {
        return ctx.queryParamAsClass("size", Integer.class).getOrDefault(DEFAULT_SIZE);
    }

    private static void send(Context ctx, Optional<MediaContent> picture) {
        var content = picture.orElseThrow(DocumentRefusal.DOCUMENT_THUMBNAIL_NOT_HERE::raise);
        ctx.contentType(content.contentType());
        ctx.result(content.data());
    }
}
