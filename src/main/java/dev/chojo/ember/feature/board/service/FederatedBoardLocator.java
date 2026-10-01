/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.refusal.BoardRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Resolves what a federated board request on the asking side is expressed in - a partner record
 * and a board key - into the partner row and, for a board held on this instance, the board itself.
 */
@Singleton
public class FederatedBoardLocator {
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final BoardService boardService;

    @Inject
    public FederatedBoardLocator(
            FederationRepository federationRepository, StationRepository stationRepository, BoardService boardService) {
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.boardService = boardService;
    }

    /**
     * Looks up a partner record.
     *
     * @param partnerId the partner record id
     * @return the partner
     * @throws RefusalResponse when no such partner exists
     */
    public FederationPartner requirePartner(int partnerId) {
        return federationRepository.findPartnerById(partnerId).orElseThrow(BoardRefusal.BOARD_PARTNER_NOT_HERE::raise);
    }

    /**
     * Resolves a board key to the full board entity on the partner station. Returns {@code null} for
     * partners on another instance, which enforce access control themselves.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the board or {@code null}
     */
    public @Nullable Board resolveFederatedBoard(int partnerId, String boardKey) {
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
    public @Nullable UUID resolveFederatedBoardUid(int partnerId, String boardKey) {
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
}
