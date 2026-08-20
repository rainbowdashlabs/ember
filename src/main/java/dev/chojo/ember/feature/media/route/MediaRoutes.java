/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * The station media library.
 *
 * <p>Three permissions open the whole library, because three features author content with it:
 * pages, news and the knowledge base. Anyone who may log in may upload and pick from what they
 * uploaded themselves, which is what lets a board ticket carry a picture. Deleting outright and
 * pruning stay with the page manager.
 *
 * <p>{@link #registerPageAliases} keeps the endpoints reachable under their former
 * {@code /pages/*} addresses for one release, so a frontend bundle that outlives the backend
 * restart does not break mid-deploy. Those aliases are registered from {@code PageRoutes} rather
 * than here, because they have to be declared ahead of the {@code /pages/{pid}} routes for
 * Javalin to read "files" as a literal segment instead of a page id.
 */
@Singleton
public class MediaRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(MediaRoutes.class);

    /**
     * Who may browse and organise the whole library: everyone who authors content with it.
     */
    private static final StationPermission[] CONTENT_PERMISSIONS = {
        StationPermission.PAGE_EDIT, StationPermission.NEWS_EDIT, StationPermission.KNOWLEDGE_EDIT
    };

    private final MediaLibraryService media;
    private final Api apiConfig;

    @Inject
    public MediaRoutes(MediaLibraryService media, Api apiConfig) {
        this.media = media;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        registerLibrary(routes, prefix + "/media");
    }

    /**
     * Registers the library under the addresses it had before it was a library. Called from
     * {@code PageRoutes} so the literal segments land ahead of {@code /pages/{pid}}.
     */
    public void registerPageAliases(JavalinDefaultRoutingApi routes, String prefix) {
        registerLibrary(routes, prefix + "/pages");
    }

    private void registerLibrary(JavalinDefaultRoutingApi routes, String base) {
        routes.get(base + "/files", this::listFiles, StationPermission.LOGIN);
        routes.post(base + "/files", this::upload, StationPermission.LOGIN);
        routes.post(base + "/files/prune", this::pruneFiles, StationPermission.PAGE_MANAGER);
        routes.put(base + "/files/{fileId}", this::updateFileMeta, CONTENT_PERMISSIONS);
        routes.delete(base + "/files/{fileId}", this::deleteFile, StationPermission.LOGIN);
        routes.put(base + "/files/{fileId}/folder", this::moveFileFolder, CONTENT_PERMISSIONS);
        routes.post(base + "/files/{fileId}/tags/{tagId}", this::assignTag, CONTENT_PERMISSIONS);
        routes.delete(base + "/files/{fileId}/tags/{tagId}", this::unassignTag, CONTENT_PERMISSIONS);
        routes.get(base + "/folders", this::listFolders, CONTENT_PERMISSIONS);
        routes.post(base + "/folders", this::createFolder, CONTENT_PERMISSIONS);
        routes.put(base + "/folders/{folderId}", this::updateFolder, CONTENT_PERMISSIONS);
        routes.delete(base + "/folders/{folderId}", this::deleteFolder, CONTENT_PERMISSIONS);
        routes.get(base + "/tags", this::listTags, CONTENT_PERMISSIONS);
        routes.post(base + "/tags", this::createTag, CONTENT_PERMISSIONS);
        routes.put(base + "/tags/{tagId}", this::updateTag, CONTENT_PERMISSIONS);
        routes.delete(base + "/tags/{tagId}", this::deleteTag, CONTENT_PERMISSIONS);
    }

    /**
     * Whether the caller authors content and may therefore see everything the station has, as
     * opposed to only what they uploaded themselves.
     */
    private static boolean browsesWholeLibrary(UserSession session) {
        for (var permission : CONTENT_PERMISSIONS) {
            if (session.hasPermission(permission)) return true;
        }
        return false;
    }

    private static int requireMember(UserSession session) {
        if (session.member() == null) throw new ForbiddenResponse("Not a station member");
        return session.member().id();
    }

    private void listFiles(Context ctx) {
        var session = UserSession.from(ctx);
        if (browsesWholeLibrary(session)) {
            ctx.json(media.listLibrary(session.stationId()));
            return;
        }
        ctx.json(media.listOwnUploads(session.stationId(), requireMember(session)));
    }

    private void upload(Context ctx) {
        var session = UserSession.from(ctx);
        int memberId = requireMember(session);
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("file is required");
        if (file.size() > apiConfig.maxUploadSizeBytes()) throw new BadRequestResponse("File too large");
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            var stored = media.upload(session.stationId(), null, memberId, file.filename(), file.contentType(), data);
            ctx.status(HttpStatus.CREATED).json(stored);
        } catch (StorageQuotaService.StorageQuotaExceededException | IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to upload media file", e);
            throw new BadRequestResponse("Failed to upload file");
        }
    }

    /**
     * Deleting bytes cannot be a per-owner act, so what this does depends on who asks. A page
     * manager removes the file outright. Anybody else may only withdraw their own upload, which
     * takes the file with it once nobody claims it and nothing points at it.
     */
    private void deleteFile(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = pathInt(ctx, "fileId");
        requireOwnedOrNotFound(ctx, fileId, media::findFile, StationFile::stationId);

        if (session.hasPermission(StationPermission.PAGE_MANAGER)) {
            if (!media.deleteFile(fileId)) throw new NotFoundResponse();
            ctx.status(HttpStatus.NO_CONTENT);
            return;
        }
        int memberId = requireMember(session);
        if (!media.mayRelease(fileId, memberId)) {
            throw new ForbiddenResponse("Only the members who uploaded a file may remove it");
        }
        media.releaseUpload(fileId, memberId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void pruneFiles(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(new PruneResult(media.pruneUnusedFiles(session.stationId())));
    }

    private void updateFileMeta(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = pathInt(ctx, "fileId");
        var body = ctx.bodyAsClass(FileMetaRequest.class);
        if (!media.updateFileMeta(session.stationId(), fileId, body.altText(), body.description())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void moveFileFolder(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = pathInt(ctx, "fileId");
        var body = ctx.bodyAsClass(MoveFileRequest.class);
        if (!media.moveFileToFolder(session.stationId(), fileId, body.folderId())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listFolders(Context ctx) {
        ctx.json(media.listFolders(UserSession.from(ctx).stationId()));
    }

    private void createFolder(Context ctx) {
        var session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(FolderRequest.class);
        if (body.name() == null || body.name().isBlank()) throw new BadRequestResponse("name is required");
        var folder = media.createFolder(
                session.stationId(), body.parentId(), body.name(), body.sortOrder() != null ? body.sortOrder() : 0);
        ctx.status(HttpStatus.CREATED).json(folder);
    }

    private void updateFolder(Context ctx) {
        var session = UserSession.from(ctx);
        int folderId = pathInt(ctx, "folderId");
        var body = ctx.bodyAsClass(FolderRequest.class);
        if (!media.updateFolder(
                session.stationId(),
                folderId,
                body.parentId(),
                body.name(),
                body.sortOrder() != null ? body.sortOrder() : 0)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deleteFolder(Context ctx) {
        var session = UserSession.from(ctx);
        if (!media.deleteFolder(session.stationId(), pathInt(ctx, "folderId"))) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listTags(Context ctx) {
        ctx.json(media.listTags(UserSession.from(ctx).stationId()));
    }

    private void createTag(Context ctx) {
        var session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(TagRequest.class);
        if (body.name() == null || body.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.status(HttpStatus.CREATED).json(media.createTag(session.stationId(), body.name(), body.color()));
    }

    private void updateTag(Context ctx) {
        var session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(TagRequest.class);
        if (!media.updateTag(session.stationId(), pathInt(ctx, "tagId"), body.name(), body.color())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deleteTag(Context ctx) {
        var session = UserSession.from(ctx);
        if (!media.deleteTag(session.stationId(), pathInt(ctx, "tagId"))) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void assignTag(Context ctx) {
        var session = UserSession.from(ctx);
        if (!media.assignTag(session.stationId(), pathInt(ctx, "fileId"), pathInt(ctx, "tagId"))) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void unassignTag(Context ctx) {
        var session = UserSession.from(ctx);
        if (!media.unassignTag(session.stationId(), pathInt(ctx, "fileId"), pathInt(ctx, "tagId"))) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    record PruneResult(int removed) {}

    record FileMetaRequest(String altText, String description) {}

    record FolderRequest(Integer parentId, String name, Integer sortOrder) {}

    record TagRequest(String name, String color) {}

    record MoveFileRequest(Integer folderId) {}
}
