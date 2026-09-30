/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.DocumentPage;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.DocumentResponse;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.StoreQuery;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.StationService;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.UploadedFile;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * The document store of a member.
 *
 * <p>Who may see and change what follows from the document rather than from the reader alone, and
 * is {@link DocumentAccessService}'s to decide: a member's own documents are theirs and their
 * guardian's to read, and everything else needs the right to read the documents that name a member.
 */
@Singleton
public class DocumentRoutes implements Routes {

    /** As much as a document may weigh. The same bound the knowledge base uses. */
    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024;

    /** How many documents a page of the store holds. */
    private static final int PAGE_SIZE = 24;

    private final DocumentService documentService;
    private final DocumentCatalogService catalog;
    private final StationMemberService memberService;
    private final StationService stationService;
    private final DocumentAccessService documentAccess;

    @Inject
    public DocumentRoutes(
            DocumentService documentService,
            DocumentCatalogService catalog,
            StationMemberService memberService,
            StationService stationService,
            DocumentAccessService documentAccess) {
        this.documentAccess = documentAccess;
        this.documentService = documentService;
        this.catalog = catalog;
        this.memberService = memberService;
        this.stationService = stationService;
    }

    /**
     * Refuses everything where the station keeps no documents.
     *
     * <p>A station that switched the store off should not have a page for it, and a route that
     * answered anyway would be the way back in. The personal data export is deliberately not gated
     * this way: a switched-off page is not a reason to withhold somebody's own data from them.
     */
    private void requireModule(int stationId) {
        if (stationService.findDisabledModules(stationId).contains(StationModule.DOCUMENTS)) {
            throw Refusal.DOCUMENTS_SWITCHED_OFF.raise();
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/documents", this::listStation, StationPermission.DOCUMENT_READ);
        routes.post(prefix + "/documents", this::uploadForStation, StationPermission.DOCUMENT_EDIT);
        routes.get(prefix + "/documents/tags", this::listTags, StationPermission.DOCUMENT_READ);
        routes.put(prefix + "/documents/{id}/tags", this::setTags, StationPermission.DOCUMENT_EDIT);
        routes.get(prefix + "/documents/{id}/content", this::content, StationPermission.LOGIN);
        routes.get(prefix + "/documents/{id}/thumbnail", this::thumbnail, StationPermission.LOGIN);
        routes.put(prefix + "/documents/{id}/members", this::setMembers, StationPermission.DOCUMENT_EDIT_MEMBER);
        routes.delete(prefix + "/documents/{id}", this::delete, StationPermission.LOGIN);

        routes.get(prefix + "/station-members/{memberId}/documents", this::list, StationPermission.LOGIN);
        routes.post(prefix + "/station-members/{memberId}/documents", this::upload, StationPermission.LOGIN);
    }

    /** Request body for the members a document is bound to. */
    public record BindRequest(List<Integer> memberIds) {}

    /** Request body for the words a document is sorted by. */
    public record TagsRequest(List<String> tags) {}

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/documents",
            methods = HttpMethod.GET,
            summary = "The documents kept for a member",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentResponse[].class)))
    private void list(Context ctx) {
        int memberId = pathInt(ctx, "memberId");
        var session = UserSession.from(ctx);
        int stationId = requireMemberStation(ctx, memberId);
        documentAccess.requireMayList(session, memberId);
        ctx.json(catalog.forMember(stationId, memberId, documentAccess.readsEveryMember(session)));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/documents",
            methods = HttpMethod.POST,
            summary = "Put a document on a member",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = DocumentResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void upload(Context ctx) throws IOException {
        int memberId = pathInt(ctx, "memberId");
        var session = UserSession.from(ctx);
        var member = requireMemberStation(ctx, memberId);
        documentAccess.requireMayUpload(session, memberId);

        ctx.status(HttpStatus.CREATED).json(catalog.view(take(ctx, member, List.of(memberId), session)));
    }

    /**
     * Reads an upload and everything said about it, and puts it in the store.
     *
     * <p>Shared by the two ways in: onto a member, and into the store without anybody attached.
     */
    private Document take(Context ctx, int stationId, List<Integer> memberIds, UserSession session) throws IOException {
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) throw Refusal.DOCUMENT_UPLOAD_MISSING_FILE.raise();
        if (file.size() > MAX_UPLOAD_SIZE) throw Refusal.DOCUMENT_UPLOAD_TOO_LARGE.raise();

        String title = ctx.formParam("title");
        if (title == null || title.isBlank()) title = file.filename();
        boolean hidden = Boolean.parseBoolean(ctx.formParam("hidden"));
        boolean keepOnArchive = Boolean.parseBoolean(ctx.formParam("keepOnArchive"));
        documentAccess.requireMayHide(session, hidden);

        byte[] data;
        try (var in = file.content()) {
            data = in.readAllBytes();
        }
        return documentService.store(
                stationId,
                memberIds,
                title.strip(),
                file.filename(),
                file.contentType(),
                data,
                hidden,
                keepOnArchive,
                session.member() != null ? session.member().id() : null,
                tagsOf(ctx));
    }

    /** The members the store was narrowed to, given as one comma-separated parameter. */
    private List<Integer> requestedMembers(Context ctx) {
        String raw = ctx.queryParam("memberIds");
        if (raw == null || raw.isBlank()) return List.of();
        var ids = Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(id -> !id.isEmpty())
                .map(Integer::valueOf)
                .toList();
        for (int memberId : ids) {
            requireMemberStation(ctx, memberId);
        }
        return ids;
    }

    /** The words the upload was labelled with, given as one comma-separated field. */
    private static List<String> tagsOf(Context ctx) {
        String tags = ctx.formParam("tags");
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(","))
                .map(String::strip)
                .filter(tag -> !tag.isEmpty())
                .toList();
    }

    /** The station the reader is signed in to, which every document belongs to. */
    private static int requireStation(UserSession session) {
        if (session.stationId() == null) throw Refusal.NO_STATION_CHOSEN_FOR_DOCUMENTS.raise();
        return session.stationId();
    }

    /**
     * A page of the store, narrowed to what the reader may see before any filter of theirs is read.
     *
     * <p>Without the permission for member documents the listing is the station's own paperwork and
     * nothing else: the documents that name nobody, none of the hidden ones, and no narrowing to a
     * member however the request asks for one. The search is what makes this more than tidiness. It
     * runs against what was read out of the documents, so a listing that reached a member document
     * would answer whether a word appears in it, which is the content of the document rather than
     * the name on it.
     */
    @OpenApi(
            path = "/api/v1/documents",
            methods = HttpMethod.GET,
            summary = "The document store of the station, a page at a time",
            tags = {"Members"},
            queryParams = {
                @OpenApiParam(name = "memberIds", description = "Only what is bound to one of them, comma separated"),
                @OpenApiParam(name = "search", description = "Words in the title or in the documents themselves"),
                @OpenApiParam(
                        name = "unbound",
                        type = Boolean.class,
                        description = "Only the documents that name nobody, which are the station's own paperwork"),
                @OpenApiParam(name = "page", type = Integer.class),
                @OpenApiParam(name = "size", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentPage.class)))
    private void listStation(Context ctx) {
        var session = UserSession.from(ctx);
        int stationId = requireStation(session);
        requireModule(stationId);
        boolean readsMemberDocuments = session.hasPermission(StationPermission.DOCUMENT_READ_MEMBER);
        List<Integer> memberIds = readsMemberDocuments ? requestedMembers(ctx) : List.of();
        String search = ctx.queryParam("search");
        if (search != null && search.isBlank()) search = null;
        int size = Math.clamp(ctx.queryParamAsClass("size", Integer.class).getOrDefault(PAGE_SIZE), 1, 100);
        int page = Math.max(ctx.queryParamAsClass("page", Integer.class).getOrDefault(0), 0);
        boolean unboundOnly = !readsMemberDocuments
                || ctx.queryParamAsClass("unbound", Boolean.class).getOrDefault(false);
        ctx.json(catalog.page(
                stationId, new StoreQuery(memberIds, search, readsMemberDocuments, unboundOnly, size, page)));
    }

    @OpenApi(
            path = "/api/v1/documents",
            methods = HttpMethod.POST,
            summary = "Put a document in the store without binding it to anybody",
            tags = {"Members"},
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = DocumentResponse.class)))
    private void uploadForStation(Context ctx) throws IOException {
        var session = UserSession.from(ctx);
        int stationId = requireStation(session);
        requireModule(stationId);
        ctx.status(HttpStatus.CREATED).json(catalog.view(take(ctx, stationId, formMembers(ctx), session)));
    }

    /**
     * The members an upload was already put on, given as one comma-separated field. Whom a
     * document concerns is usually known while it is being handed over.
     */
    /**
     * The members an upload names, which naming anybody at all is a member document operation.
     *
     * <p>Binding at upload reaches the same place as binding afterwards, so it asks the same
     * permission. Without this an upload would be the way around {@link #setMembers}.
     */
    private List<Integer> formMembers(Context ctx) {
        String raw = ctx.formParam("memberIds");
        if (raw == null || raw.isBlank()) return List.of();
        var ids = Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(id -> !id.isEmpty())
                .map(Integer::valueOf)
                .toList();
        if (!ids.isEmpty() && !UserSession.from(ctx).hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER)) {
            throw Refusal.DOCUMENT_MEMBERS_NOT_YOURS_TO_NAME.raise();
        }
        for (int memberId : ids) {
            requireMemberStation(ctx, memberId);
        }
        return ids;
    }

    @OpenApi(
            path = "/api/v1/documents/tags",
            methods = HttpMethod.GET,
            summary = "The words the station sorts its documents by",
            tags = {"Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void listTags(Context ctx) {
        int stationId = requireStation(UserSession.from(ctx));
        ctx.json(catalog.tagNames(stationId));
    }

    @OpenApi(
            path = "/api/v1/documents/{id}/tags",
            methods = HttpMethod.PUT,
            summary = "Set the words a document is sorted by, writing the new ones",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentResponse.class)))
    private void setTags(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        var document = requireOwnedDocument(ctx, id);
        documentAccess.requireMayEdit(session, id);
        ctx.json(catalog.setTags(document, ctx.bodyAsClass(TagsRequest.class).tags()));
    }

    @OpenApi(
            path = "/api/v1/documents/{id}/content",
            methods = HttpMethod.GET,
            summary = "The document itself",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void content(Context ctx) {
        var document = requireReadable(ctx);
        var data = documentService.read(document).orElseThrow(Refusal.DOCUMENT_CONTENT_NOT_HERE::raise);
        var disposition = SafeInlineMime.isInlineSafe(document.mimeType())
                ? SafeContentDisposition.Disposition.INLINE
                : SafeContentDisposition.Disposition.ATTACHMENT;
        ctx.contentType(SafeInlineMime.safeContentType(document.mimeType()));
        ctx.header("Content-Disposition", SafeContentDisposition.build(disposition, document.fileName()));
        ctx.result(data);
    }

    @OpenApi(
            path = "/api/v1/documents/{id}/thumbnail",
            methods = HttpMethod.GET,
            summary = "The picture a tile shows of a document",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void thumbnail(Context ctx) {
        var document = requireReadable(ctx);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(256);
        var picture = documentService.thumbnail(document, size).orElseThrow(Refusal.DOCUMENT_THUMBNAIL_NOT_HERE::raise);
        ctx.contentType(picture.contentType());
        ctx.result(picture.data());
    }

    @OpenApi(
            path = "/api/v1/documents/{id}/members",
            methods = HttpMethod.PUT,
            summary = "Set the members a document is bound to",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentResponse.class)))
    private void setMembers(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        var document = requireOwnedDocument(ctx, id);
        var request = ctx.bodyAsClass(BindRequest.class);
        var memberIds = request.memberIds() != null ? request.memberIds() : List.<Integer>of();
        for (int memberId : memberIds) {
            requireMemberStation(ctx, memberId);
        }
        ctx.json(catalog.setMembers(document, memberIds));
    }

    @OpenApi(
            path = "/api/v1/documents/{id}",
            methods = HttpMethod.DELETE,
            summary = "Remove a document",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        var document = requireOwnedDocument(ctx, id);
        documentAccess.requireMayDelete(session, document);
        documentService.delete(document);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /** The document behind the path, when it belongs to the reader's station and they may see it. */
    private Document requireReadable(Context ctx) {
        var document = requireOwnedDocument(ctx, pathInt(ctx, "id"));
        documentAccess.requireReadable(UserSession.from(ctx), document);
        return document;
    }

    /** The station of the member named in the path, which has to be the reader's own. */
    private int requireMemberStation(Context ctx, int memberId) {
        var member =
                RouteSupport.requireOwnedOrNotFound(ctx, memberId, memberService::findById, StationMember::stationId);
        return member.stationId();
    }

    /** The document behind the path, when it belongs to the reader's own station. */
    private Document requireOwnedDocument(Context ctx, int id) {
        return RouteSupport.requireOwnedOrNotFound(ctx, id, catalog::find, Document::stationId);
    }

    private static int pathInt(Context ctx, String name) {
        return ctx.pathParamAsClass(name, Integer.class).get();
    }
}
