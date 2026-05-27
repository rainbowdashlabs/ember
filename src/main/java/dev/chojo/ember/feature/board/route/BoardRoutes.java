/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardFieldConfig;
import dev.chojo.ember.feature.board.entity.LaneData;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.service.BoardService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class BoardRoutes implements Routes {
    private final BoardService boardService;

    @Inject
    public BoardRoutes(BoardService boardService) {
        this.boardService = boardService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/boards", this::list, Roles.USER);
        routes.post(prefix + "/boards", this::create, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{id}", this::get, Roles.USER);
        routes.put(prefix + "/boards/{id}", this::update, Roles.BOARD_MANAGER);
        routes.delete(prefix + "/boards/{id}", this::delete, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{id}/can-edit", this::canEdit, Roles.USER);
        routes.get(prefix + "/boards/{id}/lanes", this::getLanes, Roles.USER);
        routes.put(prefix + "/boards/{id}/lanes", this::setLanes, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{id}/fields", this::getFields, Roles.USER);
        routes.put(prefix + "/boards/{id}/fields", this::setFields, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{id}/access/view", this::getViewAccess, Roles.BOARD_MANAGER);
        routes.put(prefix + "/boards/{id}/access/view", this::setViewAccess, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{id}/access/edit", this::getEditAccess, Roles.BOARD_MANAGER);
        routes.put(prefix + "/boards/{id}/access/edit", this::setEditAccess, Roles.BOARD_MANAGER);
        routes.post(prefix + "/boards/{id}/backlog", this::enableBacklog, Roles.BOARD_MANAGER);
        routes.delete(prefix + "/boards/{id}/backlog", this::disableBacklog, Roles.BOARD_MANAGER);
        // Labels
        routes.get(prefix + "/boards/{id}/labels", this::getLabels, Roles.USER);
        routes.post(prefix + "/boards/{id}/labels", this::createLabel, Roles.USER);
        routes.put(prefix + "/boards/{id}/labels/{labelId}", this::updateLabel, Roles.BOARD_MANAGER);
        routes.delete(prefix + "/boards/{id}/labels/{labelId}", this::deleteLabel, Roles.BOARD_MANAGER);
        routes.get(prefix + "/boards/{id}/ticket-labels", this::getAllTicketLabels, Roles.USER);
    }

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

    private void get(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var board = boardService.findById(id).orElseThrow(NotFoundResponse::new);
        if (board.stationId() != session.stationId())
            throw new ForbiddenResponse("Cannot access resources from another station");
        if (session.member() != null
                && !boardService.canView(id, session.member().id()))
            throw new ForbiddenResponse("No access to this board");
        ctx.json(board);
    }

    private void canEdit(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        boolean editable = session.member() != null
                && boardService.canEdit(id, session.member().id());
        ctx.json(java.util.Map.of("canEdit", editable));
    }

    private void update(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var board = boardService.findById(id).orElseThrow(NotFoundResponse::new);
        if (board.stationId() != session.stationId())
            throw new ForbiddenResponse("Cannot access resources from another station");
        var req = ctx.bodyAsClass(UpdateBoardRequest.class);
        boardService.update(id, req.name(), req.description(), req.hideDoneAfterDays());
        boardService.findById(id).ifPresentOrElse(ctx::json, () -> {
            throw new NotFoundResponse();
        });
    }

    private void delete(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var board = boardService.findById(id).orElseThrow(NotFoundResponse::new);
        if (board.stationId() != session.stationId())
            throw new ForbiddenResponse("Cannot access resources from another station");
        if (boardService.delete(id)) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private void getLanes(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(boardService.findLanes(id));
    }

    private void setLanes(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var board = boardService.findById(id).orElseThrow(NotFoundResponse::new);
        if (board.stationId() != session.stationId())
            throw new ForbiddenResponse("Cannot access resources from another station");
        var req = ctx.bodyAsClass(LaneRequest[].class);
        boardService.replaceLanes(
                id,
                java.util.Arrays.stream(req)
                        .map(l -> new LaneData(l.name(), l.color()))
                        .toList());
        ctx.json(boardService.findLanes(id));
    }

    private void getFields(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(boardService.findFields(id));
    }

    private void setFields(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var board = boardService.findById(id).orElseThrow(NotFoundResponse::new);
        if (board.stationId() != session.stationId())
            throw new ForbiddenResponse("Cannot access resources from another station");
        var req = ctx.bodyAsClass(FieldRequest[].class);
        boardService.replaceFields(
                id,
                java.util.Arrays.stream(req)
                        .map(f -> new BoardField(
                                0,
                                id,
                                f.name(),
                                f.fieldType(),
                                f.config() != null ? f.config() : new BoardFieldConfig(false, List.of(), null),
                                0))
                        .toList());
        ctx.json(boardService.findFields(id));
    }

    private void getViewAccess(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        boardService.findById(id).orElseThrow(NotFoundResponse::new);
        var access = boardService.getViewAccess(id);
        ctx.json(access);
    }

    private void setViewAccess(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(AccessRequest.class);
        boardService.setViewAccess(
                id,
                req.roleIds() != null ? req.roleIds() : List.of(),
                req.groupIds() != null ? req.groupIds() : List.of(),
                req.tagIds() != null ? req.tagIds() : List.of());
        ctx.status(HttpStatus.OK).json(req);
    }

    private void getEditAccess(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        boardService.findById(id).orElseThrow(NotFoundResponse::new);
        var access = boardService.getEditAccess(id);
        ctx.json(access);
    }

    private void setEditAccess(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(AccessRequest.class);
        boardService.setEditAccess(
                id,
                req.roleIds() != null ? req.roleIds() : List.of(),
                req.groupIds() != null ? req.groupIds() : List.of(),
                req.tagIds() != null ? req.tagIds() : List.of());
        ctx.status(HttpStatus.OK).json(req);
    }

    private void enableBacklog(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var lane = boardService.enableBacklog(id);
        ctx.status(HttpStatus.CREATED).json(lane);
    }

    private void disableBacklog(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        boardService.disableBacklog(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    // -- Labels --

    private void getLabels(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(boardService.findLabels(id));
    }

    private void createLabel(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        var req = ctx.bodyAsClass(LabelRequest.class);
        if (req.name() == null || req.name().isBlank()) throw new BadRequestResponse("name is required");
        var label = boardService.createLabel(id, req.name().trim(), req.color() != null ? req.color() : randomColor());
        ctx.status(HttpStatus.CREATED).json(label);
    }

    private void updateLabel(Context ctx) {
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        var req = ctx.bodyAsClass(LabelRequest.class);
        boardService.updateLabel(labelId, req.name(), req.color());
        ctx.status(HttpStatus.OK);
    }

    private void deleteLabel(Context ctx) {
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        boardService.deleteLabel(labelId);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void getAllTicketLabels(Context ctx) {
        int id = ctx.pathParamAsClass("id", Integer.class).get();
        ctx.json(boardService.findAllTicketLabels(id));
    }

    private static String randomColor() {
        var colors =
                new String[] {"#ef4444", "#f59e0b", "#22c55e", "#3b82f6", "#8b5cf6", "#ec4899", "#14b8a6", "#f97316"};
        return colors[new java.util.Random().nextInt(colors.length)];
    }

    // -- Request/Response records --

    public record CreateBoardRequest(String name, String description, String shortKey, LanePreset preset) {}

    public record UpdateBoardRequest(String name, String description, int hideDoneAfterDays) {}

    public record LaneRequest(String name, String color) {}

    public record FieldRequest(String name, String fieldType, BoardFieldConfig config) {}

    public record LabelRequest(String name, String color) {}

    public record AccessRequest(List<Integer> roleIds, List<Integer> groupIds, List<Integer> tagIds) {}
}
