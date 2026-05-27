/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.board.service.FederatedBoardProxyService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;

/**
 * Authenticated local proxy endpoints for federated board access.
 * These are called by station members to access boards shared from partner stations.
 * Enforces local access overrides and share mode ceiling before proxying.
 */
@Singleton
public class FederatedBoardLocalRoutes implements Routes {

    private final FederatedBoardProxyService proxyService;
    private final FederatedBoardService federatedBoardService;
    private final BoardService boardService;
    private final BoardTicketService ticketService;

    @Inject
    public FederatedBoardLocalRoutes(
            FederatedBoardProxyService proxyService,
            FederatedBoardService federatedBoardService,
            BoardService boardService,
            BoardTicketService ticketService) {
        this.proxyService = proxyService;
        this.federatedBoardService = federatedBoardService;
        this.boardService = boardService;
        this.ticketService = ticketService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String p = prefix + "/federation/boards";

        // Discovery
        routes.get(p, this::discoverBoards, Roles.USER);

        // Bookmarks
        routes.get(p + "/bookmarks", this::listBookmarks, Roles.USER);
        routes.post(p + "/bookmarks", this::createBookmark, Roles.USER);
        routes.delete(p + "/bookmarks/{bookmarkId}", this::deleteBookmark, Roles.USER);

        // Board read (proxied)
        routes.get(p + "/{partnerId}/{boardId}", this::getBoard, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/lanes", this::getLanes, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/labels", this::getLabels, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/fields", this::getFields, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets", this::listTickets, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/search", this::searchTickets, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/{ticketId}", this::getTicket, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/{ticketId}/comments", this::getComments, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/{ticketId}/checklist", this::getChecklist, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/{ticketId}/links", this::getLinks, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/{ticketId}/labels", this::getTicketLabels, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/{ticketId}/transitions", this::getTransitions, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/{ticketId}/history", this::getHistory, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/{ticketId}/attachments", this::getAttachments, Roles.USER);
        routes.get(p + "/{partnerId}/{boardId}/tickets/{ticketId}/watchers", this::getWatchers, Roles.USER);

        // Board write (FULL mode only, proxied)
        routes.post(p + "/{partnerId}/{boardId}/tickets", this::createTicket, Roles.USER);
        routes.put(p + "/{partnerId}/{boardId}/tickets/{ticketId}", this::updateTicket, Roles.USER);
        routes.delete(p + "/{partnerId}/{boardId}/tickets/{ticketId}", this::deleteTicket, Roles.USER);
        routes.put(p + "/{partnerId}/{boardId}/tickets/{ticketId}/move", this::moveTicket, Roles.USER);
        routes.put(p + "/{partnerId}/{boardId}/tickets/{ticketId}/reorder", this::reorderTickets, Roles.USER);
        routes.post(p + "/{partnerId}/{boardId}/tickets/{ticketId}/comments", this::addComment, Roles.USER);
        routes.post(p + "/{partnerId}/{boardId}/tickets/{ticketId}/checklist", this::addChecklistItem, Roles.USER);
        routes.put(
                p + "/{partnerId}/{boardId}/tickets/{ticketId}/checklist/{itemId}",
                this::updateChecklistItem,
                Roles.USER);
        routes.delete(
                p + "/{partnerId}/{boardId}/tickets/{ticketId}/checklist/{itemId}",
                this::deleteChecklistItem,
                Roles.USER);
        routes.post(p + "/{partnerId}/{boardId}/tickets/{ticketId}/labels/{labelId}", this::addTicketLabel, Roles.USER);
        routes.delete(
                p + "/{partnerId}/{boardId}/tickets/{ticketId}/labels/{labelId}", this::removeTicketLabel, Roles.USER);
        routes.post(p + "/{partnerId}/{boardId}/labels", this::createLabel, Roles.USER);
        routes.post(p + "/{partnerId}/{boardId}/tickets/{ticketId}/watch", this::watchTicket, Roles.USER);
        routes.delete(p + "/{partnerId}/{boardId}/tickets/{ticketId}/watch", this::unwatchTicket, Roles.USER);

        // Local access override management
        routes.get(p + "/{partnerId}/{boardId}/access/override", this::getOverride, Roles.BOARD_MANAGER);
        routes.put(p + "/{partnerId}/{boardId}/access/override", this::setOverride, Roles.BOARD_MANAGER);
    }

    // -- Helpers --

    private void requireView(int partnerId, int boardId, UserSession session) {
        if (!proxyService.canView(partnerId, boardId, session.member().id())) {
            throw new ForbiddenResponse("No view access to this federated board");
        }
    }

    private void requireWrite(int partnerId, int boardId, UserSession session) {
        if (!proxyService.canWrite(partnerId, boardId, session.member().id())) {
            throw new ForbiddenResponse("No write access to this federated board");
        }
    }

    // -- Discovery & Bookmarks --

    private void discoverBoards(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(proxyService.discoverBoards(session.stationId()));
    }

    private void listBookmarks(Context ctx) {
        var session = UserSession.from(ctx);
        ctx.json(proxyService.findBookmarks(session.member().id()));
    }

    private void createBookmark(Context ctx) {
        var session = UserSession.from(ctx);
        var req = ctx.bodyAsClass(BookmarkRequest.class);
        ctx.json(proxyService.createBookmark(
                session.member().id(),
                req.partnerId(),
                req.remoteBoardId(),
                req.remoteBoardName(),
                req.remoteBoardShortKey(),
                BoardShareMode.valueOf(req.shareMode())));
    }

    private void deleteBookmark(Context ctx) {
        int bookmarkId = ctx.pathParamAsClass("bookmarkId", Integer.class).get();
        proxyService.deleteBookmark(bookmarkId);
        ctx.status(204);
    }

    // -- Read proxies (direct DB for local partners) --

    private void getBoard(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        var mode = federatedBoardService.getShareMode(boardId, partnerId).orElse(BoardShareMode.READ_ONLY);
        ctx.json(Map.of("board", board, "shareMode", mode.name()));
    }

    private void getLanes(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        ctx.json(boardService.findLanes(boardId));
    }

    private void getLabels(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        ctx.json(boardService.findLabels(boardId));
    }

    private void getFields(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        ctx.json(boardService.findFields(boardId));
    }

    private void listTickets(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        ctx.json(ticketService.findByBoard(boardId));
    }

    private void searchTickets(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        String q = ctx.queryParam("q");
        if (q == null || q.isBlank()) {
            ctx.json(ticketService.findByBoard(boardId));
        } else {
            ctx.json(ticketService.search(boardId, q));
        }
    }

    private void getTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    private void getComments(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findComments(ticketId));
    }

