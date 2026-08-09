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
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.knowledgebase.service.KbAuthorNameService;
import dev.chojo.ember.feature.knowledgebase.service.KbContentService;
import dev.chojo.ember.feature.knowledgebase.service.KbIconService;
import dev.chojo.ember.feature.knowledgebase.service.KbImageService;
import dev.chojo.ember.feature.knowledgebase.service.KbPresentationService;
import dev.chojo.ember.feature.knowledgebase.service.KbSearchService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseFederationService;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
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

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static dev.chojo.ember.api.RouteSupport.pathInt;
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
            KbImageService imageService) {
        this.service = service;
        this.contentService = contentService;
        this.searchService = searchService;
        this.accessService = accessService;
        this.presentationService = presentationService;
        this.authorNameService = authorNameService;
        this.federationService = federationService;
        this.iconService = iconService;
        this.imageService = imageService;
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
        ctx.json(service.findFolders(session.stationId(), optionalFolderId(ctx, "parentId")));
    }

    private void createFolder(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(FolderRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.json(service.createFolder(
                session.stationId(),
                req.parentId(),
                req.name().trim(),
                req.description() != null ? req.description() : "",
                session.member().id()));
    }

    private void getFolder(Context ctx) {
        int id = pathInt(ctx, "id");
        ctx.json(requireOwnedFolder(ctx, service, id));
    }

    private void updateFolder(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFolder(ctx, service, id);
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
        if (!service.deleteFolder(id)) throw new NotFoundResponse();
        ctx.status(204);
    }

    private void listFiles(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findFiles(session.stationId(), optionalFolderId(ctx, "folderId")).stream()
                .map(KbFileSummary::of)
                .toList());
    }

    private void getFile(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
        ctx.json(new FileResponse(file, authorNameService.resolveMemberName(file.createdBy())));
    }

    private void updateFile(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
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
        if (!service.deleteFile(id)) throw new NotFoundResponse();
        ctx.status(204);
    }

    private void createMarkdownFile(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(MarkdownFileRequest.class);
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
        var text = contentService.getMarkdownContent(id);
        if (text.isEmpty()) throw new NotFoundResponse();
        String html = contentService.renderMarkdown(text.get());
        ctx.json(new MarkdownHtmlResponse(html, text.get()));
    }

    private void updateMarkdownContent(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, id);
        var req = ctx.bodyAsClass(ContentUpdateRequest.class);
        contentService.updateMarkdownContent(
                id, req.content() != null ? req.content() : "", session.member().id());
        ctx.status(204);
    }

    private void getOriginalFile(Context ctx) {
        int id = pathInt(ctx, "id");
        var file = requireOwnedFile(ctx, service, id);
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
        contentService.findVersion(fileId, version).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void revertToVersion(Context ctx) {
        int fileId = pathInt(ctx, "id");
        int version = pathInt(ctx, "version");
        var session = UserSession.from(ctx);
        requireOwnedFile(ctx, service, fileId);
        contentService.revertToVersion(fileId, version, session.member().id());
        ctx.status(204);
    }

    private void getRelatedFiles(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        ctx.json(service.findRelatedFiles(id));
    }

    private void setRelatedFiles(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedFile(ctx, service, id);
        var req = ctx.bodyAsClass(RelatedFilesRequest.class);
        service.setRelatedFiles(id, req.fileIds() != null ? req.fileIds() : List.of());
        ctx.json(service.findRelatedFiles(id));
    }

    private void search(Context ctx) {
        var session = UserSession.from(ctx);
        String query = ctx.queryParam("q");
        boolean federated = !"false".equals(ctx.queryParam("federated"));
        if (query == null || query.isBlank()) {
            ctx.json(List.of());
            return;
        }

        var localFuture =
                CompletableFuture.supplyAsync(() -> searchService.searchWithSnippets(session.stationId(), query));
        var federatedFuture = federated
                ? CompletableFuture.supplyAsync(() -> searchFederated(session.stationId(), query))
                : CompletableFuture.completedFuture(List.<SearchResultResponse>of());

        var localResults = localFuture.join().stream()
                .map(r -> new SearchResultResponse(
                        r.file(), r.snippet(), resolveFolderPath(r.file().folderId()), null, null))
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

    private String resolveFolderPath(Integer folderId) {
        if (folderId == null) return "/";
        var parts = new ArrayList<String>();
        Integer current = folderId;
        while (current != null) {
            var folder = service.findFolder(current);
            if (folder.isEmpty()) break;
            parts.addFirst(folder.get().name());
            current = folder.get().parentId();
        }
        return "/" + String.join("/", parts);
    }

    private void browse(Context ctx) {
        var session = UserSession.from(ctx);
        Integer folderId = optionalFolderId(ctx, "folderId");
        var folders = service.findFolders(session.stationId(), folderId);
        var files = service.findFiles(session.stationId(), folderId);
        KbFolder currentFolder = folderId != null ? service.findFolder(folderId).orElse(null) : null;

        if (!session.hasPermission(StationPermission.KNOWLEDGE_MANAGER)) {
            var access = accessService.memberAccess(
                    session.member().id(), session.member().userType());
            folders = folders.stream()
                    .filter(folder -> accessService.canAccess(access, folder.id(), null))
                    .toList();
            files = files.stream()
                    .filter(file -> accessService.canAccess(access, null, file.id()))
                    .toList();
        }

        ctx.json(new BrowseResponse(
                currentFolder, folders, files.stream().map(KbFileSummary::of).toList()));
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

    public record BrowseResponse(KbFolder currentFolder, List<KbFolder> folders, List<KbFileSummary> files) {}

    public record FileResponse(KbFile file, String lastEditedByName) {}

    public record VersionResponse(
            int id, int version, boolean isFull, int createdBy, String createdByName, Instant createdAt) {}

    public record SearchResultResponse(
            KbFile file, String snippet, String folderPath, String stationName, String sourceStationId) {}

    public record ImageUploadResponse(String imageId) {}
}
