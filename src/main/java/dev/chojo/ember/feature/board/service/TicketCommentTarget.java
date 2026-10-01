/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import dev.chojo.ember.feature.board.route.BoardRouteGuards;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.CreatedAudience;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.comment.service.CommentTarget;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.Optional;

/**
 * Comments on a board ticket.
 *
 * <p>They are read by whoever may view the ticket's board and written by whoever may edit it. Only
 * their author changes them; their author or a board manager removes them. Every comment tells the
 * ticket's watchers and whoever it mentions, wherever it was written from; a reply does not tell the
 * author of the answered comment on its own.
 */
@Singleton
public class TicketCommentTarget implements CommentTarget {
    private final BoardTicketService tickets;
    private final BoardService boards;
    private final BoardRouteGuards access;

    @Inject
    public TicketCommentTarget(BoardTicketService tickets, BoardService boards, BoardRouteGuards access) {
        this.tickets = tickets;
        this.boards = boards;
        this.access = access;
    }

    @Override
    public CommentEntityType type() {
        return CommentEntityType.BOARD_TICKET;
    }

    @Override
    public Optional<TargetInfo> find(int targetId) {
        return tickets.findById(targetId)
                .flatMap(ticket -> boards.findById(ticket.boardId()).map(board -> {
                    var address = new BoardTicketAddress(board.shortKey(), ticket.ticketNumber());
                    return new TargetInfo(
                            CommentEntityType.BOARD_TICKET,
                            ticket.id(),
                            board.stationId(),
                            board.shortKey() + "-" + ticket.ticketNumber(),
                            address,
                            false);
                }));
    }

    @Override
    public Refusal missing() {
        return Refusal.BOARD_TICKET_NOT_HERE;
    }

    @Override
    public void requireReadable(UserSession session, TargetInfo target) {
        access.requireViewAccess(boardOf(session, target), session);
    }

    @Override
    public void requireWritable(UserSession session, TargetInfo target) {
        access.requireEditAccess(boardOf(session, target), session);
    }

    /**
     * The board holding the ticket, which has to be one of the member's own station; any other
     * answers as a board that is not there.
     */
    private int boardOf(UserSession session, TargetInfo target) {
        if (!Objects.equals(target.stationId(), session.stationId())) {
            throw Refusal.BOARD_NOT_HERE_OR_NOT_YOURS.raise();
        }
        return tickets.findById(target.id())
                .orElseThrow(Refusal.BOARD_TICKET_NOT_HERE::raise)
                .boardId();
    }

    @Override
    public boolean mayModerate(UserSession session, TargetInfo target, Moderation action) {
        return action == Moderation.DELETE && session.hasPermission(StationPermission.BOARD_MANAGER);
    }

    @Override
    public CreatedAudience audienceFor(TargetInfo target, CommentOrigin origin) {
        var watchers = tickets.findWatchers(target.id());
        return new CreatedAudience(false, true, watchers.isEmpty() ? null : StationAudience.members(watchers));
    }

    @Override
    public NotificationLink link(TargetInfo target, int commentId) {
        var address = Objects.requireNonNull(target.ticketAddress(), "a ticket target carries its address");
        return NotificationLinks.comment(NotificationLinks.ticket(address, target.id()), commentId);
    }
}
