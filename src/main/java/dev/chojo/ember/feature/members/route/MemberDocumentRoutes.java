/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.RouteSupport;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.MemberDocument;
import dev.chojo.ember.feature.members.entity.MemberDocumentTag;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberDocumentRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberDocumentService;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UploadedFile;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiName;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * The document store of a member.
 *
 * <p>Who may see what is decided here rather than at the route, because it follows from the
 * document rather than from the reader alone: a member's own documents are theirs to read, and
 * everything else needs the right to read other members.
 */
@Singleton
public class MemberDocumentRoutes implements Routes {

    /** As much as a document may weigh. The same bound the knowledge base uses. */
    private static final long MAX_UPLOAD_SIZE = 50L * 1024 * 1024;

    /** How many documents a page of the store holds. */
    private static final int PAGE_SIZE = 24;

    private final MemberDocumentService documentService;
    private final MemberDocumentRepository documentRepository;
    private final StationMemberRepository memberRepository;

    @Inject
    public MemberDocumentRoutes(
            MemberDocumentService documentService,
            MemberDocumentRepository documentRepository,
            StationMemberRepository memberRepository) {
        this.documentService = documentService;
        this.documentRepository = documentRepository;
        this.memberRepository = memberRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/member-documents", this::listStation, StationPermission.MEMBER_READ);
        routes.post(prefix + "/member-documents", this::uploadForStation, StationPermission.MEMBER_EDIT);
        routes.get(prefix + "/member-documents/tags", this::listTags, StationPermission.MEMBER_READ);
        routes.put(prefix + "/member-documents/{id}/tags", this::setTags, StationPermission.MEMBER_EDIT);
        routes.get(prefix + "/station-members/{memberId}/documents", this::list, StationPermission.LOGIN);
        routes.post(prefix + "/station-members/{memberId}/documents", this::upload, StationPermission.LOGIN);
        routes.get(prefix + "/member-documents/{id}/content", this::content, StationPermission.LOGIN);
        routes.get(prefix + "/member-documents/{id}/thumbnail", this::thumbnail, StationPermission.LOGIN);
        routes.put(prefix + "/member-documents/{id}/members", this::setMembers, StationPermission.MEMBER_EDIT);
        routes.delete(prefix + "/member-documents/{id}", this::delete, StationPermission.LOGIN);
    }

    /**
     * One document as a reader sees it.
     *
     * @param memberIds the members it is bound to, so a reader can tell whose it is
     */
    @OpenApiName("MemberDocumentResponse")
    public record DocumentResponse(
            int id,
            String title,
            String fileName,
            String mimeType,
            long sizeBytes,
            boolean hidden,
            boolean keepOnArchive,
            boolean hasThumbnail,
            Integer uploadedBy,
            Instant createdAt,
            List<Integer> memberIds,
            List<String> tags) {}

    /**
     * A page of the station's documents.
     *
     * @param total how many the filters match in all, so the pages can be counted
     */
    @OpenApiName("MemberDocumentPage")
    public record DocumentPage(List<DocumentResponse> documents, int total) {}

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
        requireMemberStation(ctx, memberId);
        boolean readsOthers = session.hasPermission(StationPermission.MEMBER_READ);
        if (!readsOthers && !isSelf(session, memberId)) throw new ForbiddenResponse();
        ctx.json(documentRepository.findByMember(memberId, readsOthers).stream()
                .map(this::toResponse)
                .toList());
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
        requireMayUpload(session, memberId);

