/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FileResponse;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.DocumentFilter;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.DocumentPage;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.MemberDocumentResponse;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.StoreQuery;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The document store of a member.
 *
 * <p>Who may see and change what follows from the document rather than from the reader alone, and
 * is {@link DocumentAccessService}'s to decide: a member's own documents are theirs and their
 * guardian's to read, and everything else needs the right to read the documents that name a member.
 * Whether the station keeps documents at all, and what a file has to be to be taken in, is the
 * services' to decide on every path.
 */
@Singleton
public class DocumentRoutes implements Routes {

    /** How many documents a page of the store holds. */
    private static final int PAGE_SIZE = 24;

    private final DocumentService documentService;
    private final DocumentCatalogService catalog;
    private final StationMemberService memberService;
    private final DocumentAccessService documentAccess;

    @Inject
    public DocumentRoutes(
            DocumentService documentService,
            DocumentCatalogService catalog,
            StationMemberService memberService,
            DocumentAccessService documentAccess) {
        this.documentAccess = documentAccess;
        this.documentService = documentService;
        this.catalog = catalog;
        this.memberService = memberService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/documents", this::listStation, StationPermission.DOCUMENT_READ);
        routes.post(prefix + "/documents", this::uploadForStation, StationPermission.DOCUMENT_EDIT);
        routes.get(prefix + "/documents/ids", this::listIds, StationPermission.DOCUMENT_READ);
        routes.post(prefix + "/documents/prune", this::prune, StationPermission.LOGIN);
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
    public record BindRequest(@Nullable List<Integer> memberIds) {}

    /** Request body for the words a document is sorted by. */
    public record TagsRequest(@Nullable List<String> tags) {}

    /** Request body for the documents to remove in one go. */
    public record PruneRequest(@Nullable List<Integer> documentIds) {}

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/documents",
            methods = HttpMethod.GET,
            summary = "The documents kept for a member",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberDocumentResponse[].class)))
    private void list(Context ctx) {
        int memberId = pathInt(ctx, "memberId");
        var session = StationSession.from(ctx);
        int stationId = requireMemberStation(ctx, memberId);
        documentAccess.requireMayList(session, memberId);
        ctx.json(
                catalog.forMember(stationId, memberId, documentAccess.readsEveryMember(session), DocumentDoor.STATION));
    }

    @OpenApi(
            path = "/api/v1/station-members/{memberId}/documents",
            methods = HttpMethod.POST,
            summary = "Put a document on a member",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "memberId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberDocumentResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void upload(Context ctx) {
        int memberId = pathInt(ctx, "memberId");
        var session = StationSession.from(ctx);
        var member = requireMemberStation(ctx, memberId);
        documentAccess.requireMayUpload(session, memberId);

        ctx.status(HttpStatus.CREATED).json(catalog.view(take(ctx, member, List.of(memberId), session)));
    }

    /**
     * Reads what is said about an upload and files it.
     *
     * <p>Shared by the two ways in: onto a member, and into the store without anybody attached.
     */
    private Document take(Context ctx, int stationId, List<Integer> memberIds, StationSession session) {
        boolean hidden = Boolean.parseBoolean(ctx.formParam("hidden"));
        boolean keepOnArchive = Boolean.parseBoolean(ctx.formParam("keepOnArchive"));
        List<String> tags = listOf(ctx.formParam("tags")).toList();
        documentAccess.requireMayHide(session, hidden);
        documentAccess.requireMayLabel(session, keepOnArchive, tags);

        var filing = new DocumentService.Filing(
                memberIds,
                ctx.formParam("title"),
                hidden,
                keepOnArchive,
                Uploader.member(session.member().id()),
                tags);
        return documentService.file(stationId, filing, ctx.uploadedFile("file"), DocumentDoor.STATION);
    }

    /**
     * What a reader narrowed the store to, narrowed first to what they may see.
     *
     * <p>Without the permission for member documents the listing is the station's own paperwork and
     * nothing else: the documents that name nobody, none of the hidden ones, and no narrowing to a
     * member however the request asks for one. The search is what makes this more than tidiness. It
     * runs against what was read out of the documents, so a listing that reached a member document
     * would answer whether a word appears in it, which is the content of the document rather than
     * the name on it.
     */
    private DocumentFilter storeFilter(Context ctx, StationSession session) {
        boolean readsMemberDocuments = session.hasPermission(StationPermission.DOCUMENT_READ_MEMBER);
        String search = ctx.queryParam("search");
        if (search != null && search.isBlank()) search = null;
        return new DocumentFilter(
                readsMemberDocuments ? requestedMembers(ctx, ctx.queryParam("memberIds")) : List.of(),
                search,
                readsMemberDocuments,
                !readsMemberDocuments
                        || ctx.queryParamAsClass("unbound", Boolean.class).getOrDefault(false),
                readsMemberDocuments
                        && ctx.queryParamAsClass("departed", Boolean.class).getOrDefault(false));
    }

    /** Members named in one comma-separated value, each checked to be of the reader's station. */
    private List<Integer> requestedMembers(Context ctx, @Nullable String raw) {
        List<Integer> ids;
        try {
            ids = listOf(raw).map(Integer::valueOf).toList();
        } catch (NumberFormatException e) {
            throw DocumentRefusal.DOCUMENT_MEMBERS_NOT_NUMBERS.raise();
        }
        for (int memberId : ids) {
            requireMemberStation(ctx, memberId);
        }
        return ids;
    }

    /** The entries of one comma-separated value, stripped, with the empty ones left out. */
    private static Stream<String> listOf(@Nullable String raw) {
        if (raw == null || raw.isBlank()) return Stream.empty();
        return Arrays.stream(raw.split(",")).map(String::strip).filter(entry -> !entry.isEmpty());
    }

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
                @OpenApiParam(
                        name = "departed",
                        type = Boolean.class,
                        description = "Only the documents about people who have all left, archived or deleted"),
                @OpenApiParam(name = "page", type = Integer.class),
                @OpenApiParam(name = "size", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentPage.class)))
    private void listStation(Context ctx) {
        var session = StationSession.from(ctx);
        int size = Math.clamp(ctx.queryParamAsClass("size", Integer.class).getOrDefault(PAGE_SIZE), 1, 100);
        int page = Math.max(ctx.queryParamAsClass("page", Integer.class).getOrDefault(0), 0);
        ctx.json(catalog.page(session.stationId(), new StoreQuery(storeFilter(ctx, session), size, page)));
    }

    @OpenApi(
            path = "/api/v1/documents/ids",
            methods = HttpMethod.GET,
            summary = "Every document the filters match, by id, to act on all of them at once",
            tags = {"Members"},
            queryParams = {
                @OpenApiParam(name = "memberIds", description = "Only what is bound to one of them, comma separated"),
                @OpenApiParam(name = "search", description = "Words in the title or in the documents themselves"),
                @OpenApiParam(name = "unbound", type = Boolean.class, description = "Only the station's own paperwork"),
                @OpenApiParam(
                        name = "departed",
                        type = Boolean.class,
                        description = "Only the documents about people who have all left, archived or deleted")
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Integer[].class)))
    private void listIds(Context ctx) {
        var session = StationSession.from(ctx);
        ctx.json(catalog.ids(session.stationId(), storeFilter(ctx, session)));
    }

    @OpenApi(
            path = "/api/v1/documents",
            methods = HttpMethod.POST,
            summary = "Put a document in the store without binding it to anybody",
            tags = {"Members"},
            responses =
                    @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberDocumentResponse.class)))
    private void uploadForStation(Context ctx) {
        var session = StationSession.from(ctx);
        int stationId = session.stationId();
        ctx.status(HttpStatus.CREATED).json(catalog.view(take(ctx, stationId, formMembers(ctx, session), session)));
    }

    /**
     * The members an upload names, given as one comma-separated field, which naming anybody at all is a
     * member document operation. Whom a document concerns is usually known while it is being handed over.
     *
     * <p>Binding at upload reaches the same place as binding afterwards, so it asks the same
     * permission. Without this an upload would be the way around {@link #setMembers}.
     */
    private List<Integer> formMembers(Context ctx, StationSession session) {
        var ids = requestedMembers(ctx, ctx.formParam("memberIds"));
        if (!ids.isEmpty() && !session.hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER)) {
            throw DocumentRefusal.DOCUMENT_MEMBERS_NOT_YOURS_TO_NAME.raise();
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
        int stationId = StationSession.from(ctx).stationId();
        ctx.json(catalog.tagNames(stationId));
    }

    @OpenApi(
            path = "/api/v1/documents/{id}/tags",
            methods = HttpMethod.PUT,
            summary = "Set the words a document is sorted by, writing the new ones",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TagsRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberDocumentResponse.class)))
    private void setTags(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = StationSession.from(ctx);
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
        var data = documentService
                .open(document, DocumentDoor.STATION)
                .orElseThrow(DocumentRefusal.DOCUMENT_CONTENT_NOT_HERE::raise);
        FileResponse.send(ctx, document.mimeType(), document.fileName(), data);
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
        var picture = documentService
                .thumbnail(document, size, DocumentDoor.STATION)
                .orElseThrow(DocumentRefusal.DOCUMENT_THUMBNAIL_NOT_HERE::raise);
        ctx.contentType(picture.contentType());
        ctx.result(picture.data());
    }

    @OpenApi(
            path = "/api/v1/documents/{id}/members",
            methods = HttpMethod.PUT,
            summary = "Set the members a document is bound to",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BindRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = MemberDocumentResponse.class)))
    private void setMembers(Context ctx) {
        var document = requireOwnedDocument(ctx, pathInt(ctx, "id"));
        var memberIds =
                Objects.requireNonNullElse(ctx.bodyAsClass(BindRequest.class).memberIds(), List.<Integer>of());
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
        var session = StationSession.from(ctx);
        var document = requireOwnedDocument(ctx, pathInt(ctx, "id"));
        documentAccess.requireMayDelete(session, document);
        documentService.remove(session.stationId(), List.of(document), DocumentDoor.STATION);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Removes several documents in one go, typically what is left of people who have gone.
     *
     * <p>Every document is checked before any is removed, by the same rule as removing one, so a
     * choice that holds one document the reader may not remove removes nothing.
     */
    @OpenApi(
            path = "/api/v1/documents/prune",
            methods = HttpMethod.POST,
            summary = "Remove several documents in one go",
            tags = {"Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = PruneRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void prune(Context ctx) {
        var session = StationSession.from(ctx);
        var ids = Objects.requireNonNullElse(ctx.bodyAsClass(PruneRequest.class).documentIds(), List.<Integer>of());
        var documents =
                ids.stream().distinct().map(id -> requireOwnedDocument(ctx, id)).toList();
        documents.forEach(document -> documentAccess.requireMayDelete(session, document));
        documentService.remove(session.stationId(), documents, DocumentDoor.STATION);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /** The document behind the path, when it belongs to the reader's station and they may see it. */
    private Document requireReadable(Context ctx) {
        var document = requireOwnedDocument(ctx, pathInt(ctx, "id"));
        documentAccess.requireReadable(StationSession.from(ctx), document);
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
