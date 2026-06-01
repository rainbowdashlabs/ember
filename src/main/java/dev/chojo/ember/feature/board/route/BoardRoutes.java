/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.FederationHeaders;
import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.BoardFieldType;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryResponse;
import dev.chojo.ember.feature.board.entity.BoardTicketTransitionResponse;
import dev.chojo.ember.feature.board.entity.LaneData;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.board.service.FederatedBoardProxyService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;

@Singleton
public class BoardRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(BoardRoutes.class);

    private final BoardService boardService;
    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardProxyService proxyService;
    private final FederationRepository federationRepository;
    private final BoardTicketService ticketService;
    private final FederatedBoardRepository federatedBoardRepository;
    private final EventFederationRepository eventFederationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public BoardRoutes(
            BoardService boardService,
            FederatedBoardService federatedBoardService,
            FederatedBoardProxyService proxyService,
            FederationRepository federationRepository,
            BoardTicketService ticketService,
            FederatedBoardRepository federatedBoardRepository,
            EventFederationRepository eventFederationRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory) {
        this.boardService = boardService;
        this.federatedBoardService = federatedBoardService;
        this.proxyService = proxyService;
        this.federationRepository = federationRepository;
        this.ticketService = ticketService;
        this.federatedBoardRepository = federatedBoardRepository;
        this.eventFederationRepository = eventFederationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    private Board resolveBoard(Context ctx, int stationId) {
        String boardKey = ctx.pathParam("boardKey");
        return boardService
                .findByShortKey(stationId, boardKey)
                .orElseThrow(() -> new NotFoundResponse("Board not found: " + boardKey));
    }

    private int resolveBoardId(Context ctx, int stationId) {
        return resolveBoard(ctx, stationId).id();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // -- Local board CRUD --
        routes.get(prefix + "/boards", this::list, Roles.USER);
        routes.post(prefix + "/boards", this::create, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{boardKey}", this::get, Roles.USER);
        routes.put(prefix + "/boards/{boardKey}", this::update, Roles.BOARD_MANAGER);
        routes.delete(prefix + "/boards/{boardKey}", this::delete, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{boardKey}/can-edit", this::canEdit, Roles.USER);
        routes.get(prefix + "/boards/{boardKey}/lanes", this::getLanes, Roles.USER);
        routes.put(prefix + "/boards/{boardKey}/lanes", this::setLanes, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{boardKey}/fields", this::getFields, Roles.USER);
        routes.put(prefix + "/boards/{boardKey}/fields", this::setFields, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{boardKey}/access/view", this::getViewAccess, Roles.BOARD_MANAGER);
        routes.put(prefix + "/boards/{boardKey}/access/view", this::setViewAccess, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{boardKey}/access/edit", this::getEditAccess, Roles.BOARD_MANAGER);
        routes.put(prefix + "/boards/{boardKey}/access/edit", this::setEditAccess, Roles.BOARD_MANAGER);
        routes.post(prefix + "/boards/{boardKey}/backlog", this::enableBacklog, Roles.BOARD_MANAGER);
        routes.delete(prefix + "/boards/{boardKey}/backlog", this::disableBacklog, Roles.BOARD_MANAGER);
        // Labels
        routes.get(prefix + "/boards/{boardKey}/labels", this::getLabels, Roles.USER);
        routes.post(prefix + "/boards/{boardKey}/labels", this::createLabel, Roles.USER);
        routes.put(prefix + "/boards/{boardKey}/labels/{labelId}", this::updateLabel, Roles.BOARD_MANAGER);
        routes.delete(prefix + "/boards/{boardKey}/labels/{labelId}", this::deleteLabel, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{boardKey}/ticket-labels", this::getAllTicketLabels, Roles.USER);
        // Federation sharing config
        routes.get(prefix + "/boards/{boardKey}/federation", this::getFederationConfig, Roles.BOARD_MANAGER);
        routes.put(prefix + "/boards/{boardKey}/federation", this::setFederationConfig, Roles.BOARD_MANAGER);

        // -- Federated board local proxy endpoints --
        String fp = prefix + "/federated/boards";

        // Discovery
        routes.get(fp, this::federatedLocalDiscoverBoards, Roles.USER);

        // Bookmarks
        routes.get(fp + "/bookmarks", this::federatedLocalListBookmarks, Roles.USER);
        routes.post(fp + "/bookmarks", this::federatedLocalCreateBookmark, Roles.USER);
        routes.delete(fp + "/bookmarks/{bookmarkId}", this::federatedLocalDeleteBookmark, Roles.USER);

        // Board read (proxied)
        routes.get(fp + "/{partnerUid}/{boardKey}", this::federatedLocalGetBoard, Roles.USER);
        routes.get(fp + "/{partnerUid}/{boardKey}/lanes", this::federatedLocalGetLanes, Roles.USER);
        routes.get(fp + "/{partnerUid}/{boardKey}/labels", this::federatedLocalGetLabels, Roles.USER);
        routes.get(fp + "/{partnerUid}/{boardKey}/ticket-labels", this::federatedLocalGetAllTicketLabels, Roles.USER);
        routes.get(fp + "/{partnerUid}/{boardKey}/fields", this::federatedLocalGetFields, Roles.USER);
        routes.get(fp + "/{partnerUid}/{boardKey}/tickets", this::federatedLocalListTickets, Roles.USER);
        routes.get(fp + "/{partnerUid}/{boardKey}/tickets/search", this::federatedLocalSearchTickets, Roles.USER);
        routes.get(fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}", this::federatedLocalGetTicket, Roles.USER);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/comments",
                this::federatedLocalGetComments,
                Roles.USER);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist",
                this::federatedLocalGetChecklist,
                Roles.USER);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/links", this::federatedLocalGetLinks, Roles.USER);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/labels",
                this::federatedLocalGetTicketLabels,
                Roles.USER);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/transitions",
                this::federatedLocalGetTransitions,
                Roles.USER);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/history",
                this::federatedLocalGetHistory,
                Roles.USER);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/attachments",
                this::federatedLocalGetAttachments,
                Roles.USER);
        routes.get(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/watchers",
                this::federatedLocalGetWatchers,
                Roles.USER);

        // Board write (FULL mode only, proxied)
        routes.post(fp + "/{partnerUid}/{boardKey}/tickets", this::federatedLocalCreateTicket, Roles.USER);
        routes.put(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}", this::federatedLocalUpdateTicket, Roles.USER);
        routes.delete(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}", this::federatedLocalDeleteTicket, Roles.USER);
        routes.put(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/move",
                this::federatedLocalMoveTicket,
                Roles.USER);
        routes.put(fp + "/{partnerUid}/{boardKey}/tickets/reorder", this::federatedLocalReorderTickets, Roles.USER);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/comments",
                this::federatedLocalAddComment,
                Roles.USER);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist",
                this::federatedLocalAddChecklistItem,
                Roles.USER);
        routes.put(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
                this::federatedLocalUpdateChecklistItem,
                Roles.USER);
        routes.delete(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/checklist/{itemId}",
                this::federatedLocalDeleteChecklistItem,
                Roles.USER);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
                this::federatedLocalAddTicketLabel,
                Roles.USER);
        routes.delete(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/labels/{labelId}",
                this::federatedLocalRemoveTicketLabel,
                Roles.USER);
        routes.post(fp + "/{partnerUid}/{boardKey}/labels", this::federatedLocalCreateLabel, Roles.USER);
        routes.post(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/watch",
                this::federatedLocalWatchTicket,
                Roles.USER);
        routes.delete(
                fp + "/{partnerUid}/{boardKey}/tickets/{ticketNumber}/watch",
                this::federatedLocalUnwatchTicket,
                Roles.USER);

        // Local access override management
        routes.get(
                fp + "/{partnerUid}/{boardKey}/access/override", this::federatedLocalGetOverride, Roles.BOARD_MANAGER);
        routes.put(
                fp + "/{partnerUid}/{boardKey}/access/override", this::federatedLocalSetOverride, Roles.BOARD_MANAGER);

        // -- Federated board remote endpoints (unauthenticated, RSA-signed) --
        String rp = prefix + "/remote/boards";

        // Read endpoints (both modes)
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

        // Write endpoints (FULL mode only)
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

        // Webhook receivers (called by owning station to notify this partner)
        routes.post(rp + "/webhook/ticket-changed", this::federatedRemoteOnTicketChanged);
        routes.post(rp + "/webhook/mention", this::federatedRemoteOnMention);
        routes.post(rp + "/webhook/assignment", this::federatedRemoteOnAssignment);
        routes.post(rp + "/webhook/unassignment", this::federatedRemoteOnUnassignment);
        routes.post(rp + "/webhook/board-renamed", this::federatedRemoteOnBoardRenamed);
        routes.post(rp + "/webhook/board-unshared", this::federatedRemoteOnBoardUnshared);
        routes.post(rp + "/webhook/share-mode-changed", this::federatedRemoteOnShareModeChanged);
    }

    // ==================== Local board handlers ====================

    @OpenApi(
            path = "/api/v1/boards",
            methods = HttpMethod.GET,
            summary = "List boards visible to the current user",
            tags = {"Boards"},
            queryParams = @OpenApiParam(name = "visible", type = Boolean.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Board[].class)))
    private void list(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(List.of());
            return;
        }
        boolean visibleOnly = "true".equals(ctx.queryParam("visible"));
        if (session.roles().contains(Roles.BOARD_MANAGER) && !visibleOnly) {
            ctx.json(boardService.findByStation(session.stationId()));
        } else {
            ctx.json(boardService.findVisibleBoards(
                    session.stationId(), session.member().id()));
        }
    }

    @OpenApi(
            path = "/api/v1/boards",
            methods = HttpMethod.POST,
            summary = "Create a new board",
            tags = {"Boards"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = CreateBoardRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = Board.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void create(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(CreateBoardRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        if (req.shortKey() == null || req.shortKey().isBlank()) throw new BadRequestResponse("shortKey is required");
        Board board;
        if (req.preset() != null) {
            board = boardService.createWithPreset(
                    session.stationId(), req.name(), req.description(), req.shortKey(), req.preset());
        } else {
            board = boardService.create(session.stationId(), req.name(), req.description(), req.shortKey());
        }
        ctx.status(HttpStatus.CREATED).json(board);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}",
            methods = HttpMethod.GET,
            summary = "Get a board by short key",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Board.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void get(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var board = resolveBoard(ctx, session.stationId());
        if (session.member() != null
                && !boardService.canView(board.id(), session.member().id()))
            throw new ForbiddenResponse("No access to this board");
        ctx.json(board);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/can-edit",
            methods = HttpMethod.GET,
            summary = "Check if the current user can edit a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void canEdit(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        boolean editable = session.member() != null
                && boardService.canEdit(id, session.member().id());
        ctx.json(Map.of("canEdit", editable));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}",
            methods = HttpMethod.PUT,
            summary = "Update a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateBoardRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = Board.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void update(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var board = resolveBoard(ctx, session.stationId());
        var req = ctx.bodyAsClass(UpdateBoardRequest.class);
        boardService.update(board.id(), req.name(), req.description(), req.hideDoneAfterDays());
        boardService.findById(board.id()).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}",
            methods = HttpMethod.DELETE,
            summary = "Delete a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void delete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        if (boardService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/lanes",
            methods = HttpMethod.GET,
            summary = "Get lanes for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void getLanes(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        ctx.json(boardService.findLanes(id));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/lanes",
            methods = HttpMethod.PUT,
            summary = "Replace lanes for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LaneRequest[].class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setLanes(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var req = ctx.bodyAsClass(LaneRequest[].class);
        boardService.replaceLanes(
                id,
                Arrays.stream(req).map(l -> new LaneData(l.name(), l.color())).toList());
        ctx.json(boardService.findLanes(id));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/fields",
            methods = HttpMethod.GET,
            summary = "Get custom fields for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardField[].class)))
    private void getFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        ctx.json(boardService.findFields(id));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/fields",
            methods = HttpMethod.PUT,
            summary = "Replace custom fields for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FieldRequest[].class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BoardField[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var req = ctx.bodyAsClass(FieldRequest[].class);
        boardService.replaceFields(
                id,
                Arrays.stream(req)
                        .map(f -> new BoardField(0, id, f.name(), f.fieldType(), f.parsedConfig(), 0))
                        .toList());
        ctx.json(boardService.findFields(id));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/access/view",
            methods = HttpMethod.GET,
            summary = "Get view access restrictions for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AccessData.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getViewAccess(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var access = boardService.getViewAccess(id);
        ctx.json(access);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/access/view",
            methods = HttpMethod.PUT,
            summary = "Set view access restrictions for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AccessRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AccessRequest.class)))
    private void setViewAccess(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var req = ctx.bodyAsClass(AccessRequest.class);
        boardService.setViewAccess(
                id,
                req.roleIds() != null ? req.roleIds() : List.of(),
                req.groupIds() != null ? req.groupIds() : List.of(),
                req.tagIds() != null ? req.tagIds() : List.of());
        ctx.status(HttpStatus.OK).json(req);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/access/edit",
            methods = HttpMethod.GET,
            summary = "Get edit access restrictions for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AccessData.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getEditAccess(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var access = boardService.getEditAccess(id);
        ctx.json(access);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/access/edit",
            methods = HttpMethod.PUT,
            summary = "Set edit access restrictions for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AccessRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = AccessRequest.class)))
    private void setEditAccess(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var req = ctx.bodyAsClass(AccessRequest.class);
        boardService.setEditAccess(
                id,
                req.roleIds() != null ? req.roleIds() : List.of(),
                req.groupIds() != null ? req.groupIds() : List.of(),
                req.tagIds() != null ? req.tagIds() : List.of());
        ctx.status(HttpStatus.OK).json(req);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/backlog",
            methods = HttpMethod.POST,
            summary = "Enable backlog lane for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "201"))
    private void enableBacklog(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var lane = boardService.enableBacklog(id);
        ctx.status(HttpStatus.CREATED).json(lane);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/backlog",
            methods = HttpMethod.DELETE,
            summary = "Disable backlog lane for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void disableBacklog(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        boardService.disableBacklog(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Labels --

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/labels",
            methods = HttpMethod.GET,
            summary = "Get labels for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void getLabels(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        ctx.json(boardService.findLabels(id));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/labels",
            methods = HttpMethod.POST,
            summary = "Create a label for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LabelRequest.class)),
            responses = {
                @OpenApiResponse(status = "201"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void createLabel(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var req = ctx.bodyAsClass(LabelRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        var label = boardService.createLabel(id, req.name().trim(), req.color() != null ? req.color() : randomColor());
        ctx.status(HttpStatus.CREATED).json(label);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/labels/{labelId}",
            methods = HttpMethod.PUT,
            summary = "Update a label",
            tags = {"Boards"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LabelRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void updateLabel(Context ctx) {
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        var req = ctx.bodyAsClass(LabelRequest.class);
        boardService.updateLabel(labelId, req.name(), req.color());
        ctx.status(HttpStatus.OK);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/labels/{labelId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a label",
            tags = {"Boards"},
            pathParams = {
                @OpenApiParam(name = "boardKey", type = String.class, required = true),
                @OpenApiParam(name = "labelId", type = Integer.class, required = true)
            },
            responses = @OpenApiResponse(status = "204"))
    private void deleteLabel(Context ctx) {
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        boardService.deleteLabel(labelId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/ticket-labels",
            methods = HttpMethod.GET,
            summary = "Get all ticket-label assignments for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void getAllTicketLabels(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        ctx.json(boardService.findAllTicketLabels(id));
    }

    // -- Federation config --

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/federation",
            methods = HttpMethod.GET,
            summary = "Get federation sharing configuration for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationConfigResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getFederationConfig(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var targets = federatedBoardService.findShareTargets(id).stream()
                .map(t -> new FederationTargetResponse(t.partnerId(), t.shareMode()))
                .toList();
        var editRoleIds = federatedBoardService.findFederatedEditRoles(id);
        ctx.json(new FederationConfigResponse(targets, editRoleIds));
    }

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/federation",
            methods = HttpMethod.PUT,
            summary = "Set federation sharing configuration for a board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FederationConfigRequest.class)),
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setFederationConfig(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = resolveBoardId(ctx, session.stationId());
        var req = ctx.bodyAsClass(FederationConfigRequest.class);
        var configs = (req.targets() != null ? req.targets() : List.<FederationTargetRequest>of())
                .stream()
                        .map(t -> new FederatedBoardService.PartnerShareConfig(t.partnerId(), t.shareMode()))
                        .toList();
        if (configs.isEmpty()) {
            federatedBoardService.unshareBoard(id);
        } else {
            federatedBoardService.shareBoard(id, configs);
        }
        federatedBoardService.setFederatedEditRoles(id, req.editRoleIds() != null ? req.editRoleIds() : List.of());
        ctx.status(HttpStatus.OK).json(Map.of("ok", true));
    }

    private static String randomColor() {
        var colors =
                new String[] {"#ef4444", "#f59e0b", "#22c55e", "#3b82f6", "#8b5cf6", "#ec4899", "#14b8a6", "#f97316"};
        return colors[new Random().nextInt(colors.length)];
    }

    // ==================== Federated board local proxy helpers ====================

    private int resolvePartnerId(Context ctx) {
        var session = UserSession.from(ctx);
        UUID partnerUid = UUID.fromString(ctx.pathParam("partnerUid"));
        return federationRepository
                .findPartnerByStationAndRemoteUid(session.stationId(), partnerUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"))
                .id();
    }

    private void requireView(int partnerId, String boardKey, UserSession session) {
        int boardId = proxyService.resolveFederatedBoardId(partnerId, boardKey);
        if (boardId != -1
                && !proxyService.canView(partnerId, boardId, session.member().id())) {
            throw new ForbiddenResponse("No view access to this federated board");
        }
        // For remote partners, resolveFederatedBoardId returns -1 and access is enforced by the remote station
    }

    private void requireWrite(int partnerId, String boardKey, UserSession session) {
        int boardId = proxyService.resolveFederatedBoardId(partnerId, boardKey);
        if (boardId != -1
                && !proxyService.canWrite(partnerId, boardId, session.member().id())) {
            throw new ForbiddenResponse("No write access to this federated board");
        }
    }

    // ==================== Federated board local proxy handlers ====================

    // -- Discovery & Bookmarks --

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
                    String uid =
                            partner.map(p -> p.partnerStationId().toString()).orElse("");
                    return Map.of(
                            "id", bm.id(),
                            "memberId", bm.memberId(),
                            "partnerId", bm.partnerId(),
                            "partnerStationUid", uid,
                            "remoteBoardId", bm.remoteBoardId(),
                            "remoteBoardName", bm.remoteBoardName(),
                            "remoteBoardShortKey", bm.remoteBoardShortKey(),
                            "shareMode", bm.shareMode().name(),
                            "createdAt", bm.createdAt().toString());
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
        UUID partnerUid = UUID.fromString(req.partnerUid());
        int partnerId = federationRepository
                .findPartnerByStationAndRemoteUid(session.stationId(), partnerUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"))
                .id();
        ctx.json(proxyService.createBookmark(
                session.member().id(),
                partnerId,
                req.remoteBoardId(),
                req.remoteBoardName(),
                req.remoteBoardShortKey(),
                BoardShareMode.valueOf(req.shareMode())));
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

    // -- Read proxies --

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
                .map(this::toCommentResponse)
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

    // -- Write proxies (FULL mode only) --

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
                req.dueDate()));
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
                partnerId, boardKey, ticketNumber, req.toLaneId(), req.position(), memberUid));
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
        String displayName = session.member().displayName();
        if (displayName == null || displayName.isBlank()) {
            displayName =
                    (session.account().firstName() + " " + session.account().lastName()).trim();
        }
        ctx.json(proxyService.proxyAddComment(
                partnerId,
                boardKey,
                ticketNumber,
                req.parentId(),
                req.content(),
                session.member().uid(),
                displayName));
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
        ctx.json(proxyService.proxyAddChecklistItem(partnerId, boardKey, ticketNumber, req.title()));
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
        proxyService.proxyUpdateChecklistItem(partnerId, boardKey, ticketNumber, itemId, req.title(), req.checked());
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
        proxyService.proxyDeleteChecklistItem(partnerId, boardKey, ticketNumber, itemId);
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

    // -- Local access override management --

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
        int boardId = proxyService.resolveFederatedBoardId(partnerId, boardKey);
        if (boardId == -1) throw new NotFoundResponse("Board not found: " + boardKey);
        var view = proxyService.getLocalViewOverride(partnerId, boardId);
        var edit = proxyService.getLocalEditOverride(partnerId, boardId);
        ctx.json(Map.of("view", view, "edit", edit));
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
        int boardId = proxyService.resolveFederatedBoardId(partnerId, boardKey);
        if (boardId == -1) throw new NotFoundResponse("Board not found: " + boardKey);
        var req = ctx.bodyAsClass(LocalOverrideRequest.class);
        proxyService.setLocalViewOverride(
                partnerId, boardId, new AccessData(req.viewRoleIds(), req.viewGroupIds(), req.viewTagIds()));
        proxyService.setLocalEditOverride(
                partnerId, boardId, new AccessData(req.editRoleIds(), req.editGroupIds(), req.editTagIds()));
        ctx.status(204);
    }

    // ==================== Federated board remote helpers ====================

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

    // ==================== Federated board remote handlers ====================

    // -- Read endpoints --

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
                    return Map.of(
                            "id", board.id(),
                            "name", board.name(),
                            "description", board.description() != null ? board.description() : "",
                            "shortKey", board.shortKey(),
                            "shareMode", mode.name());
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
        ctx.json(Map.of("board", board, "shareMode", mode.name()));
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
                .toList());
    }

    private void federatedRemoteSearchTickets(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        String q = ctx.queryParam("q");
        var tickets =
                (q == null || q.isBlank()) ? ticketService.findByBoard(boardId) : ticketService.search(boardId, q);
        ctx.json(tickets.stream().map(TicketSummary::of).toList());
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
                .map(this::toCommentResponse)
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
                .map(tr -> BoardTicketTransitionResponse.from(tr, tr.actor(), memberNameResolver.resolve(tr.actor())))
                .toList());
    }

    private String resolveDisplayName(UserSession session) {
        String name = session.member().displayName();
        if (name == null || name.isBlank()) {
            name = (session.account().firstName() + " " + session.account().lastName()).trim();
        }
        return name;
    }

    private void federatedRemoteGetHistory(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int boardId = resolveRemoteBoardId(ctx, partner);
        requireRemoteView(boardId, partner);
        int ticketId = resolveRemoteTicketId(ctx, boardId);
        var history = ticketService.findHistory(ticketId);
        ctx.json(history.stream()
                .map(h -> BoardTicketHistoryResponse.from(h, h.actor(), memberNameResolver.resolve(h.actor())))
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
        ctx.json(Map.of("local", localWatchers, "federated", List.of()));
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
        var editRoles = federatedBoardService.findFederatedEditRoles(boardId);
        ctx.json(Map.of("shareMode", mode.name(), "editRoleIds", editRoles));
    }

    // -- Write endpoints (FULL mode only) --

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
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        // Use the first lane if none specified
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
        ticketService.updateTicket(
                ticketId,
                req.title(),
                req.description(),
                assigneeIdentity,
                req.priority() != null ? TicketPriority.valueOf(req.priority()) : null,
                req.dueDate() != null ? LocalDate.parse(req.dueDate()) : null,
                null);
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
        UUID federatedMemberUid = ctx.header("X-Federation-Member-Id") != null
                ? UUID.fromString(ctx.header("X-Federation-Member-Id"))
                : null;
        MemberIdentity actorIdentity =
                federatedMemberUid != null ? new MemberIdentity(partner.partnerStationId(), federatedMemberUid) : null;
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
                "LABEL_ADDED",
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
                "LABEL_REMOVED",
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

    // -- Webhook receivers (partner station receives these from owning station) --

    @OpenApi(
            path = "/api/v1/remote/boards/webhook/ticket-changed",
            methods = HttpMethod.POST,
            summary = "Webhook: ticket changed notification",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "204"))
    private void federatedRemoteOnTicketChanged(Context ctx) {
        requireFederationPartner(ctx);
        // Payload contains remoteMemberIds to notify — handled by the partner's notification system
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
        proxyService.onBoardRenamed(partner.id(), req.boardId(), req.newName(), req.newShortKey());
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
        proxyService.onBoardUnshared(partner.id(), req.boardId());
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
        proxyService.onShareModeChanged(partner.id(), req.boardId(), BoardShareMode.valueOf(req.shareMode()));
        ctx.status(204);
    }

    // -- Comment enrichment --

    private BoardCommentResponse toCommentResponse(BoardComment comment) {
        if (comment.deleted()) {
            return new BoardCommentResponse(
                    comment.id(),
                    comment.ticketId(),
                    comment.parentId(),
                    null,
                    null,
                    "",
                    true,
                    comment.createdAt(),
                    null);
        }
        String authorName = memberNameResolver.resolve(comment.author());
        if (authorName == null) authorName = "";
        return new BoardCommentResponse(
                comment.id(),
                comment.ticketId(),
                comment.parentId(),
                comment.author(),
                authorName,
                comment.content(),
                false,
                comment.createdAt(),
                comment.updatedAt());
    }

    public record BoardCommentResponse(
            int id,
            int ticketId,
            Integer parentId,
            MemberIdentity author,
            String authorName,
            String content,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt) {}

    // ==================== Request/Response records ====================

    // -- Local board records --

    public record CreateBoardRequest(String name, String description, String shortKey, LanePreset preset) {}

    public record UpdateBoardRequest(String name, String description, int hideDoneAfterDays) {}

    public record LaneRequest(String name, String color) {}

    public record FieldRequest(String name, BoardFieldType fieldType, tools.jackson.databind.JsonNode config) {
        public BoardFieldConfig parsedConfig() {
            if (config == null || config.isNull()) return BoardFieldConfig.empty(fieldType);
            return BoardFieldConfig.parse(fieldType, config.toString());
        }
    }

    public record LabelRequest(String name, String color) {}

    public record AccessRequest(List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds) {}

    public record FederationTargetRequest(int partnerId, BoardShareMode shareMode) {}

    public record FederationTargetResponse(int partnerId, BoardShareMode shareMode) {}

    public record FederationConfigRequest(List<FederationTargetRequest> targets, List<Integer> editRoleIds) {}

    public record FederationConfigResponse(List<FederationTargetResponse> targets, List<Integer> editRoleIds) {}

    // -- Federated local proxy records --

    record LocalBookmarkRequest(
            String partnerUid,
            int remoteBoardId,
            String remoteBoardName,
            String remoteBoardShortKey,
            String shareMode) {}

    record LocalCreateTicketRequest(
            Integer laneId, String title, String description, String priority, String dueDate) {}

    record LocalUpdateTicketRequest(
            String title, String description, Integer assignedMemberId, String priority, String dueDate) {}

    record LocalMoveTicketRequest(int toLaneId, int position) {}

    record LocalReorderRequest(int laneId, List<Integer> orderedIds) {}

    record LocalCommentRequest(Integer parentId, String content) {}

    record LocalChecklistItemRequest(String title) {}

    record LocalUpdateChecklistItemRequest(String title, boolean checked) {}

    record LocalCreateLabelRequest(String name, String color) {}

    record LocalOverrideRequest(
            List<Integer> viewRoleIds,
            List<Integer> viewGroupIds,
            List<Integer> viewTagIds,
            List<Integer> editRoleIds,
            List<Integer> editGroupIds,
            List<Integer> editTagIds) {}

    // -- Federated remote records --

    record RemoteCreateTicketRequest(
            UUID remoteMemberId, Integer laneId, String title, String description, String priority, String dueDate) {}

    record RemoteUpdateTicketRequest(
            String title, String description, Integer assignedMemberId, String priority, String dueDate) {}

    record RemoteMoveTicketRequest(int toLaneId, int position) {}

    record RemoteReorderRequest(int laneId, List<Integer> orderedIds) {}

    record RemoteCommentRequest(UUID remoteMemberId, String displayName, Integer parentId, String content) {}

    record RemoteEditCommentRequest(String content) {}

    record RemoteChecklistItemRequest(String title) {}

    record RemoteUpdateChecklistItemRequest(String title, boolean checked) {}

    record RemoteCreateLabelRequest(String name, String color) {}

    record RemoteLabelActionRequest(UUID remoteMemberId, String displayName) {}

    record RemoteWatchRequest(UUID remoteMemberId) {}

    record RemoteBoardRenamedWebhook(int boardId, String newName, String newShortKey) {}

    record RemoteBoardUnsharedWebhook(int boardId) {}

    record RemoteShareModeChangedWebhook(int boardId, String shareMode) {}
}
