/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import dev.chojo.ember.api.MemberIdentity;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Response DTO for board ticket history entries with unified member identity.
 */
public record BoardTicketHistoryResponse(
        int id,
        int ticketId,
        BoardTicketHistoryAction action,
        @Nullable String detail,
        @Nullable MemberIdentity actor,
        @Nullable String actorName,
        Instant createdAt) {

    public static BoardTicketHistoryResponse from(
            BoardTicketHistory h, @Nullable MemberIdentity actor, @Nullable String actorName) {
        return new BoardTicketHistoryResponse(
                h.id(), h.ticketId(), h.action(), h.detail(), actor, actorName, h.createdAt());
    }
}
