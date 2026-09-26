/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.route;

import dev.chojo.ember.api.Failures;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.service.AvatarService;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.form.entity.Form;
import dev.chojo.ember.feature.form.entity.FormPurpose;
import dev.chojo.ember.feature.form.service.FormAnalyticsAssembler;
import dev.chojo.ember.feature.form.service.FormService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.page.entity.PageVisibility;
import dev.chojo.ember.feature.page.entity.StationPage;
import dev.chojo.ember.feature.page.service.MemberListResolver;
import dev.chojo.ember.feature.page.service.PageService;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.NoSuchElementException;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

@Singleton
public class PageRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(PageRoutes.class);

    private final PageService pageService;
    private final MediaLibraryService media;
    private final FormService formService;
    private final FormAnalyticsAssembler formAnalyticsAssembler;
    private final StationMemberRepository stationMemberRepository;
    private final AvatarService avatarService;
    private final Api apiConfig;

    @Inject
    public PageRoutes(
            PageService pageService,
            MediaLibraryService media,
            FormService formService,
            FormAnalyticsAssembler formAnalyticsAssembler,
            StationMemberRepository stationMemberRepository,
            AvatarService avatarService,
            Api apiConfig) {
        this.pageService = pageService;
        this.media = media;
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
        // Register the literal "/pages/landing" path BEFORE the variable "/pages/{pid}" routes so
        // Javalin matches it as a literal segment instead of trying to parse "landing" as a page id.
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
        routes.put(prefix + "/pages/{pid}/visibility", this::setVisibility, StationPermission.PAGE_MANAGER);
        routes.get(prefix + "/pages/{pid}/share-link", this::getShareLink, StationPermission.PAGE_MANAGER);
        routes.post(prefix + "/pages/{pid}/share-link", this::replaceShareLink, StationPermission.PAGE_MANAGER);
        routes.post(prefix + "/pages/{pid}/files", this::uploadPageFile, StationPermission.PAGE_EDIT);
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
        if (form.purpose() != expected) throw Refusal.PAGE_FORM_NOT_HERE.raise();
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
     * a CONTACT form on the caller's station). Acknowledgement is idempotent - the first
     * acknowledger wins, so a second viewer cannot rewrite the audit trail.
     */
    private void acknowledgeFormResponse(Context ctx, FormPurpose expected) {
        var session = UserSession.from(ctx);
        if (session.member() == null) throw Refusal.PAGE_FORM_ANSWER_NOT_YOURS_TO_MARK.raise();
        var form = resolvePagePublicForm(ctx, expected);
        int responseId = ctx.pathParamAsClass("responseId", Integer.class).get();
        var response = formService.findResponseById(responseId).orElseThrow(Refusal.FORM_ANSWER_NOT_HERE::raise);
        if (response.formId() != form.id()) throw Refusal.FORM_ANSWER_NOT_HERE.raise();
        formService.acknowledgeResponse(responseId, session.member().id());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Expands an MEMBER_LIST_SPOTLIGHT (member-list) source descriptor to an ordered
     * {@code ResolvedMember} list - the same shape baked into the public render path - so the
     * editor preview can render the cell live. Delegates to {@link MemberListResolver} so
     * both surfaces stay in lockstep.
     */
    private void resolveMemberList(Context ctx) {
        var session = UserSession.from(ctx);
        JsonNode body;
        try {
            body = CellConfig.MAPPER.readTree(ctx.body());
        } catch (Exception e) {
            log.warn("Could not read the member list a page editor asked to resolve", e);
            throw Refusal.PAGE_MEMBER_LIST_NOT_READ.raise();
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
            throw Refusal.PAGE_NEEDS_A_TITLE.raise();
        }
        try {
            var page = pageService.create(
                    session.stationId(),
                    request.title(),
                    request.parentId(),
                    session.member().id());
            ctx.status(HttpStatus.CREATED).json(page);
        } catch (IllegalArgumentException e) {
            log.warn("Could not create a page in station {}", session.stationId(), e);
            throw Refusal.PAGE_NOT_CREATED.raise();
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
            throw Refusal.PAGE_TITLE_MISSING_ON_SAVE.raise();
        }
        if (request.slug() == null || request.slug().isBlank()) {
            throw Refusal.PAGE_ADDRESS_MISSING_ON_SAVE.raise();
        }

        List<ContentBlockService.RowData> rows = request.rows() == null
                ? List.of()
                : request.rows().stream()
                        .map(r -> new ContentBlockService.RowData(
                                r.sortOrder(),
                                r.cells() == null
                                        ? List.of()
                                        : r.cells().stream()
                                                .map(c -> new ContentBlockService.CellData(
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
                throw Refusal.PAGE_NOT_HERE_ON_SAVE.raise();
            }
            ctx.json(pageService.getPage(pid).orElseThrow(Refusal.PAGE_NOT_HERE_AFTER_SAVE::raise));
        } catch (IllegalArgumentException e) {
            throw Failures.readable(e.getMessage())
                    .map(Refusal.PAGE_NOT_SAVED::raise)
                    .orElseGet(Refusal.PAGE_NOT_SAVED::raise);
        }
    }

    /**
     * Copies a page.
     *
     * <p>A copy that fails because the page went away between the check and the copy is the only
     * refusal here. Everything else that can go wrong is Ember's, and is left to be answered as
     * such: catching it all and calling it a miss told the reader their page was gone when it was
     * sitting there and the copy had broken.
     */
    private void duplicate(Context ctx) {
        var session = UserSession.from(ctx);
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        try {
            var copy = pageService.duplicatePage(pid, session.member().id());
            ctx.status(HttpStatus.CREATED).json(copy);
        } catch (NoSuchElementException e) {
            throw Refusal.PAGE_NOT_HERE_ON_COPY.raise();
        }
    }

    private void delete(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        if (!pageService.deletePage(pid)) {
            throw Refusal.PAGE_NOT_HERE_ON_DELETE.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void setVisibility(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        var request = ctx.bodyAsClass(VisibilityRequest.class);
        if (request.visibility() == null) {
            throw Refusal.PAGE_VISIBILITY_MISSING.raise();
        }
        if (!pageService.setVisibility(pid, request.visibility())) {
            throw Refusal.PAGE_NOT_HERE_ON_VISIBILITY_CHANGE.raise();
        }
        ctx.json(pageService.getPage(pid).orElseThrow(Refusal.PAGE_NOT_HERE_AFTER_VISIBILITY_CHANGE::raise));
    }

    /**
     * The link a page reached by one is reached at. Separate from the page itself because the page
     * travels to strangers and the link must not.
     */
    private void getShareLink(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        ctx.json(new ShareLinkResponse(pageService.shareToken(pid).orElse(null)));
    }

    private void replaceShareLink(Context ctx) {
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        var request = ctx.bodyAsClass(ReplaceShareLinkRequest.class);
        var replaced = pageService.replaceShareToken(pid, request.currentToken());
        if (replaced.isEmpty()) {
            throw Refusal.PAGE_LINK_ALREADY_REPLACED.raise();
        }
        ctx.json(new ShareLinkResponse(replaced.get()));
    }

    private void setLandingPage(Context ctx) {
        var session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(LandingPageRequest.class);
        try {
            pageService.setLandingPage(session.stationId(), request.pageId());
            ctx.status(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            log.warn("Could not set the landing page of station {}", session.stationId(), e);
            throw Refusal.LANDING_PAGE_NOT_SET.raise();
        }
    }

    /**
     * Uploads a file from the page editor. It lands in the station media library like any other
     * upload; the page is recorded only as where it first came from.
     */
    private void uploadPageFile(Context ctx) {
        var session = UserSession.from(ctx);
        int pid = ctx.pathParamAsClass("pid", Integer.class).get();
        requireOwnedPage(ctx, pid);
        var file = ctx.uploadedFile("file");
        if (file == null) throw Refusal.PAGE_UPLOAD_MISSING_FILE.raise();
        if (file.size() > apiConfig.maxUploadSizeBytes()) throw Refusal.PAGE_UPLOAD_TOO_LARGE.raise();

        try (var content = file.content()) {
            byte[] data = content.readAllBytes();
            var stored = media.upload(
                    session.stationId(),
                    pid,
                    session.member() != null ? session.member().id() : null,
                    file.filename(),
                    file.contentType(),
                    data);
            ctx.status(HttpStatus.CREATED).json(stored);
        } catch (StorageQuotaService.StorageQuotaExceededException | IllegalArgumentException e) {
            log.warn("Could not keep a file uploaded from the page editor", e);
            throw Refusal.PAGE_UPLOAD_NOT_SAVED.raise();
        } catch (Exception e) {
            log.warn("Failed to upload page file", e);
            throw Refusal.PAGE_UPLOAD_NOT_PROCESSED.raise();
        }
    }

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

    /**
     * @param config the cell's settings as an object. Which record they are follows from the content
     *               type beside them, so they are bound once that is known rather than while the
     *               request is read.
     */
    record CellRequest(int sortOrder, Double widthPercent, String contentType, String content, JsonNode config) {
        CellConfig parsedConfig() {
            return CellConfig.parse(CellContentType.valueOf(contentType), config);
        }
    }

    record VisibilityRequest(PageVisibility visibility) {}

    record ShareLinkResponse(String token) {}

    record ReplaceShareLinkRequest(String currentToken) {}

    record LandingPageRequest(Integer pageId) {}
}
