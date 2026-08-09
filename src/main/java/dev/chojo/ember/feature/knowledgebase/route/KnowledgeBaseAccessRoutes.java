/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessRestriction;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.requireOwnedFile;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.requireOwnedFolder;

/**
 * Who may see a knowledge-base folder or file: the member-facing access restrictions and the
 * per-entity overrides of the station's public knowledge-base mode.
 */
@Singleton
public class KnowledgeBaseAccessRoutes implements Routes {

    private final KnowledgeBaseService service;
    private final KbAccessService accessService;

    @Inject
    public KnowledgeBaseAccessRoutes(KnowledgeBaseService service, KbAccessService accessService) {
        this.service = service;
        this.accessService = accessService;
    }

    /**
     * Maps each restriction through {@code extractor} and returns the non-null results.
     */
    private static <R> List<R> nonNullValues(
            List<KbAccessRestriction> restrictions, Function<KbAccessRestriction, R> extractor) {
        return restrictions.stream().map(extractor).filter(Objects::nonNull).toList();
    }

    private static RestrictionResponse toRestrictionResponse(List<KbAccessRestriction> restrictions) {
        return new RestrictionResponse(
                nonNullValues(restrictions, KbAccessRestriction::userType),
                nonNullValues(restrictions, KbAccessRestriction::groupId),
                nonNullValues(restrictions, KbAccessRestriction::tagId),
                nonNullValues(restrictions, KbAccessRestriction::memberId));
    }

    private static RestrictionSelection toSelection(RestrictionRequest req) {
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
        routes.get(
                prefix + "/kb/folders/{id}/public-visibility",
                this::getFolderPublicVisibility,
                StationPermission.KNOWLEDGE_EDIT);
        routes.put(
                prefix + "/kb/folders/{id}/public-visibility",
                this::setFolderPublicVisibility,
                StationPermission.KNOWLEDGE_EDIT);
    }

    private void getFolderRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        ctx.json(toRestrictionResponse(accessService.findRestrictions(id, null)));
    }

    private void setFolderRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        accessService.setRestrictions(id, null, toSelection(ctx.bodyAsClass(RestrictionRequest.class)));
        ctx.json(toRestrictionResponse(accessService.findRestrictions(id, null)));
    }

    private void getFileRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        ctx.json(toRestrictionResponse(accessService.findRestrictions(null, id)));
    }

    private void setFileRestrictions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        accessService.setRestrictions(null, id, toSelection(ctx.bodyAsClass(RestrictionRequest.class)));
        ctx.json(toRestrictionResponse(accessService.findRestrictions(null, id)));
    }

    private void getFilePublicVisibility(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        ctx.json(new PublicVisibilityResponse(
                accessService.findPublicVisibility(null, id).orElse(null)));
    }

    private void setFilePublicVisibility(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        var req = ctx.bodyAsClass(PublicVisibilityRequest.class);
        if (req.visible() == null) {
            accessService.removePublicVisibility(null, id);
        } else {
            accessService.setPublicVisibility(null, id, req.visible());
        }
        ctx.json(new PublicVisibilityResponse(req.visible()));
    }

    private void getFolderPublicVisibility(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        ctx.json(new PublicVisibilityResponse(
                accessService.findPublicVisibility(id, null).orElse(null)));
    }

    private void setFolderPublicVisibility(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        var req = ctx.bodyAsClass(PublicVisibilityRequest.class);
        if (req.visible() == null) {
            accessService.removePublicVisibility(id, null);
        } else {
            accessService.setPublicVisibility(id, null, req.visible());
        }
        ctx.json(new PublicVisibilityResponse(req.visible()));
    }

    public record RestrictionRequest(
            List<String> userTypes, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {}

    public record RestrictionResponse(
            List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {}

    public record PublicVisibilityRequest(Boolean visible) {}

    public record PublicVisibilityResponse(Boolean visible) {}
}
