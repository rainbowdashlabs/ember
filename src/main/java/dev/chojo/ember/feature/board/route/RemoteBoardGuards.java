/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Partner resolution, board and ticket lookups and the share-mode policy shared by the
 * server-to-server board route classes. The RSA signature itself is verified centrally by
 * {@link dev.chojo.ember.api.AccessManager} before any handler runs.
 */
@Singleton
public class RemoteBoardGuards {

    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final FederatedBoardService federatedBoardService;
    private final EventFederationRepository eventFederationRepository;

    @Inject
    public RemoteBoardGuards(
            BoardService boardService,
            BoardTicketService ticketService,
            FederatedBoardService federatedBoardService,
            EventFederationRepository eventFederationRepository) {
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.federatedBoardService = federatedBoardService;
        this.eventFederationRepository = eventFederationRepository;
    }

    /**
     * Returns the verified federation partner from the centrally resolved session.
     */
    public FederationPartner requirePartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    /**
     * Resolves the id of the board named by the {@code boardKey} path parameter on the partner's
     * station. Answers 404 when the partner has no board with that key.
     */
    public int resolveBoardId(Context ctx, FederationPartner partner) {
        String boardKey = ctx.pathParam("boardKey");
        return boardService
                .findByShortKey(partner.stationId(), boardKey)
                .orElseThrow(() -> new NotFoundResponse("Board not found: " + boardKey))
                .id();
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
     * Resolves the addressed board after asserting it is shared with the partner at all.
     */
    public int viewableBoardId(Context ctx, FederationPartner partner) {
        int boardId = resolveBoardId(ctx, partner);
        if (!federatedBoardService.canFederatedView(boardId, partner.id())) {
            throw new ForbiddenResponse("Board not shared with this partner");
        }
        return boardId;
    }

    /**
     * Resolves the addressed board after asserting the partner may write to it.
     */
    public int writableBoardId(Context ctx, FederationPartner partner) {
        int boardId = resolveBoardId(ctx, partner);
        if (!federatedBoardService.canFederatedWrite(boardId, partner.id())) {
            throw new ForbiddenResponse("Write access requires FULL share mode");
        }
        return boardId;
    }

    /**
     * Resolves the addressed ticket after asserting its board is shared with the partner.
     */
    public int viewableTicketId(Context ctx, FederationPartner partner) {
        return resolveTicketId(ctx, viewableBoardId(ctx, partner));
    }

    /**
     * Resolves the addressed ticket after asserting the partner may write to its board.
     */
    public int writableTicketId(Context ctx, FederationPartner partner) {
        return resolveTicketId(ctx, writableBoardId(ctx, partner));
    }

    /**
     * The acting member of the partner station as an identity, or {@code null} when the partner
     * did not name one.
     */
    public MemberIdentity remoteActor(FederationPartner partner, UUID remoteMemberUid) {
        return remoteMemberUid == null ? null : new MemberIdentity(partner.partnerStationId(), remoteMemberUid);
    }

    /**
     * Remembers the display name a partner sent along for one of its members, so later reads can
     * show a name instead of an opaque id.
     */
    public void cacheDisplayName(FederationPartner partner, UUID remoteMemberUid, String displayName) {
        if (remoteMemberUid == null || displayName == null) return;
        eventFederationRepository.cacheName(partner.id(), remoteMemberUid, displayName);
    }
}
