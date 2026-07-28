/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

/**
 * Resolves the identifiers a federated board request is expressed in — partner record, board key
 * and ticket number — into the local database ids of the owning station, and caches the display
 * names partners send along with their requests.
 */
@Singleton
public class FederatedBoardLocator {
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final EventFederationRepository eventFederationRepository;

    @Inject
    public FederatedBoardLocator(
            FederationRepository federationRepository,
            StationRepository stationRepository,
            BoardService boardService,
            BoardTicketService ticketService,
            EventFederationRepository eventFederationRepository) {
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.eventFederationRepository = eventFederationRepository;
    }

    /**
     * Looks up a partner record.
     *
     * @param partnerId the partner record id
     * @return the partner
     * @throws NotFoundResponse when no such partner exists
     */
    public FederationPartner requirePartner(int partnerId) {
        return federationRepository
                .findPartnerById(partnerId)
                .orElseThrow(() -> new NotFoundResponse("Partner not found: " + partnerId));
    }

    /**
     * Resolves a board key on the partner's station to a local board id.
     *
     * @param boardKey the board short key
     * @param partner  the partner owning the board
     * @return the board id
     * @throws NotFoundResponse when the station or the board is unknown
     */
    public int resolveBoardId(String boardKey, FederationPartner partner) {
        int partnerStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .orElseThrow(() -> new NotFoundResponse("Partner station not found"))
                .id();
        return boardService
                .findByShortKey(partnerStationId, boardKey)
                .orElseThrow(() -> new NotFoundResponse("Board not found: " + boardKey))
                .id();
    }

    /**
     * Resolves a board relative ticket number to a ticket id.
     *
     * @param boardId      the board id
     * @param ticketNumber the board relative ticket number
     * @return the ticket id
     * @throws NotFoundResponse when the board has no such ticket
     */
    public int resolveTicketId(int boardId, int ticketNumber) {
        return ticketService
                .findByBoardAndNumber(boardId, ticketNumber)
                .orElseThrow(() -> new NotFoundResponse("Ticket not found: " + ticketNumber))
                .id();
    }

    /**
     * Resolves a board key and ticket number on the partner's station to a ticket id.
     *
     * @param partner      the partner owning the board
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the ticket id
     */
    public int resolveTicketId(FederationPartner partner, String boardKey, int ticketNumber) {
        return resolveTicketId(resolveBoardId(boardKey, partner), ticketNumber);
    }

    /**
     * Resolves a board key to the full board entity on the partner station. Returns {@code null} for
     * partners on another instance, which enforce access control themselves.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the board or {@code null}
     */
    public Board resolveFederatedBoard(int partnerId, String boardKey) {
        var partner = federationRepository.findPartnerById(partnerId).orElse(null);
        if (partner == null) return null;
        return stationRepository
                .findByUid(partner.partnerStationId())
                .flatMap(station -> boardService.findByShortKey(station.id(), boardKey))
                .orElse(null);
    }

    /**
     * Resolves a board key to the board uid on the partner station.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the board uid or {@code null}
     */
    public UUID resolveFederatedBoardUid(int partnerId, String boardKey) {
        var board = resolveFederatedBoard(partnerId, boardKey);
        return board != null ? board.uid() : null;
    }

    /**
     * Returns the display name of the partner's station.
     *
     * @param partner the partner
     * @return the station name or a placeholder when the station is unknown
     */
    public String partnerStationName(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::name)
                .orElse("Partner #" + partner.id());
    }

    /**
     * Builds the identity of an acting member on the partner station.
     *
     * @param partner   the partner the member belongs to
     * @param memberUid the member uid on the partner station
     * @return the identity or {@code null} when no member was given
     */
    public MemberIdentity remoteIdentity(FederationPartner partner, UUID memberUid) {
        return memberUid != null ? new MemberIdentity(partner.partnerStationId(), memberUid) : null;
    }

    /**
     * Caches the display name a partner sent for one of its members.
     *
     * @param partnerId   the partner record id
     * @param memberUid   the member uid on the partner station
     * @param displayName the display name
     */
    public void cacheName(int partnerId, UUID memberUid, String displayName) {
        eventFederationRepository.cacheName(partnerId, memberUid, displayName);
    }

    /**
     * Caches the display name only when both the member and the name are known.
     *
     * @param partnerId   the partner record id
     * @param memberUid   the member uid on the partner station
     * @param displayName the display name
     */
    public void cacheNameIfPresent(int partnerId, UUID memberUid, String displayName) {
        if (memberUid != null && displayName != null) cacheName(partnerId, memberUid, displayName);
    }
}
