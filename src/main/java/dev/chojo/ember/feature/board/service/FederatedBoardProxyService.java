/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryResponse;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.board.entity.BoardTicketTransitionResponse;
import dev.chojo.ember.feature.board.entity.FederationBoardBookmark;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketLabelMapping;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Entry point for everything a station does with a board owned by a federation partner.
 * The work itself lives in focused services — discovery, access control, board structure, tickets
 * and ticket details — this class only routes each call to the service that owns it and keeps the
 * shared federation payload shapes.
 */
@Singleton
public class FederatedBoardProxyService {
    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardRepository federatedBoardRepository;
    private final FederatedBoardAccessService accessService;
    private final FederatedBoardDiscoveryService discoveryService;
    private final FederatedBoardStructureProxy structureProxy;
    private final FederatedTicketProxy ticketProxy;
    private final FederatedTicketDetailProxy ticketDetailProxy;
    private final FederatedBoardLocator locator;

    @Inject
    public FederatedBoardProxyService(
            FederatedBoardService federatedBoardService,
            FederatedBoardRepository federatedBoardRepository,
            FederatedBoardAccessService accessService,
            FederatedBoardDiscoveryService discoveryService,
            FederatedBoardStructureProxy structureProxy,
            FederatedTicketProxy ticketProxy,
            FederatedTicketDetailProxy ticketDetailProxy,
            FederatedBoardLocator locator) {
        this.federatedBoardService = federatedBoardService;
        this.federatedBoardRepository = federatedBoardRepository;
        this.accessService = accessService;
        this.discoveryService = discoveryService;
        this.structureProxy = structureProxy;
        this.ticketProxy = ticketProxy;
        this.ticketDetailProxy = ticketDetailProxy;
        this.locator = locator;
    }

    public List<DiscoveredBoard> discoverBoards(int stationId) {
        return discoveryService.discoverBoards(stationId);
    }

    public FederatedBoardDetail proxyGetBoard(int partnerId, String boardKey) {
        return discoveryService.proxyGetBoard(partnerId, boardKey);
    }

    public List<MemberCompletion> proxyGetMembers(int partnerId, String boardKey) {
        return discoveryService.proxyGetMembers(partnerId, boardKey);
    }

    public List<BoardLane> proxyGetLanes(int partnerId, String boardKey) {
        return structureProxy.proxyGetLanes(partnerId, boardKey);
    }

    public List<BoardLabel> proxyGetLabels(int partnerId, String boardKey) {
        return structureProxy.proxyGetLabels(partnerId, boardKey);
    }

    public List<TicketLabelMapping> proxyGetAllTicketLabels(int partnerId, String boardKey) {
        return structureProxy.proxyGetAllTicketLabels(partnerId, boardKey);
    }

    public List<BoardField> proxyGetFields(int partnerId, String boardKey) {
        return structureProxy.proxyGetFields(partnerId, boardKey);
    }

    public BoardLabel proxyCreateLabel(int partnerId, String boardKey, String name, String color) {
        return structureProxy.proxyCreateLabel(partnerId, boardKey, name, color);
    }

    public List<TicketSummary> proxyListTickets(int partnerId, String boardKey) {
        return ticketProxy.proxyListTickets(partnerId, boardKey);
    }

    public List<TicketSummary> proxySearchTickets(int partnerId, String boardKey, String query) {
        return ticketProxy.proxySearchTickets(partnerId, boardKey, query);
    }

    public BoardTicket proxyGetTicket(int partnerId, String boardKey, int ticketNumber) {
        return ticketProxy.proxyGetTicket(partnerId, boardKey, ticketNumber);
    }

    public List<BoardTicketTransitionResponse> proxyGetTransitions(int partnerId, String boardKey, int ticketNumber) {
        return ticketProxy.proxyGetTransitions(partnerId, boardKey, ticketNumber);
    }

    public List<BoardTicketHistoryResponse> proxyGetHistory(int partnerId, String boardKey, int ticketNumber) {
        return ticketProxy.proxyGetHistory(partnerId, boardKey, ticketNumber);
    }

    public BoardTicket proxyCreateTicket(
            int partnerId,
            String boardKey,
            Integer laneId,
            String title,
            String description,
            TicketPriority priority,
            LocalDate dueDate,
            UUID remoteMemberId) {
        return ticketProxy.proxyCreateTicket(
                partnerId, boardKey, laneId, title, description, priority, dueDate, remoteMemberId);
    }

    public BoardTicket proxyUpdateTicket(
            int partnerId,
            String boardKey,
            int ticketNumber,
            String title,
            String description,
            Integer assignedMemberId,
            TicketPriority priority,
            LocalDate dueDate,
            UUID remoteMemberUid,
            String displayName) {
        return ticketProxy.proxyUpdateTicket(
                partnerId,
                boardKey,
                ticketNumber,
                title,
                description,
                assignedMemberId,
                priority,
                dueDate,
                remoteMemberUid,
                displayName);
    }

