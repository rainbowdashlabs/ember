/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryAction;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryResponse;
import dev.chojo.ember.feature.board.entity.BoardTicketTransitionResponse;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.board.service.FederatedBoardProxyService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Server-to-server board endpoints served to federation partners, plus the webhook receivers a
 * partner station is notified through. Requests carry an RSA-signed envelope instead of a user
 * session; the local proxy that calls these endpoints lives in {@link FederatedBoardRoutes}.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class RemoteBoardRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(RemoteBoardRoutes.class);

    private final BoardService boardService;
    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardProxyService proxyService;
    private final BoardTicketService ticketService;
    private final EventFederationRepository eventFederationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final StationRepository stationRepository;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public RemoteBoardRoutes(
            BoardService boardService,
            FederatedBoardService federatedBoardService,
            FederatedBoardProxyService proxyService,
            BoardTicketService ticketService,
            EventFederationRepository eventFederationRepository,
            StationMemberRepository stationMemberRepository,
            StationRepository stationRepository,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory) {
        this.boardService = boardService;
        this.federatedBoardService = federatedBoardService;
        this.proxyService = proxyService;
        this.ticketService = ticketService;
        this.eventFederationRepository = eventFederationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.stationRepository = stationRepository;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String rp = prefix + "/remote/boards";

        routes.get(rp, this::federatedRemoteListSharedBoards);
        routes.get(rp + "/{boardKey}", this::federatedRemoteGetBoard);
        routes.get(rp + "/{boardKey}/lanes", this::federatedRemoteGetLanes);
        routes.get(rp + "/{boardKey}/labels", this::federatedRemoteGetLabels);
        routes.get(rp + "/{boardKey}/ticket-labels", this::federatedRemoteGetAllTicketLabels);
        routes.get(rp + "/{boardKey}/fields", this::federatedRemoteGetFields);
        routes.get(rp + "/{boardKey}/tickets", this::federatedRemoteListTickets);
        routes.get(rp + "/{boardKey}/tickets/search", this::federatedRemoteSearchTickets);
        routes.get(rp + "/{boardKey}/tickets/{ticketNumber}", this::federatedRemoteGetTicket);
        routes.get(rp + "/{boardKey}/tickets/{ticketNumber}/comments", this::federatedRemoteGetComments);
        routes.get(rp + "/{boardKey}/tickets/{ticketNumber}/checklist", this::federatedRemoteGetChecklist);
        routes.get(rp + "/{boardKey}/tickets/{ticketNumber}/links", this::federatedRemoteGetLinks);
        routes.get(rp + "/{boardKey}/tickets/{ticketNumber}/labels", this::federatedRemoteGetTicketLabels);
        routes.get(rp + "/{boardKey}/tickets/{ticketNumber}/transitions", this::federatedRemoteGetTransitions);
        routes.get(rp + "/{boardKey}/tickets/{ticketNumber}/history", this::federatedRemoteGetHistory);
        routes.get(rp + "/{boardKey}/tickets/{ticketNumber}/attachments", this::federatedRemoteGetAttachments);
        routes.get(rp + "/{boardKey}/tickets/{ticketNumber}/watchers", this::federatedRemoteGetWatchers);
        routes.get(rp + "/{boardKey}/access", this::federatedRemoteGetAccess);
        routes.get(rp + "/{boardKey}/members", this::federatedRemoteGetMembers);

        routes.post(rp + "/{boardKey}/tickets", this::federatedRemoteCreateTicket);
        routes.put(rp + "/{boardKey}/tickets/{ticketNumber}", this::federatedRemoteUpdateTicket);
        routes.delete(rp + "/{boardKey}/tickets/{ticketNumber}", this::federatedRemoteDeleteTicket);
        routes.put(rp + "/{boardKey}/tickets/{ticketNumber}/move", this::federatedRemoteMoveTicket);
        routes.put(rp + "/{boardKey}/tickets/reorder", this::federatedRemoteReorderTickets);
        routes.post(rp + "/{boardKey}/tickets/{ticketNumber}/comments", this::federatedRemoteAddComment);
        routes.put(rp + "/{boardKey}/tickets/{ticketNumber}/comments/{commentId}", this::federatedRemoteEditComment);
        routes.delete(
                rp + "/{boardKey}/tickets/{ticketNumber}/comments/{commentId}", this::federatedRemoteDeleteComment);
        routes.post(rp + "/{boardKey}/tickets/{ticketNumber}/checklist", this::federatedRemoteAddChecklistItem);
        routes.put(
                rp + "/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}", this::federatedRemoteUpdateChecklistItem);
        routes.delete(
                rp + "/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}", this::federatedRemoteDeleteChecklistItem);
        routes.post(rp + "/{boardKey}/tickets/{ticketNumber}/labels/{labelId}", this::federatedRemoteAddTicketLabel);
        routes.delete(
                rp + "/{boardKey}/tickets/{ticketNumber}/labels/{labelId}", this::federatedRemoteRemoveTicketLabel);
        routes.post(
                rp + "/{boardKey}/tickets/{ticketNumber}/labels/{labelId}/remove",
                this::federatedRemoteRemoveTicketLabel);
        routes.post(rp + "/{boardKey}/labels", this::federatedRemoteCreateLabel);
        routes.post(rp + "/{boardKey}/tickets/{ticketNumber}/watch", this::federatedRemoteWatchTicket);
        routes.delete(rp + "/{boardKey}/tickets/{ticketNumber}/watch", this::federatedRemoteUnwatchTicket);
        routes.post(rp + "/{boardKey}/tickets/{ticketNumber}/links", this::federatedRemoteCreateLink);
        routes.delete(rp + "/{boardKey}/tickets/{ticketNumber}/links/{linkedNumber}", this::federatedRemoteDeleteLink);

        routes.post(rp + "/webhook/ticket-changed", this::federatedRemoteOnTicketChanged);
        routes.post(rp + "/webhook/mention", this::federatedRemoteOnMention);
        routes.post(rp + "/webhook/assignment", this::federatedRemoteOnAssignment);
        routes.post(rp + "/webhook/unassignment", this::federatedRemoteOnUnassignment);
        routes.post(rp + "/webhook/board-renamed", this::federatedRemoteOnBoardRenamed);
        routes.post(rp + "/webhook/board-unshared", this::federatedRemoteOnBoardUnshared);
        routes.post(rp + "/webhook/share-mode-changed", this::federatedRemoteOnShareModeChanged);
    }

    /**
     * Returns the verified federation partner from the centrally resolved session.
     * The signature is verified by {@link dev.chojo.ember.api.AccessManager} before this handler runs.
     */
    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    private int resolveRemoteBoardId(Context ctx, FederationPartner partner) {
        String boardKey = ctx.pathParam("boardKey");
        return boardService
                .findByShortKey(partner.stationId(), boardKey)
                .orElseThrow(() -> new NotFoundResponse("Board not found: " + boardKey))
                .id();
    }

    private int resolveRemoteTicketId(Context ctx, int boardId) {
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        return ticketService
                .findByBoardAndNumber(boardId, ticketNumber)
                .orElseThrow(() -> new NotFoundResponse("Ticket not found: " + ticketNumber))
                .id();
    }

    private void requireRemoteView(int boardId, FederationPartner partner) {
        if (!federatedBoardService.canFederatedView(boardId, partner.id())) {
            throw new ForbiddenResponse("Board not shared with this partner");
        }
    }

    private void requireRemoteWrite(int boardId, FederationPartner partner) {
        if (!federatedBoardService.canFederatedWrite(boardId, partner.id())) {
            throw new ForbiddenResponse("Write access requires FULL share mode");
        }
    }

    @OpenApi(
            path = "/api/v1/remote/boards",
            methods = HttpMethod.GET,
            summary = "List boards shared with the requesting partner",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "200"))
    private void federatedRemoteListSharedBoards(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var boardIds = federatedBoardService.findSharedBoardIds(partner.id());
        var boards = boardIds.stream()
                .map(id -> boardService.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(board -> {
                    var mode = federatedBoardService
                            .getShareMode(board.id(), partner.id())
                            .orElse(BoardShareMode.READ_ONLY);
                    var requiredUserType = federatedBoardService
                            .getRequiredUserType(board.id(), partner.id())
                            .orElse(StationUserType.MEMBER);
                    return new RemoteSharedBoardResponse(
                            board.uid(),
                            board.name(),
                            board.description() != null ? board.description() : "",
                            board.shortKey(),
                            mode,
                            requiredUserType);
                })
                .toList();
        ctx.json(boards);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}",
            methods = HttpMethod.GET,
            summary = "Get a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void federatedRemoteGetBoard(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        var mode = federatedBoardService.getShareMode(boardId, partner.id()).orElse(BoardShareMode.READ_ONLY);
        String stationName =
                stationRepository.findById(board.stationId()).map(Station::name).orElse("");
        ctx.json(FederatedBoardProxyService.FederatedBoardDetail.of(board, mode, stationName, stationRepository));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/lanes",
            methods = HttpMethod.GET,
            summary = "Get lanes for a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void federatedRemoteGetLanes(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        ctx.json(boardService.findLanes(boardId));
    }

    private void federatedRemoteGetLabels(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        ctx.json(boardService.findLabels(boardId));
    }

    private void federatedRemoteGetAllTicketLabels(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        ctx.json(boardService.findAllTicketLabels(boardId));
    }

    private void federatedRemoteGetFields(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        ctx.json(boardService.findFields(boardId));
    }

    private void federatedRemoteListTickets(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        ctx.json(ticketService.findByBoard(boardId).stream()
                .map(TicketSummary::of)
                .map(t -> t.assignee() != null ? t.withAssignee(memberNameResolver.enrichDisplay(t.assignee())) : t)
                .toList());
    }

    private void federatedRemoteSearchTickets(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        String q = ctx.queryParam("q");
        var tickets =
                (q == null || q.isBlank()) ? ticketService.findByBoard(boardId) : ticketService.search(boardId, q);
        ctx.json(tickets.stream()
                .map(TicketSummary::of)
                .map(t -> t.assignee() != null ? t.withAssignee(memberNameResolver.enrichDisplay(t.assignee())) : t)
                .toList());
    }

    private void federatedRemoteGetTicket(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    private void federatedRemoteGetComments(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        ctx.json(ticketService.findComments(ticketId).stream()
                .map(comment -> CommentResponseMapper.fromBoard(memberNameResolver, comment))
                .toList());
    }

    private void federatedRemoteGetChecklist(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        ctx.json(ticketService.findChecklistItems(ticketId));
    }

    private void federatedRemoteGetLinks(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        ctx.json(ticketService.findLinks(ticketId));
    }

    private void federatedRemoteGetTicketLabels(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    private void federatedRemoteGetTransitions(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var transitions = ticketService.findTransitions(ticketId);
        ctx.json(transitions.stream()
                .map(tr -> {
                    var resolved = memberNameResolver.resolveDisplay(tr.actor());
                    return BoardTicketTransitionResponse.from(tr, resolved.identity(), resolved.name());
                })
                .toList());
    }

    private void federatedRemoteGetHistory(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var history = ticketService.findHistory(ticketId);
        ctx.json(history.stream()
                .map(h -> {
                    var resolved = memberNameResolver.resolveDisplay(h.actor());
                    return BoardTicketHistoryResponse.from(h, resolved.identity(), resolved.name());
                })
                .toList());
    }

    private void federatedRemoteGetAttachments(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        ctx.json(ticketService.findAttachments(ticketId));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/watchers",
            methods = HttpMethod.GET,
            summary = "Get watchers for a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedRemoteGetWatchers(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var localWatchers = ticketService.findWatchers(ticketId);
        ctx.json(new WatcherResponse(localWatchers, List.of()));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/access",
            methods = HttpMethod.GET,
            summary = "Get access configuration for a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void federatedRemoteGetAccess(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        var mode = federatedBoardService.getShareMode(boardId, partner.id()).orElse(BoardShareMode.READ_ONLY);
        var editUserTypes = federatedBoardService.findFederatedEditUserTypes(boardId);
        ctx.json(new RemoteAccessResponse(mode, editUserTypes));
    }

    private void federatedRemoteGetMembers(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        ctx.json(memberIdentityFactory.enrichCompletions(stationMemberRepository.findCompletions(board.stationId())));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets",
            methods = HttpMethod.POST,
            summary = "Create a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteCreateTicketRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedRemoteCreateTicket(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        var req = ctx.bodyAsClass(RemoteCreateTicketRequest.class);
        boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        int laneId = req.laneId() != null
                ? req.laneId()
                : boardService.findLanes(boardId).getFirst().id();
        var creatorIdentity = new MemberIdentity(partner.partnerStationId(), req.remoteMemberId());
        var ticket = ticketService.createTicket(
                boardId,
                laneId,
                req.title(),
                req.description(),
                null,
                req.priority() != null ? TicketPriority.valueOf(req.priority()) : TicketPriority.MEDIUM,
                req.dueDate() != null ? LocalDate.parse(req.dueDate()) : null,
                creatorIdentity);
        ctx.json(ticket);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.PUT,
            summary = "Update a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteUpdateTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void federatedRemoteUpdateTicket(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(RemoteUpdateTicketRequest.class);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        MemberIdentity assigneeIdentity = req.assignedMemberId() != null
                ? memberIdentityFactory.local(board.stationId(), req.assignedMemberId())
                : null;
        var actorIdentity = req.remoteMemberUid() != null
                ? new MemberIdentity(partner.partnerStationId(), req.remoteMemberUid())
                : null;
        if (req.displayName() != null && req.remoteMemberUid() != null) {
            eventFederationRepository.cacheName(partner.id(), req.remoteMemberUid(), req.displayName());
        }
        ticketService.updateTicket(
                ticketId,
                req.title(),
                req.description(),
                assigneeIdentity,
                req.priority() != null ? TicketPriority.valueOf(req.priority()) : null,
                req.dueDate() != null ? LocalDate.parse(req.dueDate()) : null,
                actorIdentity);
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}",
            methods = HttpMethod.DELETE,
            summary = "Delete a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteDeleteTicket(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        ticketService.deleteTicket(ticketId);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/move",
            methods = HttpMethod.PUT,
            summary = "Move a ticket to a different lane on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteMoveTicketRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void federatedRemoteMoveTicket(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(RemoteMoveTicketRequest.class);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        var actorIdentity = req.remoteMemberUid() != null
                ? new MemberIdentity(partner.partnerStationId(), req.remoteMemberUid())
                : null;
        if (req.displayName() != null && req.remoteMemberUid() != null) {
            eventFederationRepository.cacheName(partner.id(), req.remoteMemberUid(), req.displayName());
        }
        ticketService.moveTicket(ticketId, ticket.laneId(), req.toLaneId(), req.position(), actorIdentity);
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/reorder",
            methods = HttpMethod.PUT,
            summary = "Reorder tickets in a lane on a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteReorderRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteReorderTickets(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        var req = ctx.bodyAsClass(RemoteReorderRequest.class);
        ticketService.reorderTickets(req.laneId(), req.orderedIds());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/comments",
            methods = HttpMethod.POST,
            summary = "Add a comment to a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteCommentRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedRemoteAddComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(RemoteCommentRequest.class);
        var authorIdentity = new MemberIdentity(partner.partnerStationId(), req.remoteMemberId());
        var comment = ticketService.createComment(ticketId, req.parentId(), authorIdentity, req.content());
        eventFederationRepository.cacheName(partner.id(), req.remoteMemberId(), req.displayName());
        ctx.json(comment);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/comments/{commentId}",
            methods = HttpMethod.PUT,
            summary = "Edit a comment on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteEditCommentRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteEditComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteEditCommentRequest.class);
        ticketService.updateComment(commentId, req.content());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/comments/{commentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a comment on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "commentId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteDeleteComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int commentId = ctx.pathParamAsClass("commentId", Integer.class).get();
        ticketService.deleteComment(commentId);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/checklist",
            methods = HttpMethod.POST,
            summary = "Add a checklist item to a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteChecklistItemRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedRemoteAddChecklistItem(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(RemoteChecklistItemRequest.class);
        if (req.displayName() != null && req.remoteMemberUid() != null) {
            eventFederationRepository.cacheName(partner.id(), req.remoteMemberUid(), req.displayName());
        }
        ctx.json(ticketService.addChecklistItem(ticketId, req.title(), 0));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
            methods = HttpMethod.PUT,
            summary = "Update a checklist item on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteUpdateChecklistItemRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteUpdateChecklistItem(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteUpdateChecklistItemRequest.class);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        if (req.displayName() != null && req.remoteMemberUid() != null) {
            eventFederationRepository.cacheName(partner.id(), req.remoteMemberUid(), req.displayName());
        }
        ticketService.updateChecklistItem(itemId, ticketId, req.title(), req.checked(), 0);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a checklist item on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "itemId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteDeleteChecklistItem(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        ticketService.deleteChecklistItem(itemId, ticketId, 0);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
            methods = HttpMethod.POST,
            summary = "Add a label to a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "200"))
    private void federatedRemoteAddTicketLabel(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteLabelActionRequest.class);
        boardService.addLabelToTicket(ticketId, labelId);
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        ticketService.logHistory(
                ticketId,
                BoardTicketHistoryAction.LABEL_ADDED,
                label != null ? label.name() : "?",
                new MemberIdentity(partner.partnerStationId(), req.remoteMemberId()));
        if (req.displayName() != null) {
            eventFederationRepository.cacheName(partner.id(), req.remoteMemberId(), req.displayName());
        }
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
            methods = HttpMethod.DELETE,
            summary = "Remove a label from a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteRemoveTicketLabel(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        var req = ctx.bodyAsClass(RemoteLabelActionRequest.class);
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        boardService.removeLabelFromTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId,
                BoardTicketHistoryAction.LABEL_REMOVED,
                label != null ? label.name() : "?",
                new MemberIdentity(partner.partnerStationId(), req.remoteMemberId()));
        if (req.displayName() != null) {
            eventFederationRepository.cacheName(partner.id(), req.remoteMemberId(), req.displayName());
        }
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/labels",
            methods = HttpMethod.POST,
            summary = "Create a label on a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteCreateLabelRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void federatedRemoteCreateLabel(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        var req = ctx.bodyAsClass(RemoteCreateLabelRequest.class);
        ctx.json(boardService.createLabel(boardId, req.name(), req.color() != null ? req.color() : "#6b7280"));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/watch",
            methods = HttpMethod.POST,
            summary = "Watch a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteWatchRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteWatchTicket(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(RemoteWatchRequest.class);
        var watcherIdentity = new MemberIdentity(partner.partnerStationId(), req.remoteMemberId());
        ticketService.addWatcher(ticketId, watcherIdentity);
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/tickets/{ticketNumber}/watch",
            methods = HttpMethod.DELETE,
            summary = "Unwatch a ticket on a shared board",
            tags = {"Boards Remote"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "ticketNumber", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteWatchRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteUnwatchTicket(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(RemoteWatchRequest.class);
        var watcherIdentity = new MemberIdentity(partner.partnerStationId(), req.remoteMemberId());
        ticketService.removeWatcher(ticketId, watcherIdentity);
        ctx.status(204);
    }

    private void federatedRemoteCreateLink(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var req = ctx.bodyAsClass(RemoteLinkRequest.class);
        int linkedTicketId = ticketService
                .findByBoardAndNumber(boardId, req.linkedTicketNumber())
                .orElseThrow(NotFoundResponse::new)
                .id();
        var actorIdentity = req.remoteMemberUid() != null
                ? new MemberIdentity(partner.partnerStationId(), req.remoteMemberUid())
                : null;
        if (req.displayName() != null && req.remoteMemberUid() != null) {
            eventFederationRepository.cacheName(partner.id(), req.remoteMemberUid(), req.displayName());
        }
        ticketService.linkTickets(ticketId, linkedTicketId, req.linkType(), actorIdentity);
        ctx.status(HttpStatus.CREATED);
    }

    /**
     * Removes a link between two tickets. The request body is optional because legacy partners send
     * none, so an unparsable body leaves the action unattributed instead of failing the call.
     */
    private void federatedRemoteDeleteLink(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteWrite(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        int linkedNumber = ctx.pathParamAsClass("linkedNumber", Integer.class).get();
        int linkedTicketId = ticketService
                .findByBoardAndNumber(boardId, linkedNumber)
                .orElseThrow(NotFoundResponse::new)
                .id();
        MemberIdentity actorIdentity = null;
        try {
            var req = ctx.bodyAsClass(RemoteDeleteLinkRequest.class);
            if (req != null && req.remoteMemberUid() != null) {
                UUID memberUid = req.remoteMemberUid();
                actorIdentity = new MemberIdentity(partner.partnerStationId(), memberUid);
                if (req.displayName() != null) {
                    eventFederationRepository.cacheName(partner.id(), memberUid, req.displayName());
                }
            }
        } catch (Exception ignored) {
        }
        ticketService.unlinkTickets(ticketId, linkedTicketId, actorIdentity);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/ticket-changed",
            methods = HttpMethod.POST,
            summary = "Webhook: ticket changed notification",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteOnTicketChanged(Context ctx) {
        requireFederationPartner(ctx);
        log.info("Received board ticket-changed webhook");
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/mention",
            methods = HttpMethod.POST,
            summary = "Webhook: mention notification",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteOnMention(Context ctx) {
        requireFederationPartner(ctx);
        log.info("Received board mention webhook");
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/assignment",
            methods = HttpMethod.POST,
            summary = "Webhook: assignment notification",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteOnAssignment(Context ctx) {
        requireFederationPartner(ctx);
        log.info("Received board assignment webhook");
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/unassignment",
            methods = HttpMethod.POST,
            summary = "Webhook: unassignment notification",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteOnUnassignment(Context ctx) {
        requireFederationPartner(ctx);
        log.info("Received board unassignment webhook");
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/board-renamed",
            methods = HttpMethod.POST,
            summary = "Webhook: board renamed notification",
            tags = {"Boards Remote"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteBoardRenamedWebhook.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteOnBoardRenamed(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var req = ctx.bodyAsClass(RemoteBoardRenamedWebhook.class);
        proxyService.onBoardRenamed(partner.id(), req.boardUid(), req.newName(), req.newShortKey());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/board-unshared",
            methods = HttpMethod.POST,
            summary = "Webhook: board unshared notification",
            tags = {"Boards Remote"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteBoardUnsharedWebhook.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteOnBoardUnshared(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var req = ctx.bodyAsClass(RemoteBoardUnsharedWebhook.class);
        proxyService.onBoardUnshared(partner.id(), req.boardUid());
        ctx.status(204);
    }

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/share-mode-changed",
            methods = HttpMethod.POST,
            summary = "Webhook: share mode changed notification",
            tags = {"Boards Remote"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteShareModeChangedWebhook.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteOnShareModeChanged(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var req = ctx.bodyAsClass(RemoteShareModeChangedWebhook.class);
        proxyService.onShareModeChanged(partner.id(), req.boardUid(), req.shareMode());
        ctx.status(204);
    }

    public record RemoteSharedBoardResponse(
            UUID uid,
            String name,
            String description,
            String shortKey,
            BoardShareMode shareMode,
            StationUserType requiredUserType) {}

    record RemoteCreateTicketRequest(
            UUID remoteMemberId, Integer laneId, String title, String description, String priority, String dueDate) {}

    record RemoteUpdateTicketRequest(
            String title,
            String description,
            Integer assignedMemberId,
            String priority,
            String dueDate,
            UUID remoteMemberUid,
            String displayName) {}

    record RemoteMoveTicketRequest(int toLaneId, int position, UUID remoteMemberUid, String displayName) {}

    record RemoteReorderRequest(int laneId, List<Integer> orderedIds) {}

    record RemoteCommentRequest(UUID remoteMemberId, String displayName, Integer parentId, String content) {}

    record RemoteEditCommentRequest(String content) {}

    record RemoteChecklistItemRequest(String title, UUID remoteMemberUid, String displayName) {}

    record RemoteUpdateChecklistItemRequest(String title, boolean checked, UUID remoteMemberUid, String displayName) {}

    record RemoteLinkRequest(int linkedTicketNumber, LinkType linkType, UUID remoteMemberUid, String displayName) {}

    record RemoteDeleteLinkRequest(UUID remoteMemberUid, String displayName) {}

    record RemoteCreateLabelRequest(String name, String color) {}

    record RemoteLabelActionRequest(UUID remoteMemberId, String displayName) {}

    record RemoteWatchRequest(UUID remoteMemberId) {}

    record RemoteBoardRenamedWebhook(UUID boardUid, String newName, String newShortKey) {}

    record RemoteBoardUnsharedWebhook(UUID boardUid) {}

    record RemoteShareModeChangedWebhook(UUID boardUid, BoardShareMode shareMode) {}

    record WatcherResponse(List<Integer> local, List<Object> federated) {}

    record RemoteAccessResponse(BoardShareMode shareMode, List<StationUserType> editUserTypes) {}
}
