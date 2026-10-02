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
 * Response DTO for board ticket transitions with unified member identity.
 */
public record BoardTicketTransitionResponse(
        int id,
        int ticketId,
        @Nullable Integer fromLaneId,
        @Nullable Integer toLaneId,
        @Nullable MemberIdentity actor,
        @Nullable String actorName,
        Instant movedAt) {

    public static BoardTicketTransitionResponse from(
            BoardTicketTransition tr, @Nullable MemberIdentity actor, @Nullable String actorName) {
        return new BoardTicketTransitionResponse(
                tr.id(), tr.ticketId(), tr.fromLaneId(), tr.toLaneId(), actor, actorName, tr.movedAt());
    }
}
