/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import dev.chojo.ember.api.MemberIdentity;

import java.time.Instant;

/**
 * Response DTO for board ticket history entries with unified member identity.
 */
public record BoardTicketHistoryResponse(
        int id,
        int ticketId,
        BoardTicketHistoryAction action,
        String detail,
        MemberIdentity actor,
        String actorName,
        Instant createdAt) {

    public static BoardTicketHistoryResponse from(BoardTicketHistory h, MemberIdentity actor, String actorName) {
        return new BoardTicketHistoryResponse(
                h.id(), h.ticketId(), h.action(), h.detail(), actor, actorName, h.createdAt());
    }
}
