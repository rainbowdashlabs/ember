/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryResponse;
import dev.chojo.ember.feature.board.entity.BoardTicketTransitionResponse;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketRoutes;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Proxies the ticket lifecycle of a federated board — listing, searching, creating, editing,
 * moving and deleting tickets, plus their transition and history trails.
 */
@Singleton
public class FederatedTicketProxy {
    private static final Logger log = LoggerFactory.getLogger(FederatedTicketProxy.class);

    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;
    private final FederatedBoardRemoteGateway gateway;
    private final FederatedBoardLocator locator;

    @Inject
    public FederatedTicketProxy(
            BoardService boardService,
            BoardTicketService ticketService,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory,
            FederatedBoardRemoteGateway gateway,
            FederatedBoardLocator locator) {
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
        this.gateway = gateway;
        this.locator = locator;
    }

    /**
     * Lists all tickets of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the ticket summaries
     */
    public List<TicketSummary> proxyListTickets(int partnerId, String boardKey) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(partner, RemoteBoardTicketRoutes.LIST_TICKETS.at(boardKey), TicketSummary.class);
        }
        return summarize(ticketService.findByBoard(locator.resolveBoardId(boardKey, partner)));
    }

    /**
     * Searches the tickets of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @param query     the search query, all tickets when blank
     * @return the matching ticket summaries
     */
    public List<TicketSummary> proxySearchTickets(int partnerId, String boardKey, String query) {
        var partner = locator.requirePartner(partnerId);
        boolean blankQuery = query == null || query.isBlank();
        if (partner.isRemote()) {
            var request = RemoteBoardTicketRoutes.SEARCH_TICKETS.at(boardKey);
            if (!blankQuery) {
                request = request.query("q", query);
            }
            return gateway.getList(partner, request, TicketSummary.class);
        }
        int boardId = locator.resolveBoardId(boardKey, partner);
        return summarize(blankQuery ? ticketService.findByBoard(boardId) : ticketService.search(boardId, query));
    }

    /**
     * Returns a single ticket of a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the ticket
     */
    public BoardTicket proxyGetTicket(int partnerId, String boardKey, int ticketNumber) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.get(
                    partner, RemoteBoardTicketRoutes.GET_TICKET.at(boardKey, ticketNumber), BoardTicket.class);
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        return enrichTicket(ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new));
    }

    /**
     * Returns the transitions of a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the transitions with resolved actors
     */
    public List<BoardTicketTransitionResponse> proxyGetTransitions(int partnerId, String boardKey, int ticketNumber) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(
                    partner,
                    RemoteBoardTicketRoutes.GET_TRANSITIONS.at(boardKey, ticketNumber),
                    BoardTicketTransitionResponse.class);
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        return ticketService.findTransitions(ticketId).stream()
                .map(tr -> {
                    var resolved = memberNameResolver.resolveDisplay(tr.actor());
                    return BoardTicketTransitionResponse.from(tr, resolved.identity(), resolved.name());
                })
                .toList();
    }

    /**
     * Returns the history of a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the history entries with resolved actors
     */
    public List<BoardTicketHistoryResponse> proxyGetHistory(int partnerId, String boardKey, int ticketNumber) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(
                    partner,
                    RemoteBoardTicketRoutes.GET_HISTORY.at(boardKey, ticketNumber),
                    BoardTicketHistoryResponse.class);
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        return ticketService.findHistory(ticketId).stream()
                .map(h -> {
                    var resolved = memberNameResolver.resolveDisplay(h.actor());
                    return BoardTicketHistoryResponse.from(h, resolved.identity(), resolved.name());
                })
                .toList();
    }

    /**
     * Creates a ticket on a federated board.
     *
     * @param partnerId      the partner record id
     * @param boardKey       the board short key
     * @param laneId         the target lane, the first lane when omitted
     * @param title          the ticket title
     * @param description    the ticket description
     * @param priority       the ticket priority, medium when omitted
     * @param dueDate        the due date
     * @param remoteMemberId the creating member on the partner station
     * @return the created ticket
     */
    public BoardTicket proxyCreateTicket(
            int partnerId,
            String boardKey,
            Integer laneId,
            String title,
            String description,
            TicketPriority priority,
            LocalDate dueDate,
            UUID remoteMemberId) {
        var partner = locator.requirePartner(partnerId);
        log.info("Federated ticket creation on partner {} board {} by member {}", partnerId, boardKey, remoteMemberId);
        if (partner.isRemote()) {
            var body = new CreateTicketBody(
                    remoteMemberId,
                    laneId != null ? String.valueOf(laneId) : "",
                    title != null ? title : "",
                    description != null ? description : "",
                    priority,
                    dueDate);
            return gateway.post(partner, RemoteBoardTicketRoutes.CREATE_TICKET.at(boardKey), body, BoardTicket.class);
        }
        int boardId = locator.resolveBoardId(boardKey, partner);
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
                priority != null ? priority : TicketPriority.MEDIUM,
                dueDate,
                creatorIdentity);
    }

    /**
     * Updates a ticket on a federated board.
     *
     * @param partnerId         the partner record id
     * @param boardKey          the board short key
     * @param ticketNumber      the board relative ticket number
     * @param title             the new title
     * @param description       the new description
     * @param assignedMemberId  the new assignee on the owning station
     * @param priority          the new priority
     * @param dueDate           the new due date
     * @param remoteMemberUid   the acting member on the partner station
     * @param displayName       the display name of the acting member
     * @return the updated ticket
     */
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
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated ticket update on partner {} board {} ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberUid);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            if (title != null) body.put("title", title);
            if (description != null) body.put("description", description);
            if (assignedMemberId != null) body.put("assignedMemberId", assignedMemberId);
            if (priority != null) body.put("priority", priority);
            if (dueDate != null) body.put("dueDate", dueDate);
            if (remoteMemberUid != null) body.put("remoteMemberUid", remoteMemberUid.toString());
            if (displayName != null) body.put("displayName", displayName);
            return gateway.put(
                    partner, RemoteBoardTicketRoutes.UPDATE_TICKET.at(boardKey, ticketNumber), body, BoardTicket.class);
        }
        int boardId = locator.resolveBoardId(boardKey, partner);
        int ticketId = locator.resolveTicketId(boardId, ticketNumber);
        var board = boardService.findById(boardId).orElseThrow(NotFoundResponse::new);
        MemberIdentity assigneeIdentity =
                assignedMemberId != null ? memberIdentityFactory.local(board.stationId(), assignedMemberId) : null;
        MemberIdentity actorIdentity = locator.remoteIdentity(partner, remoteMemberUid);
        locator.cacheNameIfPresent(partnerId, remoteMemberUid, displayName);
        ticketService.updateTicket(ticketId, title, description, assigneeIdentity, priority, dueDate, actorIdentity);
        return ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
    }

    /**
     * Moves a ticket to another lane on a federated board.
     *
     * @param partnerId       the partner record id
     * @param boardKey        the board short key
     * @param ticketNumber    the board relative ticket number
     * @param toLaneId        the target lane
     * @param position        the target position within the lane
     * @param remoteMemberUid the acting member on the partner station
     * @param displayName     the display name of the acting member
     * @return the moved ticket
     */
    public BoardTicket proxyMoveTicket(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int toLaneId,
            int position,
            UUID remoteMemberUid,
            String displayName) {
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated ticket move on partner {} board {} ticket {} to lane {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                toLaneId,
                remoteMemberUid);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("toLaneId", toLaneId);
            body.put("position", position);
            if (remoteMemberUid != null) body.put("remoteMemberUid", remoteMemberUid.toString());
            if (displayName != null) body.put("displayName", displayName);
            return gateway.put(
                    partner, RemoteBoardTicketRoutes.MOVE_TICKET.at(boardKey, ticketNumber), body, BoardTicket.class);
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        var ticket = ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
        MemberIdentity actorIdentity = locator.remoteIdentity(partner, remoteMemberUid);
        locator.cacheNameIfPresent(partnerId, remoteMemberUid, displayName);
        ticketService.moveTicket(ticketId, ticket.laneId(), toLaneId, position, actorIdentity);
        return ticketService.findById(ticketId).orElseThrow(NotFoundResponse::new);
    }

    /**
     * Reorders the tickets within a lane of a federated board.
     *
     * @param partnerId  the partner record id
     * @param boardKey   the board short key
     * @param laneId     the lane to reorder
     * @param orderedIds the ticket ids in their new order
     */
    public void proxyReorderTickets(int partnerId, String boardKey, int laneId, List<Integer> orderedIds) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            gateway.put(
                    partner, RemoteBoardTicketRoutes.REORDER_TICKETS.at(boardKey), new ReorderBody(laneId, orderedIds));
            return;
        }
        ticketService.reorderTickets(laneId, orderedIds);
    }

    /**
     * Deletes a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     */
    public void proxyDeleteTicket(int partnerId, String boardKey, int ticketNumber) {
        var partner = locator.requirePartner(partnerId);
        log.info("Federated ticket deletion on partner {} board {} ticket {}", partnerId, boardKey, ticketNumber);
        if (partner.isRemote()) {
            gateway.delete(partner, RemoteBoardTicketRoutes.DELETE_TICKET.at(boardKey, ticketNumber));
            return;
        }
        ticketService.deleteTicket(locator.resolveTicketId(partner, boardKey, ticketNumber));
    }

    private List<TicketSummary> summarize(List<BoardTicket> tickets) {
        return tickets.stream()
                .map(TicketSummary::of)
                .map(t -> t.assignee() != null ? t.withAssignee(memberNameResolver.enrichDisplay(t.assignee())) : t)
                .toList();
    }

    private BoardTicket enrichTicket(BoardTicket ticket) {
        MemberIdentity assignee =
                ticket.assignee() != null ? memberNameResolver.enrichDisplay(ticket.assignee()) : null;
        MemberIdentity creator = ticket.creator() != null ? memberNameResolver.enrichDisplay(ticket.creator()) : null;
        return ticket.withIdentities(assignee, creator);
    }

    record CreateTicketBody(
            UUID remoteMemberId,
            String laneId,
            String title,
            String description,
            TicketPriority priority,
            LocalDate dueDate) {}

    record ReorderBody(int laneId, List<Integer> orderedIds) {}
}
