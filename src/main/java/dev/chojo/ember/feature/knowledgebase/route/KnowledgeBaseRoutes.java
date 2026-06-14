/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.CommentDeleted;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessRestriction;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.PandocConverter;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

@Singleton
public class KnowledgeBaseRoutes implements Routes {
    private static final Logger log = getLogger(KnowledgeBaseRoutes.class);
    private static final long MAX_UPLOAD_SIZE = 50 * 1024 * 1024; // 50 MB

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final Pattern MENTION_PATTERN_LEGACY = Pattern.compile("@\\[(\\d+):([^\\]]+)]");
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[([^/]+)/([^:]+):([^\\]]+)]");

    private final KnowledgeBaseService service;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;
    private final ImageService imageService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final KbCommentRepository kbCommentRepository;
    private final EventFederationRepository eventFederationRepository;
    private final FederationHttpClient federationHttpClient;
    private final DomainEventBus eventBus;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public KnowledgeBaseRoutes(
            KnowledgeBaseService service,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository,
            ImageService imageService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            KbCommentRepository kbCommentRepository,
            EventFederationRepository eventFederationRepository,
            FederationHttpClient federationHttpClient,
            DomainEventBus eventBus,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory,
            StationMemberService stationMemberService) {
        this.service = service;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
        this.imageService = imageService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.kbCommentRepository = kbCommentRepository;
        this.eventFederationRepository = eventFederationRepository;
        this.federationHttpClient = federationHttpClient;
        this.eventBus = eventBus;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
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

    private String resolveMemberName(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .map(member -> {
                    if (member.displayName() != null && !member.displayName().isBlank()) {
                        return member.displayName();
                    }
                    if (member.accountId() != null) {
                        return accountRepository
                                .findById(member.accountId())
                                .map(Account::fullName)
                                .orElse("Unbekannt");
                    }
                    return "Unbekannt";
                })
                .orElse("Unbekannt");
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Folders
        routes.get(prefix + "/kb/folders", this::listFolders, StationPermission.USER);
        routes.post(prefix + "/kb/folders", this::createFolder, StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/kb/folders/{id}", this::getFolder, StationPermission.USER);
        routes.put(prefix + "/kb/folders/{id}", this::updateFolder, StationPermission.KNOWLEDGE_EDIT);
        routes.delete(prefix + "/kb/folders/{id}", this::deleteFolder, StationPermission.KNOWLEDGE_EDIT);

        // Files
        routes.get(prefix + "/kb/files", this::listFiles, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}", this::getFile, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}", this::updateFile, StationPermission.KNOWLEDGE_EDIT);
        routes.delete(prefix + "/kb/files/{id}", this::deleteFile, StationPermission.KNOWLEDGE_EDIT);

        // File creation
        routes.post(prefix + "/kb/files/markdown", this::createMarkdownFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/youtube", this::createYoutubeFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/upload", this::uploadFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/import-document", this::importDocument, StationPermission.KNOWLEDGE_EDIT);
        routes.post(prefix + "/kb/files/link", this::createLinkFile, StationPermission.KNOWLEDGE_EDIT);

        // File content
        routes.get(prefix + "/kb/files/{id}/content", this::getFileContent, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}/html", this::getMarkdownHtml, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}/content", this::updateMarkdownContent, StationPermission.KNOWLEDGE_EDIT);

        // Presentation original file
        routes.get(prefix + "/kb/files/{id}/original", this::getOriginalFile, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}/original", this::reuploadOriginal, StationPermission.KNOWLEDGE_EDIT);

        // Versions (markdown only)
        routes.get(prefix + "/kb/files/{id}/versions", this::listVersions, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}/versions/{version}", this::getVersion, StationPermission.USER);
        routes.post(
                prefix + "/kb/files/{id}/versions/{version}/revert",
                this::revertToVersion,
                StationPermission.KNOWLEDGE_EDIT);

        // Related files
        routes.get(prefix + "/kb/files/{id}/related", this::getRelatedFiles, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}/related", this::setRelatedFiles, StationPermission.KNOWLEDGE_EDIT);

        // Search
        routes.get(prefix + "/kb/search", this::search, StationPermission.USER);

        // Browse (combined folders + files for a given parent)
        routes.get(prefix + "/kb/browse", this::browse, StationPermission.USER);

        // Access restrictions
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

        // Public visibility overrides
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

        // Folder icons
        routes.get(prefix + "/kb/folders/{id}/icon", this::getFolderIcon, StationPermission.USER);
        routes.post(prefix + "/kb/folders/{id}/icon", this::uploadFolderIcon, StationPermission.KNOWLEDGE_EDIT);

        // KB Images (for markdown embedding)
        routes.post(prefix + "/kb/files/{id}/images", this::uploadKbImage, StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/kb/images/{imageId}", this::getKbImage, StationPermission.USER);

        // Tags
        routes.get(prefix + "/kb/tags", this::listTags, StationPermission.USER);
        routes.get(prefix + "/kb/tags/{name}/scope", this::getTagScope, StationPermission.USER);
        routes.get(prefix + "/kb/files/{id}/tags", this::getFileTags, StationPermission.USER);
        routes.put(prefix + "/kb/files/{id}/tags", this::setFileTags, StationPermission.KNOWLEDGE_EDIT);
        routes.get(prefix + "/kb/folders/{id}/tags", this::getFolderTags, StationPermission.USER);
        routes.put(prefix + "/kb/folders/{id}/tags", this::setFolderTags, StationPermission.KNOWLEDGE_EDIT);

        // KB Comments (local)
        routes.get(prefix + "/kb/files/{fileId}/comments", this::listComments, StationPermission.LOGIN);
        routes.post(prefix + "/kb/files/{fileId}/comments", this::createComment, StationPermission.LOGIN);
        routes.put(prefix + "/kb/comments/{commentId}", this::updateComment, StationPermission.LOGIN);
        routes.delete(prefix + "/kb/comments/{commentId}", this::deleteComment, StationPermission.LOGIN);

        // Federated (user-facing, bearer token auth)
        routes.get(prefix + "/federated/kb", this::federatedBrowseKb, StationPermission.USER);
        routes.get(prefix + "/federated/{stationuid}/kb/files/{id}", this::federatedGetFile, StationPermission.USER);
        routes.get(
                prefix + "/federated/{stationuid}/kb/files/{id}/content",
                this::federatedGetFileContent,
                StationPermission.USER);
        routes.post(
                prefix + "/federated/kb/files/{id}/copy", this::federatedCopyFile, StationPermission.KNOWLEDGE_EDIT);
        routes.post(
                prefix + "/federated/{stationuid}/kb/files/{id}/copy",
                this::federatedCopyFile,
                StationPermission.KNOWLEDGE_EDIT);

        // Federated KB comments (user-facing proxy)
        routes.get(
                prefix + "/federated/{stationuid}/kb/files/{fileId}/comments",
                this::federatedListComments,
                StationPermission.LOGIN);
        routes.post(
                prefix + "/federated/{stationuid}/kb/files/{fileId}/comments",
                this::federatedCreateComment,
                StationPermission.LOGIN);
        routes.put(
                prefix + "/federated/{stationuid}/kb/comments/{commentId}",
                this::federatedUpdateComment,
                StationPermission.LOGIN);
        routes.delete(
                prefix + "/federated/{stationuid}/kb/comments/{commentId}",
                this::federatedDeleteComment,
                StationPermission.LOGIN);

        // Remote (server-to-server, RSA signature auth)
        routes.get(prefix + "/remote/kb/browse", this::remoteBrowseKb);
        routes.get(prefix + "/remote/kb/search", this::remoteSearchKb);
        routes.get(prefix + "/remote/kb/files/{id}", this::remoteGetFile);
        routes.get(prefix + "/remote/kb/files/{id}/content", this::remoteGetFileContent);
        routes.get(prefix + "/remote/kb/files/{fileId}/comments", this::remoteListComments);
        routes.post(prefix + "/remote/kb/files/{fileId}/comments", this::remoteCreateComment);
        routes.put(prefix + "/remote/kb/comments/{commentId}", this::remoteUpdateComment);
        routes.delete(prefix + "/remote/kb/comments/{commentId}", this::remoteDeleteComment);
    }

    // -- Folders --

    private void listFolders(Context ctx) {
        var session = UserSession.from(ctx);
        Integer parentId = ctx.queryParam("parentId") != null
                ? ctx.queryParamAsClass("parentId", Integer.class).get()
                : null;
        ctx.json(service.findFolders(session.stationId(), parentId));
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.findFolder(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void updateFolder(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var folder = service.findFolder(id).orElseThrow(NotFoundResponse::new);
        if (folder.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var folder = service.findFolder(id).orElseThrow(NotFoundResponse::new);
        if (folder.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (!service.deleteFolder(id)) throw new NotFoundResponse();
        ctx.status(204);
    }

    // -- Files --

    private void listFiles(Context ctx) {
        var session = UserSession.from(ctx);
        Integer folderId = ctx.queryParam("folderId") != null
                ? ctx.queryParamAsClass("folderId", Integer.class).get()
                : null;
        ctx.json(service.findFiles(session.stationId(), folderId).stream()
                .map(KbFileSummary::of)
                .toList());
    }

    private void getFile(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        service.findFile(id)
                .ifPresentOrElse(file -> ctx.json(new FileResponse(file, resolveMemberName(file.createdBy()))), () -> {
                    throw new NotFoundResponse();
                });
    }

    private void updateFile(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
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
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        if (!service.deleteFile(id)) throw new NotFoundResponse();
        ctx.status(204);
    }

    // -- File Creation --

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
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("file is required");
        if (file.size() > MAX_UPLOAD_SIZE) throw new BadRequestResponse("File too large (max 50MB)");
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
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("file is required");
        if (file.size() > MAX_UPLOAD_SIZE) throw new BadRequestResponse("File too large (max 50MB)");

        String name = ctx.formParam("name");
        if (name == null || name.isBlank()) {
            name = file.filename();
            // Strip extension for the title
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
        }
        String description = ctx.formParam("description");
        Integer folderId = null;
        String folderIdStr = ctx.formParam("folderId");
        if (folderIdStr != null && !folderIdStr.isBlank()) folderId = Integer.parseInt(folderIdStr);

        // Detect format from filename/mime
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

    // -- Content --

    private void getFileContent(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);

        switch (file.fileType()) {
            case MARKDOWN, TEXT -> {
                var text = service.getMarkdownContent(id);
                if (text.isEmpty()) throw new NotFoundResponse();
                ctx.contentType(ContentType.TEXT_PLAIN);
                ctx.result(text.get());
            }
            case PDF, IMAGE, OTHER -> {
                var data = service.getFileContent(id);
                if (data.isEmpty()) throw new NotFoundResponse();
                ctx.contentType(file.mimeType() != null ? file.mimeType() : "application/octet-stream");
                ctx.header("Content-Disposition", "inline; filename=\"" + file.name() + "\"");
                ctx.result(data.get());
            }
            case PRESENTATION -> {
                // Return the converted PDF for viewing
                var pdf = service.getPresentationPdf(id);
                if (pdf.isEmpty()) throw new NotFoundResponse("Conversion not ready");
                ctx.contentType("application/pdf");
                ctx.header("Content-Disposition", "inline; filename=\"" + file.name() + ".pdf\"");
                ctx.result(pdf.get());
            }
            case YOUTUBE -> ctx.json(new YoutubeResponse(file.youtubeUrl()));
            case LINK -> ctx.json(new LinkResponse(file.linkUrl()));
        }
    }

    private void getMarkdownHtml(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var text = service.getMarkdownContent(id);
        if (text.isEmpty()) throw new NotFoundResponse();
        String html = service.renderMarkdown(text.get());
        ctx.json(new MarkdownHtmlResponse(html, text.get()));
    }

    private void updateMarkdownContent(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(ContentUpdateRequest.class);
        service.updateMarkdownContent(
                id, req.content() != null ? req.content() : "", session.member().id());
        ctx.status(204);
    }

    // -- Presentation Original --

    private void getOriginalFile(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.fileType() != KbFileType.PRESENTATION) {
            throw new BadRequestResponse("Only presentation files have an original");
        }
        var data = service.getFileContent(id);
        if (data.isEmpty()) throw new NotFoundResponse();
        ctx.contentType(file.mimeType() != null ? file.mimeType() : "application/octet-stream");
        ctx.header("Content-Disposition", "attachment; filename=\"" + file.name() + "\"");
        ctx.result(data.get());
    }

    private void reuploadOriginal(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.fileType() != KbFileType.PRESENTATION) {
            throw new BadRequestResponse("Only presentation files support re-upload");
        }
        if (file.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var uploaded = ctx.uploadedFile("file");
        if (uploaded == null) throw new BadRequestResponse("file is required");
        if (uploaded.size() > MAX_UPLOAD_SIZE) throw new BadRequestResponse("File too large (max 50MB)");
        try (var content = uploaded.content()) {
            byte[] data = content.readAllBytes();
            service.reuploadPresentation(id, data, uploaded.contentType(), uploaded.filename());
            ctx.json(service.findFile(id).orElseThrow());
        } catch (Exception e) {
            log.warn("Failed to re-upload presentation file", e);
            throw new InternalServerErrorResponse("Failed to re-upload file");
        }
    }

    // -- Versions --

    private void listVersions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var versions = service.findVersions(id);
        ctx.json(versions.stream()
                .map(v -> new VersionResponse(
                        v.id(),
                        v.version(),
                        v.isFull(),
                        v.createdBy(),
                        resolveMemberName(v.createdBy()),
                        v.createdAt()))
                .toList());
    }

    private void getVersion(Context ctx) {
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        int version = ctx.pathParamAsClass("version", Integer.class).get();
        service.findVersion(fileId, version).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void revertToVersion(Context ctx) {
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        int version = ctx.pathParamAsClass("version", Integer.class).get();
        var session = UserSession.from(ctx);
        var file = service.findFile(fileId).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        service.revertToVersion(fileId, version, session.member().id());
        ctx.status(204);
    }

    // -- Related Files --

    private void getRelatedFiles(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(service.findRelatedFiles(id));
    }

    private void setRelatedFiles(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(RelatedFilesRequest.class);
        service.setRelatedFiles(id, req.fileIds() != null ? req.fileIds() : List.of());
        ctx.json(service.findRelatedFiles(id));
    }

    // -- Search --

    private void search(Context ctx) {
        var session = UserSession.from(ctx);
        String query = ctx.queryParam("q");
        boolean federated = !"false".equals(ctx.queryParam("federated"));
        if (query == null || query.isBlank()) {
            ctx.json(List.of());
            return;
        }

        // Local search
        var localFuture = CompletableFuture.supplyAsync(() -> service.searchWithSnippets(session.stationId(), query));

        // Federated search (parallel)
        var federatedFuture = federated
                ? CompletableFuture.supplyAsync(() -> searchFederated(session.stationId(), query))
                : CompletableFuture.completedFuture(List.<SearchResultResponse>of());

        var localResults = localFuture.join().stream()
                .map(r -> new SearchResultResponse(
                        r.file(), r.snippet(), resolveFolderPath(r.file().folderId()), null, null))
                .toList();

        var fedResults = federatedFuture.join();

        var all = new ArrayList<>(localResults);
        all.addAll(fedResults);
        ctx.json(all);
    }

    private List<SearchResultResponse> searchFederated(int stationId, String query) {
        return service.searchFederatedKb(stationId, query).stream()
                .map(r ->
                        new SearchResultResponse(r.file().toKbFile(), r.snippet(), "", r.stationName(), r.stationUid()))
                .toList();
    }

    // -- Browse (combined) --

    private void browse(Context ctx) {
        var session = UserSession.from(ctx);
        Integer folderId = ctx.queryParam("folderId") != null
                ? ctx.queryParamAsClass("folderId", Integer.class).get()
                : null;
        var folders = service.findFolders(session.stationId(), folderId);
        var files = service.findFiles(session.stationId(), folderId);
        KbFolder currentFolder = folderId != null ? service.findFolder(folderId).orElse(null) : null;

        // Filter by access restrictions unless user has KNOWLEDGE_MANAGER role
        if (!session.hasPermission(StationPermission.KNOWLEDGE_MANAGER)) {
            int memberId = session.member().id();
            StationUserType memberUserType = session.member().userType();
            var memberGroupIds = memberGroupRepository.findGroupsForMember(memberId).stream()
                    .map(MemberGroup::id)
                    .toList();
            var memberTagIds = userTagRepository.findTagsForMember(memberId).stream()
                    .map(UserTag::id)
                    .toList();

            folders = folders.stream()
                    .filter(f ->
                            service.canAccess(memberId, f.id(), null, memberUserType, memberGroupIds, memberTagIds))
                    .toList();
            files = files.stream()
                    .filter(f ->
                            service.canAccess(memberId, null, f.id(), memberUserType, memberGroupIds, memberTagIds))
                    .toList();
        }

        ctx.json(new BrowseResponse(
                currentFolder, folders, files.stream().map(KbFileSummary::of).toList()));
    }

    // -- Access Restrictions --

    private void getFolderRestrictions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var restrictions = service.findRestrictions(id, null);
        ctx.json(toRestrictionResponse(restrictions));
    }

    private void setFolderRestrictions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var folder = service.findFolder(id).orElseThrow(NotFoundResponse::new);
        if (folder.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(RestrictionRequest.class);
        service.setRestrictions(
                id,
                null,
                req.userTypes() != null ? req.userTypes() : List.of(),
                req.groupIds() != null ? req.groupIds() : List.of(),
                req.tagIds() != null ? req.tagIds() : List.of(),
                req.memberIds() != null ? req.memberIds() : List.of());
        ctx.json(toRestrictionResponse(service.findRestrictions(id, null)));
    }

    private void getFileRestrictions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var restrictions = service.findRestrictions(null, id);
        ctx.json(toRestrictionResponse(restrictions));
    }

    private void setFileRestrictions(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(RestrictionRequest.class);
        service.setRestrictions(
                null,
                id,
                req.userTypes() != null ? req.userTypes() : List.of(),
                req.groupIds() != null ? req.groupIds() : List.of(),
                req.tagIds() != null ? req.tagIds() : List.of(),
                req.memberIds() != null ? req.memberIds() : List.of());
        ctx.json(toRestrictionResponse(service.findRestrictions(null, id)));
    }

    private RestrictionResponse toRestrictionResponse(List<KbAccessRestriction> restrictions) {
        var userTypes = restrictions.stream()
                .map(KbAccessRestriction::userType)
                .filter(Objects::nonNull)
                .toList();
        var groupIds = restrictions.stream()
                .map(KbAccessRestriction::groupId)
                .filter(Objects::nonNull)
                .toList();
        var tagIds = restrictions.stream()
                .map(KbAccessRestriction::tagId)
                .filter(Objects::nonNull)
                .toList();
        var memberIds = restrictions.stream()
                .map(KbAccessRestriction::memberId)
                .filter(Objects::nonNull)
                .toList();
        return new RestrictionResponse(userTypes, groupIds, tagIds, memberIds);
    }

    // -- Public Visibility --

    private void getFilePublicVisibility(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var visible = service.findPublicVisibility(null, id).orElse(null);
        ctx.json(new PublicVisibilityResponse(visible));
    }

    private void setFilePublicVisibility(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(PublicVisibilityRequest.class);
        if (req.visible() == null) {
            service.removePublicVisibility(null, id);
        } else {
            service.setPublicVisibility(null, id, req.visible());
        }
        ctx.json(new PublicVisibilityResponse(req.visible()));
    }

    private void getFolderPublicVisibility(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var visible = service.findPublicVisibility(id, null).orElse(null);
        ctx.json(new PublicVisibilityResponse(visible));
    }

    private void setFolderPublicVisibility(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var folder = service.findFolder(id).orElseThrow(NotFoundResponse::new);
        if (folder.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(PublicVisibilityRequest.class);
        if (req.visible() == null) {
            service.removePublicVisibility(id, null);
        } else {
            service.setPublicVisibility(id, null, req.visible());
        }
        ctx.json(new PublicVisibilityResponse(req.visible()));
    }

    // -- Request/Response Records --

    public record FolderRequest(Integer parentId, String name, String description, String iconUrl, Integer position) {}

    public record PublicVisibilityRequest(Boolean visible) {}

    public record PublicVisibilityResponse(Boolean visible) {}

    public record FileUpdateRequest(String name, String description, String iconUrl, Integer position) {}

    public record MarkdownFileRequest(Integer folderId, String name, String description, String content) {}

    public record YoutubeFileRequest(Integer folderId, String name, String description, String youtubeUrl) {}

    public record LinkFileRequest(Integer folderId, String name, String description, String linkUrl) {}

    public record RestrictionRequest(
            List<String> userTypes, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {}

    public record ContentUpdateRequest(String content) {}

    public record YoutubeResponse(String youtubeUrl) {}

    public record LinkResponse(String linkUrl) {}

    public record MarkdownHtmlResponse(String html, String markdown) {}

    public record RestrictionResponse(
            List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {}

    public record BrowseResponse(KbFolder currentFolder, List<KbFolder> folders, List<KbFileSummary> files) {}

    // -- Folder Icons --

    private void getFolderIcon(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(256);
        imageService
                .read(ImageCategory.KB_ICONS, "folder-" + id, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "private, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    private void uploadFolderIcon(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var folder = service.findFolder(id).orElseThrow(NotFoundResponse::new);
        if (folder.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var file = ctx.uploadedFile("icon");
        if (file == null) throw new BadRequestResponse("No file uploaded");
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            imageService.store(ImageCategory.KB_ICONS, "folder-" + id, data, file.contentType(), 5 * 1024 * 1024);
            // Mark folder as having an icon so the frontend shows it
            service.updateFolder(id, folder.name(), folder.description(), "folder-" + id, folder.position());
            ctx.json(new MessageResponse("Icon updated"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid argument storing folder icon for folder {}", id, e);
            throw new BadRequestResponse(e.getMessage());
        } catch (IOException e) {
            log.error("Failed to process image", e);
            throw new InternalServerErrorResponse("Failed to process image");
        }
    }

    // -- KB Images --

    private void uploadKbImage(Context ctx) {
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        var session = UserSession.from(ctx);
        var kbFile = service.findFile(fileId).orElseThrow(NotFoundResponse::new);
        if (kbFile.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var file = ctx.uploadedFile("image");
        if (file == null) throw new BadRequestResponse("No image uploaded");
        if (!ALLOWED_IMAGE_TYPES.contains(file.contentType())) {
            throw new BadRequestResponse("Invalid file type. Allowed: PNG, JPEG, WebP");
        }
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            String imageId = "file-" + fileId + "-" + System.currentTimeMillis();
            imageService.store(ImageCategory.KB_IMAGES, imageId, data, file.contentType(), 10 * 1024 * 1024);
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
        String imageId = ctx.pathParam("imageId");
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(1024);
        imageService
                .read(ImageCategory.KB_IMAGES, imageId, size)
                .ifPresentOrElse(
                        img -> {
                            ctx.contentType(img.contentType());
                            ctx.header("Cache-Control", "private, max-age=300");
                            ctx.result(img.data());
                        },
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    // -- Tags --

    private void listTags(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(service.findTagsByStation(session.stationId()));
    }

    private void getFileTags(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(service.findFileTags(id));
    }

    private void setFileTags(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var file = service.findFile(id).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(TagRequest.class);
        ctx.json(service.setFileTags(id, req.tags(), session.stationId()));
    }

    private void getFolderTags(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(service.findFolderTags(id));
    }

    private void getTagScope(Context ctx) {
        var session = UserSession.from(ctx);
        String tagName = ctx.pathParam("name");
        int stationId = session.stationId();

        var matchingFiles = service.findFilesByTag(stationId, tagName);
        var allFolders = service.findAllFolders(stationId);

        // Apply access restrictions for non-managers.
        if (!session.hasPermission(StationPermission.KNOWLEDGE_MANAGER)) {
            int memberId = session.member().id();
            StationUserType memberUserType = session.member().userType();
            var memberGroupIds = memberGroupRepository.findGroupsForMember(memberId).stream()
                    .map(MemberGroup::id)
                    .toList();
            var memberTagIds = userTagRepository.findTagsForMember(memberId).stream()
                    .map(UserTag::id)
                    .toList();
            matchingFiles = matchingFiles.stream()
                    .filter(f ->
                            service.canAccess(memberId, null, f.id(), memberUserType, memberGroupIds, memberTagIds))
                    .toList();
        }

        // Build parent lookup map for folder ancestry traversal.
        var folderParent = new java.util.HashMap<Integer, Integer>();
        for (var folder : allFolders) {
            folderParent.put(folder.id(), folder.parentId());
        }

        var matchingFileIds = matchingFiles.stream().map(KbFile::id).toList();
        var ancestorFolderIds = new java.util.HashSet<Integer>();
        for (var file : matchingFiles) {
            Integer cur = file.folderId();
            while (cur != null && !ancestorFolderIds.contains(cur)) {
                ancestorFolderIds.add(cur);
                cur = folderParent.get(cur);
            }
        }

        ctx.json(new TagScopeResponse(matchingFileIds, new java.util.ArrayList<>(ancestorFolderIds)));
    }

    public record TagScopeResponse(List<Integer> matchingFileIds, List<Integer> ancestorFolderIds) {}

    private void setFolderTags(Context ctx) {
        var session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var folder = service.findFolder(id).orElseThrow(NotFoundResponse::new);
        if (folder.stationId() != session.stationId()) {
            throw new ForbiddenResponse("Cannot access resources from another station");
        }
        var req = ctx.bodyAsClass(TagRequest.class);
        ctx.json(service.setFolderTags(id, req.tags(), session.stationId()));
    }

    public record TagRequest(List<String> tags) {}

    public record RelatedFilesRequest(List<Integer> fileIds) {}

    public record FileResponse(KbFile file, String lastEditedByName) {
        // Jackson will serialize both the file fields and the name
    }

    public record VersionResponse(
            int id, int version, boolean isFull, int createdBy, String createdByName, Instant createdAt) {}

    public record SearchResultResponse(
            KbFile file, String snippet, String folderPath, String stationName, String sourceStationId) {}

    public record ImageUploadResponse(String imageId) {}

    public record FederatedKbItem(
            int remoteId, String title, String description, String stationName, int stationId, int partnerId) {}

    public record FileContentResponse(int fileId, String content) {}

    public record RemoteKbFileSummary(int id, String name, String description, String fileType, String updatedAt) {}

    public record RemoteKbSearchResultItem(int id, String name, String description, String snippet) {}

    // -- Federated endpoints (user-facing, aggregates from partners) --

    private void federatedBrowseKb(Context ctx) {
        var session = UserSession.from(ctx);
        var items = service.browseSharedKb(session.stationId());
        ctx.json(items.stream()
                .map(i -> {
                    String name = stationRepository
                            .findById(i.sourceStationId())
                            .map(Station::name)
                            .orElse("Unknown");
                    return new FederatedKbItem(
                            i.file().id(),
                            i.file().name(),
                            i.file().description() != null ? i.file().description() : "",
                            name,
                            i.sourceStationId(),
                            i.partnerId());
                })
                .toList());
    }

    private void federatedGetFile(Context ctx) {
        var session = UserSession.from(ctx);
        var stationUid = UUID.fromString(ctx.pathParam("stationuid"));
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(service.getFederatedKbFile(session.stationId(), stationUid, fileId));
    }

    private void federatedGetFileContent(Context ctx) {
        var session = UserSession.from(ctx);
        var stationUid = UUID.fromString(ctx.pathParam("stationuid"));
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        var content = service.getFederatedKbFileContent(session.stationId(), stationUid, fileId);
        ctx.json(new FileContentResponse(fileId, content));
    }

    private void federatedCopyFile(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        var copied =
                service.copyKbFile(fileId, session.stationId(), session.member().id());
        ctx.status(HttpStatus.CREATED).json(copied);
    }

    // -- Remote endpoints (server-to-server, RSA signature auth) --

    private void remoteBrowseKb(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var shares = federationRepository.findKbShares(partner.stationId());
        var result = shares.stream()
                .filter(s -> s.fileId() != null)
                .flatMap(s -> service.findFile(s.fileId()).stream())
                .filter(file -> file.stationId() == partner.stationId())
                .map(file -> new RemoteKbFileSummary(
                        file.id(),
                        file.name(),
                        file.description() != null ? file.description() : "",
                        file.fileType().name(),
                        file.updatedAt().toString()))
                .toList();
        ctx.json(result);
    }

    private void remoteSearchKb(Context ctx) {
        var partner = requireFederationPartner(ctx);
        String query = ctx.queryParam("q");
        if (query == null || query.isBlank()) {
            ctx.json(List.of());
            return;
        }
        var results = service.searchWithSnippets(partner.stationId(), query);
        var shares = federationRepository.findKbShares(partner.stationId());
        var sharedFileIds = shares.stream()
                .map(FederationShare::fileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        ctx.json(results.stream()
                .filter(r -> sharedFileIds.contains(r.file().id()))
                .map(r -> new RemoteKbSearchResultItem(
                        r.file().id(),
                        r.file().name(),
                        r.file().description() != null ? r.file().description() : "",
                        r.snippet() != null ? r.snippet() : ""))
                .toList());
    }

    private void remoteGetFile(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        var file = service.findFile(fileId).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("File not shared with this partner");
        }
        ctx.json(file);
    }

    private void remoteGetFileContent(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        var file = service.findFile(fileId).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("File not shared with this partner");
        }
        var content = service.getMarkdownContent(fileId).orElse("");
        ctx.json(new FileContentResponse(fileId, content));
    }

    // -- Local KB comment endpoints --

    private void listComments(Context ctx) {
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        var comments = kbCommentRepository.findByFile(fileId);
        ctx.json(comments.stream().map(this::toCommentResponse).toList());
    }

    private void createComment(Context ctx) {
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateKbCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        String authorName = resolveMemberName(session.member().id());
        var comment = service.createComment(
                session.stationId(), fileId, req.parentId(), session.member().id(), authorName, req.content());
        ctx.status(HttpStatus.CREATED).json(toCommentResponse(comment));
    }

    private void updateComment(Context ctx) {
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var comment = kbCommentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
        var memberIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        if (comment.author() == null || !comment.author().sameMember(memberIdentity)) {
            throw new ForbiddenResponse("You can only edit your own comments");
        }
        var req = ctx.bodyAsClass(UpdateKbCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        kbCommentRepository.update(commentId, req.content());
        var updated = kbCommentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
        ctx.json(toCommentResponse(updated));
    }

    private void deleteComment(Context ctx) {
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        UserSession session = UserSession.from(ctx);
        var comment = kbCommentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
        var authorIdentity = memberIdentityFactory.local(
                session.stationId(), session.member().id());
        boolean isAuthor = comment.author() != null && comment.author().sameMember(authorIdentity);
        boolean canModerate = session.hasPermission(StationPermission.KNOWLEDGE_MANAGER);
        if (!isAuthor && !canModerate) {
            throw new ForbiddenResponse("You can only delete your own comments");
        }
        if (kbCommentRepository.delete(commentId)) {
            String preview =
                    comment.content().length() > 100 ? comment.content().substring(0, 100) + "..." : comment.content();
            eventBus.publish(new CommentDeleted(session.stationId(), commentId, preview));
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Remote KB comment endpoints (server-to-server) --

    private void remoteListComments(Context ctx) {
        requireFederationPartner(ctx);
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        var comments = kbCommentRepository.findByFile(fileId);
        ctx.json(comments.stream().map(this::toCommentResponse).toList());
    }

    private void remoteCreateComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteKbCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var authorIdentity = new MemberIdentity(partner.partnerStationId(), req.remoteMemberUid());
        var comment = kbCommentRepository.create(fileId, req.parentId(), authorIdentity, req.content());
        eventFederationRepository.cacheName(partner.id(), req.remoteMemberUid(), req.displayName());
        ctx.status(HttpStatus.CREATED).json(toCommentResponse(comment));
    }

    private void remoteUpdateComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteKbCommentUpdateRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var comment = kbCommentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
        var expectedAuthor = new MemberIdentity(partner.partnerStationId(), req.remoteMemberUid());
        if (comment.author() == null || !comment.author().sameMember(expectedAuthor)) {
            throw new ForbiddenResponse("You can only edit your own comments");
        }
        kbCommentRepository.update(commentId, req.content());
        var updated = kbCommentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
        ctx.json(toCommentResponse(updated));
    }

    private void remoteDeleteComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteKbCommentDeleteRequest.class);
        var comment = kbCommentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
        var expectedAuthor = new MemberIdentity(partner.partnerStationId(), req.remoteMemberUid());
        if (comment.author() == null || !comment.author().sameMember(expectedAuthor)) {
            throw new ForbiddenResponse("You can only delete your own comments");
        }
        if (kbCommentRepository.delete(commentId)) {
            String preview =
                    comment.content().length() > 100 ? comment.content().substring(0, 100) + "..." : comment.content();
            eventBus.publish(new CommentDeleted(partner.stationId(), commentId, preview));
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    // -- Federated KB comment proxy endpoints (user-facing) --

    private void federatedListComments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();

        if (partner.isRemote()) {
            var result = federationHttpClient.getList(
                    partner.remoteHost(),
                    "/remote/kb/files/" + fileId + "/comments",
                    station.id(),
                    station.federationPrivateKey(),
                    KbCommentResponse.class);
            ctx.json(result);
        } else {
            var comments = kbCommentRepository.findByFile(fileId);
            ctx.json(comments.stream().map(this::toCommentResponse).toList());
        }
    }

    private void federatedCreateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        var req = ctx.bodyAsClass(CreateKbCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }

        UUID memberUid = session.member().uid();
        String displayName = session.account().fullName().trim();

        if (partner.isRemote()) {
            var body = new RemoteKbCommentRequest(memberUid, displayName, req.parentId(), req.content());
            var result = federationHttpClient.post(
                    partner.remoteHost(),
                    "/remote/kb/files/" + fileId + "/comments",
                    body,
                    station.id(),
                    station.federationPrivateKey(),
                    KbCommentResponse.class);
            if (result == null) throw new InternalServerErrorResponse("Failed to create comment on partner");
            ctx.status(HttpStatus.CREATED).json(result);
        } else {
            var authorId = new MemberIdentity(partner.partnerStationId(), memberUid);
            var comment = kbCommentRepository.create(fileId, req.parentId(), authorId, req.content());
            eventFederationRepository.cacheName(partner.id(), memberUid, displayName);
            ctx.status(HttpStatus.CREATED).json(toCommentResponse(comment));
        }
    }

    private void federatedUpdateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(UpdateKbCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }

        UUID memberUid = session.member().uid();

        if (partner.isRemote()) {
            var body = new RemoteKbCommentUpdateRequest(memberUid, req.content());
            var result = federationHttpClient.put(
                    partner.remoteHost(),
                    "/remote/kb/comments/" + commentId,
                    body,
                    station.id(),
                    station.federationPrivateKey(),
                    KbCommentResponse.class);
            if (result == null) throw new InternalServerErrorResponse("Failed to update comment on partner");
            ctx.json(result);
        } else {
            var existingComment = kbCommentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
            if (existingComment.author() == null
                    || !existingComment.author().memberUid().equals(memberUid)) {
                throw new ForbiddenResponse("You can only edit your own comments");
            }
            kbCommentRepository.update(commentId, req.content());
            var updated = kbCommentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
            ctx.json(toCommentResponse(updated));
        }
    }

    private void federatedDeleteComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();

        UUID memberUid = session.member().uid();

        if (partner.isRemote()) {
            boolean success = federationHttpClient.delete(
                    partner.remoteHost(),
                    "/remote/kb/comments/" + commentId,
                    station.id(),
                    station.federationPrivateKey());
            if (!success) throw new InternalServerErrorResponse("Failed to delete comment on partner");
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            var existingComment = kbCommentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
            if (existingComment.author() == null
                    || !existingComment.author().memberUid().equals(memberUid)) {
                throw new ForbiddenResponse("You can only delete your own comments");
            }
            if (kbCommentRepository.delete(commentId)) {
                ctx.status(HttpStatus.NO_CONTENT);
            } else {
                throw new NotFoundResponse();
            }
        }
    }

    // -- Federation helpers --

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    private FederationPartner resolvePartner(Context ctx, int stationId) {
        var partnerUid = UUID.fromString(ctx.pathParam("stationuid"));
        return federationRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"));
    }

    // -- KB Comment response mapping --

    private KbCommentResponse toCommentResponse(KbComment comment) {
        if (comment.deleted()) {
            return new KbCommentResponse(
                    comment.id(),
                    comment.fileId(),
                    comment.parentId(),
                    null,
                    null,
                    "",
                    true,
                    comment.createdAt(),
                    null);
        }

        // Resolve author name and display metadata from inline identity
        var identity = comment.author();
        String authorName = "";
        if (identity != null) {
            var resolved = memberNameResolver.resolveDisplay(identity);
            identity = resolved.identity();
            authorName = resolved.name() != null ? resolved.name() : "";
        }
        return new KbCommentResponse(
                comment.id(),
                comment.fileId(),
                comment.parentId(),
                identity,
                authorName,
                comment.content(),
                false,
                comment.createdAt(),
                comment.updatedAt());
    }

    // -- KB Comment request/response records --

    public record CreateKbCommentRequest(Integer parentId, String content) {}

    public record UpdateKbCommentRequest(String content) {}

    public record RemoteKbCommentRequest(UUID remoteMemberUid, String displayName, Integer parentId, String content) {}

    public record RemoteKbCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    public record RemoteKbCommentDeleteRequest(UUID remoteMemberUid) {}

    public record KbCommentResponse(
            int id,
            int fileId,
            Integer parentId,
            MemberIdentity author,
            String authorName,
            String content,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt) {}
}