    public void proxyDeleteTicket(int partnerId, String boardKey, int ticketNumber) {
        ticketProxy.proxyDeleteTicket(partnerId, boardKey, ticketNumber);
    }

    public BoardTicket proxyMoveTicket(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int toLaneId,
            int position,
            UUID remoteMemberUid,
            String displayName) {
        return ticketProxy.proxyMoveTicket(
                partnerId, boardKey, ticketNumber, toLaneId, position, remoteMemberUid, displayName);
    }

    public void proxyReorderTickets(int partnerId, String boardKey, int laneId, List<Integer> orderedIds) {
        ticketProxy.proxyReorderTickets(partnerId, boardKey, laneId, orderedIds);
    }

    public List<BoardComment> proxyGetComments(int partnerId, String boardKey, int ticketNumber) {
        return ticketDetailProxy.proxyGetComments(partnerId, boardKey, ticketNumber);
    }

    public BoardComment proxyAddComment(
            int partnerId,
            String boardKey,
            int ticketNumber,
            Integer parentId,
            String content,
            UUID remoteMemberId,
            String displayName) {
        return ticketDetailProxy.proxyAddComment(
                partnerId, boardKey, ticketNumber, parentId, content, remoteMemberId, displayName);
    }

    public List<BoardChecklistItem> proxyGetChecklist(int partnerId, String boardKey, int ticketNumber) {
        return ticketDetailProxy.proxyGetChecklist(partnerId, boardKey, ticketNumber);
    }

    public BoardChecklistItem proxyAddChecklistItem(
            int partnerId, String boardKey, int ticketNumber, String title, UUID remoteMemberUid, String displayName) {
        return ticketDetailProxy.proxyAddChecklistItem(
                partnerId, boardKey, ticketNumber, title, remoteMemberUid, displayName);
    }

    public void proxyUpdateChecklistItem(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int itemId,
            String title,
            boolean checked,
            UUID remoteMemberUid,
            String displayName) {
        ticketDetailProxy.proxyUpdateChecklistItem(
                partnerId, boardKey, ticketNumber, itemId, title, checked, remoteMemberUid, displayName);
    }

    public void proxyDeleteChecklistItem(
            int partnerId, String boardKey, int ticketNumber, int itemId, UUID remoteMemberUid, String displayName) {
        ticketDetailProxy.proxyDeleteChecklistItem(
                partnerId, boardKey, ticketNumber, itemId, remoteMemberUid, displayName);
    }

    public List<BoardTicketLink> proxyGetLinks(int partnerId, String boardKey, int ticketNumber) {
        return ticketDetailProxy.proxyGetLinks(partnerId, boardKey, ticketNumber);
    }

    public void proxyCreateLink(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int linkedTicketNumber,
            LinkType linkType,
            UUID remoteMemberUid,
            String displayName) {
        ticketDetailProxy.proxyCreateLink(
                partnerId, boardKey, ticketNumber, linkedTicketNumber, linkType, remoteMemberUid, displayName);
    }

    public void proxyDeleteLink(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int linkedTicketNumber,
            UUID remoteMemberUid,
            String displayName) {
        ticketDetailProxy.proxyDeleteLink(
                partnerId, boardKey, ticketNumber, linkedTicketNumber, remoteMemberUid, displayName);
    }

    public List<BoardLabel> proxyGetTicketLabels(int partnerId, String boardKey, int ticketNumber) {
        return ticketDetailProxy.proxyGetTicketLabels(partnerId, boardKey, ticketNumber);
    }

    public List<BoardLabel> proxyAddTicketLabel(
            int partnerId, String boardKey, int ticketNumber, int labelId, UUID remoteMemberId, String displayName) {
        return ticketDetailProxy.proxyAddTicketLabel(
                partnerId, boardKey, ticketNumber, labelId, remoteMemberId, displayName);
    }

    public void proxyRemoveTicketLabel(
            int partnerId, String boardKey, int ticketNumber, int labelId, UUID remoteMemberId, String displayName) {
        ticketDetailProxy.proxyRemoveTicketLabel(
                partnerId, boardKey, ticketNumber, labelId, remoteMemberId, displayName);
    }

    public FederatedWatcherData proxyGetWatchers(int partnerId, String boardKey, int ticketNumber) {
        return ticketDetailProxy.proxyGetWatchers(partnerId, boardKey, ticketNumber);
    }

    public void proxyWatchTicket(int partnerId, String boardKey, int ticketNumber, UUID remoteMemberId) {
        ticketDetailProxy.proxyWatchTicket(partnerId, boardKey, ticketNumber, remoteMemberId);
    }

