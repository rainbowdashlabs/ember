/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederatedContentService;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessRestriction;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.service.KnowledgeBaseService;
import dev.chojo.ember.feature.media.service.ImageCategory;
import dev.chojo.ember.feature.media.service.ImageService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

@Singleton
public class KnowledgeBaseRoutes implements Routes {
    private static final Logger log = getLogger(KnowledgeBaseRoutes.class);
    private static final long MAX_UPLOAD_SIZE = 50 * 1024 * 1024; // 50 MB

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final KnowledgeBaseService service;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;
    private final ImageService imageService;
    private final FederationService federationService;
    private final FederatedContentService federatedContentService;
    private final FederationRepository federationRepository;
    private final FederationHttpClient federationHttpClient;
    private final StationRepository stationRepository;

    @Inject
    public KnowledgeBaseRoutes(
            KnowledgeBaseService service,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository,
            ImageService imageService,
            FederationService federationService,
            FederatedContentService federatedContentService,
            FederationRepository federationRepository,
            FederationHttpClient federationHttpClient,
            StationRepository stationRepository) {
        this.service = service;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
        this.imageService = imageService;
        this.federationService = federationService;
        this.federatedContentService = federatedContentService;
        this.federationRepository = federationRepository;
        this.federationHttpClient = federationHttpClient;
        this.stationRepository = stationRepository;
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
        routes.get(prefix + "/kb/folders", this::listFolders, Roles.USER);
        routes.post(prefix + "/kb/folders", this::createFolder, Roles.KNOWLEDGE_MANAGER);
        routes.get(prefix + "/kb/folders/{id}", this::getFolder, Roles.USER);
        routes.put(prefix + "/kb/folders/{id}", this::updateFolder, Roles.KNOWLEDGE_MANAGER);
        routes.delete(prefix + "/kb/folders/{id}", this::deleteFolder, Roles.KNOWLEDGE_MANAGER);

        // Files
        routes.get(prefix + "/kb/files", this::listFiles, Roles.USER);
        routes.get(prefix + "/kb/files/{id}", this::getFile, Roles.USER);
        routes.put(prefix + "/kb/files/{id}", this::updateFile, Roles.KNOWLEDGE_MANAGER);
        routes.delete(prefix + "/kb/files/{id}", this::deleteFile, Roles.KNOWLEDGE_MANAGER);

        // File creation
        routes.post(prefix + "/kb/files/markdown", this::createMarkdownFile, Roles.KNOWLEDGE_MANAGER);
        routes.post(prefix + "/kb/files/youtube", this::createYoutubeFile, Roles.KNOWLEDGE_MANAGER);
        routes.post(prefix + "/kb/files/upload", this::uploadFile, Roles.KNOWLEDGE_MANAGER);
        routes.post(prefix + "/kb/files/import-document", this::importDocument, Roles.KNOWLEDGE_MANAGER);
        routes.post(prefix + "/kb/files/link", this::createLinkFile, Roles.KNOWLEDGE_MANAGER);

        // File content
        routes.get(prefix + "/kb/files/{id}/content", this::getFileContent, Roles.USER);
        routes.get(prefix + "/kb/files/{id}/html", this::getMarkdownHtml, Roles.USER);
        routes.put(prefix + "/kb/files/{id}/content", this::updateMarkdownContent, Roles.KNOWLEDGE_MANAGER);

        // Versions (markdown only)
        routes.get(prefix + "/kb/files/{id}/versions", this::listVersions, Roles.KNOWLEDGE_MANAGER);
        routes.get(prefix + "/kb/files/{id}/versions/{version}", this::getVersion, Roles.KNOWLEDGE_MANAGER);
        routes.post(
                prefix + "/kb/files/{id}/versions/{version}/revert", this::revertToVersion, Roles.KNOWLEDGE_MANAGER);

        // Related files
        routes.get(prefix + "/kb/files/{id}/related", this::getRelatedFiles, Roles.USER);
        routes.put(prefix + "/kb/files/{id}/related", this::setRelatedFiles, Roles.KNOWLEDGE_MANAGER);

        // Search
        routes.get(prefix + "/kb/search", this::search, Roles.USER);

        // Browse (combined folders + files for a given parent)
        routes.get(prefix + "/kb/browse", this::browse, Roles.USER);

        // Access restrictions
        routes.get(prefix + "/kb/folders/{id}/restrictions", this::getFolderRestrictions, Roles.KNOWLEDGE_MANAGER);
        routes.put(prefix + "/kb/folders/{id}/restrictions", this::setFolderRestrictions, Roles.KNOWLEDGE_MANAGER);
        routes.get(prefix + "/kb/files/{id}/restrictions", this::getFileRestrictions, Roles.KNOWLEDGE_MANAGER);
        routes.put(prefix + "/kb/files/{id}/restrictions", this::setFileRestrictions, Roles.KNOWLEDGE_MANAGER);

        // Folder icons
        routes.get(prefix + "/kb/folders/{id}/icon", this::getFolderIcon, Roles.USER);
        routes.post(prefix + "/kb/folders/{id}/icon", this::uploadFolderIcon, Roles.KNOWLEDGE_MANAGER);

        // KB Images (for markdown embedding)
        routes.post(prefix + "/kb/files/{id}/images", this::uploadKbImage, Roles.KNOWLEDGE_MANAGER);
        routes.get(prefix + "/kb/images/{imageId}", this::getKbImage, Roles.USER);

        // Tags
        routes.get(prefix + "/kb/tags", this::listTags, Roles.USER);
        routes.get(prefix + "/kb/files/{id}/tags", this::getFileTags, Roles.USER);
        routes.put(prefix + "/kb/files/{id}/tags", this::setFileTags, Roles.KNOWLEDGE_MANAGER);
        routes.get(prefix + "/kb/folders/{id}/tags", this::getFolderTags, Roles.USER);
        routes.put(prefix + "/kb/folders/{id}/tags", this::setFolderTags, Roles.KNOWLEDGE_MANAGER);

        // Federated (user-facing, bearer token auth)
        routes.get(prefix + "/federated/kb", this::federatedBrowseKb, Roles.USER);
        routes.get(prefix + "/federated/{stationuid}/kb/files/{id}", this::federatedGetFile, Roles.USER);
        routes.get(prefix + "/federated/{stationuid}/kb/files/{id}/content", this::federatedGetFileContent, Roles.USER);
        routes.post(prefix + "/federated/kb/files/{id}/copy", this::federatedCopyFile, Roles.KNOWLEDGE_MANAGER);
        routes.post(
                prefix + "/federated/{stationuid}/kb/files/{id}/copy",
                this::federatedCopyFile,
                Roles.KNOWLEDGE_MANAGER);

        // Remote (server-to-server, RSA signature auth)
        routes.get(prefix + "/remote/kb/browse", this::remoteBrowseKb);
        routes.get(prefix + "/remote/kb/search", this::remoteSearchKb);
        routes.get(prefix + "/remote/kb/files/{id}", this::remoteGetFile);
        routes.get(prefix + "/remote/kb/files/{id}/content", this::remoteGetFileContent);
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
        ctx.json(service.findFiles(session.stationId(), folderId));
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
        return federatedContentService.searchFederatedKb(stationId, query).stream()
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
        if (!session.hasRole(Roles.KNOWLEDGE_MANAGER)) {
            int memberId = session.member().id();
            var memberRoleIds = stationMemberRepository.findRoles(memberId).stream()
                    .map(Role::id)
                    .toList();
            var memberGroupIds = memberGroupRepository.findGroupsForMember(memberId).stream()
                    .map(MemberGroup::id)
                    .toList();
            var memberTagIds = userTagRepository.findTagsForMember(memberId).stream()
                    .map(UserTag::id)
                    .toList();

            folders = folders.stream()
                    .filter(f -> service.canAccess(memberId, f.id(), null, memberRoleIds, memberGroupIds, memberTagIds))
                    .toList();
            files = files.stream()
                    .filter(f -> service.canAccess(memberId, null, f.id(), memberRoleIds, memberGroupIds, memberTagIds))
                    .toList();
        }

        ctx.json(new BrowseResponse(currentFolder, folders, files));
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
                req.roleIds() != null ? req.roleIds() : List.of(),
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
                req.roleIds() != null ? req.roleIds() : List.of(),
                req.groupIds() != null ? req.groupIds() : List.of(),
                req.tagIds() != null ? req.tagIds() : List.of(),
                req.memberIds() != null ? req.memberIds() : List.of());
        ctx.json(toRestrictionResponse(service.findRestrictions(null, id)));
    }

    private RestrictionResponse toRestrictionResponse(List<KbAccessRestriction> restrictions) {
        var roleIds = restrictions.stream()
                .filter(r -> r.roleId() != null)
                .map(KbAccessRestriction::roleId)
                .toList();
        var groupIds = restrictions.stream()
                .filter(r -> r.groupId() != null)
                .map(KbAccessRestriction::groupId)
                .toList();
        var tagIds = restrictions.stream()
                .filter(r -> r.tagId() != null)
                .map(KbAccessRestriction::tagId)
                .toList();
        var memberIds = restrictions.stream()
                .filter(r -> r.memberId() != null)
                .map(KbAccessRestriction::memberId)
                .toList();
        return new RestrictionResponse(roleIds, groupIds, tagIds, memberIds);
    }

    // -- Request/Response Records --

    public record FolderRequest(Integer parentId, String name, String description, String iconUrl, Integer position) {}

    public record FileUpdateRequest(String name, String description, String iconUrl, Integer position) {}

    public record MarkdownFileRequest(Integer folderId, String name, String description, String content) {}

    public record YoutubeFileRequest(Integer folderId, String name, String description, String youtubeUrl) {}

    public record LinkFileRequest(Integer folderId, String name, String description, String linkUrl) {}

    public record RestrictionRequest(
            List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {}

    public record ContentUpdateRequest(String content) {}

    public record YoutubeResponse(String youtubeUrl) {}

    public record LinkResponse(String linkUrl) {}

    public record MarkdownHtmlResponse(String html, String markdown) {}

    public record RestrictionResponse(
            List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {}

    public record BrowseResponse(KbFolder currentFolder, List<KbFolder> folders, List<KbFile> files) {}

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

    // -- Federated endpoints (user-facing, aggregates from partners) --

    private void federatedBrowseKb(Context ctx) {
        var session = UserSession.from(ctx);
        var items = federatedContentService.browseSharedKb(session.stationId());
        ctx.json(items.stream()
                .map(i -> {
                    String name = stationRepository
                            .findById(i.sourceStationId())
                            .map(Station::name)
                            .orElse("Unknown");
                    return Map.of(
                            "remoteId", i.file().id(),
                            "title", i.file().name(),
                            "description",
                                    i.file().description() != null ? i.file().description() : "",
                            "stationName", name,
                            "stationId", i.sourceStationId(),
                            "partnerId", i.partnerId());
                })
                .toList());
    }

    private void federatedGetFile(Context ctx) {
        var session = UserSession.from(ctx);
        var stationUid = UUID.fromString(ctx.pathParam("stationuid"));
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(federatedContentService.getFederatedKbFile(session.stationId(), stationUid, fileId));
    }

    private void federatedGetFileContent(Context ctx) {
        var session = UserSession.from(ctx);
        var stationUid = UUID.fromString(ctx.pathParam("stationuid"));
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        var content = federatedContentService.getFederatedKbFileContent(session.stationId(), stationUid, fileId);
        ctx.json(Map.of("fileId", fileId, "content", content));
    }

    private void federatedCopyFile(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = ctx.pathParamAsClass("id", Integer.class).get();
        var copied = federatedContentService.copyKbFile(
                fileId, session.stationId(), session.member().id());
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
                .map(file -> Map.<String, Object>of(
                        "id", file.id(),
                        "name", file.name(),
                        "description", file.description() != null ? file.description() : "",
                        "fileType", file.fileType().name(),
                        "updatedAt", file.updatedAt().toString()))
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
                .filter(s -> s.fileId() != null)
                .map(FederationShare::fileId)
                .collect(Collectors.toSet());
        ctx.json(results.stream()
                .filter(r -> sharedFileIds.contains(r.file().id()))
                .map(r -> Map.<String, Object>of(
                        "id",
                        r.file().id(),
                        "name",
                        r.file().name(),
                        "description",
                        r.file().description() != null ? r.file().description() : "",
                        "snippet",
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
        ctx.json(Map.of("fileId", fileId, "content", content));
    }

    // -- Federation helpers --

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }
}
