/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbTagService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.requireOwnedFile;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.requireOwnedFolder;

/**
 * Knowledge-base tagging: the station's tag vocabulary, the tags on a folder or file, and the
 * scope a tag spans in the tree.
 */
@Singleton
public class KnowledgeBaseTagRoutes implements Routes {

    private final KnowledgeBaseService service;
    private final KbTagService tagService;
    private final KbAccessService accessService;

    @Inject
    public KnowledgeBaseTagRoutes(
            KnowledgeBaseService service, KbTagService tagService, KbAccessService accessService) {
        this.service = service;
        this.tagService = tagService;
        this.accessService = accessService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/kb/tags", this::listTags, StationPermission.USER);
        routes.get(prefix + "/kb/tags/{name}/scope", this::getTagScope, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}/tags", this::getFileTags, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}/tags", this::setFileTags, StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/kb/folders/{id}/tags", this::getFolderTags, StationPermission.USER);
        routes.put(prefix + "/kb/folders/{id}/tags", this::setFolderTags, StationPermission.KNOWLEDGE_EDIT);
    }

    private void listTags(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(tagService.findTagsByStation(session.stationId()));
    }

    private void getTagScope(Context ctx) {
        var session = UserSession.from(ctx);
        String tagName = ctx.pathParam("name");
        int stationId = session.stationId();

        var matchingFiles = tagService.findFilesByTag(stationId, tagName);
        var allFolders = service.findAllFolders(stationId);

        if (!session.hasPermission(StationPermission.KNOWLEDGE_MANAGER)) {
            var access = accessService.memberAccess(
                    session.member().id(), session.member().userType());
            matchingFiles = matchingFiles.stream()
                    .filter(file -> accessService.canAccess(access, null, file.id()))
                    .toList();
        }

        var folderParent = new HashMap<Integer, Integer>();
        for (var folder : allFolders) {
            folderParent.put(folder.id(), folder.parentId());
        }

        var matchingFileIds = matchingFiles.stream().map(KbFile::id).toList();
        var ancestorFolderIds = new HashSet<Integer>();
        for (var file : matchingFiles) {
            Integer current = file.folderId();
            while (current != null && !ancestorFolderIds.contains(current)) {
                ancestorFolderIds.add(current);
                current = folderParent.get(current);
            }
        }

        ctx.json(new TagScopeResponse(matchingFileIds, new ArrayList<>(ancestorFolderIds)));
    }

    private void getFileTags(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        ctx.json(tagService.findFileTags(id));
    }

    private void setFileTags(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        var req = ctx.bodyAsClass(TagRequest.class);
        ctx.json(tagService.setFileTags(id, req.tags(), session.stationId()));
    }

    private void getFolderTags(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        ctx.json(tagService.findFolderTags(id));
    }

    private void setFolderTags(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        var req = ctx.bodyAsClass(TagRequest.class);
        ctx.json(tagService.setFolderTags(id, req.tags(), session.stationId()));
    }

    public record TagRequest(List<String> tags) {}

    public record TagScopeResponse(List<Integer> matchingFileIds, List<Integer> ancestorFolderIds) {}
}