    public void proxyUnwatchTicket(int partnerId, String boardKey, int ticketNumber, UUID remoteMemberId) {
        ticketDetailProxy.proxyUnwatchTicket(partnerId, boardKey, ticketNumber, remoteMemberId);
    }

    public List<BoardTicketAttachment> proxyGetAttachments(int partnerId, String boardKey, int ticketNumber) {
        return ticketDetailProxy.proxyGetAttachments(partnerId, boardKey, ticketNumber);
    }

    public Optional<BoardShareMode> getEffectiveShareMode(int partnerId, int boardId) {
        return accessService.getEffectiveShareMode(partnerId, boardId);
    }

    public boolean passesLocalViewOverride(int partnerId, UUID remoteBoardUid, int memberId) {
        return accessService.passesLocalViewOverride(partnerId, remoteBoardUid, memberId);
    }

    public boolean passesLocalEditOverride(int partnerId, UUID remoteBoardUid, int memberId) {
        return accessService.passesLocalEditOverride(partnerId, remoteBoardUid, memberId);
    }

    public boolean canView(int partnerId, UUID remoteBoardUid, int boardId, int memberId) {
        return accessService.canView(partnerId, remoteBoardUid, boardId, memberId);
    }

    public boolean canWrite(int partnerId, UUID remoteBoardUid, int boardId, int memberId) {
        return accessService.canWrite(partnerId, remoteBoardUid, boardId, memberId);
    }

    public void setLocalViewOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        accessService.setLocalViewOverride(partnerId, remoteBoardUid, access);
    }

    public void setLocalEditOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        accessService.setLocalEditOverride(partnerId, remoteBoardUid, access);
    }

    public AccessData getLocalViewOverride(int partnerId, UUID remoteBoardUid) {
        return accessService.getLocalViewOverride(partnerId, remoteBoardUid);
    }

    public AccessData getLocalEditOverride(int partnerId, UUID remoteBoardUid) {
        return accessService.getLocalEditOverride(partnerId, remoteBoardUid);
    }

    public FederationBoardBookmark createBookmark(
            int memberId, int partnerId, UUID remoteBoardUid, String name, String shortKey, BoardShareMode shareMode) {
        return federatedBoardService.createBookmark(memberId, partnerId, remoteBoardUid, name, shortKey, shareMode);
    }

    public void deleteBookmark(int bookmarkId) {
        federatedBoardService.deleteBookmark(bookmarkId);
    }

    public void deleteBookmarkByBoard(int memberId, int partnerId, UUID remoteBoardUid) {
        federatedBoardService.deleteBookmarkByBoard(memberId, partnerId, remoteBoardUid);
    }

    public List<FederationBoardBookmark> findBookmarks(int memberId) {
        return federatedBoardService.findBookmarks(memberId);
    }

    /**
     * Called via webhook when the owning station renames a board.
     * Updates all bookmarks for this board.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @param newName        the new board name
     * @param newShortKey    the new board short key
     */
    public void onBoardRenamed(int partnerId, UUID remoteBoardUid, String newName, String newShortKey) {
        federatedBoardRepository.updateBookmarkName(partnerId, remoteBoardUid, newName, newShortKey);
    }

    /**
     * Called via webhook when the owning station unshares a board.
     * Deletes all bookmarks for this board.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     */
    public void onBoardUnshared(int partnerId, UUID remoteBoardUid) {
        federatedBoardRepository.deleteBookmarksByBoard(partnerId, remoteBoardUid);
    }

    /**
     * Called via webhook when the share mode changes.
     *
     * @param partnerId      the partner record id
     * @param remoteBoardUid the board uid on the partner station
     * @param newMode        the new share mode
     */
    public void onShareModeChanged(int partnerId, UUID remoteBoardUid, BoardShareMode newMode) {
        federatedBoardRepository.updateBookmarkShareMode(partnerId, remoteBoardUid, newMode);
    }

    /**
     * Resolves a boardKey to a remote board UUID for the given partner.
     * For local partners, resolves via DB. For remote partners, returns null
     * (the remote station handles access control).
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the board uid or {@code null}
     */
    public UUID resolveFederatedBoardUid(int partnerId, String boardKey) {
        return locator.resolveFederatedBoardUid(partnerId, boardKey);
    }

    /**
     * Resolves a boardKey to the full Board entity on the partner station.
     * For local partners, resolves via DB. For remote partners, returns null
     * (the remote station handles access control).
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the board or {@code null}
     */
    public Board resolveFederatedBoard(int partnerId, String boardKey) {
        return locator.resolveFederatedBoard(partnerId, boardKey);
    }

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

    public record FederatedWatcherData(List<Integer> local, List<Object> federated) {}

    public record RemoteDiscoveredBoard(
            String uid,
            String name,
            String shortKey,
            String description,
            BoardShareMode shareMode,
            StationUserType requiredUserType) {}
}
