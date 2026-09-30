/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteAccessResponse;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteSharedBoardResponse;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.UUID;

/**
 * Finds the boards a station may reach through its federation partners and the board level metadata
 * behind them, and answers partners asking which of this station's boards they may reach.
 */
@Singleton
public class FederatedBoardDiscoveryService implements FederationServer {
    private final FederatedBoardService federatedBoardService;
    private final BoardService boardService;
    private final FederationService federationService;
    private final StationRepository stationRepository;
    private final StationMemberService memberService;
    private final MemberIdentityFactory memberIdentityFactory;
    private final FederationTransport transport;
    private final FederatedBoardGuards guards;
    private final FederatedBoardLocator locator;
    private final FederationFanout fanout;

    @Inject
    public FederatedBoardDiscoveryService(
            FederatedBoardService federatedBoardService,
            BoardService boardService,
            FederationService federationService,
            StationRepository stationRepository,
            StationMemberService memberService,
            MemberIdentityFactory memberIdentityFactory,
            FederationTransport transport,
            FederatedBoardGuards guards,
            FederatedBoardLocator locator,
            FederationFanout fanout) {
        this.federatedBoardService = federatedBoardService;
        this.boardService = boardService;
        this.federationService = federationService;
        this.stationRepository = stationRepository;
        this.memberService = memberService;
        this.memberIdentityFactory = memberIdentityFactory;
        this.transport = transport;
        this.guards = guards;
        this.locator = locator;
        this.fanout = fanout;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(RemoteBoardRoutes.LIST_SHARED_BOARDS, (partner, params, body) -> serveSharedBoards(partner));
        endpoints.serve(RemoteBoardRoutes.GET_BOARD, (partner, params, body) -> serveBoard(partner, params));
        endpoints.serve(RemoteBoardRoutes.GET_ACCESS, (partner, params, body) -> serveAccess(partner, params));
        endpoints.serve(RemoteBoardRoutes.GET_MEMBERS, (partner, params, body) -> serveMembers(partner, params));
    }

    /**
     * The boards this station shares with a partner.
     *
     * @param partner the partnership the request arrived on
     * @return one entry per shared board
     */
    public List<RemoteSharedBoardResponse> serveSharedBoards(ServingPartner partner) {
        return federatedBoardService.findSharedBoardIds(partner.partnerId()).stream()
                .flatMap(boardId -> boardService.findById(boardId).stream())
                .map(board -> new RemoteSharedBoardResponse(
                        board.uid(),
                        board.name(),
                        board.description() != null ? board.description() : "",
                        board.shortKey(),
                        shareMode(board.id(), partner),
                        federatedBoardService
                                .getRequiredUserType(board.id(), partner.partnerId())
                                .orElse(StationUserType.MEMBER)))
                .toList();
    }

    /**
     * The metadata of one shared board.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board
     * @return the board, the partner's share mode and the owning station's name
     */
    public FederatedBoardDetail serveBoard(ServingPartner partner, PathParams params) {
        int boardId = guards.viewableBoardId(partner, params);
        var board = boardService.findById(boardId).orElseThrow(Refusal.REMOTE_BOARD_NOT_HERE_ON_READ::raise);
        String stationName =
                stationRepository.findById(board.stationId()).map(Station::name).orElse("");
        return FederatedBoardDetail.of(board, shareMode(boardId, partner), stationName, stationRepository);
    }

    /**
     * How the partner may use one shared board.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board
     * @return the share mode and the partner user types that may edit
     */
    public RemoteAccessResponse serveAccess(ServingPartner partner, PathParams params) {
        int boardId = guards.viewableBoardId(partner, params);
        return new RemoteAccessResponse(
                shareMode(boardId, partner), federatedBoardService.findFederatedEditUserTypes(boardId));
    }