        ctx.status(HttpStatus.CREATED).json(toResponse(take(ctx, member, List.of(memberId), session)));
    }

    /**
     * Reads an upload and everything said about it, and puts it in the store.
     *
     * <p>Shared by the two ways in: onto a member, and into the store without anybody attached.
     */
    private MemberDocument take(Context ctx, int stationId, List<Integer> memberIds, UserSession session)
            throws IOException {
        UploadedFile file = ctx.uploadedFile("file");
        if (file == null) throw new BadRequestResponse("file is required");
        if (file.size() > MAX_UPLOAD_SIZE) throw new BadRequestResponse("File too large (max 50MB)");

        String title = ctx.formParam("title");
        if (title == null || title.isBlank()) title = file.filename();
        boolean hidden = Boolean.parseBoolean(ctx.formParam("hidden"));
        boolean keepOnArchive = Boolean.parseBoolean(ctx.formParam("keepOnArchive"));
        if (hidden && !session.hasPermission(StationPermission.MEMBER_READ)) throw new ForbiddenResponse();

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
        if (session.stationId() == null) throw new ForbiddenResponse();
        return session.stationId();
    }

    @OpenApi(
            path = "/api/v1/member-documents",
            methods = HttpMethod.GET,
            summary = "The document store of the station, a page at a time",
            tags = {"Members"},
            queryParams = {
                @OpenApiParam(name = "memberIds", description = "Only what is bound to one of them, comma separated"),
                @OpenApiParam(name = "search", description = "Words in the title or in the documents themselves"),
                @OpenApiParam(name = "page", type = Integer.class),
                @OpenApiParam(name = "size", type = Integer.class)
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentPage.class)))
    private void listStation(Context ctx) {
        var session = UserSession.from(ctx);
        int stationId = requireStation(session);
        List<Integer> memberIds = requestedMembers(ctx);
        String search = ctx.queryParam("search");
        if (search != null && search.isBlank()) search = null;
        int size = Math.clamp(ctx.queryParamAsClass("size", Integer.class).getOrDefault(PAGE_SIZE), 1, 100);
        int page = Math.max(ctx.queryParamAsClass("page", Integer.class).getOrDefault(0), 0);
        String config = documentService.searchConfigOf(stationId);

        var documents =
                documentRepository.findByStation(stationId, memberIds, search, true, config, size, page * size).stream()
                        .map(this::toResponse)
                        .toList();
        int total = documentRepository.countByStation(stationId, memberIds, search, true, config);
        ctx.json(new DocumentPage(documents, total));
    }

    @OpenApi(
            path = "/api/v1/member-documents",
            methods = HttpMethod.POST,
            summary = "Put a document in the store without binding it to anybody",
            tags = {"Members"},
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = DocumentResponse.class)))
    private void uploadForStation(Context ctx) throws IOException {
        var session = UserSession.from(ctx);
        int stationId = requireStation(session);
        ctx.status(HttpStatus.CREATED).json(toResponse(take(ctx, stationId, formMembers(ctx), session)));
    }

    /**
     * The members an upload was already put on, given as one comma-separated field. Whom a
     * document concerns is usually known while it is being handed over.
     */
    private List<Integer> formMembers(Context ctx) {
        String raw = ctx.formParam("memberIds");
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

    @OpenApi(
            path = "/api/v1/member-documents/tags",
            methods = HttpMethod.GET,
            summary = "The words the station sorts its documents by",
            tags = {"Members"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = String[].class)))
    private void listTags(Context ctx) {
        int stationId = requireStation(UserSession.from(ctx));
        ctx.json(documentRepository.findTagsByStation(stationId).stream()
                .map(MemberDocumentTag::name)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/member-documents/{id}/tags",
            methods = HttpMethod.PUT,
            summary = "Set the words a document is sorted by, writing the new ones",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DocumentResponse.class)))
    private void setTags(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        var document = requireOwnedDocument(ctx, id);
        var request = ctx.bodyAsClass(TagsRequest.class);
        documentRepository.setTags(id, document.stationId(), request.tags() != null ? request.tags() : List.of());
        ctx.json(toResponse(document));
    }

    @OpenApi(
            path = "/api/v1/member-documents/{id}/content",
            methods = HttpMethod.GET,
            summary = "The document itself",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void content(Context ctx) {
        var document = requireReadable(ctx);
        var data = documentService.read(document).orElseThrow(NotFoundResponse::new);
        var disposition = SafeInlineMime.isInlineSafe(document.mimeType())
                ? SafeContentDisposition.Disposition.INLINE
                : SafeContentDisposition.Disposition.ATTACHMENT;
        ctx.contentType(SafeInlineMime.safeContentType(document.mimeType()));
        ctx.header("Content-Disposition", SafeContentDisposition.build(disposition, document.fileName()));
        ctx.result(data);
    }

    @OpenApi(
            path = "/api/v1/member-documents/{id}/thumbnail",
            methods = HttpMethod.GET,
            summary = "The picture a tile shows of a document",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void thumbnail(Context ctx) {
        var document = requireReadable(ctx);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(256);
        var picture = documentService.thumbnail(document, size).orElseThrow(NotFoundResponse::new);
        ctx.contentType(picture.contentType());
        ctx.result(picture.data());
    }

    @OpenApi(
            path = "/api/v1/member-documents/{id}/members",
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
        documentRepository.setMembers(id, memberIds);
        ctx.json(toResponse(document));
    }

    @OpenApi(
            path = "/api/v1/member-documents/{id}",
            methods = HttpMethod.DELETE,
            summary = "Remove a document",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void delete(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        var document = requireOwnedDocument(ctx, id);
        boolean ownUpload = session.member() != null
                && document.uploadedBy() != null
                && document.uploadedBy() == session.member().id();
        if (!session.hasPermission(StationPermission.MEMBER_EDIT) && !ownUpload) throw new ForbiddenResponse();
        documentService.delete(document);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The document behind the path, when the reader may see it at all. A document of one's own is
     * readable without any permission; a hidden one never is, because hiding it means hiding it
     * from the member it belongs to.
     */
    private MemberDocument requireReadable(Context ctx) {
        int id = pathInt(ctx, "id");
        var session = UserSession.from(ctx);
        var document = requireOwnedDocument(ctx, id);
        if (session.hasPermission(StationPermission.MEMBER_READ)) return document;
        if (document.hidden()) throw new NotFoundResponse();
        if (session.member() == null
                || !documentRepository.isBoundTo(id, session.member().id())) {
            throw new ForbiddenResponse();
        }
        return document;
    }

    private void requireMayUpload(UserSession session, int memberId) {
        if (session.hasPermission(StationPermission.MEMBER_EDIT)) return;
        if (isSelf(session, memberId) && session.hasPermission(StationPermission.MEMBER_SELF_UPLOAD)) return;
        throw new ForbiddenResponse();
    }

    private static boolean isSelf(UserSession session, int memberId) {
        return session.member() != null && session.member().id() == memberId;
    }

    /** The station of the member named in the path, which has to be the reader's own. */
    private int requireMemberStation(Context ctx, int memberId) {
        var member = RouteSupport.requireOwnedOrNotFound(
                ctx, memberId, memberRepository::findById, StationMember::stationId);
        return member.stationId();
    }

    /** The document behind the path, when it belongs to the reader's own station. */
    private MemberDocument requireOwnedDocument(Context ctx, int id) {
        return RouteSupport.requireOwnedOrNotFound(ctx, id, documentRepository::findById, MemberDocument::stationId);
    }

    private DocumentResponse toResponse(MemberDocument document) {
        return new DocumentResponse(
                document.id(),
                document.title(),
                document.fileName(),
                document.mimeType(),
                document.sizeBytes(),
                document.hidden(),
                document.keepOnArchive(),
                document.hasThumbnail(),
                document.uploadedBy(),
                document.createdAt(),
                documentRepository.membersOf(document.id()),
                documentRepository.findTags(document.id()).stream()
                        .map(MemberDocumentTag::name)
                        .toList());
    }

    private static int pathInt(Context ctx, String name) {
        return ctx.pathParamAsClass(name, Integer.class).get();
    }
}
