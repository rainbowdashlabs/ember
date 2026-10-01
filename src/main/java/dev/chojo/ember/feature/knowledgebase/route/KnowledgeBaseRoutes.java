/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.route.SaveBlocksRequest;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileVersion;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.KbRefusalReason;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbBrowseService;
import dev.chojo.ember.feature.knowledgebase.service.KbBrowseService.BrowseResponse;
import dev.chojo.ember.feature.knowledgebase.service.KbBulkService;
import dev.chojo.ember.feature.knowledgebase.service.KbBulkService.BulkOutcome;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFilePictureService;
import dev.chojo.ember.feature.knowledgebase.service.KbGuards;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.knowledgebase.service.KbImageService;
import dev.chojo.ember.feature.knowledgebase.service.KbMoveService;
import dev.chojo.ember.feature.knowledgebase.service.KbMoveService.MovePreview;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfExportService;
import dev.chojo.ember.feature.knowledgebase.service.KbPresentationService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KbTrashService;
import dev.chojo.ember.feature.knowledgebase.service.KbTrashService.DeleteImpact;
import dev.chojo.ember.feature.knowledgebase.service.KbTrashService.RestoreResult;
import dev.chojo.ember.feature.knowledgebase.service.KbTrashService.TrashView;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.util.PandocConverter;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.knowledgebase.service.KbGuards.requireLevel;
import static dev.chojo.ember.feature.knowledgebase.service.KbGuards.requireOwnedFile;
import static dev.chojo.ember.feature.knowledgebase.service.KbGuards.requireOwnedFolder;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Local knowledge-base routes: folders, files and their content, version history, related files,
 * search, browsing and the images embedded in knowledge-base content.
 */
@Singleton
public class KnowledgeBaseRoutes implements Routes {
    private static final Logger log = getLogger(KnowledgeBaseRoutes.class);
    private static final long MAX_UPLOAD_SIZE = 50 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final KnowledgeBaseService service;
    private final KbContentService contentService;
    private final KbSearchService searchService;
    private final KbAccessService accessService;
    private final KbPresentationService presentationService;
    private final KbAuthorNameService authorNameService;
    private final KnowledgeBaseFederationService federationService;
    private final KbIconService iconService;
    private final KbImageService imageService;
    private final KbFilePictureService pictureService;
    private final KbPdfExportService pdfExportService;
    private final KbMoveService moveService;
    private final KbBulkService bulkService;
    private final KbTrashService trashService;
    private final KbBrowseService browseService;

    @Inject
    public KnowledgeBaseRoutes(
            KnowledgeBaseService service,
            KbContentService contentService,
            KbSearchService searchService,
            KbAccessService accessService,
            KbPresentationService presentationService,
            KbAuthorNameService authorNameService,
            KnowledgeBaseFederationService federationService,
            KbIconService iconService,
            KbImageService imageService,
            KbFilePictureService pictureService,
            KbPdfExportService pdfExportService,
            KbMoveService moveService,
            KbBulkService bulkService,
            KbTrashService trashService,
            KbBrowseService browseService) {
        this.moveService = moveService;
        this.trashService = trashService;
        this.bulkService = bulkService;
        this.service = service;
        this.contentService = contentService;
        this.searchService = searchService;
        this.accessService = accessService;
        this.presentationService = presentationService;
        this.authorNameService = authorNameService;
        this.federationService = federationService;
        this.iconService = iconService;
        this.imageService = imageService;
        this.pictureService = pictureService;
        this.pdfExportService = pdfExportService;
        this.browseService = browseService;
    }

