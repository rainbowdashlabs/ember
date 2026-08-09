/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.page.entity.CellConfig;
import dev.chojo.ember.feature.page.entity.CellContentType;
import dev.chojo.ember.feature.page.entity.PageFile;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.MemberListResolver;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

@Singleton
public class PageRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(PageRoutes.class);

    private final PageService pageService;
    private final FormService formService;
    private final FormAnalyticsAssembler formAnalyticsAssembler;
    private final StationMemberRepository stationMemberRepository;
    private final AvatarService avatarService;
    private final Api apiConfig;

    @Inject
    public PageRoutes(
            PageService pageService,
            FormService formService,
            FormAnalyticsAssembler formAnalyticsAssembler,
            StationMemberRepository stationMemberRepository,
            AvatarService avatarService,
            Api apiConfig) {
        this.pageService = pageService;
        this.formService = formService;
        this.formAnalyticsAssembler = formAnalyticsAssembler;
        this.stationMemberRepository = stationMemberRepository;
        this.avatarService = avatarService;
        this.apiConfig = apiConfig;
    }

    /**
     * Loads a page and asserts it belongs to the caller's station, returning it. Answers 404
     * when the page is absent or owned by another station, so a page id from one station cannot
     * be read, edited, published, duplicated, or deleted by another station.
     */
    private StationPage requireOwnedPage(Context ctx, int pageId) {
        return requireOwnedOrNotFound(ctx, pageId, pageService::getPage, StationPage::stationId);
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/pages", this::list, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages", this::create, StationPermission.PAGE_EDIT);
        // Register the literal "/pages/files" and "/pages/landing" paths BEFORE the variable
        // "/pages/{pid}" routes so Javalin matches them as literal segments instead of trying to
        // parse "files"/"landing" as an int page id.
        routes.get(prefix + "/pages/files", this::listFiles, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages/files", this::uploadStationFile, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages/files/prune", this::pruneFiles, StationPermission.PAGE_MANAGER);
        routes.put(prefix + "/pages/files/{fileId}", this::updateFileMeta, StationPermission.PAGE_EDIT);
        routes.delete(prefix + "/pages/files/{fileId}", this::deleteFile, StationPermission.PAGE_MANAGER);
        routes.put(prefix + "/pages/files/{fileId}/folder", this::moveFileFolder, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages/files/{fileId}/tags/{tagId}", this::assignTag, StationPermission.PAGE_EDIT);
        routes.delete(prefix + "/pages/files/{fileId}/tags/{tagId}", this::unassignTag, StationPermission.PAGE_EDIT);
        routes.get(prefix + "/pages/folders", this::listFolders, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages/folders", this::createFolder, StationPermission.PAGE_EDIT);
        routes.put(prefix + "/pages/folders/{folderId}", this::updateFolder, StationPermission.PAGE_EDIT);
        routes.delete(prefix + "/pages/folders/{folderId}", this::deleteFolder, StationPermission.PAGE_EDIT);
        routes.get(prefix + "/pages/tags", this::listTags, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages/tags", this::createTag, StationPermission.PAGE_EDIT);
        routes.put(prefix + "/pages/tags/{tagId}", this::updateTag, StationPermission.PAGE_EDIT);
        routes.delete(prefix + "/pages/tags/{tagId}", this::deleteTag, StationPermission.PAGE_EDIT);
        routes.put(prefix + "/pages/landing", this::setLandingPage, StationPermission.PAGE_MANAGER);
        routes.get(prefix + "/pages/search", this::searchPicker, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages/member-list/resolve", this::resolveMemberList, StationPermission.PAGE_EDIT);
        routes.get(
                prefix + "/pages/polls/forms/{id}/analytics",
                ctx -> getFormAnalytics(ctx, FormPurpose.POLL),
                StationPermission.PAGE_POLLS_VIEW);
        routes.get(
                prefix + "/pages/polls/forms/{id}/responses",
                ctx -> listFormResponses(ctx, FormPurpose.POLL),
                StationPermission.PAGE_POLLS_VIEW);
        routes.get(
                prefix + "/pages/polls/forms/{id}/responses/{responseId}",
                ctx -> getFormResponseDetail(ctx, FormPurpose.POLL),
                StationPermission.PAGE_POLLS_VIEW);
        routes.get(
                prefix + "/pages/forms/{id}/responses",
                ctx -> listFormResponses(ctx, FormPurpose.CONTACT),
                StationPermission.PAGE_FORMS_VIEW);
        routes.get(
                prefix + "/pages/forms/{id}/responses/{responseId}",
                ctx -> getFormResponseDetail(ctx, FormPurpose.CONTACT),
                StationPermission.PAGE_FORMS_VIEW);
        routes.post(
                prefix + "/pages/forms/{id}/responses/{responseId}/acknowledge",
                ctx -> acknowledgeFormResponse(ctx, FormPurpose.CONTACT),
                StationPermission.PAGE_FORMS_VIEW);
        routes.get(prefix + "/pages/{pid}", this::get, StationPermission.PAGE_EDIT);
        routes.put(prefix + "/pages/{pid}", this::save, StationPermission.PAGE_EDIT);
        routes.post(prefix + "/pages/{pid}/duplicate", this::duplicate, StationPermission.PAGE_EDIT);
        routes.delete(prefix + "/pages/{pid}", this::delete, StationPermission.PAGE_MANAGER);
        routes.put(prefix + "/pages/{pid}/publish", this::togglePublish, StationPermission.PAGE_MANAGER);
        routes.post(prefix + "/pages/{pid}/files", this::uploadPageFile, StationPermission.PAGE_EDIT);
        routes.delete(prefix + "/pages/{pid}/files/{fileId}", this::deleteFile, StationPermission.PAGE_EDIT);
    }

    private void list(Context ctx) {
        var session = UserSession.from(ctx);
        var pages = pageService.listPages(session.stationId());
        var landingPageId = pageService.getLandingPageId(session.stationId()).orElse(null);
        ctx.json(new PagesListResponse(pages, landingPageId));
    }

    private void searchPicker(Context ctx) {
        var session = UserSession.from(ctx);
        String q = ctx.queryParam("q");
        int requested = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(5);
        int limit = Math.clamp(requested, 1, 20);
        ctx.json(pageService.searchPagePicker(session.stationId(), q, limit));
    }

    /**
     * Resolves the form referenced by the {@code id} path parameter, verifies it belongs to the
     * caller's station and has the supplied {@code purpose}, and returns it. Used by the
     * page-editor analytics endpoints to ensure a {@code PAGE_EDIT}-only manager cannot access
     * INTERNAL form data through the {@code /pages/forms/…} surface.
     */
    private Form resolvePagePublicForm(Context ctx, FormPurpose expected) {
        int id = pathInt(ctx, "id");
        var form = requireOwnedOrNotFound(ctx, id, formService::findById, Form::stationId);
        if (form.purpose() != expected) throw new NotFoundResponse();
        return form;
    }

    private void getFormAnalytics(Context ctx, FormPurpose expected) {
        var form = resolvePagePublicForm(ctx, expected);
        ctx.json(formAnalyticsAssembler.buildAnalytics(form.id()));
    }

    private void listFormResponses(Context ctx, FormPurpose expected) {
        var form = resolvePagePublicForm(ctx, expected);
        ctx.json(formAnalyticsAssembler.listResponses(form.id()));
    }

    private void getFormResponseDetail(Context ctx, FormPurpose expected) {
        var form = resolvePagePublicForm(ctx, expected);
        int responseId = ctx.pathParamAsClass("responseId", Integer.class).get();
        ctx.json(formAnalyticsAssembler.getResponseDetail(form.id(), responseId));
    }

    /**
     * Marks a CONTACT-form submission as acknowledged by the calling station member. Requires
     * the response to belong to the form id in the path (which has already been verified to be
     * a CONTACT form on the caller's station). Acknowledgement is idempotent — the first
     * acknowledger wins, so a second viewer cannot rewrite the audit trail.
     */
    private void acknowledgeFormResponse(Context ctx, FormPurpose expected) {
        var session = UserSession.from(ctx);
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        var form = resolvePagePublicForm(ctx, expected);
        int responseId = ctx.pathParamAsClass("responseId", Integer.class).get();
        var response = formService.findResponseById(responseId).orElseThrow(NotFoundResponse::new);
        if (response.formId() != form.id()) throw new NotFoundResponse();
        formService.acknowledgeResponse(responseId, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Expands an MEMBER_LIST_SPOTLIGHT (member-list) source descriptor to an ordered
     * {@code ResolvedMember} list — the same shape baked into the public render path — so the
     * editor preview can render the cell live. Delegates to {@link MemberListResolver} so
     * both surfaces stay in lockstep.
     */
    private void resolveMemberList(Context ctx) {
        var session = UserSession.from(ctx);
        JsonNode body;
        try {
            body = CellConfig.MAPPER.readTree(ctx.body());
        } catch (Exception e) {
            throw new BadRequestResponse("Invalid request body");
        }
        var source = body.path("source");
        CellConfig.MemberListSortBy sortBy = null;
        if (body.path("sortBy").isString()) {
            try {
                sortBy = CellConfig.MemberListSortBy.valueOf(body.path("sortBy").asString());
            } catch (IllegalArgumentException ignored) {
            }
        }
        var descriptions = new HashMap<String, String>();
        var descriptionsNode = body.path("memberDescriptions");
        if (descriptionsNode.isObject()) {
            for (var entry : descriptionsNode.properties()) {
                if (entry.getValue().isString())
                    descriptions.put(entry.getKey(), entry.getValue().asString());
            }
        }
        var memberOrder = new ArrayList<String>();
        var orderNode = body.path("memberOrder");
        if (orderNode.isArray()) {
            for (var n : orderNode) {
                if (n.isString()) memberOrder.add(n.asString());
            }
        }
        ctx.json(MemberListResolver.resolve(
                stationMemberRepository,
                avatarService,
                session.stationId(),
                source,
                sortBy,
                descriptions,
                memberOrder));
    }

    private void create(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreatePageRequest.class);
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestResponse("title is required");
        }
        try {
            var page = pageService.create(
                    session.stationId(),
                    request.title(),
                    request.parentId(),
                    session.member().id());
            ctx.status(HttpStatus.CREATED).json(page);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void get(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        ctx.json(requireOwnedPage(ctx, pid));
    }

    private void save(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        var request = ctx.bodyAsClass(SavePageRequest.class);
        if (request.title() == null || request.title().isBlank()) {
            throw new BadRequestResponse("title is required");
        }
        if (request.slug() == null || request.slug().isBlank()) {
            throw new BadRequestResponse("slug is required");
        }

        List<PageService.RowData> rows = request.rows() == null
                ? List.of()
                : request.rows().stream()
                        .map(r -> new PageService.RowData(
                                r.sortOrder(),
                                r.cells() == null
                                        ? List.of()
                                        : r.cells().stream()
                                                .map(c -> new PageService.CellData(
                                                        c.sortOrder(),
                                                        c.widthPercent() != null ? c.widthPercent() : 100.0,
                                                        CellContentType.valueOf(c.contentType()),
                                                        c.content() != null ? c.content() : "",
                                                        c.parsedConfig()))
                                                .toList()))
                        .toList();

        try {
            if (!pageService.savePage(
                    pid,
                    request.title(),
                    request.slug(),
                    request.parentId(),
                    request.metaDescription(),
                    request.ogImageId(),
                    rows)) {
                throw new NotFoundResponse();
            }
            ctx.json(pageService.getPage(pid).orElseThrow());
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void duplicate(Context ctx) {
        var session = UserSession.from(ctx);
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        try {
            var copy = pageService.duplicatePage(pid, session.member().id());
            ctx.status(HttpStatus.CREATED).json(copy);
        } catch (Exception e) {
            throw new NotFoundResponse();
        }
    }

    private void delete(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        if (!pageService.deletePage(pid)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void togglePublish(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        var request = ctx.bodyAsClass(PublishRequest.class);
        if (!pageService.setPublished(pid, request.published())) {
            throw new NotFoundResponse();
        }
        ctx.json(pageService.getPage(pid).orElseThrow());
    }

    private void setLandingPage(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(LandingPageRequest.class);
        try {
            pageService.setLandingPage(session.stationId(), request.pageId());
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        }
    }

    private void uploadPageFile(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("file is required");
        if (file.size() > apiConfig.maxUploadSizeBytes()) throw new BadRequestResponse("File too large");

        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            var image = pageService.uploadPageFile(pid, file.filename(), file.contentType(), data);
            ctx.status(HttpStatus.CREATED).json(image);
        } catch (StorageQuotaService.StorageQuotaExceededException | IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to upload page image", e);
            throw new BadRequestResponse("Failed to upload image");
        }
    }

    private void deleteFile(Context ctx) {
        int fileId = pathInt(ctx, "fileId");
        requireOwnedOrNotFound(ctx, fileId, pageService::findFile, PageFile::stationId);
        if (!pageService.deleteFile(fileId)) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listFiles(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(pageService.listFilesWithUsage(session.stationId()));
    }

    private void pruneFiles(Context ctx) {
        var session = UserSession.from(ctx);
        int removed = pageService.pruneUnusedFiles(session.stationId());
        ctx.json(new PruneResult(removed));
    }

    private void updateFileMeta(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        var body = ctx.bodyAsClass(FileMetaRequest.class);
        if (!pageService.updateFileMeta(session.stationId(), fileId, body.altText(), body.description())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void moveFileFolder(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        var body = ctx.bodyAsClass(MoveFileRequest.class);
        if (!pageService.moveFileToFolder(session.stationId(), fileId, body.folderId())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listFolders(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(pageService.listFolders(session.stationId()));
    }

    private void createFolder(Context ctx) {
        var session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(FolderRequest.class);
        if (body.name() == null || body.name().isBlank()) throw new BadRequestResponse("name is required");
        var folder = pageService.createFolder(
                session.stationId(), body.parentId(), body.name(), body.sortOrder() != null ? body.sortOrder() : 0);
        ctx.status(HttpStatus.CREATED).json(folder);
    }

    private void updateFolder(Context ctx) {
        var session = UserSession.from(ctx);
        int folderId = ctx.pathParamAsClass("folderId", Integer.class).get();
        var body = ctx.bodyAsClass(FolderRequest.class);
        if (!pageService.updateFolder(
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
        int folderId = ctx.pathParamAsClass("folderId", Integer.class).get();
        if (!pageService.deleteFolder(session.stationId(), folderId)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listTags(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(pageService.listTags(session.stationId()));
    }

    private void createTag(Context ctx) {
        var session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(TagRequest.class);
        if (body.name() == null || body.name().isBlank()) throw new BadRequestResponse("name is required");
        ctx.status(HttpStatus.CREATED).json(pageService.createTag(session.stationId(), body.name(), body.color()));
    }

    private void updateTag(Context ctx) {
        var session = UserSession.from(ctx);
        int tagId = ctx.pathParamAsClass("tagId", Integer.class).get();
        var body = ctx.bodyAsClass(TagRequest.class);
        if (!pageService.updateTag(session.stationId(), tagId, body.name(), body.color())) {
            throw new NotFoundResponse();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void deleteTag(Context ctx) {
        var session = UserSession.from(ctx);
        int tagId = ctx.pathParamAsClass("tagId", Integer.class).get();
        if (!pageService.deleteTag(session.stationId(), tagId)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void assignTag(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        int tagId = ctx.pathParamAsClass("tagId", Integer.class).get();
        if (!pageService.assignTag(session.stationId(), fileId, tagId)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void unassignTag(Context ctx) {
        var session = UserSession.from(ctx);
        int fileId = ctx.pathParamAsClass("fileId", Integer.class).get();
        int tagId = ctx.pathParamAsClass("tagId", Integer.class).get();
        if (!pageService.unassignTag(session.stationId(), fileId, tagId)) throw new NotFoundResponse();
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void uploadStationFile(Context ctx) {
        var session = UserSession.from(ctx);
        var file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("file is required");
        if (file.size() > apiConfig.maxUploadSizeBytes()) throw new BadRequestResponse("File too large");
        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            var stored = pageService.uploadStationFile(session.stationId(), file.filename(), file.contentType(), data);
            ctx.status(HttpStatus.CREATED).json(stored);
        } catch (StorageQuotaService.StorageQuotaExceededException | IllegalArgumentException e) {
            throw new BadRequestResponse(e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to upload station file", e);
            throw new BadRequestResponse("Failed to upload file");
        }
    }

    record PruneResult(int removed) {}

    record FileMetaRequest(String altText, String description) {}

    record FolderRequest(Integer parentId, String name, Integer sortOrder) {}

    record TagRequest(String name, String color) {}

    record MoveFileRequest(Integer folderId) {}

    // Response records
    record PagesListResponse(List<StationPage> pages, Integer landingPageId) {}

    // Request records
    record CreatePageRequest(String title, Integer parentId) {}

    record SavePageRequest(
            String title,
            String slug,
            Integer parentId,
            String metaDescription,
            Integer ogImageId,
            List<RowRequest> rows) {}

    record RowRequest(int sortOrder, List<CellRequest> cells) {}

    record CellRequest(int sortOrder, Double widthPercent, String contentType, String content, Object config) {
        CellConfig parsedConfig() {
            if (config == null) return CellContentType.valueOf(contentType).emptyConfig();
            try {
                String json = CellConfig.MAPPER.writeValueAsString(config);
                return CellConfig.parse(CellContentType.valueOf(contentType), json);
            } catch (Exception e) {
                return CellContentType.valueOf(contentType).emptyConfig();
            }
        }
    }

    record PublishRequest(boolean published) {}

    record LandingPageRequest(Integer pageId) {}
}
