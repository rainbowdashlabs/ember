/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentMode;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.knowledgebase.service.KbImageService;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfExportService;
import dev.chojo.ember.feature.knowledgebase.service.KbPresentationService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.PandocConverter;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UploadedFile;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.requireLevel;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.requireOwnedFile;
import static dev.chojo.ember.feature.knowledgebase.route.KbRouteAccess.requireOwnedFolder;
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
    private final KbPdfExportService pdfExportService;
    private final StationRepository stationRepository;

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
            KbPdfExportService pdfExportService,
            StationRepository stationRepository) {
        this.service = service;
        this.contentService = contentService;
        this.searchService = searchService;
        this.accessService = accessService;
        this.presentationService = presentationService;
        this.authorNameService = authorNameService;
        this.federationService = federationService;
        this.iconService = iconService;
        this.imageService = imageService;
        this.pdfExportService = pdfExportService;
        this.stationRepository = stationRepository;
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
        if (file == null) throw new BadRequestResponse("file is required");
        if (file.size() > MAX_UPLOAD_SIZE) throw new BadRequestResponse("File too large (max 50MB)");
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
        routes.post(prefix + "/kb/folders", this::createFolder, StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/kb/folders/{id}", this::getFolder, StationPermission.USER);
        routes.put(prefix + "/kb/folders/{id}", this::updateFolder, StationPermission.KNOWLEDGE_EDIT);
        routes.delete(prefix + "/kb/folders/{id}", this::deleteFolder, StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/files", this::listFiles, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}", this::getFile, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}", this::updateFile, StationPermission.KNOWLEDGE_EDIT);
        routes.delete(prefix + "/kb/files/{id}", this::deleteFile, StationPermission.KNOWLEDGE_EDIT);

        routes.post(prefix + "/kb/files/markdown", this::createMarkdownFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/youtube", this::createYoutubeFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/upload", this::uploadFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/import-document", this::importDocument, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/link", this::createLinkFile, StationPermission.KNOWLEDGE_EDIT);

        routes.get(prefix + "/kb/files/{id}/content", this::getFileContent, StationPermission.USER);
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

    private void listFolders(Context ctx) {
        var session = UserSession.from(ctx);
        Integer parentId = optionalFolderId(ctx, "parentId");
        var folders = service.findFolders(session.stationId(), parentId);
        var levels = accessService.childLevels(
                KbRouteAccess.accessOf(ctx, accessService),
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

    private void createFolder(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(FolderRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        requireWriteInFolder(ctx, req.parentId());
        ctx.json(service.createFolder(
                session.stationId(),
                req.parentId(),
                req.name().trim(),
                req.description() != null ? req.description() : "",
                session.member().id()));
    }

    private void getFolder(Context ctx) {
        int id = pathInt(ctx, "id");
        var folder = requireOwnedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.READ);
        ctx.json(folder);
    }

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
            throw new NotFoundResponse();
        }
        service.findFolder(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void deleteFolder(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.MANAGE);
        if (!service.deleteFolder(id)) throw new NotFoundResponse();
        ctx.status(204);
    }

    private void listFiles(Context ctx) {
        var session = UserSession.from(ctx);
        var files = service.findFiles(session.stationId(), optionalFolderId(ctx, "folderId"));
        var readable = accessService.readableFiles(
                KbRouteAccess.accessOf(ctx, accessService),
                files.stream().map(KbAccessService.FileNode::of).toList());
        ctx.json(files.stream()
                .filter(file -> readable.contains(file.id()))
                .map(KbFileSummary::of)
                .toList());
    }

    private void getFile(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        var level = accessService.explainLevel(KbRouteAccess.accessOf(ctx, accessService), null, id);
        ctx.json(new FileResponse(
                file, authorNameService.resolveMemberName(file.createdBy()), level.level(), level.source()));
    }

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
            throw new NotFoundResponse();
        }
        service.findFile(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void deleteFile(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.MANAGE);
        if (!service.deleteFile(id)) throw new NotFoundResponse();
        ctx.status(204);
    }

    private void createMarkdownFile(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(MarkdownFileRequest.class);
        requireWriteInFolder(ctx, req.folderId());
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.json(service.createMarkdownFile(
                session.stationId(),
                req.folderId(),
                req.name().trim(),
                req.description() != null ? req.description() : "",
                req.content() != null ? req.content() : "",
                session.member().id()));
    }

    private void createYoutubeFile(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(YoutubeFileRequest.class);
        requireWriteInFolder(ctx, req.folderId());
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        if (req.youtubeUrl() == null || req.youtubeUrl().isBlank())
            throw new BadRequestResponse("youtubeUrl is required");
        ctx.json(service.createYoutubeFile(
                session.stationId(),
                req.folderId(),
                req.name().trim(),
                req.description() != null ? req.description() : "",
                req.youtubeUrl(),
                session.member().id()));
    }

    private void createLinkFile(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(LinkFileRequest.class);
        requireWriteInFolder(ctx, req.folderId());
        if (req.linkUrl() == null || req.linkUrl().isBlank()) throw new BadRequestResponse("linkUrl is required");
        ctx.json(service.createLinkFile(
                session.stationId(),
                req.folderId(),
                req.name(),
                req.description(),
                req.linkUrl().trim(),
                session.member().id()));
    }

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
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            ctx.json(service.createUploadedFile(
                    session.stationId(),
                    folderId,
                    name.trim(),
                    description != null ? description : "",
                    data,
                    file.contentType(),
                    session.member().id()));
        } catch (Exception e) {
            log.warn("Failed to read uploaded file for KB", e);
            throw new BadRequestResponse("Failed to read file");
        }
    }

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
            throw new BadRequestResponse("Unsupported document format. Supported: .docx, .odt, .html, .rtf");
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
            throw new BadRequestResponse("Document conversion failed");
        }
    }

    private void getFileContent(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);

        switch (file.fileType()) {
            case MARKDOWN, TEXT -> {
                var text = contentService.getMarkdownContent(id);
                if (text.isEmpty()) throw new NotFoundResponse();
                ctx.contentType(ContentType.TEXT_PLAIN);
                ctx.result(text.get());
            }
            case PDF, IMAGE, OTHER -> {
                var data = contentService.getFileContent(id);
                if (data.isEmpty()) throw new NotFoundResponse();
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
                if (pdf.isEmpty()) throw new NotFoundResponse("Conversion not ready");
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

    private void getMarkdownHtml(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        var text = contentService.getMarkdownContent(id);
        if (text.isEmpty()) throw new NotFoundResponse();
        String html = contentService.renderMarkdown(text.get());
        ctx.json(new MarkdownHtmlResponse(html, text.get()));
    }

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
    private void getBlocks(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        ctx.json(new BlocksResponse(file.contentMode(), contentService.loadBlocks(file)));
    }

    private void saveBlocks(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        var request = ctx.bodyAsClass(SaveBlocksRequest.class);
        var saved = contentService
                .saveBlocks(id, request.toRowData(), session.member().id())
                .orElseThrow(NotFoundResponse::new);
        ctx.json(new BlocksResponse(saved.contentMode(), contentService.loadBlocks(saved)));
    }

    /**
     * Turns a plain article into one built from blocks. What the author already wrote becomes a
     * single markdown block, which they then split up as they like.
     */
    private void enableBlocks(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        var switched = contentService.switchToRich(id).orElseThrow(NotFoundResponse::new);
        ctx.json(new BlocksResponse(switched.contentMode(), contentService.loadBlocks(switched)));
    }

    private void getPdfExport(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        if (!KbPdfExportService.isExportable(file.fileType())) {
            throw new BadRequestResponse("Only markdown and text files can be rendered as PDF");
        }
        var session = UserSession.from(ctx);
        try {
            byte[] pdf =
                    pdfExportService.render(file, session.account().fullName().trim());
            ctx.contentType("application/pdf");
            ctx.header(
                    "Content-Disposition",
                    SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, file.name() + ".pdf"));
            ctx.result(pdf);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InternalServerErrorResponse("Failed to render PDF");
        } catch (Exception e) {
            log.warn("Failed to render text file {} as PDF", id, e);
            throw new InternalServerErrorResponse("Failed to render PDF");
        }
    }

    private void getOriginalFile(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        if (file.fileType() != KbFileType.PRESENTATION) {
            throw new BadRequestResponse("Only presentation files have an original");
        }
        var data = contentService.getFileContent(id);
        if (data.isEmpty()) throw new NotFoundResponse();
        ctx.contentType(SafeInlineMime.safeContentType(file.mimeType()));
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, file.name()));
        ctx.result(data.get());
    }

    private void reuploadOriginal(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        if (file.fileType() != KbFileType.PRESENTATION) {
            throw new BadRequestResponse("Only presentation files support re-upload");
        }
        var uploaded = requireUpload(ctx);
        try (var content = uploaded.content()) {
            byte[] data = content.readAllBytes();
            presentationService.reuploadPresentation(id, data, uploaded.contentType(), uploaded.filename());
            ctx.json(service.findFile(id).orElseThrow());
        } catch (Exception e) {
            log.warn("Failed to re-upload presentation file", e);
            throw new InternalServerErrorResponse("Failed to re-upload file");
        }
    }

    private void listVersions(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        ctx.json(contentService.findVersions(id).stream()
                .map(v -> new VersionResponse(
                        v.id(),
                        v.version(),
                        v.isFull(),
                        v.createdBy(),
                        authorNameService.resolveMemberName(v.createdBy()),
                        v.createdAt()))
                .toList());
    }

    private void getVersion(Context ctx) {
        int fileId = pathInt(ctx, "id");
        int version = pathInt(ctx, "version");
        requireOwnedFile(ctx, service, fileId);
        requireLevel(ctx, accessService, null, fileId, KbAccessLevel.READ);
        contentService.findVersion(fileId, version).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void revertToVersion(Context ctx) {
        int fileId = pathInt(ctx, "id");
        int version = pathInt(ctx, "version");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, fileId);
        requireLevel(ctx, accessService, null, fileId, KbAccessLevel.WRITE);
        contentService.revertToVersion(fileId, version, session.member().id());
        ctx.status(204);
    }

    private void getRelatedFiles(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.READ);
        ctx.json(readableRelatedFiles(ctx, id));
    }

    private void setRelatedFiles(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        requireLevel(ctx, accessService, null, id, KbAccessLevel.WRITE);
        var req = ctx.bodyAsClass(RelatedFilesRequest.class);
        service.setRelatedFiles(id, req.fileIds() != null ? req.fileIds() : List.of());
        ctx.json(readableRelatedFiles(ctx, id));
    }

    /**
     * The articles one article points at, minus the ones the caller may not open. Being allowed to
     * read the article that names them says nothing about the ones it names, which may sit anywhere
     * in the tree.
     */
    private List<KbFile> readableRelatedFiles(Context ctx, int fileId) {
        var related = service.findRelatedFiles(fileId);
        var readable = accessService.readableFiles(
                KbRouteAccess.accessOf(ctx, accessService),
                related.stream().map(KbAccessService.FileNode::of).toList());
        return related.stream().filter(file -> readable.contains(file.id())).toList();
    }

    /**
     * A search reaches an article without walking the folders above it, so every hit is measured
     * against the caller's level for that article before it is answered. Both the excerpt and the
     * title of an article the caller may not open would otherwise be handed over by searching for a
     * word in it.
     */
    private void search(Context ctx) {
        var session = UserSession.from(ctx);
        String query = ctx.queryParam("q");
        boolean federated = !"false".equals(ctx.queryParam("federated"));
        if (query == null || query.isBlank()) {
            ctx.json(List.of());
            return;
        }

        var access = KbRouteAccess.accessOf(ctx, accessService);
        var localFuture =
                CompletableFuture.supplyAsync(() -> searchService.searchWithSnippets(session.stationId(), query));
        var federatedFuture = federated
                ? CompletableFuture.supplyAsync(() -> searchFederated(session.stationId(), query))
                : CompletableFuture.completedFuture(List.<SearchResultResponse>of());

        var hits = localFuture.join();
        var readable = accessService.readableFiles(
                access,
                hits.stream().map(r -> KbAccessService.FileNode.of(r.file())).toList());
        var visible =
                hits.stream().filter(r -> readable.contains(r.file().id())).toList();
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
        all.addAll(federatedFuture.join());
        ctx.json(all);
    }

    private List<SearchResultResponse> searchFederated(int stationId, String query) {
        return federationService.searchFederatedKb(stationId, query).stream()
                .map(r ->
                        new SearchResultResponse(r.file().toKbFile(), r.snippet(), "", r.stationName(), r.stationUid()))
                .toList();
    }

    private void browse(Context ctx) {
        var session = UserSession.from(ctx);
        Integer folderId = optionalFolderId(ctx, "folderId");
        var folders = service.findFolders(session.stationId(), folderId);
        var files = service.findFiles(session.stationId(), folderId);
        KbFolder currentFolder = folderId != null ? service.findFolder(folderId).orElse(null) : null;

        var access = KbRouteAccess.accessOf(ctx, accessService);
        if (!session.hasPermission(StationPermission.KNOWLEDGE_MANAGER)) {
            folders = folders.stream()
                    .filter(folder -> accessService.canAccess(access, folder.id(), null))
                    .toList();
            files = files.stream()
                    .filter(file -> accessService.canAccess(access, null, file.id()))
                    .toList();
        }

        var levels = accessService.childLevels(
                access,
                folderId,
                folders.stream()
                        .map(folder -> new KbAccessService.ChildNode(folder.id(), folder.restrictionMode()))
                        .toList(),
                files.stream()
                        .map(file -> new KbAccessService.ChildNode(file.id(), file.restrictionMode()))
                        .toList());

        var mode = stationRepository
                .findById(session.stationId())
                .map(Station::publicKbMode)
                .orElse(PublicKbMode.OFF);
        var narrowedFolders = federationService.narrowlyShared(session.stationId(), true);
        var narrowedFiles = federationService.narrowlyShared(session.stationId(), false);
        var openFolders = federationService.broadlyShared(session.stationId(), true);
        var openFiles = federationService.broadlyShared(session.stationId(), false);

        ctx.json(new BrowseResponse(
                currentFolder,
                folders,
                files.stream().map(KbFileSummary::of).toList(),
                accessService.effectiveLevel(access, folderId, null),
                levels.folders(),
                levels.files(),
                reachOf(folders.stream().map(KbFolder::id).toList(), mode, true, narrowedFolders, openFolders),
                reachOf(files.stream().map(KbFile::id).toList(), mode, false, narrowedFiles, openFiles)));
    }

    /**
     * How far each entry of one level reaches: onto the public web, out to every partner station, or out
     * to some of them only.
     *
     * <p>An entry restricted to certain readers here counts as the narrow case even when it is shared with
     * every partner, because the sharper thing to know about it is that not everyone who meets it may open
     * it.
     */
    private Reach reachOf(
            List<Integer> ids, PublicKbMode mode, boolean folders, Set<Integer> narrowed, Set<Integer> opened) {
        var publicly = ids.stream()
                .filter(id -> accessService.isPubliclyVisible(mode, folders ? id : null, folders ? null : id))
                .collect(Collectors.toSet());
        var narrowly = ids.stream()
                .filter(id -> narrowed.contains(id)
                        || !accessService
                                .findRestrictions(folders ? id : null, folders ? null : id)
                                .isEmpty())
                .collect(Collectors.toSet());
        var federated = ids.stream()
                .filter(opened::contains)
                .filter(id -> !narrowly.contains(id))
                .collect(Collectors.toSet());
        return new Reach(publicly, federated, narrowly);
    }

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

    private void uploadFolderIcon(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        var folder = requireOwnedFolder(ctx, service, id);
        requireLevel(ctx, accessService, id, null, KbAccessLevel.WRITE);
        var file = ctx.uploadedFile("icon");
        if (file == null) throw new BadRequestResponse("No file uploaded");
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            iconService.store(session.stationId(), id, data, file.contentType(), 5 * 1024 * 1024);
            service.updateFolder(id, folder.name(), folder.description(), iconService.key(id), folder.position());
            ctx.json(new MessageResponse("Icon updated"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument storing folder icon for folder {}", id, e);
            throw new BadRequestResponse(e.getMessage());
        } catch (IOException e) {
            log.error("Failed to process image", e);
            throw new InternalServerErrorResponse("Failed to process image");
        }
    }

    private void uploadKbImage(Context ctx) {
        int fileId = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, fileId);
        requireLevel(ctx, accessService, null, fileId, KbAccessLevel.WRITE);
        var file = ctx.uploadedFile("image");
        if (file == null) throw new BadRequestResponse("No image uploaded");
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            String imageId = "file-" + fileId + "-" + System.currentTimeMillis();
            imageService.store(session.stationId(), imageId, data, file.contentType(), 10 * 1024 * 1024);
            ctx.json(new ImageUploadResponse(imageId));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument storing KB image for file {}", fileId, e);
            throw new BadRequestResponse(e.getMessage());
        } catch (IOException e) {
            log.error("Failed to process image", e);
            throw new InternalServerErrorResponse("Failed to process image");
        }
    }

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

    public record YoutubeResponse(String youtubeUrl) {}

    public record LinkResponse(String linkUrl) {}

    public record MarkdownHtmlResponse(String html, String markdown) {}

    /**
     * The blocks of an article, together with how it was written. A plain article answers with an
     * empty list rather than a 404, so the reader can ask before it knows which kind it has.
     */
    public record BlocksResponse(ContentMode contentMode, List<ContentRow> rows) {}

    /**
     * Request body for saving the blocks of a rich article.
     */
    public record SaveBlocksRequest(List<BlockRowRequest> rows) {
        List<ContentBlockService.RowData> toRowData() {
            if (rows == null) return List.of();
            return rows.stream().map(BlockRowRequest::toRowData).toList();
        }
    }

    public record BlockRowRequest(int sortOrder, List<BlockCellRequest> cells) {
        ContentBlockService.RowData toRowData() {
            return new ContentBlockService.RowData(
                    sortOrder,
                    cells == null
                            ? List.of()
                            : cells.stream().map(BlockCellRequest::toCellData).toList());
        }
    }

    /**
     * @param config the block's settings as an object. Which record they are follows from the
     *               content type beside them, so they are bound once that is known rather than
     *               while the request is read.
     */
    public record BlockCellRequest(
            int sortOrder, Double widthPercent, String contentType, String content, JsonNode config) {
        ContentBlockService.CellData toCellData() {
            var type = CellContentType.valueOf(contentType);
            return new ContentBlockService.CellData(
                    sortOrder,
                    widthPercent != null ? widthPercent : 100.0,
                    type,
                    content != null ? content : "",
                    CellConfig.parse(type, config));
        }
    }

    /**
     * A folder's contents together with what the caller may do with each entry, so a listing can
     * offer exactly the actions that will be accepted rather than the ones the station permission
     * suggests. {@code currentLevel} is what the caller may do in the folder itself, which decides
     * whether anything may be created in it.
     */
    public record BrowseResponse(
            KbFolder currentFolder,
            List<KbFolder> folders,
            List<KbFileSummary> files,
            KbAccessLevel currentLevel,
            Map<Integer, KbAccessLevel> folderLevels,
            Map<Integer, KbAccessLevel> fileLevels,
            Reach folderReach,
            Reach fileReach) {}

    /**
     * How far each entry of one level reaches, so the screen can mark it.
     *
     * <p>Two facts per entry, and only two: whether it stands on the public wiki, and whether it is shared
     * beyond this station without being open to everyone here. Resolved once for the level rather than
     * once per drawn tile.
     *
     * @param publicly  the ids that are on the public wiki
     * @param federated the ids every partner station reads, which is not the same as nobody outside
     * @param narrowly  the ids shared with named stations, or restricted to some of this station's readers
     */
    public record Reach(Set<Integer> publicly, Set<Integer> federated, Set<Integer> narrowly) {}

    /**
     * A file with what the reader may do with it and, when a folder decided that, which one - so
     * the page can say why an action is missing instead of just not showing it.
     */
    public record FileResponse(
            KbFile file, String lastEditedByName, KbAccessLevel accessLevel, String accessLevelSource) {}

    public record VersionResponse(
            int id, int version, boolean isFull, int createdBy, String createdByName, Instant createdAt) {}

    public record SearchResultResponse(
            KbFile file, String snippet, String folderPath, String stationName, String sourceStationUid) {}

    public record ImageUploadResponse(String imageId) {}
}
