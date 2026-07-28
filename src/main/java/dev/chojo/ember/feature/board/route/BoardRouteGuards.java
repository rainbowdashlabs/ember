/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Board and ticket lookups plus the view/edit access policy shared by the local board route
 * classes. Keeps the board-key and ticket-number resolution in one place instead of repeating it
 * in every handler.
 */
@Singleton
public class BoardRouteGuards {

    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public BoardRouteGuards(
            BoardService boardService, BoardTicketService ticketService, MemberIdentityFactory memberIdentityFactory) {
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    /**
     * Loads the board named by the {@code boardKey} path parameter. Answers 404 when the station
     * has no board with that key.
     */
    public Board resolveBoard(Context ctx, int stationId) {
        String boardKey = ctx.pathParam("boardKey");
        return boardService
                .findByShortKey(stationId, boardKey)
                .orElseThrow(() -> new NotFoundResponse("Board not found: " + boardKey));
    }

    /**
     * Resolves the id of the board named by the {@code boardKey} path parameter.
     */
    public int resolveBoardId(Context ctx, int stationId) {
        return resolveBoard(ctx, stationId).id();
    }

    /**
     * Resolves the id of the ticket named by the {@code ticketNumber} path parameter on the given
     * board. Answers 404 when the board has no ticket with that number.
     */
    public int resolveTicketId(Context ctx, int boardId) {
        int ticketNumber = ctx.pathParamAsClass("ticketNumber", Integer.class).get();
        return ticketService
                .findByBoardAndNumber(boardId, ticketNumber)
                .orElseThrow(() -> new NotFoundResponse("Ticket not found: " + ticketNumber))
                .id();
    }

    /**
     * Resolves the ticket named by the path parameters after asserting the session may edit its
     * board.
     */
    public int editableTicketId(Context ctx, UserSession session) {
        int boardId = resolveBoardId(ctx, session.stationId());
        requireEditAccess(boardId, session);
        return resolveTicketId(ctx, boardId);
    }

    /**
     * Resolves the ticket named by the path parameters after asserting the session may view its
     * board.
     */
    public int viewableTicketId(Context ctx, UserSession session) {
        int boardId = resolveBoardId(ctx, session.stationId());
        requireViewAccess(boardId, session);
        return resolveTicketId(ctx, boardId);
    }

    /**
     * Resolves the ticket named by the path parameters without any board-level access check.
     */
    public int ticketId(Context ctx, UserSession session) {
        return resolveTicketId(ctx, resolveBoardId(ctx, session.stationId()));
    }

    /**
     * Asserts the session may edit the board. Answers 400 when the caller is not a station member
     * and 403 when the board is not editable for them.
     */
    public void requireEditAccess(int boardId, UserSession session) {
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        boolean isManager = session.permissions().contains(StationPermission.BOARD_MANAGER);
        if (!boardService.canEdit(boardId, session.member().id(), isManager))
            throw new ForbiddenResponse("No edit access to this board");
    }

    /**
     * Asserts the session may view the board. Answers 400 when the caller is not a station member
     * and 403 when the board is not visible to them.
     */
    public void requireViewAccess(int boardId, UserSession session) {
        if (session.member() == null) throw new BadRequestResponse("Not a station member");
        boolean isManager = session.permissions().contains(StationPermission.BOARD_MANAGER);
        if (!boardService.canView(boardId, session.member().id(), isManager))
            throw new ForbiddenResponse("No access to this board");
    }

    /**
     * The acting station member of the session as a federation aware identity.
     */
    public MemberIdentity actor(UserSession session) {
        return memberIdentityFactory.local(session.stationId(), session.member().id());
    }

    /**
     * A member of the session's station as a federation aware identity, or {@code null} when no
     * member is given.
     */
    public MemberIdentity member(UserSession session, Integer memberId) {
        return memberId == null ? null : memberIdentityFactory.local(session.stationId(), memberId);
    }
}
