/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessGrant;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService.EntryAudience;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.knowledgebase.service.KbGuards.requireLevel;
import static dev.chojo.ember.feature.knowledgebase.service.KbGuards.requireOwnedFile;
import static dev.chojo.ember.feature.knowledgebase.service.KbGuards.requireOwnedFolder;

/**
 * Who may see a knowledge-base folder or file: the member-facing access restrictions and the
 * per-entity overrides of the station's public knowledge-base mode.
 */
@Singleton
public class KnowledgeBaseAccessRoutes implements Routes {

    private final KnowledgeBaseService service;
    private final KbAccessService accessService;
    private final KnowledgeBaseFederationService federationService;

    @Inject
    public KnowledgeBaseAccessRoutes(
            KnowledgeBaseService service,
            KbAccessService accessService,
            KnowledgeBaseFederationService federationService) {
        this.service = service;
        this.accessService = accessService;
        this.federationService = federationService;
    }

    /**
     * Maps each restriction through {@code extractor} and returns the non-null results.
     */
    private static <R> List<R> nonNullValues(List<KbAccessGrant> restrictions, Function<KbAccessGrant, R> extractor) {
        return restrictions.stream().map(extractor).filter(Objects::nonNull).toList();
    }

    private static KbRestrictionResponse toRestrictionResponse(List<KbAccessGrant> restrictions) {
        return new KbRestrictionResponse(
                nonNullValues(restrictions, KbAccessGrant::userType),
                nonNullValues(restrictions, KbAccessGrant::groupId),
                nonNullValues(restrictions, KbAccessGrant::tagId),
                nonNullValues(restrictions, KbAccessGrant::memberId),
                restrictions.stream()
                        .map(g -> new KbGrant(g.userType(), g.groupId(), g.tagId(), g.memberId(), g.level()))
                        .toList());
    }

    /**
     * Writes the audience of a folder or file, taking the levelled grants when the editor sent them
     * and the plain audience lists otherwise.
     */
    private void applyRestrictions(@Nullable Integer folderId, @Nullable Integer fileId, KbRestrictionRequest req) {
        if (req.grants() != null) {
            accessService.setGrants(
                    folderId,
                    fileId,
                    req.grants().stream()
                            .map(g -> new KbAccessService.GrantEntry(
                                    g.userType(), g.groupId(), g.tagId(), g.memberId(), g.level()))
                            .toList());
            return;
        }
        accessService.setRestrictions(folderId, fileId, toSelection(req));
    }

    private static RestrictionSelection toSelection(KbRestrictionRequest req) {
        return new RestrictionSelection(
                req.userTypes() == null
                        ? List.of()
                        : req.userTypes().stream().map(StationUserType::valueOf).toList(),
                req.groupIds(),
                req.tagIds(),
                req.memberIds(),
                null);
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/kb/folders/{id}/restrictions",
                this::getFolderRestrictions,
                StationPermission.KNOWLEDGE_FEDERATE);
        routes.put(
                prefix + "/kb/folders/{id}/restrictions",
                this::setFolderRestrictions,
                StationPermission.KNOWLEDGE_FEDERATE);
        routes.get(
                prefix + "/kb/files/{id}/restrictions",
                this::getFileRestrictions,
                StationPermission.KNOWLEDGE_FEDERATE);
        routes.put(
                prefix + "/kb/files/{id}/restrictions",
                this::setFileRestrictions,
                StationPermission.KNOWLEDGE_FEDERATE);

        routes.get(
                prefix + "/kb/files/{id}/public-visibility",
                this::getFilePublicVisibility,
                StationPermission.KNOWLEDGE_EDIT);
        routes.put(
                prefix + "/kb/files/{id}/public-visibility",
                this::setFilePublicVisibility,
                StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/kb/audiences", this::getAudiences, StationPermission.KNOWLEDGE_FEDERATE);
        routes.put(prefix + "/kb/audiences", this::setAudience, StationPermission.KNOWLEDGE_FEDERATE);

        routes.get(
                prefix + "/kb/folders/{id}/public-visibility",
                this::getFolderPublicVisibility,
                StationPermission.KNOWLEDGE_EDIT);
        routes.put(
                prefix + "/kb/folders/{id}/public-visibility",
                this::setFolderPublicVisibility,
                StationPermission.KNOWLEDGE_EDIT);
    }

