/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.BoardFieldType;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.LaneData;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
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
import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Local board endpoints: board CRUD, lanes, custom fields, access restrictions, labels and the
 * federation sharing configuration. The federated proxy and server-to-server surfaces of the same
 * domain live in {@link FederatedBoardRoutes} and {@link RemoteBoardRoutes}.
 */
@SuppressWarnings("DefaultAnnotationParam")
@Singleton
public class BoardRoutes implements Routes {

    private final BoardService boardService;
    private final FederatedBoardService federatedBoardService;
    private final StationMemberService memberService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final BoardRouteGuards guards;

    @Inject
    public BoardRoutes(
            BoardService boardService,
            FederatedBoardService federatedBoardService,
            StationMemberService memberService,
            MemberIdentityFactory memberIdentityFactory,
            BoardRouteGuards guards) {
        this.boardService = boardService;
        this.federatedBoardService = federatedBoardService;
        this.memberService = memberService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.guards = guards;
    }

    private static String randomColor() {
        var colors =
                new String[] {"#ef4444", "#f59e0b", "#22c55e", "#3b82f6", "#8b5cf6", "#ec4899", "#14b8a6", "#f97316"};
        return colors[new Random().nextInt(colors.length)];
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/boards", this::list, StationPermission.BOARD_USE);
        routes.post(prefix + "/boards", this::create, StationPermission.BOARD_EDIT);
        routes.get(prefix + "/boards/{boardKey}", this::get, StationPermission.BOARD_USE);
        routes.put(prefix + "/boards/{boardKey}", this::update, StationPermission.BOARD_EDIT);
        routes.delete(prefix + "/boards/{boardKey}", this::delete, StationPermission.BOARD_MANAGER);
        routes.get(prefix + "/boards/{boardKey}/can-edit", this::canEdit, StationPermission.BOARD_USE);
        routes.get(prefix + "/boards/{boardKey}/lanes", this::getLanes, StationPermission.BOARD_USE);
        routes.put(prefix + "/boards/{boardKey}/lanes", this::setLanes, StationPermission.BOARD_EDIT);
        routes.get(prefix + "/boards/{boardKey}/fields", this::getFields, StationPermission.BOARD_USE);
        routes.put(prefix + "/boards/{boardKey}/fields", this::setFields, StationPermission.BOARD_EDIT);
        routes.get(prefix + "/boards/{boardKey}/access/view", this::getViewAccess, StationPermission.BOARD_EDIT);
        routes.put(prefix + "/boards/{boardKey}/access/view", this::setViewAccess, StationPermission.BOARD_EDIT);
        routes.get(prefix + "/boards/{boardKey}/access/edit", this::getEditAccess, StationPermission.BOARD_EDIT);
        routes.put(prefix + "/boards/{boardKey}/access/edit", this::setEditAccess, StationPermission.BOARD_EDIT);
        routes.post(prefix + "/boards/{boardKey}/backlog", this::enableBacklog, StationPermission.BOARD_EDIT);
        routes.delete(prefix + "/boards/{boardKey}/backlog", this::disableBacklog, StationPermission.BOARD_EDIT);
        routes.get(prefix + "/boards/{boardKey}/labels", this::getLabels, StationPermission.BOARD_USE);
        routes.post(prefix + "/boards/{boardKey}/labels", this::createLabel, StationPermission.BOARD_EDIT);
        routes.put(prefix + "/boards/{boardKey}/labels/{labelId}", this::updateLabel, StationPermission.BOARD_EDIT);
        routes.delete(prefix + "/boards/{boardKey}/labels/{labelId}", this::deleteLabel, StationPermission.BOARD_EDIT);
        routes.get(prefix + "/boards/{boardKey}/ticket-labels", this::getAllTicketLabels, StationPermission.BOARD_USE);
        routes.get(prefix + "/boards/{boardKey}/members", this::listBoardMembers, StationPermission.BOARD_USE);
        routes.get(
                prefix + "/boards/{boardKey}/assignable-members",
                this::listAssignableMembers,
                StationPermission.BOARD_USE);
        routes.get(
                prefix + "/boards/{boardKey}/federation", this::getFederationConfig, StationPermission.BOARD_FEDERATE);
        routes.put(
                prefix + "/boards/{boardKey}/federation", this::setFederationConfig, StationPermission.BOARD_FEDERATE);
    }

    private Board resolveBoard(Context ctx, int stationId) {
        return guards.resolveBoard(ctx, stationId);
    }