    /**
     * The members of the station owning a shared board, for assigning and mentioning.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board
     * @return the member completions
     */
    public List<MemberCompletion> serveMembers(ServingPartner partner, PathParams params) {
        var board = boardService
                .findById(guards.viewableBoardId(partner, params))
                .orElseThrow(Refusal.REMOTE_BOARD_NOT_HERE_FOR_MEMBERS::raise);
        return memberIdentityFactory.enrichCompletions(memberService.findCompletions(board.stationId()));
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
                .filter(p -> federationService.hasCapability(p, CapabilityType.BOARD_SHARE, Direction.IMPORT))
                .toList();
        return fanout.fanOut(partners, this::discoverAt).items();
    }

    /**
     * Returns the board metadata of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the board detail
     */
    public FederatedBoardDetail proxyGetBoard(int partnerId, String boardKey) {
        return transport.get(
                locator.requirePartner(partnerId),
                RemoteBoardRoutes.GET_BOARD.at(boardKey),
                FederatedBoardDetail.class);
    }

    /**
     * Returns the members of the station owning a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the member completions of the owning station
     */
    public List<MemberCompletion> proxyGetMembers(int partnerId, String boardKey) {
        return transport.getList(
                locator.requirePartner(partnerId), RemoteBoardRoutes.GET_MEMBERS.at(boardKey), MemberCompletion.class);
    }

    private List<DiscoveredBoard> discoverAt(FederationPartner partner) {
        String stationName = locator.partnerStationName(partner);
        return transport
                .getList(partner, RemoteBoardRoutes.LIST_SHARED_BOARDS.at(), RemoteSharedBoardResponse.class)
                .stream()
                .map(board -> new DiscoveredBoard(
                        partner.id(),
                        partner.partnerStationId().toString(),
                        board.uid(),
                        board.name(),
                        board.shortKey(),
                        board.description(),
                        board.shareMode(),
                        stationName,
                        board.requiredUserType() != null ? board.requiredUserType() : StationUserType.MEMBER))
                .toList();
    }

    private BoardShareMode shareMode(int boardId, ServingPartner partner) {
        return federatedBoardService.getShareMode(boardId, partner.partnerId()).orElse(BoardShareMode.READ_ONLY);
    }

    /**
     * A board a station can reach through one of its federation partners, as the discovery listing
     * presents it.
     */
    public record DiscoveredBoard(
            int partnerId,
            String partnerStationUid,
            UUID remoteBoardUid,
            String name,
            String shortKey,
            String description,
            BoardShareMode shareMode,
            String partnerStationName,
            StationUserType requiredUserType) {}

    /**
     * Board representation for remote federation responses where stationId is a UUID string.
     */
    public record RemoteBoard(
            int id,
            String stationId,
            String name,
            String description,
            String shortKey,
            int hideDoneAfterDays,
            int ticketCounter,
            Integer backlogLaneId,
            String createdAt) {

        /**
         * Returns whether the board has a backlog lane.
         *
         * @return whether a backlog lane is configured
         */
        public boolean hasBacklog() {
            return backlogLaneId != null;
        }
    }

    /**
     * The board level metadata a station sees for one federated board.
     */
    public record FederatedBoardDetail(RemoteBoard board, BoardShareMode shareMode, String stationName) {

        /**
         * Creates a FederatedBoardDetail from a local Board entity.
         *
         * @param board             the local board
         * @param shareMode         the share mode granted to the partner
         * @param stationName       the name of the owning station
         * @param stationRepository the repository used to resolve the station uid
         * @return the federated board detail
         */
        public static FederatedBoardDetail of(
                Board board, BoardShareMode shareMode, String stationName, StationRepository stationRepository) {
            var stationUid = stationRepository.resolveUid(board.stationId()).toString();
            return new FederatedBoardDetail(
                    new RemoteBoard(
                            board.id(),
                            stationUid != null ? stationUid : String.valueOf(board.stationId()),
                            board.name(),
                            board.description(),
                            board.shortKey(),
                            board.hideDoneAfterDays(),
                            board.ticketCounter(),
                            board.backlogLaneId(),
                            board.createdAt() != null ? board.createdAt().toString() : null),
                    shareMode,
                    stationName);
        }
    }
}