    /**
     * Which partner stations each entry of this station's wiki is shared with.
     *
     * <p>Guarded by the knowledge federation right rather than the right to run the station's federation
     * settings: choosing who an article goes to is a thing done to an article, from the article.
     */
    @OpenApi(
            path = "/api/v1/kb/audiences",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EntryAudience[].class)))
    private void getAudiences(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(federationService.findAudiences(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/kb/audiences",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AudienceRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void setAudience(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(AudienceRequest.class);
        federationService.setAudience(
                session.stationId(),
                req.fileId(),
                req.folderId(),
                req.shared(),
                req.everyStation() ? ShareScope.ALL_PARTNERS : ShareScope.SPECIFIC,
                req.partnerIds() != null ? req.partnerIds() : List.of());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record AudienceRequest(
            Integer fileId, Integer folderId, boolean shared, boolean everyStation, List<Integer> partnerIds) {}

    @OpenApi(
            path = "/api/v1/kb/folders/{id}/restrictions",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbRestrictionResponse.class)))
    private void getFolderRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        ctx.json(toRestrictionResponse(accessService.findRestrictions(id, null)));
    }

    @OpenApi(
            path = "/api/v1/kb/folders/{id}/restrictions",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = KbRestrictionRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbRestrictionResponse.class)))
    private void setFolderRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.MANAGE);
        applyRestrictions(id, null, ctx.bodyAsClass(KbRestrictionRequest.class));
        ctx.json(toRestrictionResponse(accessService.findRestrictions(id, null)));
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/restrictions",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbRestrictionResponse.class)))
    private void getFileRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        ctx.json(toRestrictionResponse(accessService.findRestrictions(null, id)));
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/restrictions",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = KbRestrictionRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbRestrictionResponse.class)))
    private void setFileRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.MANAGE);
        applyRestrictions(null, id, ctx.bodyAsClass(KbRestrictionRequest.class));
        ctx.json(toRestrictionResponse(accessService.findRestrictions(null, id)));
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/public-visibility",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicVisibilityResponse.class)))
    private void getFilePublicVisibility(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        ctx.json(new PublicVisibilityResponse(
                accessService.findPublicVisibility(null, id).orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/public-visibility",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PublicVisibilityRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicVisibilityResponse.class)))
    private void setFilePublicVisibility(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.MANAGE);
        var req = ctx.bodyAsClass(PublicVisibilityRequest.class);
        if (req.visible() == null) {
            accessService.removePublicVisibility(null, id);
        } else {
            accessService.setPublicVisibility(null, id, req.visible());
        }
        ctx.json(new PublicVisibilityResponse(req.visible()));
    }

    @OpenApi(
            path = "/api/v1/kb/folders/{id}/public-visibility",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicVisibilityResponse.class)))
    private void getFolderPublicVisibility(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        ctx.json(new PublicVisibilityResponse(
                accessService.findPublicVisibility(id, null).orElse(null)));
    }

    @OpenApi(
            path = "/api/v1/kb/folders/{id}/public-visibility",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PublicVisibilityRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = PublicVisibilityResponse.class)))
    private void setFolderPublicVisibility(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.MANAGE);
        var req = ctx.bodyAsClass(PublicVisibilityRequest.class);
        if (req.visible() == null) {
            accessService.removePublicVisibility(id, null);
        } else {
            accessService.setPublicVisibility(id, null, req.visible());
        }
        ctx.json(new PublicVisibilityResponse(req.visible()));
    }

    /**
     * The audience of a folder or file. {@code grants} carries the same audience with a level per
     * entry and wins when present; the flat lists remain for callers that only set an audience.
     */
    public record KbRestrictionRequest(
            List<String> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            List<GrantRequest> grants) {}

    public record GrantRequest(
            StationUserType userType, Integer groupId, Integer tagId, Integer memberId, KbAccessLevel level) {}

    /**
     * One audience of a folder or file and what it may do. Exactly one of the audience fields is
     * set; a missing level leaves the station permission in charge.
     */
    public record KbGrant(
            @Nullable StationUserType userType,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            @Nullable Integer memberId,
            @Nullable KbAccessLevel level) {}

    public record KbRestrictionResponse(
            List<StationUserType> userTypes,
            List<Integer> groupIds,
            List<Integer> tagIds,
            List<Integer> memberIds,
            List<KbGrant> grants) {}

    public record PublicVisibilityRequest(Boolean visible) {}

    public record PublicVisibilityResponse(@Nullable Boolean visible) {}
}
