/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.FederationHeaders;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.FederatedBoardProxyService;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Local proxy endpoints for boards shared by federation partners. Every handler resolves the
 * partner from the path, checks the locally stored access rules and forwards the call to the
 * owning station. The endpoints the owning station itself serves live in {@link RemoteBoardRoutes}.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class FederatedBoardRoutes implements Routes {

    private final FederatedBoardProxyService proxyService;
    private final FederationRepository federationRepository;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public FederatedBoardRoutes(
            FederatedBoardProxyService proxyService,
            FederationRepository federationRepository,
            MemberNameResolver memberNameResolver) {
        this.proxyService = proxyService;
        this.federationRepository = federationRepository;
        this.memberNameResolver = memberNameResolver;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String fp = prefix + "/federated/boards";

        routes.get(fp, this::federatedLocalDiscoverBoards, StationPermission.BOARD_USE);

        routes.get(fp + "/bookmarks", this::federatedLocalListBookmarks, StationPermission.BOARD_USE);
        routes.post(fp + "/bookmarks", this::federatedLocalCreateBookmark, StationPermission.BOARD_USE);
        routes.delete(fp + "/bookmarks/{bookmarkId}", this::federatedLocalDeleteBookmark, StationPermission.BOARD_USE);

        routes.get(fp + "/{partnerUid}/{boardKey}", this::federatedLocalGetBoard, StationPermission.BOARD_USE);
        routes.get(fp + "/{partnerUid}/{boardKey}/lanes", this::federatedLocalGetLanes, StationPermission.BOARD_USE);
        routes.get(fp + "/{partnerUid}/{boardKey}/labels", this::federatedLocalGetLabels, StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/ticket-labels",
                this::federatedLocalGetAllTicketLabels,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/members", this::federatedLocalGetMembers, StationPermission.BOARD_USE);
        routes.get(fp + "/{partnerUid}/{boardKey}/fields", this::federatedLocalGetFields, StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets", this::federatedLocalListTickets, StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/search",
                this::federatedLocalSearchTickets,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}",
                this::federatedLocalGetTicket,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/comments",
                this::federatedLocalGetComments,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist",
                this::federatedLocalGetChecklist,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/links",
                this::federatedLocalGetLinks,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/labels",
                this::federatedLocalGetTicketLabels,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/transitions",
                this::federatedLocalGetTransitions,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/history",
                this::federatedLocalGetHistory,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/attachments",
                this::federatedLocalGetAttachments,
                StationPermission.BOARD_USE);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/watchers",
                this::federatedLocalGetWatchers,
                StationPermission.BOARD_USE);

        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets", this::federatedLocalCreateTicket, StationPermission.BOARD_USE);
        routes.put(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}",
                this::federatedLocalUpdateTicket,
                StationPermission.BOARD_USE);
        routes.delete(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}",
                this::federatedLocalDeleteTicket,
                StationPermission.BOARD_USE);
        routes.put(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/move",
                this::federatedLocalMoveTicket,
                StationPermission.BOARD_USE);
        routes.put(
                fp + "/{partnerUid}/{boardKey}/tickets/reorder",
                this::federatedLocalReorderTickets,
                StationPermission.BOARD_USE);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/comments",
                this::federatedLocalAddComment,
                StationPermission.BOARD_USE);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist",
                this::federatedLocalAddChecklistItem,
                StationPermission.BOARD_USE);
        routes.put(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
                this::federatedLocalUpdateChecklistItem,
                StationPermission.BOARD_USE);
        routes.delete(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
                this::federatedLocalDeleteChecklistItem,
                StationPermission.BOARD_USE);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
                this::federatedLocalAddTicketLabel,
                StationPermission.BOARD_USE);
        routes.delete(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
                this::federatedLocalRemoveTicketLabel,
                StationPermission.BOARD_USE);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/labels", this::federatedLocalCreateLabel, StationPermission.BOARD_USE);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/watch",
                this::federatedLocalWatchTicket,
                StationPermission.BOARD_USE);
        routes.delete(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/watch",
                this::federatedLocalUnwatchTicket,
                StationPermission.BOARD_USE);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/links",
                this::federatedLocalCreateLink,
                StationPermission.BOARD_USE);
        routes.delete(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/links/{linkedNumber}",
                this::federatedLocalDeleteLink,
                StationPermission.BOARD_USE);

        routes.get(
                fp + "/{partnerUid}/{boardKey}/access/override",
                this::federatedLocalGetOverride,
                StationPermission.BOARD_EDIT);
        routes.put(
                fp + "/{partnerUid}/{boardKey}/access/override",
                this::federatedLocalSetOverride,
                StationPermission.BOARD_EDIT);
    }

    private int resolvePartnerId(Context ctx) {
        var session = UserSession.from(ctx);
        UUID partnerUid = pathUuid(ctx, "partnerUid");
        return federationRepository
                .findPartnerByStationAndRemoteUid(session.stationId(), partnerUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"))
                .id();
    }

    /**
     * Rejects the request unless the session may view the federated board. A board that cannot be
     * resolved locally is owned by a remote partner, which enforces access on its own side.
     */
    private void requireView(int partnerId, String boardKey, UserSession session) {
        var board = proxyService.resolveFederatedBoard(partnerId, boardKey);
        if (board == null) return;
        if (!proxyService.canView(
                partnerId, board.uid(), board.id(), session.member().id())) {
            throw new ForbiddenResponse("No view access to this federated board");
        }
    }

    /**
     * Rejects the request unless the session may write to the federated board. A board that cannot
     * be resolved locally is owned by a remote partner, which enforces access on its own side.
     */
    private void requireWrite(int partnerId, String boardKey, UserSession session) {
        var board = proxyService.resolveFederatedBoard(partnerId, boardKey);
        if (board == null) return;
        if (!proxyService.canWrite(
                partnerId, board.uid(), board.id(), session.member().id())) {
            throw new ForbiddenResponse("No write access to this federated board");
        }
    }

    private String resolveDisplayName(UserSession session) {
        String name = session.member().displayName();
        if (name == null || name.isBlank()) {
            name = (session.account().firstName() + " " + session.account().lastName()).trim();
        }
        return name;
    }

    @OpenApi(
            path = "/api/v1/federated/boards",
            methods = HttpMethod.GET,
            summary = "Discover federated boards from all partners",
            tags = {"Federated Boards"},
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalDiscoverBoards(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(proxyService.discoverBoards(session.stationId()));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/bookmarks",
            methods = HttpMethod.GET,
            summary = "List federated board bookmarks for the current user",
            tags = {"Federated Boards"},
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalListBookmarks(Context ctx) {
        var session = UserSession.from(ctx);
        var bookmarks = proxyService.findBookmarks(session.member().id());
        var enriched = bookmarks.stream()
                .map(bm -> {
                    var partner = federationRepository.findPartnerById(bm.partnerId());
                    UUID uid = partner.map(FederationPartner::partnerStationId).orElse(null);
                    return new EnrichedBookmark(
                            bm.id(),
                            bm.memberId(),
                            bm.partnerId(),
                            uid,
                            bm.remoteBoardUid(),
                            bm.remoteBoardName(),
                            bm.remoteBoardShortKey(),
                            bm.shareMode(),
                            bm.createdAt());
                })
                .toList();
        ctx.json(enriched);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/bookmarks",
            methods = HttpMethod.POST,
            summary = "Create a federated board bookmark",
            tags = {"Federated Boards"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalBookmarkRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalCreateBookmark(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(LocalBookmarkRequest.class);
        UUID partnerUid = req.partnerUid();
        int partnerId = federationRepository
                .findPartnerByStationAndRemoteUid(session.stationId(), partnerUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"))
                .id();
        ctx.json(proxyService.createBookmark(
                session.member().id(),
                partnerId,
                req.remoteBoardUid(),
                req.remoteBoardName(),
                req.remoteBoardShortKey(),
                req.shareMode()));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/bookmarks/{bookmarkId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a federated board bookmark",
            tags = {"Federated Boards"},
            pathParams = @OpenApiParam(name = "bookmarkId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void federatedLocalDeleteBookmark(Context ctx) {
        int bookmarkId = ctx.pathParamAsClass("bookmarkId", Integer.class).get();
        proxyService.deleteBookmark(bookmarkId);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}",
            methods = HttpMethod.GET,
            summary = "Get a federated board via proxy",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetBoard(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        var result = proxyService.proxyGetBoard(partnerId, boardKey);
        if (result.stationName() != null) {
            ctx.header(FederationHeaders.HEADER_STATION_NAME, result.stationName());
        }
        ctx.json(result);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/lanes",
            methods = HttpMethod.GET,
            summary = "Get lanes for a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetLanes(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        ctx.json(proxyService.proxyGetLanes(partnerId, boardKey));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/labels",
            methods = HttpMethod.GET,
            summary = "Get labels for a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetLabels(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        ctx.json(proxyService.proxyGetLabels(partnerId, boardKey));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/ticket-labels",
            methods = HttpMethod.GET,
            summary = "Get all ticket-label assignments for a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetAllTicketLabels(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        ctx.json(proxyService.proxyGetAllTicketLabels(partnerId, boardKey));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/members",
            methods = HttpMethod.GET,
            summary = "List members that can be assigned to tickets on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetMembers(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        ctx.json(proxyService.proxyGetMembers(partnerId, boardKey));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/fields",
            methods = HttpMethod.GET,
            summary = "Get custom fields for a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetFields(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        ctx.json(proxyService.proxyGetFields(partnerId, boardKey));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets",
            methods = HttpMethod.GET,
            summary = "List tickets on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalListTickets(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        ctx.json(proxyService.proxyListTickets(partnerId, boardKey));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/search",
            methods = HttpMethod.GET,
            summary = "Search tickets on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            queryParams = @OpenApiParam(name = "q", type = String.class),
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalSearchTickets(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        String q = ctx.queryParam("q");
        ctx.json(proxyService.proxySearchTickets(partnerId, boardKey, q));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.GET,
            summary = "Get a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        ctx.json(proxyService.proxyGetTicket(partnerId, boardKey, ticketNumber));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/comments",
            methods = HttpMethod.GET,
            summary = "Get comments for a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetComments(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        ctx.json(proxyService.proxyGetComments(partnerId, boardKey, ticketNumber).stream()
                .map(comment -> CommentResponseMapper.fromBoard(memberNameResolver, comment))
                .toList());
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist",
            methods = HttpMethod.GET,
            summary = "Get checklist for a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetChecklist(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        ctx.json(proxyService.proxyGetChecklist(partnerId, boardKey, ticketNumber));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/links",
            methods = HttpMethod.GET,
            summary = "Get links for a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetLinks(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        ctx.json(proxyService.proxyGetLinks(partnerId, boardKey, ticketNumber));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/labels",
            methods = HttpMethod.GET,
            summary = "Get labels for a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetTicketLabels(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        ctx.json(proxyService.proxyGetTicketLabels(partnerId, boardKey, ticketNumber));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/transitions",
            methods = HttpMethod.GET,
            summary = "Get transitions for a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetTransitions(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        ctx.json(proxyService.proxyGetTransitions(partnerId, boardKey, ticketNumber));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/history",
            methods = HttpMethod.GET,
            summary = "Get history for a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetHistory(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        ctx.json(proxyService.proxyGetHistory(partnerId, boardKey, ticketNumber));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/attachments",
            methods = HttpMethod.GET,
            summary = "Get attachments for a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetAttachments(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        ctx.json(proxyService.proxyGetAttachments(partnerId, boardKey, ticketNumber));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/watchers",
            methods = HttpMethod.GET,
            summary = "Get watchers for a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetWatchers(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireView(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        ctx.json(proxyService.proxyGetWatchers(partnerId, boardKey, ticketNumber));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets",
            methods = HttpMethod.POST,
            summary = "Create a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalCreateTicketRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalCreateTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        var req = ctx.bodyAsClass(LocalCreateTicketRequest.class);
        ctx.json(proxyService.proxyCreateTicket(
                partnerId,
                boardKey,
                req.laneId(),
                req.title(),
                req.description(),
                req.priority(),
                req.dueDate(),
                session.member().uid()));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.PUT,
            summary = "Update a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalUpdateTicketRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalUpdateTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        var req = ctx.bodyAsClass(LocalUpdateTicketRequest.class);
        ctx.json(proxyService.proxyUpdateTicket(
                partnerId,
                boardKey,
                ticketNumber,
                req.title(),
                req.description(),
                req.assignedMemberId(),
                req.priority(),
                req.dueDate(),
                session.member().uid(),
                resolveDisplayName(session)));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.DELETE,
            summary = "Delete a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void federatedLocalDeleteTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        proxyService.proxyDeleteTicket(partnerId, boardKey, ticketNumber);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/move",
            methods = HttpMethod.PUT,
            summary = "Move a ticket to a different lane on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalMoveTicketRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalMoveTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        var req = ctx.bodyAsClass(LocalMoveTicketRequest.class);
        UUID memberUid = session.member() != null ? session.member().uid() : null;
        ctx.json(proxyService.proxyMoveTicket(
                partnerId,
                boardKey,
                ticketNumber,
                req.toLaneId(),
                req.position(),
                memberUid,
                resolveDisplayName(session)));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/reorder",
            methods = HttpMethod.PUT,
            summary = "Reorder tickets in a lane on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalReorderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedLocalReorderTickets(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        var req = ctx.bodyAsClass(LocalReorderRequest.class);
        proxyService.proxyReorderTickets(partnerId, boardKey, req.laneId(), req.orderedIds());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/comments",
            methods = HttpMethod.POST,
            summary = "Add a comment to a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalCommentRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalAddComment(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        var req = ctx.bodyAsClass(LocalCommentRequest.class);
        ctx.json(proxyService.proxyAddComment(
                partnerId,
                boardKey,
                ticketNumber,
                req.parentId(),
                req.content(),
                session.member().uid(),
                resolveDisplayName(session)));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist",
            methods = HttpMethod.POST,
            summary = "Add a checklist item to a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalChecklistItemRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalAddChecklistItem(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        var req = ctx.bodyAsClass(LocalChecklistItemRequest.class);
        ctx.json(proxyService.proxyAddChecklistItem(
                partnerId, boardKey, ticketNumber, req.title(), session.member().uid(), resolveDisplayName(session)));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
            methods = HttpMethod.PUT,
            summary = "Update a checklist item on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalUpdateChecklistItemRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedLocalUpdateChecklistItem(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        var req = ctx.bodyAsClass(LocalUpdateChecklistItemRequest.class);
        proxyService.proxyUpdateChecklistItem(
                partnerId,
                boardKey,
                ticketNumber,
                itemId,
                req.title(),
                req.checked(),
                session.member().uid(),
                resolveDisplayName(session));
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a checklist item on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void federatedLocalDeleteChecklistItem(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        proxyService.proxyDeleteChecklistItem(
                partnerId, boardKey, ticketNumber, itemId, session.member().uid(), resolveDisplayName(session));
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
            methods = HttpMethod.POST,
            summary = "Add a label to a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalAddTicketLabel(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        ctx.json(proxyService.proxyAddTicketLabel(
                partnerId, boardKey, ticketNumber, labelId, session.member().uid(), resolveDisplayName(session)));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a label from a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void federatedLocalRemoveTicketLabel(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        proxyService.proxyRemoveTicketLabel(
                partnerId, boardKey, ticketNumber, labelId, session.member().uid(), resolveDisplayName(session));
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/labels",
            methods = HttpMethod.POST,
            summary = "Create a label on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalCreateLabelRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalCreateLabel(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        var req = ctx.bodyAsClass(LocalCreateLabelRequest.class);
        ctx.json(proxyService.proxyCreateLabel(partnerId, boardKey, req.name(), req.color()));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/watch",
            methods = HttpMethod.POST,
            summary = "Watch a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void federatedLocalWatchTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        proxyService.proxyWatchTicket(
                partnerId, boardKey, ticketNumber, session.member().uid());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/tickets/{ticketNumber}/watch",
            methods = HttpMethod.DELETE,
            summary = "Unwatch a ticket on a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void federatedLocalUnwatchTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        proxyService.proxyUnwatchTicket(
                partnerId, boardKey, ticketNumber, session.member().uid());
        ctx.status(204);
    }

    private void federatedLocalCreateLink(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        var req = ctx.bodyAsClass(LocalLinkRequest.class);
        proxyService.proxyCreateLink(
                partnerId,
                boardKey,
                ticketNumber,
                req.linkedTicketNumber(),
                req.linkType(),
                session.member().uid(),
                resolveDisplayName(session));
        ctx.status(HttpStatus.CREATED);
    }

    private void federatedLocalDeleteLink(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        requireWrite(partnerId, boardKey, session);
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        int linkedNumber = ctx.pathParamAsClass("linkedNumber", Integer.class).get();
        proxyService.proxyDeleteLink(
                partnerId,
                boardKey,
                ticketNumber,
                linkedNumber,
                session.member().uid(),
                resolveDisplayName(session));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/access/override",
            methods = HttpMethod.GET,
            summary = "Get local access overrides for a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedLocalGetOverride(Context ctx) {
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        UUID boardUid = proxyService.resolveFederatedBoardUid(partnerId, boardKey);
        if (boardUid == null) throw new NotFoundResponse("Board not found: " + boardKey);
        var view = proxyService.getLocalViewOverride(partnerId, boardUid);
        var edit = proxyService.getLocalEditOverride(partnerId, boardUid);
        ctx.json(new AccessOverrideResponse(view, edit));
    }

    @OpenApi(
            path = "/api/v1/federated/boards/{partnerUid}/{boardKey}/access/override",
            methods = HttpMethod.PUT,
            summary = "Set local access overrides for a federated board",
            tags = {"Federated Boards"},
            pathParams = {
                @OpenApiParam(name = "partnerUid", type = String.class, required = true),
                @OpenApiParam(name = "boardKey", type = String.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LocalOverrideRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedLocalSetOverride(Context ctx) {
        int partnerId = resolvePartnerId(ctx);
        String boardKey = ctx.pathParam("boardKey");
        UUID boardUid = proxyService.resolveFederatedBoardUid(partnerId, boardKey);
        if (boardUid == null) throw new NotFoundResponse("Board not found: " + boardKey);
        var req = ctx.bodyAsClass(LocalOverrideRequest.class);
        proxyService.setLocalViewOverride(
                partnerId, boardUid, new AccessData(req.viewUserTypes(), req.viewGroupIds(), req.viewTagIds()));
        proxyService.setLocalEditOverride(
                partnerId, boardUid, new AccessData(req.editUserTypes(), req.editGroupIds(), req.editTagIds()));
        ctx.status(204);
    }

    record LocalBookmarkRequest(
            UUID partnerUid,
            UUID remoteBoardUid,
            String remoteBoardName,
            String remoteBoardShortKey,
            BoardShareMode shareMode) {}

    record LocalCreateTicketRequest(
            Integer laneId, String title, String description, TicketPriority priority, LocalDate dueDate) {}

    record LocalUpdateTicketRequest(
            String title, String description, Integer assignedMemberId, TicketPriority priority, LocalDate dueDate) {}

    record LocalMoveTicketRequest(int toLaneId, int position) {}

    record LocalReorderRequest(int laneId, List<Integer> orderedIds) {}

    record LocalCommentRequest(Integer parentId, String content) {}

    record LocalLinkRequest(int linkedTicketNumber, LinkType linkType) {}

    record LocalChecklistItemRequest(String title) {}

    record LocalUpdateChecklistItemRequest(String title, boolean checked) {}

    record LocalCreateLabelRequest(String name, String color) {}

    record LocalOverrideRequest(
            List<StationUserType> viewUserTypes,
            List<Integer> viewGroupIds,
            List<Integer> viewTagIds,
            List<StationUserType> editUserTypes,
            List<Integer> editGroupIds,
            List<Integer> editTagIds) {}

    record EnrichedBookmark(
            int id,
            int memberId,
            int partnerId,
            UUID partnerStationUid,
            UUID remoteBoardUid,
            String remoteBoardName,
            String remoteBoardShortKey,
            BoardShareMode shareMode,
            Instant createdAt) {}

    record AccessOverrideResponse(AccessData view, AccessData edit) {}
}