    private int resolveBoardId(Context ctx, int stationId) {
        return guards.resolveBoardId(ctx, stationId);
    }

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
        if (session.permissions().contains(StationPermission.BOARD_MANAGER) && !visibleOnly) {
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
        boolean isManager = session.permissions().contains(StationPermission.BOARD_MANAGER);
        if (session.member() != null
                && !boardService.canView(board.id(), session.member().id(), isManager))
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
        boolean isManager = session.permissions().contains(StationPermission.BOARD_MANAGER);
        boolean editable = session.member() != null
                && boardService.canEdit(id, session.member().id(), isManager);
        ctx.json(new CanEditResponse(editable));
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
                Arrays.stream(req)
                        .map(l -> new LaneData(l.id(), l.name(), l.color()))
                        .toList());
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
                req.userTypes() != null ? req.userTypes() : List.of(),
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
                req.userTypes() != null ? req.userTypes() : List.of(),
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
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        if (boardService.findLabels(boardId).stream().noneMatch(l -> l.id() == labelId)) {
            throw new NotFoundResponse();
        }
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
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        if (boardService.findLabels(boardId).stream().noneMatch(l -> l.id() == labelId)) {
            throw new NotFoundResponse();
        }
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

    @OpenApi(
            path = "/api/v1/boards/{boardKey}/members",
            methods = HttpMethod.GET,
            summary = "List the station's members, for rendering the names a board shows",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void listBoardMembers(Context ctx) {
        UserSession session = UserSession.from(ctx);
        resolveBoardId(ctx, session.stationId());
        ctx.json(memberIdentityFactory.enrichCompletions(memberService.findCompletions(session.stationId())));
    }

    /**
     * Whom a ticket on this board may be handed to. Narrower than the station's members, which the
     * board still needs in full to put a name on whoever is already on a ticket.
     */
    @OpenApi(
            path = "/api/v1/boards/{boardKey}/assignable-members",
            methods = HttpMethod.GET,
            summary = "List members that may be assigned tickets on this board",
            tags = {"Boards"},
            pathParams = @OpenApiParam(name = "boardKey", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200"))
    private void listAssignableMembers(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int boardId = resolveBoardId(ctx, session.stationId());
        var allowed = boardService.findMembersWhoMayEdit(boardId, session.stationId());
        ctx.json(memberIdentityFactory.enrichCompletions(memberService.findCompletions(session.stationId()).stream()
                .filter(completion -> allowed.contains(completion.id()))
                .toList()));
    }

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
                .map(t -> new FederationTargetResponse(t.partnerId(), t.shareMode(), t.requiredUserType()))
                .toList();
        var editUserTypes = federatedBoardService.findFederatedEditUserTypes(id);
        ctx.json(new FederationConfigResponse(targets, editUserTypes));
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
                        .map(t -> new FederatedBoardService.PartnerShareConfig(
                                t.partnerId(),
                                t.shareMode(),
                                t.requiredUserType() != null ? t.requiredUserType() : StationUserType.MEMBER))
                        .toList();
        if (configs.isEmpty()) {
            federatedBoardService.unshareBoard(id);
        } else {
            federatedBoardService.shareBoard(id, configs);
        }
        federatedBoardService.setFederatedEditUserTypes(
                id, req.editUserTypes() != null ? req.editUserTypes() : List.of());
        ctx.status(HttpStatus.OK).json(new OkResponse(true));
    }

    public record CreateBoardRequest(String name, String description, String shortKey, LanePreset preset) {}

    public record UpdateBoardRequest(String name, String description, int hideDoneAfterDays) {}

    public record LaneRequest(Integer id, String name, String color) {}

    /**
     * @param config the field's settings as an object. Which record they are follows from the field
     *               type beside them, so they are bound once that is known rather than while the
     *               request is read.
     */
    public record FieldRequest(String name, BoardFieldType fieldType, JsonNode config) {
        public BoardFieldConfig parsedConfig() {
            return BoardFieldConfig.parse(fieldType, config);
        }
    }

    public record LabelRequest(String name, String color) {}

    public record AccessRequest(List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds) {}

    public record FederationTargetRequest(int partnerId, BoardShareMode shareMode, StationUserType requiredUserType) {}

    public record FederationTargetResponse(int partnerId, BoardShareMode shareMode, StationUserType requiredUserType) {}

    public record FederationConfigRequest(List<FederationTargetRequest> targets, List<StationUserType> editUserTypes) {}

    public record FederationConfigResponse(
            List<FederationTargetResponse> targets, List<StationUserType> editUserTypes) {}

    record CanEditResponse(boolean canEdit) {}

    record OkResponse(boolean ok) {}
}
