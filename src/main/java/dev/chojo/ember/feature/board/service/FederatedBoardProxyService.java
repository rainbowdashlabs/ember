/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.roles.StationPermission;
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
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupService groupService;
    private final UserTagService tagService;
    private final EventFederationRepository eventFederationRepository;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;

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
            StationMemberRepository stationMemberRepository,
            MemberGroupService groupService,
            UserTagService tagService,
            EventFederationRepository eventFederationRepository,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory) {
        this.federatedBoardService = federatedBoardService;
        this.federatedBoardRepository = federatedBoardRepository;
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
        this.memberService = memberService;
        this.stationMemberRepository = stationMemberRepository;
        this.groupService = groupService;
        this.tagService = tagService;
        this.eventFederationRepository = eventFederationRepository;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
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
        // Direct lookup: boards shared with this partner record
        var boardIds = new ArrayList<>(federatedBoardService.findSharedBoardIds(partner.id()));

        // Reverse lookup: our partner record may differ from the owning station's record.
        // Find the owning station's partner record that points to our station.
        var ourStationUid = stationRepository
                .findById(partner.stationId())
                .map(Station::uid)
                .orElse(null);
        if (ourStationUid != null) {
            var owningStation =
                    stationRepository.findByUid(partner.partnerStationId()).orElse(null);
            if (owningStation != null) {
                var owningPartner =
                        federationRepository.findPartnerByStationAndRemoteUid(owningStation.id(), ourStationUid);
                owningPartner.ifPresent(op -> {
                    for (var id : federatedBoardService.findSharedBoardIds(op.id())) {
                        if (!boardIds.contains(id)) boardIds.add(id);
                    }
                });
            }
        }

        return boardIds.stream()
                .map(boardId -> boardService
                        .findById(boardId)
                        .map(board -> {
                            var mode =
                                    getEffectiveShareMode(partner.id(), boardId).orElse(BoardShareMode.READ_ONLY);
                            var requiredRole = federatedBoardService
                                    .getRequiredRole(boardId, partner.id())
                                    .orElse("USER");
                            return new DiscoveredBoard(
                                    partner.id(),
                                    partner.partnerStationId().toString(),
                                    board.uid(),
                                    board.name(),
                                    board.shortKey(),
                                    board.description(),
                                    mode,
                                    partnerStationName(partner),
                                    requiredRole);
                        })
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<DiscoveredBoard> discoverBoardsViaHttp(int localStationId, FederationPartner partner) {
        var remoteBoards = httpClient.getList(
                partner.remoteHost(),
                "/remote/boards",
                localStationId,
                getPrivateKey(localStationId),
                RemoteDiscoveredBoard.class);
        return remoteBoards.stream()
                .map(b -> new DiscoveredBoard(
                        partner.id(),
                        partner.partnerStationId().toString(),
                        UUID.fromString(b.uid()),
                        b.name(),
                        b.shortKey(),
                        b.description(),
                        BoardShareMode.valueOf(b.shareMode()),
                        partnerStationName(partner),
                        b.requiredRole() != null ? b.requiredRole() : "USER"))
                .toList();
    }

    // -- Read Proxy Methods --

    public FederatedBoardDetail proxyGetBoard(int partnerId, String boardKey) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGet(partner, "/remote/boards/" + boardKey, FederatedBoardDetail.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        var mode = getEffectiveShareMode(partnerId, boardId).orElse(BoardShareMode.READ_ONLY);
        String stationName = stationRepository
                .findById(board.stationId())
                .map(Station::name)
                .orElse("Station #" + board.stationId());
        return FederatedBoardDetail.of(board, mode.name(), stationName, stationRepository);
    }

    public List<MemberCompletion> proxyGetMembers(int partnerId, String boardKey) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardKey + "/members", MemberCompletion.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        return stationMemberRepository.findCompletions(board.stationId());
    }

    public List<BoardLane> proxyGetLanes(int partnerId, String boardKey) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardKey + "/lanes", BoardLane.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        return boardService.findLanes(boardId);
    }

    public List<BoardLabel> proxyGetLabels(int partnerId, String boardKey) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardKey + "/labels", BoardLabel.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        return boardService.findLabels(boardId);
    }

    public List<TicketLabelMapping> proxyGetAllTicketLabels(int partnerId, String boardKey) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardKey + "/ticket-labels", TicketLabelMapping.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        return boardService.findAllTicketLabels(boardId);
    }

    public List<BoardField> proxyGetFields(int partnerId, String boardKey) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardKey + "/fields", BoardField.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        return boardService.findFields(boardId);
    }

    public List<TicketSummary> proxyListTickets(int partnerId, String boardKey) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(partner, "/remote/boards/" + boardKey + "/tickets", TicketSummary.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        return ticketService.findByBoard(boardId).stream()
                .map(TicketSummary::of)
                .map(t -> t.assignee() != null ? t.withAssignee(memberNameResolver.enrichDisplay(t.assignee())) : t)
                .toList();
    }

    public List<TicketSummary> proxySearchTickets(int partnerId, String boardKey, String query) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            String path = "/remote/boards/" + boardKey + "/tickets/search";
            if (query != null && !query.isBlank()) {
                path += "?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            }
            return remoteGetList(partner, path, TicketSummary.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        List<BoardTicket> tickets = (query == null || query.isBlank())
                ? ticketService.findByBoard(boardId)
                : ticketService.search(boardId, query);
        return tickets.stream()
                .map(TicketSummary::of)
                .map(t -> t.assignee() != null ? t.withAssignee(memberNameResolver.enrichDisplay(t.assignee())) : t)
                .toList();
    }

    public BoardTicket proxyGetTicket(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGet(partner, "/remote/boards/" + boardKey + "/tickets/" + ticketNumber, BoardTicket.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        return enrichTicket(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    public List<BoardComment> proxyGetComments(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/comments",
                    BoardComment.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        return ticketService.findComments(ticketId).stream()
                .map(c -> c.author() != null ? c.withAuthor(memberNameResolver.enrichDisplay(c.author())) : c)
                .toList();
    }

    public List<BoardChecklistItem> proxyGetChecklist(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/checklist",
                    BoardChecklistItem.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        return ticketService.findChecklistItems(ticketId);
    }

    public List<BoardTicketLink> proxyGetLinks(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/links",
                    BoardTicketLink.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        return ticketService.findLinks(ticketId);
    }

    public List<BoardLabel> proxyGetTicketLabels(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner, "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/labels", BoardLabel.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        return boardService.findLabelsForTicket(ticketId);
    }

    public List<BoardTicketTransitionResponse> proxyGetTransitions(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/transitions",
                    BoardTicketTransitionResponse.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        return ticketService.findTransitions(ticketId).stream()
                .map(tr -> {
                    var resolved = memberNameResolver.resolveDisplay(tr.actor());
                    return BoardTicketTransitionResponse.from(tr, resolved.identity(), resolved.name());
                })
                .toList();
    }

    public List<BoardTicketHistoryResponse> proxyGetHistory(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/history",
                    BoardTicketHistoryResponse.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        return ticketService.findHistory(ticketId).stream()
                .map(h -> {
                    var resolved = memberNameResolver.resolveDisplay(h.actor());
                    return BoardTicketHistoryResponse.from(h, resolved.identity(), resolved.name());
                })
                .toList();
    }

    public List<BoardTicketAttachment> proxyGetAttachments(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGetList(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/attachments",
                    BoardTicketAttachment.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        return ticketService.findAttachments(ticketId);
    }

    public FederatedWatcherData proxyGetWatchers(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remoteGet(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/watchers",
                    FederatedWatcherData.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        var localWatchers = ticketService.findWatchers(ticketId);
        return new FederatedWatcherData(localWatchers, List.of());
    }

    private BoardTicket enrichTicket(BoardTicket ticket) {
        MemberIdentity assignee =
                ticket.assignee() != null ? memberNameResolver.enrichDisplay(ticket.assignee()) : null;
        MemberIdentity creator = ticket.creator() != null ? memberNameResolver.enrichDisplay(ticket.creator()) : null;
        return ticket.withIdentities(assignee, creator);
    }

    // -- Write Proxy Methods --

    public BoardTicket proxyCreateTicket(
            int partnerId,
            String boardKey,
            Integer laneId,
            String title,
            String description,
            String priority,
            String dueDate,
            UUID remoteMemberId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new CreateTicketBody(
                    remoteMemberId,
                    laneId != null ? String.valueOf(laneId) : "",
                    title != null ? title : "",
                    description != null ? description : "",
                    priority != null ? priority : "",
                    dueDate != null ? dueDate : "");
            return remotePost(partner, "/remote/boards/" + boardKey + "/tickets", body, BoardTicket.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int effectiveLaneId = laneId != null
                ? laneId
                : boardService.findLanes(boardId).getFirst().id();
        var creatorIdentity = new MemberIdentity(partner.partnerStationId(), remoteMemberId);
        return ticketService.createTicket(
                boardId,
                effectiveLaneId,
                title,
                description,
                null,
                priority != null ? TicketPriority.valueOf(priority) : TicketPriority.MEDIUM,
                dueDate != null ? LocalDate.parse(dueDate) : null,
                creatorIdentity);
    }

    public BoardTicket proxyUpdateTicket(
            int partnerId,
            String boardKey,
            int ticketNumber,
            String title,
            String description,
            Integer assignedMemberId,
            String priority,
            String dueDate,
            UUID remoteMemberUid,
            String displayName) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            if (title != null) body.put("title", title);
            if (description != null) body.put("description", description);
            if (assignedMemberId != null) body.put("assignedMemberId", assignedMemberId);
            if (priority != null) body.put("priority", priority);
            if (dueDate != null) body.put("dueDate", dueDate);
            if (remoteMemberUid != null) body.put("remoteMemberUid", remoteMemberUid.toString());
            if (displayName != null) body.put("displayName", displayName);
            return remotePut(
                    partner, "/remote/boards/" + boardKey + "/tickets/" + ticketNumber, body, BoardTicket.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        MemberIdentity assigneeIdentity =
                assignedMemberId != null ? memberIdentityFactory.local(board.stationId(), assignedMemberId) : null;
        MemberIdentity actorIdentity =
                remoteMemberUid != null ? new MemberIdentity(partner.partnerStationId(), remoteMemberUid) : null;
        if (displayName != null && remoteMemberUid != null) {
            eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        }
        ticketService.updateTicket(
                ticketId,
                title,
                description,
                assigneeIdentity,
                priority != null ? TicketPriority.valueOf(priority) : null,
                dueDate != null ? LocalDate.parse(dueDate) : null,
                actorIdentity);
        return ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
    }

    public void proxyDeleteTicket(int partnerId, String boardKey, int ticketNumber) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            remoteDelete(partner, "/remote/boards/" + boardKey + "/tickets/" + ticketNumber);
            return;
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        ticketService.deleteTicket(ticketId);
    }

    public BoardTicket proxyMoveTicket(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int toLaneId,
            int position,
            UUID remoteMemberUid,
            String displayName) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("toLaneId", toLaneId);
            body.put("position", position);
            if (remoteMemberUid != null) body.put("remoteMemberUid", remoteMemberUid.toString());
            if (displayName != null) body.put("displayName", displayName);
            return remotePut(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/move",
                    body,
                    BoardTicket.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        MemberIdentity actorIdentity =
                remoteMemberUid != null ? new MemberIdentity(partner.partnerStationId(), remoteMemberUid) : null;
        if (displayName != null && remoteMemberUid != null) {
            eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        }
        ticketService.moveTicket(ticketId, ticket.laneId(), toLaneId, position, actorIdentity);
        return ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
    }

    public void proxyReorderTickets(int partnerId, String boardKey, int laneId, List<Integer> orderedIds) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new ReorderBody(laneId, orderedIds);
            remotePut(partner, "/remote/boards/" + boardKey + "/tickets/reorder", body);
            return;
        }
        ticketService.reorderTickets(laneId, orderedIds);
    }

    public BoardComment proxyAddComment(
            int partnerId,
            String boardKey,
            int ticketNumber,
            Integer parentId,
            String content,
            UUID remoteMemberId,
            String displayName) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("remoteMemberId", remoteMemberId);
            body.put("displayName", displayName != null ? displayName : "");
            body.put("parentId", parentId != null ? parentId : "");
            body.put("content", content != null ? content : "");
            return remotePost(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/comments",
                    body,
                    BoardComment.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        var authorIdentity = new MemberIdentity(partner.partnerStationId(), remoteMemberId);
        var comment = ticketService.createComment(ticketId, parentId, authorIdentity, content);
        eventFederationRepository.cacheName(partnerId, remoteMemberId, displayName);
        return comment;
    }

    public BoardChecklistItem proxyAddChecklistItem(
            int partnerId, String boardKey, int ticketNumber, String title, UUID remoteMemberUid, String displayName) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("title", title);
            if (remoteMemberUid != null) body.put("remoteMemberUid", remoteMemberUid.toString());
            if (displayName != null) body.put("displayName", displayName);
            return remotePost(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/checklist",
                    body,
                    BoardChecklistItem.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        if (displayName != null && remoteMemberUid != null) {
            eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        }
        return ticketService.addChecklistItem(ticketId, title, 0);
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
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("title", title);
            body.put("checked", checked);
            if (remoteMemberUid != null) body.put("remoteMemberUid", remoteMemberUid.toString());
            if (displayName != null) body.put("displayName", displayName);
            remotePut(
                    partner, "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/checklist/" + itemId, body);
            return;
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        if (displayName != null && remoteMemberUid != null) {
            eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        }
        ticketService.updateChecklistItem(itemId, ticketId, title, checked, 0);
    }

    public void proxyDeleteChecklistItem(
            int partnerId, String boardKey, int ticketNumber, int itemId, UUID remoteMemberUid, String displayName) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            remoteDelete(partner, "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/checklist/" + itemId);
            return;
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        if (displayName != null && remoteMemberUid != null) {
            eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        }
        ticketService.deleteChecklistItem(itemId, ticketId, 0);
    }

    public List<BoardLabel> proxyAddTicketLabel(
            int partnerId, String boardKey, int ticketNumber, int labelId, UUID remoteMemberId, String displayName) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            return remotePostList(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/labels/" + labelId,
                    new LabelActionBody(remoteMemberId, displayName),
                    BoardLabel.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        boardService.addLabelToTicket(ticketId, labelId);
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        ticketService.logHistory(
                ticketId,
                "LABEL_ADDED",
                label != null ? label.name() : "?",
                new MemberIdentity(partner.partnerStationId(), remoteMemberId));
        if (displayName != null) eventFederationRepository.cacheName(partnerId, remoteMemberId, displayName);
        return boardService.findLabelsForTicket(ticketId);
    }

    public void proxyRemoveTicketLabel(
            int partnerId, String boardKey, int ticketNumber, int labelId, UUID remoteMemberId, String displayName) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            remotePost(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/labels/" + labelId + "/remove",
                    new LabelActionBody(remoteMemberId, displayName));
            return;
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        var label = boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .orElse(null);
        boardService.removeLabelFromTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId,
                "LABEL_REMOVED",
                label != null ? label.name() : "?",
                new MemberIdentity(partner.partnerStationId(), remoteMemberId));
        if (displayName != null) eventFederationRepository.cacheName(partnerId, remoteMemberId, displayName);
    }

    private record LabelActionBody(UUID remoteMemberId, String displayName) {}

    public BoardLabel proxyCreateLabel(int partnerId, String boardKey, String name, String color) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new CreateLabelBody(name, color != null ? color : "#6b7280");
            return remotePost(partner, "/remote/boards/" + boardKey + "/labels", body, BoardLabel.class);
        }
        int boardId = resolveBoardId(boardKey, partner);
        return boardService.createLabel(boardId, name, color != null ? color : "#6b7280");
    }

    public void proxyWatchTicket(int partnerId, String boardKey, int ticketNumber, UUID remoteMemberId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new RemoteMemberBody(remoteMemberId);
            remotePost(partner, "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/watch", body);
            return;
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        var identity = new MemberIdentity(partner.partnerStationId(), remoteMemberId);
        ticketService.addWatcher(ticketId, identity);
    }

    public void proxyUnwatchTicket(int partnerId, String boardKey, int ticketNumber, UUID remoteMemberId) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            remoteDelete(partner, "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/watch");
            return;
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        var identity = new MemberIdentity(partner.partnerStationId(), remoteMemberId);
        ticketService.removeWatcher(ticketId, identity);
    }

    public void proxyCreateLink(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int linkedTicketNumber,
            String linkType,
            UUID remoteMemberUid,
            String displayName) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("linkedTicketNumber", linkedTicketNumber);
            body.put("linkType", linkType);
            if (remoteMemberUid != null) body.put("remoteMemberUid", remoteMemberUid.toString());
            if (displayName != null) body.put("displayName", displayName);
            remotePost(partner, "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/links", body);
            return;
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        int linkedTicketId = resolveTicketId(boardId, linkedTicketNumber);
        var actorIdentity =
                remoteMemberUid != null ? new MemberIdentity(partner.partnerStationId(), remoteMemberUid) : null;
        if (displayName != null && remoteMemberUid != null) {
            eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        }
        ticketService.linkTickets(ticketId, linkedTicketId, LinkType.valueOf(linkType), actorIdentity);
    }

    public void proxyDeleteLink(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int linkedTicketNumber,
            UUID remoteMemberUid,
            String displayName) {
        var partner = findPartner(partnerId);
        if (partner.isRemote()) {
            var body = new java.util.HashMap<String, Object>();
            if (remoteMemberUid != null) body.put("remoteMemberUid", remoteMemberUid.toString());
            if (displayName != null) body.put("displayName", displayName);
            remoteDelete(
                    partner,
                    "/remote/boards/" + boardKey + "/tickets/" + ticketNumber + "/links/" + linkedTicketNumber,
                    body);
            return;
        }
        int boardId = resolveBoardId(boardKey, partner);
        int ticketId = resolveTicketId(boardId, ticketNumber);
        int linkedTicketId = resolveTicketId(boardId, linkedTicketNumber);
        var actorIdentity =
                remoteMemberUid != null ? new MemberIdentity(partner.partnerStationId(), remoteMemberUid) : null;
        if (displayName != null && remoteMemberUid != null) {
            eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        }
        ticketService.unlinkTickets(ticketId, linkedTicketId, actorIdentity);
    }

    // -- Access Control --

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
    public boolean passesLocalViewOverride(int partnerId, UUID remoteBoardUid, int memberId) {
        if (!federatedBoardRepository.hasLocalViewOverride(partnerId, remoteBoardUid)) return true;
        var override = federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardUid);
        return matchesAccess(memberId, override);
    }

    /**
     * Checks if a member passes the local edit override (if one exists).
     * Returns true if no override is set.
     */
    public boolean passesLocalEditOverride(int partnerId, UUID remoteBoardUid, int memberId) {
        if (!federatedBoardRepository.hasLocalEditOverride(partnerId, remoteBoardUid)) return true;
        var override = federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardUid);
        return matchesAccess(memberId, override);
    }

    /**
     * Full view access check.
     * 1. Board must be shared with this partner.
     * 2. If local view override exists → use ONLY the local override (replaces shared requirement).
     * 3. If no local override → use the shared required role from the share target.
     */
    public boolean canView(int partnerId, UUID remoteBoardUid, int boardId, int memberId) {
        var shareInfo = getEffectiveShareInfo(partnerId, boardId);
        if (shareInfo == null) return false;

        // Local override completely replaces the shared requirement
        if (federatedBoardRepository.hasLocalViewOverride(partnerId, remoteBoardUid)) {
            var override = federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardUid);
            return matchesAccess(memberId, override);
        }

        // No local override — use shared required role
        return memberHasRole(memberId, shareInfo.requiredRole());
    }

    /**
     * Full write access check.
     * Share mode must be FULL, then same override logic as view + edit override.
     */
    public boolean canWrite(int partnerId, UUID remoteBoardUid, int boardId, int memberId) {
        var shareInfo = getEffectiveShareInfo(partnerId, boardId);
        if (shareInfo == null || shareInfo.shareMode() != BoardShareMode.FULL) return false;

        // View check first
        if (!canView(partnerId, remoteBoardUid, boardId, memberId)) return false;

        // Edit override (if exists) further restricts who can write
        if (federatedBoardRepository.hasLocalEditOverride(partnerId, remoteBoardUid)) {
            var editOverride = federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardUid);
            return matchesAccess(memberId, editOverride);
        }
        return true;
    }

    private boolean memberHasRole(int memberId, String requiredRoleName) {
        if (requiredRoleName == null || requiredRoleName.equals("USER")) return true; // USER = everyone
        var memberRoles = memberService.findPermissions(memberId).stream()
                .map(Permission::permission)
                .collect(java.util.stream.Collectors.toSet());
        var expanded = StationPermission.expand(memberRoles);
        try {
            return expanded.contains(StationPermission.valueOf(requiredRoleName));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private record ShareInfo(BoardShareMode shareMode, String requiredRole) {}

    private ShareInfo getEffectiveShareInfo(int partnerId, int boardId) {
        var mode = getEffectiveShareMode(partnerId, boardId);
        if (mode.isEmpty()) return null;
        var requiredRole = getSharedRequiredRole(partnerId, boardId);
        return new ShareInfo(mode.get(), requiredRole);
    }

    private String getSharedRequiredRole(int partnerId, int boardId) {
        var partner = federationRepository.findPartnerById(partnerId).orElse(null);
        if (partner == null) return "USER";
        var ourStationUid = stationRepository
                .findById(partner.stationId())
                .map(Station::uid)
                .orElse(null);
        if (ourStationUid == null) return "USER";
        var board = boardService.findById(boardId).orElse(null);
        if (board == null) return "USER";
        var owningPartner = federationRepository.findPartnerByStationAndRemoteUid(board.stationId(), ourStationUid);
        return owningPartner
                .flatMap(op -> federatedBoardService.getRequiredRole(boardId, op.id()))
                .orElse("USER");
    }

    // -- Local Overrides --

    public void setLocalViewOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        federatedBoardRepository.setLocalViewOverride(partnerId, remoteBoardUid, access);
    }

    public void setLocalEditOverride(int partnerId, UUID remoteBoardUid, AccessData access) {
        federatedBoardRepository.setLocalEditOverride(partnerId, remoteBoardUid, access);
    }

    public AccessData getLocalViewOverride(int partnerId, UUID remoteBoardUid) {
        return federatedBoardRepository.findLocalViewOverride(partnerId, remoteBoardUid);
    }

    public AccessData getLocalEditOverride(int partnerId, UUID remoteBoardUid) {
        return federatedBoardRepository.findLocalEditOverride(partnerId, remoteBoardUid);
    }

    // -- Bookmarks --

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
     */
    public void onBoardRenamed(int partnerId, UUID remoteBoardUid, String newName, String newShortKey) {
        federatedBoardRepository.updateBookmarkName(partnerId, remoteBoardUid, newName, newShortKey);
    }

    /**
     * Called via webhook when the owning station unshares a board.
     * Deletes all bookmarks for this board.
     */
    public void onBoardUnshared(int partnerId, UUID remoteBoardUid) {
        federatedBoardRepository.deleteBookmarksByBoard(partnerId, remoteBoardUid);
    }

    /**
     * Called via webhook when the share mode changes.
     */
    public void onShareModeChanged(int partnerId, UUID remoteBoardUid, BoardShareMode newMode) {
        federatedBoardRepository.updateBookmarkShareMode(partnerId, remoteBoardUid, newMode);
    }

    // -- Helpers --

    private FederationPartner findPartner(int partnerId) {
        return federationRepository
                .findPartnerById(partnerId)
                .orElseThrow(() -> new NotFoundResponse("Partner not found: " + partnerId));
    }

    /**
     * Resolves a boardKey to a remote board UUID for the given partner.
     * For local partners, resolves via DB. For remote partners, returns null
     * (the remote station handles access control).
     */
    public UUID resolveFederatedBoardUid(int partnerId, String boardKey) {
        var board = resolveFederatedBoard(partnerId, boardKey);
        return board != null ? board.uid() : null;
    }

    /**
     * Resolves a boardKey to the full Board entity on the partner station.
     * For local partners, resolves via DB. For remote partners, returns null
     * (the remote station handles access control).
     */
    public Board resolveFederatedBoard(int partnerId, String boardKey) {
        var partner = federationRepository.findPartnerById(partnerId).orElse(null);
        if (partner == null) return null;
        return stationRepository
                .findByUid(partner.partnerStationId())
                .flatMap(station -> boardService.findByShortKey(station.id(), boardKey))
                .orElse(null);
    }

    private int resolveBoardId(String boardKey, FederationPartner partner) {
        int partnerStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .orElseThrow(() -> new NotFoundResponse("Partner station not found"))
                .id();
        return boardService
                .findByShortKey(partnerStationId, boardKey)
                .orElseThrow(() -> new NotFoundResponse("Board not found: " + boardKey))
                .id();
    }

    private int resolveTicketId(int boardId, int ticketNumber) {
        return ticketService
                .findByBoardAndNumber(boardId, ticketNumber)
                .orElseThrow(() -> new NotFoundResponse("Ticket not found: " + ticketNumber))
                .id();
    }

    private <T> T remoteGet(FederationPartner partner, String path, Class<T> type) {
        try {
            var result = httpClient.get(
                    partner.remoteHost(), path, partner.stationId(), getPrivateKey(partner.stationId()), type);
            if (result == null) throw new NotFoundResponse("Empty response from remote partner");
            return result;
        } catch (NotFoundResponse e) {
            throw e;
        } catch (Exception e) {
            throw new NotFoundResponse("Failed to fetch from remote partner: " + e.getMessage());
        }
    }

    private <T> List<T> remoteGetList(FederationPartner partner, String path, Class<T> elementType) {
        return httpClient.getList(
                partner.remoteHost(), path, partner.stationId(), getPrivateKey(partner.stationId()), elementType);
    }

    private <T> T remotePost(FederationPartner partner, String path, Object body, Class<T> type) {
        try {
            var result = httpClient.post(
                    partner.remoteHost(), path, body, partner.stationId(), getPrivateKey(partner.stationId()), type);
            if (result == null) throw new NotFoundResponse("Empty response from remote partner");
            return result;
        } catch (NotFoundResponse e) {
            throw e;
        } catch (Exception e) {
            throw new NotFoundResponse("Failed to post to remote partner: " + e.getMessage());
        }
    }

    private <T> List<T> remotePostList(FederationPartner partner, String path, Object body, Class<T> elementType) {
        return httpClient.postList(
                partner.remoteHost(), path, body, partner.stationId(), getPrivateKey(partner.stationId()), elementType);
    }

    private <T> T remotePut(FederationPartner partner, String path, Object body, Class<T> type) {
        try {
            var result = httpClient.put(
                    partner.remoteHost(), path, body, partner.stationId(), getPrivateKey(partner.stationId()), type);
            if (result == null) throw new NotFoundResponse("Empty response from remote partner");
            return result;
        } catch (NotFoundResponse e) {
            throw e;
        } catch (Exception e) {
            throw new NotFoundResponse("Failed to update on remote partner: " + e.getMessage());
        }
    }

    private void remotePut(FederationPartner partner, String path, Object body) {
        httpClient.put(partner.remoteHost(), path, body, partner.stationId(), getPrivateKey(partner.stationId()));
    }

    private void remotePost(FederationPartner partner, String path, Object body) {
        httpClient.post(partner.remoteHost(), path, body, partner.stationId(), getPrivateKey(partner.stationId()));
    }

    private void remoteDelete(FederationPartner partner, String path) {
        httpClient.delete(partner.remoteHost(), path, partner.stationId(), getPrivateKey(partner.stationId()));
    }

    private void remoteDelete(FederationPartner partner, String path, Object body) {
        httpClient.delete(partner.remoteHost(), path, body, partner.stationId(), getPrivateKey(partner.stationId()));
    }

    private boolean matchesAccess(int memberId, AccessData access) {
        if (!access.userTypes().isEmpty()) {
            var member = memberService.findById(memberId);
            if (member.isPresent()
                    && access.userTypes().contains(member.get().userType().name())) return true;
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
            UUID remoteBoardUid,
            String name,
            String shortKey,
            String description,
            BoardShareMode shareMode,
            String partnerStationName,
            String requiredRole) {}

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

        public boolean hasBacklog() {
            return backlogLaneId != null;
        }
    }

    public record FederatedBoardDetail(RemoteBoard board, String shareMode, String stationName) {

        /**
         * Creates a FederatedBoardDetail from a local Board entity.
         */
        public static FederatedBoardDetail of(
                Board board, String shareMode, String stationName, StationRepository stationRepository) {
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
            String uid, String name, String shortKey, String description, String shareMode, String requiredRole) {}

    record CreateTicketBody(
            UUID remoteMemberId, String laneId, String title, String description, String priority, String dueDate) {}

    record ReorderBody(int laneId, List<Integer> orderedIds) {}

    record CreateLabelBody(String name, String color) {}

    record RemoteMemberBody(UUID remoteMemberId) {}
}