    private static String detectPandocFormat(String filename, String mimeType) {
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".docx")) return "docx";
            if (lower.endsWith(".odt")) return "odt";
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "html";
            if (lower.endsWith(".rtf")) return "rtf";
            if (lower.endsWith(".epub")) return "epub";
            if (lower.endsWith(".tex") || lower.endsWith(".latex")) return "latex";
        }
        if (mimeType != null) {
            if (mimeType.contains("wordprocessingml") || mimeType.contains("msword")) return "docx";
            if (mimeType.contains("opendocument.text")) return "odt";
            if (mimeType.equals("text/html")) return "html";
            if (mimeType.equals("text/rtf") || mimeType.equals("application/rtf")) return "rtf";
        }
        return null;
    }

    /**
     * Reads the required multipart upload named {@code file}, answering {@code 400} when it is
     * missing or larger than the maximum upload size.
     */
    private static UploadedFile requireUpload(Context ctx) {
        var file = ctx.uploadedFile("file");
        if (file == null) throw Refusal.KB_UPLOAD_MISSING_FILE.raise();
        if (file.size() > MAX_UPLOAD_SIZE) throw Refusal.KB_UPLOAD_TOO_LARGE.raise();
        return file;
    }

    private static Integer optionalFolderId(Context ctx, String param) {
        return ctx.queryParam(param) != null
                ? ctx.queryParamAsClass(param, Integer.class).get()
                : null;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/kb/folders", this::listFolders, StationPermission.USER);
        routes.get(prefix + "/kb/folders/tree", this::folderTree, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/folders", this::createFolder, StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/kb/folders/{id}", this::getFolder, StationPermission.USER);
        routes.put(prefix + "/kb/folders/{id}/parent", this::moveFolder, StationPermission.KNOWLEDGE_EDIT);
        routes.put(prefix + "/kb/folders/{id}", this::updateFolder, StationPermission.KNOWLEDGE_EDIT);
        routes.delete(prefix + "/kb/folders/{id}", this::deleteFolder, StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/files", this::listFiles, StationPermission.USER);
        routes.get(prefix + "/kb/files/recent", this::listRecentFiles, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}", this::getFile, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}", this::updateFile, StationPermission.KNOWLEDGE_EDIT);
        routes.put(prefix + "/kb/files/{id}/folder", this::moveFile, StationPermission.KNOWLEDGE_EDIT);
        routes.delete(prefix + "/kb/files/{id}", this::deleteFile, StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/move/preview", this::movePreview, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/bulk/move", this::bulkMove, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/bulk/tags", this::bulkTags, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/bulk/delete", this::bulkDelete, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/bulk/delete/impact", this::bulkDeleteImpact, StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/trash", this::listTrash, StationPermission.KNOWLEDGE_EDIT);
        routes.delete(prefix + "/kb/trash", this::emptyTrash, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/trash/folders/{id}/restore", this::restoreFolder, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/trash/files/{id}/restore", this::restoreFile, StationPermission.KNOWLEDGE_EDIT);
        routes.delete(prefix + "/kb/trash/folders/{id}", this::purgeFolder, StationPermission.KNOWLEDGE_EDIT);
        routes.delete(prefix + "/kb/trash/files/{id}", this::purgeFile, StationPermission.KNOWLEDGE_EDIT);

        routes.post(prefix + "/kb/files/markdown", this::createMarkdownFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/youtube", this::createYoutubeFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/upload", this::uploadFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/import-document", this::importDocument, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/link", this::createLinkFile, StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/files/{id}/content", this::getFileContent, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}/picture", this::getFilePicture, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}/html", this::getMarkdownHtml, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}/content", this::updateMarkdownContent, StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/kb/files/{id}/blocks", this::getBlocks, StationPermission.LOGIN);
        routes.put(prefix + "/kb/files/{id}/blocks", this::saveBlocks, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/{id}/blocks/enable", this::enableBlocks, StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/files/{id}/pdf", this::getPdfExport, StationPermission.USER);

        routes.get(prefix + "/kb/files/{id}/original", this::getOriginalFile, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}/original", this::reuploadOriginal, StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/files/{id}/versions", this::listVersions, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}/versions/{version}", this::getVersion, StationPermission.USER);
        routes.post(
                prefix + "/kb/files/{id}/versions/{version}/revert",
                this::revertToVersion,
                StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/files/{id}/related", this::getRelatedFiles, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}/related", this::setRelatedFiles, StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/search", this::search, StationPermission.USER);
        routes.get(prefix + "/kb/browse", this::browse, StationPermission.USER);

        routes.get(prefix + "/kb/folders/{id}/icon", this::getFolderIcon, StationPermission.USER);
        routes.post(prefix + "/kb/folders/{id}/icon", this::uploadFolderIcon, StationPermission.KNOWLEDGE_EDIT);

        routes.post(prefix + "/kb/files/{id}/images", this::uploadKbImage, StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/kb/images/{imageId}", this::getKbImage, StationPermission.USER);
    }

    @OpenApi(
            path = "/api/v1/kb/folders",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFolder[].class)))
    private void listFolders(Context ctx) {
        var session = UserSession.from(ctx);
        Integer parentId = optionalFolderId(ctx, "parentId");
        var folders = service.findFolders(session.stationId(), parentId);
        var levels = accessService.childLevels(
                KbGuards.accessOf(ctx, accessService),
                parentId,
                folders.stream()
                        .map(folder -> new KbAccessService.ChildNode(folder.id(), folder.restrictionMode()))
                        .toList(),
                List.of());
        ctx.json(folders.stream()
                .filter(folder -> levels.folders()
                        .getOrDefault(folder.id(), KbAccessLevel.NONE)
                        .covers(KbAccessLevel.READ))
                .toList());
    }

    /**
     * Creating something inside a folder is a write to that folder, so a member whose grant there
     * is read-only cannot drop a file into it by naming it in the request.
     */
    private void requireWriteInFolder(Context ctx, Integer folderId) {
        if (folderId == null) return;
        requireLevel(ctx, accessService, folderId, null, KbAccessLevel.WRITE);
    }

    @OpenApi(
            path = "/api/v1/kb/folders",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FolderRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFolder.class)))
    private void createFolder(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(FolderRequest.class);
        if (req.name() == null || req.name().isBlank()) throw Refusal.KB_FOLDER_NEEDS_A_NAME.raise();
        requireWriteInFolder(ctx, req.parentId());
        ctx.json(service.createFolder(
                session.stationId(),
                req.parentId(),
                req.name().trim(),
                req.description() != null ? req.description() : "",
                session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/kb/folders/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFolder.class)))
    private void getFolder(Context ctx) {
        int id = pathInt(ctx, "id");
        var folder = requireOwnedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.READ);
        ctx.json(folder);
    }

    @OpenApi(
            path = "/api/v1/kb/folders/{id}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FolderRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFolder.class)))
    private void updateFolder(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.WRITE);
        var req = ctx.bodyAsClass(FolderRequest.class);
        if (!service.updateFolder(
                id,
                req.name(),
                req.description() != null ? req.description() : "",
                req.iconUrl(),
                req.position() != null ? req.position() : 0)) {
            throw Refusal.KB_FOLDER_NOT_CHANGED.raise();
        }
        service.findFolder(id).ifPresentOrElse(ctx::json, () -> {
            throw Refusal.KB_FOLDER_NOT_HERE_AFTER_CHANGE.raise();
        });
    }

    /**
     * Puts a folder in the trash, with everything inside it.
     */
    @OpenApi(path = "/api/v1/kb/folders/{id}", methods = HttpMethod.DELETE, responses = @OpenApiResponse(status = "204"))
    private void deleteFolder(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.MANAGE);
        if (!trashService.deleteFolder(id, memberIdOf(session))) throw Refusal.KB_FOLDER_NOT_TRASHED.raise();
        ctx.status(204);
    }

    /**
     * Every folder of the station the caller may see, with what they may do in each, so a picker
     * can show the whole tree and grey out what it would refuse rather than offer it and fail.
     */
    @OpenApi(
            path = "/api/v1/kb/folders/tree",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FolderTreeEntry[].class)))
    private void folderTree(Context ctx) {
        var session = UserSession.from(ctx);
        var access = KbGuards.accessOf(ctx, accessService);
        var folders = service.findAllFolders(session.stationId());
        var levels = accessService.treeLevels(
                access,
                folders.stream()
                        .map(folder ->
                                new KbAccessService.TreeNode(folder.id(), folder.parentId(), folder.restrictionMode()))
                        .toList());
        ctx.json(folders.stream()
                .map(folder -> new FolderTreeEntry(
                        folder.id(),
                        folder.parentId(),
                        folder.name(),
                        levels.getOrDefault(folder.id(), KbAccessLevel.NONE)))
                .filter(entry -> entry.level() != KbAccessLevel.NONE)
                .toList());
    }

    /**
     * Moves a folder, with everything under it, into another folder of the station.
     *
     * <p>A refusal is part of the answer rather than a failed request: the reader picked a folder
     * that turns out to hold a folder of the same name, or that lies inside the one being moved,
     * and the screen has to say which of those it was.
     */
    @OpenApi(
            path = "/api/v1/kb/folders/{id}/parent",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MoveFolderRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MoveResponse.class)))
    private void moveFolder(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        var req = ctx.bodyAsClass(MoveFolderRequest.class);
        requireUsableTarget(ctx, req.parentId());
        ctx.json(MoveResponse.of(moveService.moveFolder(
                KbGuards.accessOf(ctx, accessService), session.stationId(), id, req.parentId())));
    }

    /**
     * Moves an article into another folder of the station.
     */
    @OpenApi(
            path = "/api/v1/kb/files/{id}/folder",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MoveFileRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MoveResponse.class)))
    private void moveFile(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        var req = ctx.bodyAsClass(MoveFileRequest.class);
        requireUsableTarget(ctx, req.folderId());
        ctx.json(MoveResponse.of(
                moveService.moveFile(KbGuards.accessOf(ctx, accessService), session.stationId(), id, req.folderId())));
    }

    /**
     * Answers {@code 404} when the folder a move aims at is not one the caller may put anything
     * into. The target failing is the whole request failing, unlike a single entry of a selection.
     */
    private void requireUsableTarget(Context ctx, Integer targetFolderId) {
        var session = UserSession.from(ctx);
        var problem =
                moveService.checkTarget(KbGuards.accessOf(ctx, accessService), session.stationId(), targetFolderId);
        if (problem != null) throw Refusal.KB_MOVE_TARGET_NOT_USABLE.raise();
    }

    /**
     * How far an entry reaches now and how far it would reach in the folder a reader is about to
     * move it into. Read before the move, because a folder can publish what it is given.
     */
    @OpenApi(
            path = "/api/v1/kb/move/preview",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MovePreview.class)))
    private void movePreview(Context ctx) {
        var session = UserSession.from(ctx);
        Integer folderId = optionalFolderId(ctx, "folderId");
        Integer fileId = optionalFolderId(ctx, "fileId");
        if (folderId == null && fileId == null) throw Refusal.KB_MOVE_PREVIEW_NEEDS_AN_ENTRY.raise();
        if (folderId != null) requireOwnedFolder(ctx, service, folderId);
        else requireOwnedFile(ctx, service, fileId);
        ctx.json(moveService.preview(session.stationId(), folderId, fileId, optionalFolderId(ctx, "targetFolderId")));
    }

    @OpenApi(
            path = "/api/v1/kb/bulk/move",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BulkMoveRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BulkOutcome.class)))
    private void bulkMove(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(BulkMoveRequest.class);
        requireUsableTarget(ctx, req.targetFolderId());
        ctx.json(bulkService.move(
                KbGuards.accessOf(ctx, accessService),
                session.stationId(),
                req.folderIds() != null ? req.folderIds() : List.of(),
                req.fileIds() != null ? req.fileIds() : List.of(),
                req.targetFolderId()));
    }

    /**
     * Puts a marked selection in the trash. Nothing here is final, which is what lets one press
     * stand for twenty entries.
     */
    @OpenApi(
            path = "/api/v1/kb/bulk/delete",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BulkDeleteRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BulkOutcome.class)))
    private void bulkDelete(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(BulkDeleteRequest.class);
        ctx.json(bulkService.delete(
                KbGuards.accessOf(ctx, accessService),
                session.stationId(),
                session.member().id(),
                req.folderIds() != null ? req.folderIds() : List.of(),
                req.fileIds() != null ? req.fileIds() : List.of()));
    }

    /**
     * How much a marked selection would really take, folder contents counted, so the confirmation
     * can say the true number rather than the number of ticked boxes.
     */
    @OpenApi(
            path = "/api/v1/kb/bulk/delete/impact",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BulkDeleteRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DeleteImpact.class)))
    private void bulkDeleteImpact(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(BulkDeleteRequest.class);
        ctx.json(trashService.impactOf(
                session.stationId(),
                req.folderIds() != null ? req.folderIds() : List.of(),
                req.fileIds() != null ? req.fileIds() : List.of()));
    }

    /**
     * What the caller may take back out of the station's trash.
     *
     * <p>Reach decides, not the station permission: everyone who could have deleted an entry finds
     * it here, and nobody reads the name of something they were never allowed to open.
     */
    @OpenApi(
            path = "/api/v1/kb/trash",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TrashView.class)))
    private void listTrash(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(trashService.list(KbGuards.accessOf(ctx, accessService), session.stationId()));
    }

    /**
     * Clears out everything the caller sees in the trash, and with it the storage it was holding.
     */
    @OpenApi(
            path = "/api/v1/kb/trash",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = EmptyTrashResponse.class)))
    private void emptyTrash(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(
                new EmptyTrashResponse(trashService.empty(KbGuards.accessOf(ctx, accessService), session.stationId())));
    }

    @OpenApi(
            path = "/api/v1/kb/trash/folders/{id}/restore",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RestoreResult.class)))
    private void restoreFolder(Context ctx) {
        int id = trashedFolder(ctx);
        ctx.json(trashService.restoreFolder(id));
    }

    @OpenApi(
            path = "/api/v1/kb/trash/files/{id}/restore",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RestoreResult.class)))
    private void restoreFile(Context ctx) {
        int id = trashedFile(ctx);
        ctx.json(trashService.restoreFile(id));
    }

    @OpenApi(
            path = "/api/v1/kb/trash/folders/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void purgeFolder(Context ctx) {
        int id = trashedFolder(ctx);
        if (!trashService.purgeFolder(id)) throw Refusal.KB_FOLDER_NOT_PURGED.raise();
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/kb/trash/files/{id}",
            methods = HttpMethod.DELETE,
            responses = @OpenApiResponse(status = "204"))
    private void purgeFile(Context ctx) {
        int id = trashedFile(ctx);
        if (!trashService.purgeFile(id)) throw Refusal.KB_ARTICLE_NOT_PURGED.raise();
        ctx.status(204);
    }

    /**
     * Reads the folder a trash action names and asserts it is one of this station's, in the trash,
     * and one the caller may manage. The path a folder was deleted from is still there, so the
     * ordinary reach check answers this without knowing anything about deletion.
     */
    private int trashedFolder(Context ctx) {
        int id = pathInt(ctx, "id");
        KbGuards.requireOwnedTrashedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.MANAGE);
        return id;
    }

    private int trashedFile(Context ctx) {
        int id = pathInt(ctx, "id");
        KbGuards.requireOwnedTrashedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.MANAGE);
        return id;
    }

    /**
     * The member behind a session, or {@code null} for a session that holds station rights without
     * a member row of its own, which is what the trash then records as the deleting member.
     */
    private static Integer memberIdOf(UserSession session) {
        return session.member() == null ? null : session.member().id();
    }

    @OpenApi(
            path = "/api/v1/kb/bulk/tags",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BulkTagsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BulkOutcome.class)))
    private void bulkTags(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(BulkTagsRequest.class);
        ctx.json(bulkService.tag(
                KbGuards.accessOf(ctx, accessService),
                session.stationId(),
                req.folderIds() != null ? req.folderIds() : List.of(),
                req.fileIds() != null ? req.fileIds() : List.of(),
                req.addTags() != null ? req.addTags() : List.of(),
                req.removeTags() != null ? req.removeTags() : List.of()));
    }

    /**
     * The articles changed most recently, for a picker that has to show something before anything
     * has been typed into it. Filtered the way a listing is, so it cannot name an article the
     * reader may not open.
     */
    @OpenApi(
            path = "/api/v1/kb/files/recent",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SearchResultResponse[].class)))
    private void listRecentFiles(Context ctx) {
        var session = UserSession.from(ctx);
        var access = KbGuards.accessOf(ctx, accessService);
        int limit =
                Math.min(Math.max(ctx.queryParamAsClass("limit", Integer.class).getOrDefault(10), 1), 50);
        var found = service.findRecentFiles(session.stationId(), limit * 4);
        var readable = accessService.readableFiles(
                access, found.stream().map(KbAccessService.FileNode::of).toList());
        var visible = found.stream()
                .filter(file -> readable.contains(file.id()))
                .limit(limit)
                .toList();
        var folderPaths = service.findFolderPaths(visible.stream()
                .map(KbFile::folderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        ctx.json(visible.stream()
                .map(file ->
                        new SearchResultResponse(file, "", folderPaths.getOrDefault(file.folderId(), "/"), null, null))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/kb/files",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFileSummary[].class)))
    private void listFiles(Context ctx) {
        var session = UserSession.from(ctx);
        var files = service.findFiles(session.stationId(), optionalFolderId(ctx, "folderId"));
        var readable = accessService.readableFiles(
                KbGuards.accessOf(ctx, accessService),
                files.stream().map(KbAccessService.FileNode::of).toList());
        ctx.json(files.stream()
                .filter(file -> readable.contains(file.id()))
                .map(KbFileSummary::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FileResponse.class)))
    private void getFile(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        var level = accessService.explainLevel(KbGuards.accessOf(ctx, accessService), null, id);
        ctx.json(new FileResponse(
                file, authorNameService.resolveMemberName(file.createdBy()), level.level(), level.source()));
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FileUpdateRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFile.class)))
    private void updateFile(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        var req = ctx.bodyAsClass(FileUpdateRequest.class);
        if (!service.updateFile(
                id,
                req.name(),
                req.description() != null ? req.description() : "",
                req.iconUrl(),
                req.position() != null ? req.position() : 0)) {
            throw Refusal.KB_ARTICLE_NOT_CHANGED.raise();
        }
        service.findFile(id).ifPresentOrElse(ctx::json, () -> {
            throw Refusal.KB_ARTICLE_NOT_HERE_AFTER_CHANGE.raise();
        });
    }

    /**
     * Puts an article in the trash.
     */
    @OpenApi(path = "/api/v1/kb/files/{id}", methods = HttpMethod.DELETE, responses = @OpenApiResponse(status = "204"))
    private void deleteFile(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.MANAGE);
        if (!trashService.deleteFile(id, memberIdOf(session))) throw Refusal.KB_ARTICLE_NOT_TRASHED.raise();
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/kb/files/markdown",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = MarkdownFileRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFile.class)))
    private void createMarkdownFile(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(MarkdownFileRequest.class);
        requireWriteInFolder(ctx, req.folderId());
        if (req.name() == null || req.name().isBlank()) throw Refusal.KB_ARTICLE_NEEDS_A_NAME.raise();
        ctx.json(service.createMarkdownFile(
                session.stationId(),
                req.folderId(),
                req.name().trim(),
                req.description() != null ? req.description() : "",
                req.content() != null ? req.content() : "",
                session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/kb/files/youtube",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = YoutubeFileRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFile.class)))
    private void createYoutubeFile(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(YoutubeFileRequest.class);
        requireWriteInFolder(ctx, req.folderId());
        if (req.name() == null || req.name().isBlank()) throw Refusal.KB_VIDEO_NEEDS_A_NAME.raise();
        if (req.youtubeUrl() == null || req.youtubeUrl().isBlank()) throw Refusal.KB_VIDEO_NEEDS_AN_ADDRESS.raise();
        ctx.json(service.createYoutubeFile(
                session.stationId(),
                req.folderId(),
                req.name().trim(),
                req.description() != null ? req.description() : "",
                req.youtubeUrl(),
                session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/kb/files/link",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LinkFileRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFile.class)))
    private void createLinkFile(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(LinkFileRequest.class);
        requireWriteInFolder(ctx, req.folderId());
        if (req.linkUrl() == null || req.linkUrl().isBlank()) throw Refusal.KB_LINK_NEEDS_AN_ADDRESS.raise();
        ctx.json(service.createLinkFile(
                session.stationId(),
                req.folderId(),
                req.name(),
                req.description(),
                req.linkUrl().trim(),
                session.member().id()));
    }

    @OpenApi(
            path = "/api/v1/kb/files/upload",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFile.class)))
    private void uploadFile(Context ctx) {
        var session = UserSession.from(ctx);
        var file = requireUpload(ctx);
        String name = ctx.formParam("name");
        if (name == null || name.isBlank()) name = file.filename();
        String description = ctx.formParam("description");
        Integer folderId = null;
        String folderIdStr = ctx.formParam("folderId");
        if (folderIdStr != null && !folderIdStr.isBlank()) folderId = Integer.parseInt(folderIdStr);
        requireWriteInFolder(ctx, folderId);
        byte[] data;
        try (var content = file.content()) {
            data = content.readAllBytes();
        } catch (Exception e) {
            log.warn("Failed to read uploaded file for KB", e);
            throw Refusal.KB_UPLOAD_NOT_READ.raise();
        }
        var created = service.createUploadedFile(
                session.stationId(),
                folderId,
                name.trim(),
                description != null ? description : "",
                data,
                file.contentType(),
                session.member().id());
        pictureService.make(session.stationId(), created.id(), created.mimeType(), data);
        ctx.json(created);
    }

    @OpenApi(
            path = "/api/v1/kb/files/import-document",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFile.class)))
    private void importDocument(Context ctx) {
        var session = UserSession.from(ctx);
        var file = requireUpload(ctx);

        String name = ctx.formParam("name");
        if (name == null || name.isBlank()) {
            name = file.filename();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
        }
        String description = ctx.formParam("description");
        Integer folderId = null;
        String folderIdStr = ctx.formParam("folderId");
        if (folderIdStr != null && !folderIdStr.isBlank()) folderId = Integer.parseInt(folderIdStr);
        requireWriteInFolder(ctx, folderId);

        String format = detectPandocFormat(file.filename(), file.contentType());
        if (format == null) {
            throw Refusal.KB_IMPORT_KIND_UNKNOWN.raise();
        }

        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            String markdown = PandocConverter.toMarkdown(data, format);
            ctx.json(service.createMarkdownFile(
                    session.stationId(),
                    folderId,
                    name.trim(),
                    description != null ? description : "",
                    markdown,
                    session.member().id()));
        } catch (Exception e) {
            log.warn("Document conversion failed for KB import", e);
            throw Refusal.KB_IMPORT_FAILED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/content",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void getFileContent(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);

        switch (file.fileType()) {
            case MARKDOWN, TEXT -> {
                var text = contentService.getMarkdownContent(id);
                if (text.isEmpty()) throw Refusal.KB_ARTICLE_TEXT_NOT_HERE.raise();
                ctx.contentType(ContentType.TEXT_PLAIN);
                ctx.result(text.get());
            }
            case PDF, IMAGE, OTHER -> {
                var data = contentService.getFileContent(id);
                if (data.isEmpty()) throw Refusal.KB_FILE_CONTENT_NOT_HERE.raise();
                String mime = SafeInlineMime.safeContentType(file.mimeType());
                var disposition = SafeInlineMime.isInlineSafe(file.mimeType())
                        ? SafeContentDisposition.Disposition.INLINE
                        : SafeContentDisposition.Disposition.ATTACHMENT;
                ctx.contentType(mime);
                ctx.header("Content-Disposition", SafeContentDisposition.build(disposition, file.name()));
                ctx.result(data.get());
            }
            case PRESENTATION -> {
                var pdf = presentationService.getPresentationPdf(id);
                if (pdf.isEmpty()) throw Refusal.KB_PRESENTATION_NOT_READY.raise();
                ctx.contentType("application/pdf");
                ctx.header(
                        "Content-Disposition",
                        SafeContentDisposition.build(SafeContentDisposition.Disposition.INLINE, file.name() + ".pdf"));
                ctx.result(pdf.get());
            }
            case YOUTUBE -> ctx.json(new YoutubeResponse(file.youtubeUrl()));
            case LINK -> ctx.json(new LinkResponse(file.linkUrl()));
        }
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/html",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MarkdownHtmlResponse.class)))
    private void getMarkdownHtml(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        var text = contentService.getMarkdownContent(id);
        if (text.isEmpty()) throw Refusal.KB_ARTICLE_TEXT_NOT_HERE_AS_PAGE.raise();
        String html = contentService.renderMarkdown(text.get());
        ctx.json(new MarkdownHtmlResponse(html, text.get()));
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/content",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ContentUpdateRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void updateMarkdownContent(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        var req = ctx.bodyAsClass(ContentUpdateRequest.class);
        contentService.updateMarkdownContent(
                id, req.content() != null ? req.content() : "", session.member().id());
        ctx.status(204);
    }

    /**
     * The blocks a rich article is built from. Reading them needs only read access to the article,
     * because they are the article: the stored text is a projection of them.
     */
    @OpenApi(
            path = "/api/v1/kb/files/{id}/blocks",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BlocksResponse.class)))
    private void getBlocks(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        ctx.json(blocksOf(file));
    }

    private BlocksResponse blocksOf(KbFile file) {
        return new BlocksResponse(
                file.contentMode(), contentService.loadBlocks(file), contentService.describedBlocks(file));
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/blocks",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SaveBlocksRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BlocksResponse.class)))
    private void saveBlocks(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        var request = ctx.bodyAsClass(SaveBlocksRequest.class);
        var saved = contentService
                .saveBlocks(id, request.toRowData(), session.member().id())
                .orElseThrow(Refusal.KB_BLOCKS_NOT_SAVED::raise);
        ctx.json(blocksOf(saved));
    }

    /**
     * Turns a plain article into one built from blocks. What the author already wrote becomes a
     * single markdown block, which they then split up as they like.
     */
    @OpenApi(
            path = "/api/v1/kb/files/{id}/blocks/enable",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BlocksResponse.class)))
    private void enableBlocks(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        var switched = contentService.switchToRich(id).orElseThrow(Refusal.KB_BLOCKS_NOT_ENABLED::raise);
        ctx.json(blocksOf(switched));
    }

    @OpenApi(path = "/api/v1/kb/files/{id}/pdf", methods = HttpMethod.GET, responses = @OpenApiResponse(status = "200"))
    private void getPdfExport(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        if (!KbPdfExportService.isExportable(file.fileType())) {
            throw Refusal.KB_NOT_A_PDF_TO_MAKE.raise();
        }
        var session = UserSession.from(ctx);
        try {
            byte[] pdf = pdfExportService.render(
                    file, NameParts.of(session.account()).official());
            ctx.contentType("application/pdf");
            ctx.header(
                    "Content-Disposition",
                    SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, file.name() + ".pdf"));
            ctx.result(pdf);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Refusal.KB_PDF_STOPPED.raise();
        } catch (Exception e) {
            log.warn("Failed to render text file {} as PDF", id, e);
            throw Refusal.KB_PDF_NOT_MADE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/original",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void getOriginalFile(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        if (file.fileType() != KbFileType.PRESENTATION) {
            throw Refusal.KB_ORIGINAL_NOT_KEPT.raise();
        }
        var data = contentService.getFileContent(id);
        if (data.isEmpty()) throw Refusal.KB_ORIGINAL_NOT_HERE.raise();
        ctx.contentType(SafeInlineMime.safeContentType(file.mimeType()));
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, file.name()));
        ctx.result(data.get());
    }

    /**
     * Replaces the file a presentation was made from.
     *
     * <p>The read-back sits outside the catch, which takes everything the replacement itself can
     * throw. Inside it, a presentation that could not be read back would be caught there too and
     * answered as a replacement that failed, which is the one thing it is not: the file is in.
     */
    @OpenApi(
            path = "/api/v1/kb/files/{id}/original",
            methods = HttpMethod.PUT,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFile.class)))
    private void reuploadOriginal(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        if (file.fileType() != KbFileType.PRESENTATION) {
            throw Refusal.KB_ORIGINAL_NOT_REPLACEABLE.raise();
        }
        var uploaded = requireUpload(ctx);
        try (var content = uploaded.content()) {
            byte[] data = content.readAllBytes();
            presentationService.reuploadPresentation(id, data, uploaded.contentType(), uploaded.filename());
        } catch (Exception e) {
            log.warn("Failed to re-upload presentation file", e);
            throw Refusal.KB_PRESENTATION_NOT_REPLACED.raise();
        }
        ctx.json(service.findFile(id).orElseThrow(Refusal.KB_FILE_NOT_HERE_AFTER_REUPLOAD::raise));
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/versions",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbVersionResponse[].class)))
    private void listVersions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        ctx.json(contentService.findVersions(id).stream()
                .map(v -> new KbVersionResponse(
                        v.id(),
                        v.version(),
                        v.isFull(),
                        v.createdBy(),
                        authorNameService.resolveMemberName(v.createdBy()),
                        v.createdAt()))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/versions/{version}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = KbFileVersion.class)))
    private void getVersion(Context ctx) {
        int fileId = pathInt(ctx, "id");
        int version = pathInt(ctx, "version");
        requireOwnedFile(ctx, service, fileId);
        requireLevel(ctx, accessService, null, fileId, KbAccessLevel.READ);
        contentService.findVersion(fileId, version).ifPresentOrElse(ctx::json, () -> {
            throw Refusal.KB_VERSION_NOT_HERE.raise();
        });
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/versions/{version}/revert",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "204"))
    private void revertToVersion(Context ctx) {
        int fileId = pathInt(ctx, "id");
        int version = pathInt(ctx, "version");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, fileId);
        requireLevel(ctx, accessService, null, fileId, KbAccessLevel.WRITE);
        contentService.revertToVersion(fileId, version, session.member().id());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/related",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RelatedFilesResponse.class)))
    private void getRelatedFiles(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        ctx.json(relatedResponse(ctx, id));
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/related",
            methods = HttpMethod.PUT,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RelatedFilesRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = RelatedFilesResponse.class)))
    private void setRelatedFiles(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        var req = ctx.bodyAsClass(RelatedFilesRequest.class);
        service.setRelatedFiles(id, req.fileIds() != null ? req.fileIds() : List.of());
        ctx.json(relatedResponse(ctx, id));
    }

    /**
     * Both directions of an article's cross-references, each filtered against what the reader may
     * open on the other end.
     *
     * <p>An article the reader may not open is left out rather than counted as one that was hidden:
     * a number that stands one higher gives away that the article exists, and its title is often
     * the whole of what was worth keeping from them.
     */
    private RelatedFilesResponse relatedResponse(Context ctx, int fileId) {
        var access = KbGuards.accessOf(ctx, accessService);
        return new RelatedFilesResponse(
                readable(access, service.findRelatedFiles(fileId)), readable(access, service.findBacklinks(fileId)));
    }

    /**
     * The articles of one list the caller may open, resolved for the whole list at once.
     *
     * <p>Being allowed to read the article that names them says nothing about the ones it names,
     * which may sit anywhere in the tree, so each has to be asked about separately. Asking one
     * query per article turns a page of cross-references into a page of queries, which is why the
     * levels for the whole list are resolved together.
     */
    private List<KbFile> readable(KbAccessService.MemberAccess access, List<KbFile> files) {
        var readable = accessService.readableFiles(
                access, files.stream().map(KbAccessService.FileNode::of).toList());
        return files.stream().filter(file -> readable.contains(file.id())).toList();
    }

    /**
     * A search reaches an article without walking the folders above it, so every hit is measured
     * against the caller's level for that article before it is answered. Both the excerpt and the
     * title of an article the caller may not open would otherwise be handed over by searching for a
     * word in it.
     */
    @OpenApi(
            path = "/api/v1/kb/search",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = SearchResultResponse[].class)))
    private void search(Context ctx) {
        var session = UserSession.from(ctx);
        String query = ctx.queryParam("q");
        boolean federated = !"false".equals(ctx.queryParam("federated"));
        if (query == null || query.isBlank()) {
            ctx.json(List.of());
            return;
        }

        var access = KbGuards.accessOf(ctx, accessService);
        var hits = searchService.searchWithSnippets(session.stationId(), query);
        var readable = accessService.readableFiles(
                access,
                hits.stream().map(r -> KbAccessService.FileNode.of(r.file())).toList());
        var visible = hits.stream()
                .filter(r -> readable.contains(r.file().id()))
                .limit(KbSearchService.RESULT_LIMIT)
                .toList();
        var folderPaths = service.findFolderPaths(visible.stream()
                .map(r -> r.file().folderId())
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        var localResults = visible.stream()
                .map(r -> new SearchResultResponse(
                        r.file(), r.snippet(), folderPaths.getOrDefault(r.file().folderId(), "/"), null, null))
                .toList();

        var all = new ArrayList<>(localResults);
        if (federated) {
            all.addAll(searchFederated(session.stationId(), query));
        }
        ctx.json(all);
    }

    private List<SearchResultResponse> searchFederated(int stationId, String query) {
        return federationService.searchFederatedKb(stationId, query).stream()
                .map(r ->
                        new SearchResultResponse(r.file().toKbFile(), r.snippet(), "", r.stationName(), r.stationUid()))
                .toList();
    }

    @OpenApi(
            path = "/api/v1/kb/browse",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BrowseResponse.class)))
    private void browse(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(browseService.browse(
                session.stationId(),
                optionalFolderId(ctx, "folderId"),
                KbGuards.accessOf(ctx, accessService),
                session.hasPermission(StationPermission.KNOWLEDGE_MANAGER)));
    }

    /**
     * The picture of a file, for a tile that shows what the file is rather than what kind it is.
     *
     * <p>Kept behind the same door as the file's own bytes: a picture of a sheet is still the sheet.
     * A file with no picture answers 404, and the tile draws its icon instead.
     */
    @OpenApi(
            path = "/api/v1/kb/files/{id}/picture",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void getFilePicture(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(256);
        var picture = pictureService
                .read(file.stationId(), id, file.mimeType(), size)
                .orElseThrow(Refusal.KB_ARTICLE_PICTURE_NOT_HERE::raise);
        ctx.contentType(picture.contentType());
        ctx.header("Cache-Control", "private, max-age=300");
        ctx.result(picture.data());
    }

    @OpenApi(
            path = "/api/v1/kb/folders/{id}/icon",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void getFolderIcon(Context ctx) {
        var session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(256);
        iconService
                .read(session.stationId(), id, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "private, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    @OpenApi(
            path = "/api/v1/kb/folders/{id}/icon",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void uploadFolderIcon(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        var folder = requireOwnedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.WRITE);
        var file = ctx.uploadedFile("icon");
        if (file == null) throw Refusal.KB_FOLDER_ICON_MISSING.raise();
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw Refusal.KB_FOLDER_ICON_KIND_NOT_TAKEN.raise();
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            iconService.store(session.stationId(), id, data, file.contentType(), 5 * 1024 * 1024);
            service.updateFolder(id, folder.name(), folder.description(), iconService.key(id), folder.position());
            ctx.json(new MessageResponse("Icon updated"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument storing folder icon for folder {}", id, e);
            throw Refusal.KB_FOLDER_ICON_NOT_SAVED.raise();
        } catch (IOException e) {
            log.error("Failed to process image", e);
            throw Refusal.KB_FOLDER_ICON_NOT_PROCESSED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/kb/files/{id}/images",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ImageUploadResponse.class)))
    private void uploadKbImage(Context ctx) {
        int fileId = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, fileId);
        requireLevel(ctx, accessService, null, fileId, KbAccessLevel.WRITE);
        var file = ctx.uploadedFile("image");
        if (file == null) throw Refusal.KB_ARTICLE_IMAGE_MISSING.raise();
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw Refusal.KB_ARTICLE_IMAGE_KIND_NOT_TAKEN.raise();
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            String imageId = "file-" + fileId + "-" + System.currentTimeMillis();
            imageService.store(session.stationId(), imageId, data, file.contentType(), 10 * 1024 * 1024);
            ctx.json(new ImageUploadResponse(imageId));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument storing KB image for file {}", fileId, e);
            throw Refusal.KB_ARTICLE_IMAGE_NOT_SAVED.raise();
        } catch (IOException e) {
            log.error("Failed to process image", e);
            throw Refusal.KB_ARTICLE_IMAGE_NOT_PROCESSED.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/kb/images/{imageId}",
            methods = HttpMethod.GET,
            responses = @OpenApiResponse(status = "200"))
    private void getKbImage(Context ctx) {
        var session = UserSession.from(ctx);
        String imageId = ctx.pathParam("imageId");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(1024);
        imageService
                .read(session.stationId(), imageId, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "private, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    public record FolderRequest(Integer parentId, String name, String description, String iconUrl, Integer position) {}

    public record FileUpdateRequest(String name, String description, String iconUrl, Integer position) {}

    public record MarkdownFileRequest(Integer folderId, String name, String description, String content) {}

    public record YoutubeFileRequest(Integer folderId, String name, String description, String youtubeUrl) {}

    public record LinkFileRequest(Integer folderId, String name, String description, String linkUrl) {}

    public record ContentUpdateRequest(String content) {}

    public record RelatedFilesRequest(List<Integer> fileIds) {}

    public record MoveFolderRequest(Integer parentId) {}

    public record MoveFileRequest(Integer folderId) {}

    public record BulkMoveRequest(List<Integer> folderIds, List<Integer> fileIds, Integer targetFolderId) {}

    public record BulkTagsRequest(
            List<Integer> folderIds, List<Integer> fileIds, List<String> addTags, List<String> removeTags) {}

    public record BulkDeleteRequest(List<Integer> folderIds, List<Integer> fileIds) {}

    /**
     * How many entries emptying the trash took.
     */
    public record EmptyTrashResponse(int cleared) {}

    /**
     * One folder of the tree a move picker offers, with what the caller may do in it.
     */
    public record FolderTreeEntry(int id, @Nullable Integer parentId, String name, KbAccessLevel level) {}

    /**
     * The answer to a single move: whether the entry sits somewhere else now, and, when it does
     * not, which of the reasons a move can be turned down for it was.
     */
    public record MoveResponse(boolean moved, @Nullable String name, @Nullable KbRefusalReason reason) {
        static MoveResponse of(KbMoveService.MoveResult result) {
            return new MoveResponse(result.moved(), result.name(), result.reason());
        }
    }

    /**
     * What an article points at and what points at it. The second list is read from the same rows
     * the other way round, so a reference is visible from both ends while only one end owns it.
     */
    public record RelatedFilesResponse(List<KbFile> related, List<KbFile> backlinks) {}

    public record YoutubeResponse(String youtubeUrl) {}

    public record LinkResponse(String linkUrl) {}

    public record MarkdownHtmlResponse(String html, String markdown) {}

    /**
     * The blocks of an article, together with how it was written. A plain article answers with an
     * empty list rather than a 404, so the reader can ask before it knows which kind it has.
     *
     * <p>{@code rows} are the blocks as written, for the editor. {@code describedRows} are the same
     * blocks as a reader sees them, with a picture the article says nothing about carrying the words
     * of its media file. Keeping the two apart is what stops a save from writing those words into
     * the article.
     */
    public record BlocksResponse(ContentMode contentMode, List<ContentRow> rows, List<ContentRow> describedRows) {}

    /**
     * A file with what the reader may do with it and, when a folder decided that, which one - so
     * the page can say why an action is missing instead of just not showing it.
     */
    public record FileResponse(
            KbFile file, String lastEditedByName, KbAccessLevel accessLevel, @Nullable String accessLevelSource) {}

    public record KbVersionResponse(
            int id, int version, boolean isFull, int createdBy, String createdByName, Instant createdAt) {}

    public record SearchResultResponse(
            KbFile file,
            String snippet,
            String folderPath,
            @Nullable String stationName,
            @Nullable String sourceStationUid) {}

    public record ImageUploadResponse(String imageId) {}
}
