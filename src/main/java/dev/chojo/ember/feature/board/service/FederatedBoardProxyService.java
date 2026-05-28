/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

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
import dev.chojo.ember.feature.board.entity.BoardTicketFederatedWatcher;
import dev.chojo.ember.feature.board.entity.BoardTicketHistory;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.board.entity.BoardTicketTransition;
import dev.chojo.ember.feature.board.entity.FederationBoardBookmark;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.repository.FederatedBoardRepository;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Partner station service that discovers federated boards, proxies requests to owning stations,
 * and enforces local access overrides.
 * Local partners (same instance) use direct DB access; remote partners use HTTP.
 */
@Singleton
public class FederatedBoardProxyService {
    private static final Logger log = LoggerFactory.getLogger(FederatedBoardProxyService.class);

    private final FederatedBoardService federatedBoardService;
    private final FederatedBoardRepository federatedBoardRepository;
    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final FederationHttpClient httpClient;
    private final StationRepository stationRepository;
    private final StationMemberService memberService;
    private final MemberGroupService groupService;
    private final UserTagService tagService;
    private final JsonMapper mapper;

    @Inject
    public FederatedBoardProxyService(
            FederatedBoardService federatedBoardService,
            FederatedBoardRepository federatedBoardRepository,
            BoardService boardService,
            BoardTicketService ticketService,
            FederationService federationService,
            FederationRepository federationRepository,
            FederationHttpClient httpClient,
            StationRepository stationRepository,
            StationMemberService memberService,
            MemberGroupService groupService,
            UserTagService tagService) {
        this.federatedBoardService = federatedBoardService;
        this.federatedBoardRepository = federatedBoardRepository;
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
        this.memberService = memberService;
        this.groupService = groupService;
        this.tagService = tagService;
        this.mapper = httpClient.getMapper();
    }

    // -- Discovery --

    /**
     * Discovers all boards shared with the local station from all active partners.
     * Returns board info with partner station name and share mode.
     */
    public List<DiscoveredBoard> discoverBoards(int stationId) {
        var futures = new ArrayList<CompletableFuture<List<DiscoveredBoard>>>();
        for (var partner : federationService.findPartners(stationId)) {
            if (partner.status() != FederationPartner.FederationStatus.ACTIVE) continue;
            if (!federationService.hasCapability(partner.id(), CapabilityType.BOARD_SHARE, Direction.IMPORT)) continue;

            futures.add(CompletableFuture.supplyAsync(() -> {
                if (partner.isRemote()) {
                    return discoverBoardsViaHttp(stationId, partner);
                } else {
                    return discoverBoardsDirect(partner);
                }
            }));
        }
        return collectResults(futures);
    }