    private void getChecklist(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findChecklistItems(ticketId));
    }

    private void getLinks(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findLinks(ticketId));
    }

    private void getTicketLabels(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    private void getTransitions(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findTransitions(ticketId));
    }

    private void getHistory(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findHistory(ticketId));
    }

    private void getAttachments(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ctx.json(ticketService.findAttachments(ticketId));
    }

    private void getWatchers(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireView(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var localWatchers = ticketService.findWatchers(ticketId);
        var federatedWatchers = federatedBoardService.findFederatedWatchers(ticketId);
        ctx.json(Map.of("local", localWatchers, "federated", federatedWatchers));
    }

    // -- Write proxies (FULL mode only) --

    private void createTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        var req = ctx.bodyAsClass(CreateTicketRequest.class);
        int laneId = req.laneId() != null
                ? req.laneId()
                : boardService.findLanes(boardId).getFirst().id();
        var ticket = ticketService.createTicket(
                boardId,
                laneId,
                req.title(),
                req.description(),
                null,
                req.priority() != null
                        ? dev.chojo.ember.feature.board.entity.TicketPriority.valueOf(req.priority())
                        : dev.chojo.ember.feature.board.entity.TicketPriority.MEDIUM,
                req.dueDate() != null ? java.time.LocalDate.parse(req.dueDate()) : null,
                0);
        federatedBoardService.setFederatedCreator(
                ticket.id(), partnerId, String.valueOf(session.member().id()));
        ctx.json(ticket);
    }

    private void updateTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(UpdateTicketRequest.class);
        ticketService.updateTicket(
                ticketId,
                req.title(),
                req.description(),
                req.assignedMemberId(),
                req.priority() != null
                        ? dev.chojo.ember.feature.board.entity.TicketPriority.valueOf(req.priority())
                        : null,
                req.dueDate() != null ? java.time.LocalDate.parse(req.dueDate()) : null,
                0);
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    private void deleteTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        ticketService.deleteTicket(ticketId);
        ctx.status(204);
    }

    private void moveTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(MoveTicketRequest.class);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        ticketService.moveTicket(ticketId, ticket.laneId(), req.toLaneId(), req.position(), 0);
        ctx.json(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    private void reorderTickets(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        var req = ctx.bodyAsClass(ReorderRequest.class);
        ticketService.reorderTickets(req.laneId(), req.orderedIds());
        ctx.status(204);
    }

    private void addComment(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(CommentRequest.class);
        var comment = ticketService.createComment(ticketId, req.parentId(), 0, req.content());
        federatedBoardService.setFederatedCommentAuthor(
                comment.id(), partnerId, String.valueOf(session.member().id()));
        ctx.json(comment);
    }

    private void addChecklistItem(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        var req = ctx.bodyAsClass(ChecklistItemRequest.class);
        ctx.json(ticketService.addChecklistItem(ticketId, req.title(), 0));
    }

    private void updateChecklistItem(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        var req = ctx.bodyAsClass(UpdateChecklistItemRequest.class);
        ticketService.updateChecklistItem(itemId, ticketId, req.title(), req.checked(), 0);
        ctx.status(204);
    }

    private void deleteChecklistItem(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int itemId = ctx.pathParamAsClass("itemId", Integer.class).get();
        ticketService.deleteChecklistItem(itemId, ticketId, 0);
        ctx.status(204);
    }

    private void addTicketLabel(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        boardService.addLabelToTicket(ticketId, labelId);
        ctx.json(boardService.findLabelsForTicket(ticketId));
    }

    private void removeTicketLabel(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        int labelId = ctx.pathParamAsClass("labelId", Integer.class).get();
        boardService.removeLabelFromTicket(ticketId, labelId);
        ctx.status(204);
    }

    private void createLabel(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        var req = ctx.bodyAsClass(CreateLabelRequest.class);
        ctx.json(boardService.createLabel(boardId, req.name(), req.color() != null ? req.color() : "#6b7280"));
    }

    private void watchTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        federatedBoardService.addFederatedWatcher(
                ticketId, partnerId, String.valueOf(session.member().id()));
        ctx.status(204);
    }

    private void unwatchTicket(Context ctx) {
        var session = UserSession.from(ctx);
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        requireWrite(partnerId, boardId, session);
        int ticketId = ctx.pathParamAsClass("ticketId", Integer.class).get();
        federatedBoardService.removeFederatedWatcher(
                ticketId, partnerId, String.valueOf(session.member().id()));
        ctx.status(204);
    }

    // -- Local access override management --

    private void getOverride(Context ctx) {
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        var view = proxyService.getLocalViewOverride(partnerId, boardId);
        var edit = proxyService.getLocalEditOverride(partnerId, boardId);
        ctx.json(Map.of("view", view, "edit", edit));
    }

    private void setOverride(Context ctx) {
        int partnerId = ctx.pathParamAsClass("partnerId", Integer.class).get();
        int boardId = ctx.pathParamAsClass("boardId", Integer.class).get();
        var req = ctx.bodyAsClass(OverrideRequest.class);
        proxyService.setLocalViewOverride(
                partnerId, boardId, new AccessData(req.viewRoleIds(), req.viewGroupIds(), req.viewTagIds()));
        proxyService.setLocalEditOverride(
                partnerId, boardId, new AccessData(req.editRoleIds(), req.editGroupIds(), req.editTagIds()));
        ctx.status(204);
    }

    // -- Request records --

    record BookmarkRequest(
            int partnerId, int remoteBoardId, String remoteBoardName, String remoteBoardShortKey, String shareMode) {}

    record CreateTicketRequest(Integer laneId, String title, String description, String priority, String dueDate) {}

    record UpdateTicketRequest(
            String title, String description, Integer assignedMemberId, String priority, String dueDate) {}

    record MoveTicketRequest(int toLaneId, int position) {}

    record ReorderRequest(int laneId, List<Integer> orderedIds) {}

    record CommentRequest(Integer parentId, String content) {}

    record ChecklistItemRequest(String title) {}

    record UpdateChecklistItemRequest(String title, boolean checked) {}

    record CreateLabelRequest(String name, String color) {}

    record OverrideRequest(
            List<Integer> viewRoleIds,
            List<Integer> viewGroupIds,
            List<Integer> viewTagIds,
            List<Integer> editRoleIds,
            List<Integer> editGroupIds,
            List<Integer> editTagIds) {}
}
