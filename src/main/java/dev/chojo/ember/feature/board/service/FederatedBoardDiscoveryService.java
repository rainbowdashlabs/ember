/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.service.FederatedBoardProxyService.DiscoveredBoard;
import dev.chojo.ember.feature.board.service.FederatedBoardProxyService.FederatedBoardDetail;
import dev.chojo.ember.feature.board.service.FederatedBoardProxyService.RemoteDiscoveredBoard;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Finds the boards a station may reach through its federation partners and serves the board level
 * metadata behind them. Partners on this instance are read from the database, partners on another
 * instance are asked over HTTP.
 */
@Singleton
public class FederatedBoardDiscoveryService {
    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardAccessService accessService;
    private final BoardService boardService;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final StationMemberService memberService;
    private final FederatedBoardRemoteGateway gateway;
    private final FederatedBoardLocator locator;
    private final FederationFanout fanout;

    @Inject
    public FederatedBoardDiscoveryService(
            FederatedBoardService federatedBoardService,
            FederatedBoardAccessService accessService,
            BoardService boardService,
            FederationService federationService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            StationMemberService memberService,
            FederatedBoardRemoteGateway gateway,
            FederatedBoardLocator locator,
            FederationFanout fanout) {
        this.federatedBoardService = federatedBoardService;
        this.accessService = accessService;
        this.boardService = boardService;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.memberService = memberService;
        this.gateway = gateway;
        this.locator = locator;
        this.fanout = fanout;
    }

    /**
     * Discovers all boards shared with the local station from all active partners.
     *
     * @param stationId the local station id
     * @return the shared boards with partner station name and share mode
     */
    public List<DiscoveredBoard> discoverBoards(int stationId) {
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(p -> federationService.hasCapability(p.id(), CapabilityType.BOARD_SHARE, Direction.IMPORT))
                .toList();
        return fanout.fanOut(partners, this::discoverBoardsDirect, this::discoverBoardsViaHttp);
    }

    /**
     * Returns the board metadata of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the board detail
     */
    public FederatedBoardDetail proxyGetBoard(int partnerId, String boardKey) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.get(partner, "/remote/boards/" + boardKey, FederatedBoardDetail.class);
        }
        int boardId = locator.resolveBoardId(boardKey, partner);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        var mode = accessService.getEffectiveShareMode(partnerId, boardId).orElse(BoardShareMode.READ_ONLY);
        String stationName = stationRepository
                .findById(board.stationId())
                .map(Station::name)
                .orElse("Station #" + board.stationId());
        return FederatedBoardDetail.of(board, mode, stationName, stationRepository);
    }

    /**
     * Returns the members of the station owning a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the member completions of the owning station
     */
    public List<MemberCompletion> proxyGetMembers(int partnerId, String boardKey) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(partner, "/remote/boards/" + boardKey + "/members", MemberCompletion.class);
        }
        int boardId = locator.resolveBoardId(boardKey, partner);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        return memberService.findCompletions(board.stationId());
    }

    private List<DiscoveredBoard> discoverBoardsDirect(FederationPartner partner) {
        var boardIds = new ArrayList<>(federatedBoardService.findSharedBoardIds(partner.id()));
        collectReverseSharedBoardIds(partner, boardIds);

        return boardIds.stream()
                .map(boardId -> boardService
                        .findById(boardId)
                        .map(board -> {
                            var mode = accessService
                                    .getEffectiveShareMode(partner.id(), boardId)
                                    .orElse(BoardShareMode.READ_ONLY);
                            var requiredUserType = federatedBoardService
                                    .getRequiredUserType(boardId, partner.id())
                                    .orElse(StationUserType.MEMBER);
                            return new DiscoveredBoard(
                                    partner.id(),
                                    partner.partnerStationId().toString(),
                                    board.uid(),
                                    board.name(),
                                    board.shortKey(),
                                    board.description(),
                                    mode,
                                    locator.partnerStationName(partner),
                                    requiredUserType);
                        })
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Adds the boards shared through the owning station's own partner record. Our partner record may
     * differ from the record the owning station keeps for us, so the share target is looked up from
     * the other side as well.
     */
    private void collectReverseSharedBoardIds(FederationPartner partner, List<Integer> boardIds) {
        var ourStationUid = stationRepository
                .findById(partner.stationId())
                .map(Station::uid)
                .orElse(null);
        if (ourStationUid == null) return;
        var owningStation =
                stationRepository.findByUid(partner.partnerStationId()).orElse(null);
        if (owningStation == null) return;
        federationRepository
                .findPartnerByStationAndRemoteUid(owningStation.id(), ourStationUid)
                .ifPresent(op -> {
                    for (var id : federatedBoardService.findSharedBoardIds(op.id())) {
                        if (!boardIds.contains(id)) boardIds.add(id);
                    }
                });
    }

    private List<DiscoveredBoard> discoverBoardsViaHttp(FederationPartner partner) {
        var remoteBoards = gateway.getList(partner, "/remote/boards", RemoteDiscoveredBoard.class);
        return remoteBoards.stream()
                .map(b -> new DiscoveredBoard(
                        partner.id(),
                        partner.partnerStationId().toString(),
                        UUID.fromString(b.uid()),
                        b.name(),
                        b.shortKey(),
                        b.description(),
                        b.shareMode(),
                        locator.partnerStationName(partner),
                        b.requiredUserType() != null ? b.requiredUserType() : StationUserType.MEMBER))
                .toList();
    }
}