    private List<DiscoveredBoard> discoverBoardsDirect(FederationPartner partner) {
        var boardIds = federatedBoardService.findSharedBoardIds(partner.id());
        return boardIds.stream()
                .map(boardId -> boardService
                        .findById(boardId)
                        .map(board -> {
                            var mode = federatedBoardService
                                    .getShareMode(boardId, partner.id())
                                    .orElse(BoardShareMode.READ_ONLY);
                            return new DiscoveredBoard(
                                    partner.id(),
                                    partner.partnerStationId().toString(),
                                    board.id(),
                                    board.name(),
                                    board.shortKey(),
                                    board.description(),
                                    mode,
                                    partnerStationName(partner));
                        })
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<DiscoveredBoard> discoverBoardsViaHttp(int localStationId, FederationPartner partner) {
        try {
            var remoteBoards = httpClient.signedGetList(
                    partner.remoteHost(), "/remote/boards", localStationId, getPrivateKey(localStationId));
            return remoteBoards.stream()
                    .map(m -> new DiscoveredBoard(
                            partner.id(),
                            partner.partnerStationId().toString(),
                            ((Number) m.get("id")).intValue(),
                            (String) m.get("name"),
                            (String) m.get("shortKey"),
                            (String) m.get("description"),
                            BoardShareMode.valueOf((String) m.get("shareMode")),
                            partnerStationName(partner)))
                    .toList();
        } catch (Exception e) {
            log.error("Failed to discover boards from partner {}", partner.id(), e);
            return List.of();
        }
    }

    // -- Read Proxy Methods --

    public FederatedBoardDetail proxyGetBoard(int partnerId, int boardId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGet(partner, "/remote/boards/" + boardId, FederatedBoardDetail.class);
        }
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        var mode = getEffectiveShareMode(partnerId, boardId).orElse(BoardShareMode.READ_ONLY);
        String stationName = stationRepository
                .findById(board.stationId())
                .map(Station::name)
                .orElse("Station #" + board.stationId());
        return new FederatedBoardDetail(board, mode.name(), stationName);
    }

    public List<BoardLane> proxyGetLanes(int partnerId, int boardId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardId + "/lanes", BoardLane.class);
        }
        return boardService.findLanes(boardId);
    }

    public List<BoardLabel> proxyGetLabels(int partnerId, int boardId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardId + "/labels", BoardLabel.class);
        }
        return boardService.findLabels(boardId);
    }

    public List<BoardField> proxyGetFields(int partnerId, int boardId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardId + "/fields", BoardField.class);
        }
        return boardService.findFields(boardId);
    }

    public List<BoardTicket> proxyListTickets(int partnerId, int boardId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardId + "/tickets", BoardTicket.class);
        }
        return ticketService.findByBoard(boardId);
    }

    public List<BoardTicket> proxySearchTickets(int partnerId, int boardId, String query) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            String path = "/remote/boards/" + boardId + "/tickets/search";
            if (query != null && !query.isBlank()) {
                path += "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            }
            return remoteGetList(partner, path, BoardTicket.class);
        }
        if (query == null || query.isBlank()) {
            return ticketService.findByBoard(boardId);
        }
        return ticketService.search(boardId, query);
    }

    public BoardTicket proxyGetTicket(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGet(partner, "/remote/boards/" + boardId + "/tickets/" + ticketId, BoardTicket.class);
        }
        return ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
    }

    public List<BoardComment> proxyGetComments(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner, "/remote/boards/" + boardId + "/tickets/" + ticketId + "/comments", BoardComment.class);
        }
        return ticketService.findComments(ticketId);
    }

    public List<BoardChecklistItem> proxyGetChecklist(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardId + "/tickets/" + ticketId + "/checklist",
                    BoardChecklistItem.class);
        }
        return ticketService.findChecklistItems(ticketId);
    }

    public List<BoardTicketLink> proxyGetLinks(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner, "/remote/boards/" + boardId + "/tickets/" + ticketId + "/links", BoardTicketLink.class);
        }
        return ticketService.findLinks(ticketId);
    }

    public List<BoardLabel> proxyGetTicketLabels(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner, "/remote/boards/" + boardId + "/tickets/" + ticketId + "/labels", BoardLabel.class);
        }
        return boardService.findLabelsForTicket(ticketId);
    }

    public List<BoardTicketTransition> proxyGetTransitions(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardId + "/tickets/" + ticketId + "/transitions",
                    BoardTicketTransition.class);
        }
        return ticketService.findTransitions(ticketId);
    }

    public List<BoardTicketHistory> proxyGetHistory(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardId + "/tickets/" + ticketId + "/history",
                    BoardTicketHistory.class);
        }
        return ticketService.findHistory(ticketId);
    }

    public List<BoardTicketAttachment> proxyGetAttachments(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardId + "/tickets/" + ticketId + "/attachments",
                    BoardTicketAttachment.class);
        }
        return ticketService.findAttachments(ticketId);
    }

    public FederatedWatcherData proxyGetWatchers(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGet(
                    partner,
                    "/remote/boards/" + boardId + "/tickets/" + ticketId + "/watchers",
                    FederatedWatcherData.class);
        }
        var localWatchers = ticketService.findWatchers(ticketId);
        var federatedWatchers = federatedBoardService.findFederatedWatchers(ticketId);
        return new FederatedWatcherData(localWatchers, federatedWatchers);
    }

    // -- Write Proxy Methods --

    public BoardTicket proxyCreateTicket(
            int partnerId,
            int boardId,
            Integer laneId,
            String title,
            String description,
            String priority,
            String dueDate,
            String remoteMemberId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = Map.of(
                    "remoteMemberId", remoteMemberId,
                    "laneId", laneId != null ? laneId : "",
                    "title", title != null ? title : "",
                    "description", description != null ? description : "",
                    "priority", priority != null ? priority : "",
                    "dueDate", dueDate != null ? dueDate : "");
            return remotePost(partner, "/remote/boards/" + boardId + "/tickets", body, BoardTicket.class);
        }
        int effectiveLaneId = laneId != null
                ? laneId
                : boardService.findLanes(boardId).getFirst().id();
        var ticket = ticketService.createTicket(
                boardId,
                effectiveLaneId,
                title,
                description,
                null,
                priority != null ? TicketPriority.valueOf(priority) : TicketPriority.MEDIUM,
                dueDate != null ? LocalDate.parse(dueDate) : null,
                0);
        federatedBoardService.setFederatedCreator(ticket.id(), partnerId, remoteMemberId);
        return ticket;
    }

    public BoardTicket proxyUpdateTicket(
            int partnerId,
            int boardId,
            int ticketId,
            String title,
            String description,
            Integer assignedMemberId,
            String priority,
            String dueDate) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new java.util.HashMap<String, Object>();
            if (title != null) body.put("title", title);
            if (description != null) body.put("description", description);
            if (assignedMemberId != null) body.put("assignedMemberId", assignedMemberId);
            if (priority != null) body.put("priority", priority);
            if (dueDate != null) body.put("dueDate", dueDate);
            return remotePut(partner, "/remote/boards/" + boardId + "/tickets/" + ticketId, body, BoardTicket.class);
        }
        ticketService.updateTicket(
                ticketId,
                title,
                description,
                assignedMemberId,
                priority != null ? TicketPriority.valueOf(priority) : null,
                dueDate != null ? LocalDate.parse(dueDate) : null,
                0);
        return ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
    }

    public void proxyDeleteTicket(int partnerId, int boardId, int ticketId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            remoteDelete(partner, "/remote/boards/" + boardId + "/tickets/" + ticketId);
            return;
        }
        ticketService.deleteTicket(ticketId);
    }

    public BoardTicket proxyMoveTicket(int partnerId, int boardId, int ticketId, int toLaneId, int position) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = Map.of("toLaneId", toLaneId, "position", position);
            return remotePut(
                    partner, "/remote/boards/" + boardId + "/tickets/" + ticketId + "/move", body, BoardTicket.class);
        }
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        ticketService.moveTicket(ticketId, ticket.laneId(), toLaneId, position, 0);
        return ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
    }

    public void proxyReorderTickets(int partnerId, int boardId, int laneId, List<Integer> orderedIds) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = Map.of("laneId", laneId, "orderedIds", orderedIds);
            remotePut(partner, "/remote/boards/" + boardId + "/tickets/0/reorder", body);
            return;
        }
        ticketService.reorderTickets(laneId, orderedIds);
    }

    public BoardComment proxyAddComment(
            int partnerId, int boardId, int ticketId, Integer parentId, String content, String remoteMemberId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = Map.of(
                    "remoteMemberId", remoteMemberId,
                    "parentId", parentId != null ? parentId : "",
                    "content", content != null ? content : "");
            return remotePost(
                    partner,
                    "/remote/boards/" + boardId + "/tickets/" + ticketId + "/comments",
                    body,
                    BoardComment.class);
        }
        var comment = ticketService.createComment(ticketId, parentId, 0, content);
        federatedBoardService.setFederatedCommentAuthor(comment.id(), partnerId, remoteMemberId);
        return comment;
    }

    public BoardChecklistItem proxyAddChecklistItem(int partnerId, int boardId, int ticketId, String title) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = Map.of("title", title);
            return remotePost(
                    partner,
                    "/remote/boards/" + boardId + "/tickets/" + ticketId + "/checklist",
                    body,
                    BoardChecklistItem.class);
        }
        return ticketService.addChecklistItem(ticketId, title, 0);
    }

    public void proxyUpdateChecklistItem(
            int partnerId, int boardId, int ticketId, int itemId, String title, boolean checked) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = Map.of("title", title, "checked", checked);
            remotePut(partner, "/remote/boards/" + boardId + "/tickets/" + ticketId + "/checklist/" + itemId, body);
            return;
        }
        ticketService.updateChecklistItem(itemId, ticketId, title, checked, 0);
    }

    public void proxyDeleteChecklistItem(int partnerId, int boardId, int ticketId, int itemId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            remoteDelete(partner, "/remote/boards/" + boardId + "/tickets/" + ticketId + "/checklist/" + itemId);
            return;
        }
        ticketService.deleteChecklistItem(itemId, ticketId, 0);
    }

    public List<BoardLabel> proxyAddTicketLabel(int partnerId, int boardId, int ticketId, int labelId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remotePostList(
                    partner,
                    "/remote/boards/" + boardId + "/tickets/" + ticketId + "/labels/" + labelId,
                    Map.of(),
                    BoardLabel.class);
        }
        boardService.addLabelToTicket(ticketId, labelId);
        return boardService.findLabelsForTicket(ticketId);
    }

    public void proxyRemoveTicketLabel(int partnerId, int boardId, int ticketId, int labelId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            remoteDelete(partner, "/remote/boards/" + boardId + "/tickets/" + ticketId + "/labels/" + labelId);
            return;
        }
        boardService.removeLabelFromTicket(ticketId, labelId);
    }

    public BoardLabel proxyCreateLabel(int partnerId, int boardId, String name, String color) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = Map.of("name", name, "color", color != null ? color : "#6b7280");
            return remotePost(partner, "/remote/boards/" + boardId + "/labels", body, BoardLabel.class);
        }
        return boardService.createLabel(boardId, name, color != null ? color : "#6b7280");
    }

    public void proxyWatchTicket(int partnerId, int boardId, int ticketId, String remoteMemberId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = Map.of("remoteMemberId", remoteMemberId);
            remotePost(partner, "/remote/boards/" + boardId + "/tickets/" + ticketId + "/watch", body);
            return;
        }
        federatedBoardService.addFederatedWatcher(ticketId, partnerId, remoteMemberId);
    }

    public void proxyUnwatchTicket(int partnerId, int boardId, int ticketId, String remoteMemberId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            remoteDelete(partner, "/remote/boards/" + boardId + "/tickets/" + ticketId + "/watch");
            return;
        }
        federatedBoardService.removeFederatedWatcher(ticketId, partnerId, remoteMemberId);
    }

    // -- Access Control --

    /**
     * Returns the effective share mode, which is the ceiling of the owning station's mode
     * and the local override. Local overrides can only restrict, not escalate.
     * For local partners, reads the share mode directly. For remote, it was cached in the bookmark.
     */
    /**
     * Returns the effective share mode. The share target is stored on the owning station's
     * partner record. When queried from the partner station, the local partner record ID
     * differs from the owning station's partner record ID. We try both the direct lookup
     * and the reverse lookup (finding the owning station's partner record that points to us).
     */
    public Optional<BoardShareMode> getEffectiveShareMode(int partnerId, int boardId) {
        // Direct lookup — works if partnerId is the owning station's partner record
        var mode = federatedBoardService.getShareMode(boardId, partnerId);
        if (mode.isPresent()) return mode;

        // Reverse lookup — partnerId is OUR partner record, we need the owning station's record.
        // The owning station's record has partner_station_id = our station's UUID.
        // We can find it by looking at which partner records the board is shared with.
        var partner = federationRepository.findPartnerById(partnerId).orElse(null);
        if (partner == null) return Optional.empty();

        // Our station UUID as seen from the owning station
        var ourStationUid = stationRepository
                .findById(partner.stationId())
                .map(Station::uid)
                .orElse(null);
        if (ourStationUid == null) return Optional.empty();

        // Find the owning station's partner record that points to our station
        var board = boardService.findById(boardId).orElse(null);
        if (board == null) return Optional.empty();

        var owningPartner = federationRepository.findPartnerByStationAndRemoteUid(board.stationId(), ourStationUid);
        return owningPartner.flatMap(op -> federatedBoardService.getShareMode(boardId, op.id()));
    }

    /**
     * Checks if a member passes the local view override (if one exists).
     * Returns true if no override is set (all members can view).
     */
    public boolean passesLocalViewOverride(int partnerId, int remoteBoardId, int memberId) {
        if (!federatedBoardRepository.hasLocalViewOverride(partnerId, remoteBoardId)) return true;
        var override = federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardId);
        return matchesAccess(memberId, override);
    }

    /**
     * Checks if a member passes the local edit override (if one exists).
     * Returns true if no override is set.
     */
    public boolean passesLocalEditOverride(int partnerId, int remoteBoardId, int memberId) {
        if (!federatedBoardRepository.hasLocalEditOverride(partnerId, remoteBoardId)) return true;
        var override = federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardId);
        return matchesAccess(memberId, override);
    }

    /**
     * Full access check: share mode must be at least the required level,
     * and the member must pass the local override.
     */
    public boolean canView(int partnerId, int remoteBoardId, int memberId) {
        var mode = getEffectiveShareMode(partnerId, remoteBoardId);
        if (mode.isEmpty()) return false;
        return passesLocalViewOverride(partnerId, remoteBoardId, memberId);
    }

    public boolean canWrite(int partnerId, int remoteBoardId, int memberId) {
        var mode = getEffectiveShareMode(partnerId, remoteBoardId);
        if (mode.isEmpty() || mode.get() != BoardShareMode.FULL) return false;
        return passesLocalViewOverride(partnerId, remoteBoardId, memberId)
                && passesLocalEditOverride(partnerId, remoteBoardId, memberId);
    }

    // -- Local Overrides --

    public void setLocalViewOverride(int partnerId, int remoteBoardId, AccessData access) {
        federatedBoardRepository.setLocalViewOverride(partnerId, remoteBoardId, access);
    }

    public void setLocalEditOverride(int partnerId, int remoteBoardId, AccessData access) {
        federatedBoardRepository.setLocalEditOverride(partnerId, remoteBoardId, access);
    }

    public AccessData getLocalViewOverride(int partnerId, int remoteBoardId) {
        return federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardId);
    }

    public AccessData getLocalEditOverride(int partnerId, int remoteBoardId) {
        return federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardId);
    }

    // -- Bookmarks --

    public FederationBoardBookmark createBookmark(
            int memberId, int partnerId, int remoteBoardId, String name, String shortKey, BoardShareMode shareMode) {
        return federatedBoardService.createBookmark(memberId, partnerId, remoteBoardId, name, shortKey, shareMode);
    }

    public void deleteBookmark(int bookmarkId) {
        federatedBoardService.deleteBookmark(bookmarkId);
    }

    public void deleteBookmarkByBoard(int memberId, int partnerId, int remoteBoardId) {
        federatedBoardService.deleteBookmarkByBoard(memberId, partnerId, remoteBoardId);
    }

    public List<FederationBoardBookmark> findBookmarks(int memberId) {
        return federatedBoardService.findBookmarks(memberId);
    }

    /**
     * Called via webhook when the owning station renames a board.
     * Updates all bookmarks for this board.
     */
    public void onBoardRenamed(int partnerId, int remoteBoardId, String newName, String newShortKey) {
        federatedBoardRepository.updateBookmarkName(partnerId, remoteBoardId, newName, newShortKey);
    }

    /**
     * Called via webhook when the owning station unshares a board.
     * Deletes all bookmarks for this board.
     */
    public void onBoardUnshared(int partnerId, int remoteBoardId) {
        federatedBoardRepository.deleteBookmarksByBoard(partnerId, remoteBoardId);
    }

    /**
     * Called via webhook when the share mode changes.
     */
    public void onShareModeChanged(int partnerId, int remoteBoardId, BoardShareMode newMode) {
        federatedBoardRepository.updateBookmarkShareMode(partnerId, remoteBoardId, newMode);
    }

    // -- Helpers --

    private FederationPartner findPartner(int partnerId) {
        return federationRepository
                .findPartnerById(partnerId)
                .orElseThrow(() -> new NotFoundResponse("Partner not found: " + partnerId));
    }

    private <T> T remoteGet(FederationPartner partner, String path, Class<T> type) {
        String json = httpClient.signedGetJson(
                partner.remoteHost(), path, partner.stationId(), getPrivateKey(partner.stationId()));
        if (json == null) throw new NotFoundResponse("Empty response from remote partner");
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to parse remote response for {}", path, e);
            throw new NotFoundResponse("Failed to parse remote response");
        }
    }

    private <T> List<T> remoteGetList(FederationPartner partner, String path, Class<T> elementType) {
        String json = httpClient.signedGetJson(
                partner.remoteHost(), path, partner.stationId(), getPrivateKey(partner.stationId()));
        if (json == null) return List.of();
        try {
            var type = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed to parse remote list response for {}", path, e);
            return List.of();
        }
    }

    private <T> T remotePost(FederationPartner partner, String path, Object body, Class<T> type) {
        try {
            String jsonBody = mapper.writeValueAsString(body);
            String json = httpClient.signedPostJson(
                    partner.remoteHost(), path, jsonBody, partner.stationId(), getPrivateKey(partner.stationId()));
            if (json == null || json.isBlank()) throw new NotFoundResponse("Empty response from remote partner");
            return mapper.readValue(json, type);
        } catch (NotFoundResponse e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed remote POST for {}", path, e);
            throw new NotFoundResponse("Failed remote POST");
        }
    }

    private <T> List<T> remotePostList(FederationPartner partner, String path, Object body, Class<T> elementType) {
        try {
            String jsonBody = mapper.writeValueAsString(body);
            String json = httpClient.signedPostJson(
                    partner.remoteHost(), path, jsonBody, partner.stationId(), getPrivateKey(partner.stationId()));
            if (json == null || json.isBlank()) return List.of();
            var type = mapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.error("Failed remote POST for {}", path, e);
            return List.of();
        }
    }

    private <T> T remotePut(FederationPartner partner, String path, Object body, Class<T> type) {
        try {
            String jsonBody = mapper.writeValueAsString(body);
            String json = httpClient.signedPutJson(
                    partner.remoteHost(), path, jsonBody, partner.stationId(), getPrivateKey(partner.stationId()));
            if (json == null || json.isBlank()) throw new NotFoundResponse("Empty response from remote partner");
            return mapper.readValue(json, type);
        } catch (NotFoundResponse e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed remote PUT for {}", path, e);
            throw new NotFoundResponse("Failed remote PUT");
        }
    }

    private void remotePut(FederationPartner partner, String path, Object body) {
        try {
            String jsonBody = mapper.writeValueAsString(body);
            httpClient.signedPutJson(
                    partner.remoteHost(), path, jsonBody, partner.stationId(), getPrivateKey(partner.stationId()));
        } catch (Exception e) {
            log.error("Failed remote PUT for {}", path, e);
        }
    }

    private void remotePost(FederationPartner partner, String path, Object body) {
        try {
            String jsonBody = mapper.writeValueAsString(body);
            httpClient.signedPostJson(
                    partner.remoteHost(), path, jsonBody, partner.stationId(), getPrivateKey(partner.stationId()));
        } catch (Exception e) {
            log.error("Failed remote POST for {}", path, e);
        }
    }

    private void remoteDelete(FederationPartner partner, String path) {
        httpClient.signedDeleteRequest(
                partner.remoteHost(), path, partner.stationId(), getPrivateKey(partner.stationId()));
    }

    private boolean matchesAccess(int memberId, AccessData access) {
        if (!access.roleIds().isEmpty()) {
            var memberRoles =
                    memberService.findRoles(memberId).stream().map(Role::id).toList();
            if (memberRoles.stream().anyMatch(access.roleIds()::contains)) return true;
        }
        if (!access.groupIds().isEmpty()) {
            var memberGroupIds = groupService.findGroupsForMember(memberId).stream()
                    .map(MemberGroup::id)
                    .toList();
            if (memberGroupIds.stream().anyMatch(access.groupIds()::contains)) return true;
        }
        if (!access.tagIds().isEmpty()) {
            var memberTagIds = tagService.findTagsForMember(memberId).stream()
                    .map(UserTag::id)
                    .toList();
            if (memberTagIds.stream().anyMatch(access.tagIds()::contains)) return true;
        }
        return false;
    }

    private String partnerStationName(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::name)
                .orElse("Partner #" + partner.id());
    }

    private String getPrivateKey(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::federationPrivateKey)
                .orElse(null);
    }

    private <T> List<T> collectResults(List<CompletableFuture<List<T>>> futures) {
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel federation board discovery", e);
        }
        var result = new ArrayList<T>();
        for (var future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting federation board results", e);
            }
        }
        return result;
    }

    public record DiscoveredBoard(
            int partnerId,
            String partnerStationUid,
            int remoteBoardId,
            String name,
            String shortKey,
            String description,
            BoardShareMode shareMode,
            String partnerStationName) {}

    public record FederatedBoardDetail(Board board, String shareMode, String stationName) {}

    public record FederatedWatcherData(List<Integer> local, List<BoardTicketFederatedWatcher> federated) {}
}
