/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * The share checks every board serving function runs before it answers a partner: which board and
 * ticket the request names on the serving station, whether that board is shared with the partner at
 * all or for writing, and who of the partner's members acts.
 */
@Singleton
public class FederatedBoardGuards {
    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final FederatedBoardService federatedBoardService;
    private final EventFederationRepository eventFederationRepository;

    @Inject
    public FederatedBoardGuards(
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
     * The board named by the {@code boardKey} parameter on the serving station, once it is shared
     * with the partner.
     */
    public int viewableBoardId(ServingPartner partner, PathParams params) {
        int boardId = boardId(partner, params);
        if (!federatedBoardService.canFederatedView(boardId, partner.partnerId())) {
            throw Refusal.REMOTE_BOARD_NOT_SHARED.raise();
        }
        return boardId;
    }

    /**
     * The board named by the {@code boardKey} parameter on the serving station, once the partner may
     * write to it.
     */
    public int writableBoardId(ServingPartner partner, PathParams params) {
        int boardId = boardId(partner, params);
        if (!federatedBoardService.canFederatedWrite(boardId, partner.partnerId())) {
            throw Refusal.REMOTE_BOARD_NOT_WRITABLE.raise();
        }
        return boardId;
    }

    /**
     * The ticket named by the {@code ticketNumber} parameter, once its board is shared with the partner.
     */
    public int viewableTicketId(ServingPartner partner, PathParams params) {
        return ticketId(viewableBoardId(partner, params), params.integer("ticketNumber"));
    }

    /**
     * The ticket named by the {@code ticketNumber} parameter, once the partner may write to its board.
     */
    public int writableTicketId(ServingPartner partner, PathParams params) {
        return ticketId(writableBoardId(partner, params), params.integer("ticketNumber"));
    }

    /**
     * The ticket with a board relative number on a board.
     */
    public int ticketId(int boardId, int ticketNumber) {
        return ticketService
                .findByBoardAndNumber(boardId, ticketNumber)
                .orElseThrow(Refusal.REMOTE_BOARD_TICKET_NOT_HERE::raise)
                .id();
    }

    /**
     * A lane a partner names, once it is a lane of the board the request is about.
     */
    public int laneOnBoard(int boardId, int laneId) {
        if (boardService.findLanes(boardId).stream().noneMatch(lane -> lane.id() == laneId)) {
            throw Refusal.REMOTE_LANE_NOT_ON_BOARD.raise();
        }
        return laneId;
    }

    /**
     * A label a partner names, once it is a label of the board the request is about.
     */
    public BoardLabel labelOnBoard(int boardId, int labelId) {
        return boardService.findLabels(boardId).stream()
                .filter(label -> label.id() == labelId)
                .findFirst()
                .orElseThrow(Refusal.REMOTE_LABEL_NOT_ON_BOARD::raise);
    }

    /**
     * A checklist item a partner names, once it is on the ticket the request is about.
     */
    public int checklistItemOnTicket(int ticketId, int itemId) {
        if (ticketService.findChecklistItems(ticketId).stream().noneMatch(item -> item.id() == itemId)) {
            throw Refusal.REMOTE_CHECKLIST_ITEM_NOT_ON_TICKET.raise();
        }
        return itemId;
    }

    /**
     * A ticket of the board the request is about, named by its board relative number, as a link
     * target.
     */
    public int linkedTicketId(int boardId, int ticketNumber) {
        return ticketService
                .findByBoardAndNumber(boardId, ticketNumber)
                .orElseThrow(Refusal.REMOTE_TICKET_NOT_HERE_BY_NUMBER::raise)
                .id();
    }

    /**
     * The acting member of the partner station, or {@code null} when the partner named none.
     */
    public MemberIdentity actor(ServingPartner partner, UUID memberUid) {
        return memberUid == null ? null : new MemberIdentity(partner.askingStationUid(), memberUid);
    }

    /**
     * Remembers the display name a partner sent along for one of its members, so later reads show a
     * name instead of an opaque id.
     */
    public void cacheName(ServingPartner partner, UUID memberUid, String displayName) {
        if (memberUid == null || displayName == null) return;
        eventFederationRepository.cacheName(partner.partnerId(), memberUid, displayName);
    }

    private int boardId(ServingPartner partner, PathParams params) {
        return boardService
                .findByShortKey(partner.servingStationId(), params.text("boardKey"))
                .orElseThrow(Refusal.REMOTE_BOARD_NOT_HERE::raise)
                .id();
    }
}
