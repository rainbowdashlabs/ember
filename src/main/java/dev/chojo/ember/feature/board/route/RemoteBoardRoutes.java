/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketLabelMapping;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.FederatedBoardDiscoveryService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
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

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Board-level server-to-server endpoints served to federation partners: which boards are shared,
 * their lanes, labels, custom fields, access configuration and member completions. The ticket
 * surface lives in {@link RemoteBoardTicketRoutes}, {@link RemoteBoardTicketDetailRoutes} and
 * {@link RemoteBoardTicketLinkRoutes}, the notification receivers in
 * {@link RemoteBoardWebhookRoutes}. The local proxy calling all of them is
 * {@link FederatedBoardRoutes}.
 * <p>
 * This class also holds the request and response records shared across the whole
 * {@code /remote/boards} surface. The contract hash follows the types reachable from the
 * declared endpoints, so their location is a code-organisation choice, not a protocol one.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class RemoteBoardRoutes implements Routes {

    static final String TICKETS_PATH = "/remote/boards/{boardKey}/tickets";
    static final String TICKET_PATH = TICKETS_PATH + "/{ticketNumber}";

    public static final FederationEndpoint LIST_SHARED_BOARDS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards", RemoteSharedBoardResponse.class);
    public static final FederationEndpoint GET_BOARD = FederationEndpoint.get(
            FederationSurface.BOARD_SHARE,
            "/remote/boards/{boardKey}",
            FederatedBoardDiscoveryService.FederatedBoardDetail.class);
    public static final FederationEndpoint GET_LANES = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/lanes", BoardLane.class);
    public static final FederationEndpoint GET_LABELS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/labels", BoardLabel.class);
    public static final FederationEndpoint CREATE_LABEL = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE,
            "/remote/boards/{boardKey}/labels",
            RemoteCreateLabelRequest.class,
            BoardLabel.class);
    public static final FederationEndpoint GET_ALL_TICKET_LABELS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/ticket-labels", TicketLabelMapping.class);
    public static final FederationEndpoint GET_FIELDS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/fields", BoardField.class);
    public static final FederationEndpoint GET_ACCESS = FederationEndpoint.get(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/access", RemoteAccessResponse.class);
    public static final FederationEndpoint GET_MEMBERS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/members", MemberCompletion.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            LIST_SHARED_BOARDS,
            GET_BOARD,
            GET_LANES,
            GET_LABELS,
            CREATE_LABEL,
            GET_ALL_TICKET_LABELS,
            GET_FIELDS,
            GET_ACCESS,
            GET_MEMBERS);

    private final BoardService boardService;
    private final FederatedBoardService federatedBoardService;
    private final StationMemberService memberService;
    private final StationRepository stationRepository;
    private final MemberIdentityFactory memberIdentityFactory;
    private final RemoteBoardGuards guards;

    @Inject
    public RemoteBoardRoutes(
            BoardService boardService,
            FederatedBoardService federatedBoardService,
            StationMemberService memberService,
            StationRepository stationRepository,
            MemberIdentityFactory memberIdentityFactory,
            RemoteBoardGuards guards) {
        this.boardService = boardService;
        this.federatedBoardService = federatedBoardService;
        this.memberService = memberService;
        this.stationRepository = stationRepository;
        this.memberIdentityFactory = memberIdentityFactory;
        this.guards = guards;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, binder -> binder.handle(LIST_SHARED_BOARDS, this::listSharedBoards)
                        .handle(GET_BOARD, this::getBoard)
                        .handle(GET_LANES, this::getLanes)
                        .handle(GET_LABELS, this::getLabels)
                        .handle(CREATE_LABEL, this::createLabel)
                        .handle(GET_ALL_TICKET_LABELS, this::getAllTicketLabels)
                        .handle(GET_FIELDS, this::getFields)
                        .handle(GET_ACCESS, this::getAccess)
                        .handle(GET_MEMBERS, this::getMembers));
    }

    @OpenApi(
            path = "/api/v1/remote/boards",
            methods = HttpMethod.GET,
            summary = "List boards shared with the requesting partner",
            tags = {"Boards Remote"},
            responses = @OpenApiResponse(status = "200"))
    private void listSharedBoards(Context ctx) {
        var partner = guards.requirePartner(ctx);
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
    private void getBoard(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.viewableBoardId(ctx, partner);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        var mode = federatedBoardService.getShareMode(boardId, partner.id()).orElse(BoardShareMode.READ_ONLY);
        String stationName =
                stationRepository.findById(board.stationId()).map(Station::name).orElse("");
        ctx.json(FederatedBoardDiscoveryService.FederatedBoardDetail.of(board, mode, stationName, stationRepository));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/lanes",
            methods = HttpMethod.GET,
            summary = "Get lanes for a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void getLanes(Context ctx) {
        var partner = guards.requirePartner(ctx);
        ctx.json(boardService.findLanes(guards.viewableBoardId(ctx, partner)));
    }

    private void getLabels(Context ctx) {
        var partner = guards.requirePartner(ctx);
        ctx.json(boardService.findLabels(guards.viewableBoardId(ctx, partner)));
    }

    private void getAllTicketLabels(Context ctx) {
        var partner = guards.requirePartner(ctx);
        ctx.json(boardService.findAllTicketLabels(guards.viewableBoardId(ctx, partner)));
    }

    private void getFields(Context ctx) {
        var partner = guards.requirePartner(ctx);
        ctx.json(boardService.findFields(guards.viewableBoardId(ctx, partner)));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/access",
            methods = HttpMethod.GET,
            summary = "Get access configuration for a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void getAccess(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.viewableBoardId(ctx, partner);
        var mode = federatedBoardService.getShareMode(boardId, partner.id()).orElse(BoardShareMode.READ_ONLY);
        ctx.json(new RemoteAccessResponse(mode, federatedBoardService.findFederatedEditUserTypes(boardId)));
    }

    private void getMembers(Context ctx) {
        var partner = guards.requirePartner(ctx);
        var board = boardService.findById(guards.viewableBoardId(ctx, partner)).orElseThrow(NotFoundResponse::new);
        ctx.json(memberIdentityFactory.enrichCompletions(memberService.findCompletions(board.stationId())));
    }

    @OpenApi(
            path = "/api/v1/remote/boards/{boardKey}/labels",
            methods = HttpMethod.POST,
            summary = "Create a label on a shared board",
            tags = {"Boards Remote"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RemoteCreateLabelRequest.class)),
            responses = @OpenApiResponse(status = "200"))
    private void createLabel(Context ctx) {
        var partner = guards.requirePartner(ctx);
        int boardId = guards.writableBoardId(ctx, partner);
        var req = ctx.bodyAsClass(RemoteCreateLabelRequest.class);
        ctx.json(boardService.createLabel(boardId, req.name(), req.color() != null ? req.color() : "#6b7280"));
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
