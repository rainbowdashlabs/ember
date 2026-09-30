/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryResponse;
import dev.chojo.ember.feature.board.entity.BoardTicketTransitionResponse;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteCreateTicketRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteMoveTicketRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteReorderRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteUpdateTicketRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketRoutes;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The ticket lifecycle of a federated board - listing, searching, creating, editing, moving and
 * deleting tickets, plus their transition and history trails: asks the owning station for it, and
 * answers partners asking about the tickets of this station's shared boards.
 */
@Singleton
public class FederatedTicketProxy implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(FederatedTicketProxy.class);

    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final MemberNameResolver memberNameResolver;
    private final MemberIdentityFactory memberIdentityFactory;
    private final FederatedBoardLocator locator;
    private final FederationTransport transport;
    private final FederatedBoardGuards guards;

    @Inject
    public FederatedTicketProxy(
            BoardService boardService,
            BoardTicketService ticketService,
            MemberNameResolver memberNameResolver,
            MemberIdentityFactory memberIdentityFactory,
            FederatedBoardLocator locator,
            FederationTransport transport,
            FederatedBoardGuards guards) {
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.memberNameResolver = memberNameResolver;
        this.memberIdentityFactory = memberIdentityFactory;
        this.locator = locator;
        this.transport = transport;
        this.guards = guards;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(RemoteBoardTicketRoutes.LIST_TICKETS, (partner, params, body) -> serveTickets(partner, params));
        endpoints.serve(
                RemoteBoardTicketRoutes.SEARCH_TICKETS, (partner, params, body) -> serveSearch(partner, params));
        endpoints.<RemoteCreateTicketRequest, BoardTicket>serve(
                RemoteBoardTicketRoutes.CREATE_TICKET, this::serveNewTicket);
        endpoints.<RemoteReorderRequest, Void>serve(RemoteBoardTicketRoutes.REORDER_TICKETS, this::serveReorder);
        endpoints.serve(RemoteBoardTicketRoutes.GET_TICKET, (partner, params, body) -> serveTicket(partner, params));
        endpoints.<RemoteUpdateTicketRequest, BoardTicket>serve(
                RemoteBoardTicketRoutes.UPDATE_TICKET, this::serveTicketUpdate);
        endpoints.serve(
                RemoteBoardTicketRoutes.DELETE_TICKET, (partner, params, body) -> serveDeletion(partner, params));
        endpoints.<RemoteMoveTicketRequest, BoardTicket>serve(RemoteBoardTicketRoutes.MOVE_TICKET, this::serveMove);
        endpoints.serve(
                RemoteBoardTicketRoutes.GET_TRANSITIONS, (partner, params, body) -> serveTransitions(partner, params));
        endpoints.serve(RemoteBoardTicketRoutes.GET_HISTORY, (partner, params, body) -> serveHistory(partner, params));
        endpoints.serve(
                RemoteBoardTicketRoutes.GET_ATTACHMENTS, (partner, params, body) -> serveAttachments(partner, params));
    }

    /**
     * The tickets of a board this station shares with the partner.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board
     * @return a summary per ticket
     */
    public List<TicketSummary> serveTickets(ServingPartner partner, PathParams params) {
        return summarize(ticketService.findByBoard(guards.viewableBoardId(partner, params)));
    }

    /**
     * The tickets of a shared board matching the {@code q} query parameter, every ticket without one.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board and carries the query
     * @return a summary per matching ticket
     */
    public List<TicketSummary> serveSearch(ServingPartner partner, PathParams params) {
        int boardId = guards.viewableBoardId(partner, params);
        return summarize(params.query("q")
                .filter(query -> !query.isBlank())
                .map(query -> ticketService.search(boardId, query))
                .orElseGet(() -> ticketService.findByBoard(boardId)));
    }

    /**
     * One ticket of a shared board, with the names of its assignee and creator.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board and the ticket number
     * @return the ticket
     */
    public BoardTicket serveTicket(ServingPartner partner, PathParams params) {
        return enrichTicket(ticketService
                .findById(guards.viewableTicketId(partner, params))
                .orElseThrow(Refusal.REMOTE_TICKET_NOT_HERE_ON_READ::raise));
    }

    /**
     * The lane transitions of a ticket on a shared board.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board and the ticket number
     * @return the transitions with resolved actors
     */
    public List<BoardTicketTransitionResponse> serveTransitions(ServingPartner partner, PathParams params) {
        return ticketService.findTransitions(guards.viewableTicketId(partner, params)).stream()
                .map(transition -> {
                    var resolved = memberNameResolver.resolveDisplay(transition.actor());
                    return BoardTicketTransitionResponse.from(transition, resolved.identity(), resolved.name());
                })
                .toList();
    }

    /**
     * The change history of a ticket on a shared board.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board and the ticket number
     * @return the history entries with resolved actors
     */
    public List<BoardTicketHistoryResponse> serveHistory(ServingPartner partner, PathParams params) {
        return ticketService.findHistory(guards.viewableTicketId(partner, params)).stream()
                .map(entry -> {
                    var resolved = memberNameResolver.resolveDisplay(entry.actor());
                    return BoardTicketHistoryResponse.from(entry, resolved.identity(), resolved.name());
                })
                .toList();
    }

    /**
     * The attachments of a ticket on a shared board.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board and the ticket number
     * @return the attachments
     */
    public List<BoardTicketAttachment> serveAttachments(ServingPartner partner, PathParams params) {
        return ticketService.findAttachments(guards.viewableTicketId(partner, params));
    }

    /**
     * Opens a ticket on a board the partner may write to, in the lane it names or the board's first.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board
     * @param request the ticket and the partner's member opening it
     * @return the new ticket
     */
    public BoardTicket serveNewTicket(ServingPartner partner, PathParams params, RemoteCreateTicketRequest request) {
        int boardId = guards.writableBoardId(partner, params);
        int laneId = request.laneId() != null
                ? guards.laneOnBoard(boardId, request.laneId())
                : boardService.findLanes(boardId).getFirst().id();
        return ticketService.createTicket(
                boardId,
                laneId,
                request.title(),
                request.description(),
                null,
                request.priority() != null ? TicketPriority.valueOf(request.priority()) : TicketPriority.MEDIUM,
                request.dueDate() != null ? LocalDate.parse(request.dueDate()) : null,
                new MemberIdentity(partner.askingStationUid(), request.remoteMemberId()));
    }

    /**
     * Changes the fields of a ticket on a board the partner may write to. Fields left out stay.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board and the ticket number
     * @param request the changed fields and the partner's member making the change
     * @return the ticket as it is now
     */
    public BoardTicket serveTicketUpdate(ServingPartner partner, PathParams params, RemoteUpdateTicketRequest request) {
        int ticketId = guards.writableTicketId(partner, params);
        MemberIdentity assignee = request.assignedMemberId() != null
                ? memberIdentityFactory.local(partner.servingStationId(), request.assignedMemberId())
                : null;
        guards.cacheName(partner, request.remoteMemberUid(), request.displayName());
        ticketService.updateTicket(
                ticketId,
                request.title(),
                request.description(),
                assignee,
                request.priority() != null ? TicketPriority.valueOf(request.priority()) : null,
                request.dueDate() != null ? LocalDate.parse(request.dueDate()) : null,
                guards.actor(partner, request.remoteMemberUid()));
        return ticketService.findById(ticketId).orElseThrow(Refusal.REMOTE_TICKET_NOT_HERE_AFTER_UPDATE::raise);
    }

    /**
     * Moves a ticket to a lane of its own board.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board and the ticket number
     * @param request the target lane and position and the partner's member moving it
     * @return the ticket as it is now
     */
    public BoardTicket serveMove(ServingPartner partner, PathParams params, RemoteMoveTicketRequest request) {
        int ticketId = guards.writableTicketId(partner, params);
        var ticket = ticketService.findById(ticketId).orElseThrow(Refusal.REMOTE_TICKET_NOT_HERE_ON_MOVE::raise);
        int toLaneId = guards.laneOnBoard(ticket.boardId(), request.toLaneId());
        guards.cacheName(partner, request.remoteMemberUid(), request.displayName());
        ticketService.moveTicket(
                ticketId,
                ticket.laneId(),
                toLaneId,
                request.position(),
                guards.actor(partner, request.remoteMemberUid()));
        return ticketService.findById(ticketId).orElseThrow(Refusal.REMOTE_TICKET_NOT_HERE_AFTER_MOVE::raise);
    }

    /**
     * Puts the tickets of one lane of a board the partner may write to in a new order.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board
     * @param request the lane and its tickets in their new order
     * @return nothing
     */
    public Void serveReorder(ServingPartner partner, PathParams params, RemoteReorderRequest request) {
        int boardId = guards.writableBoardId(partner, params);
        ticketService.reorderTickets(guards.laneOnBoard(boardId, request.laneId()), request.orderedIds());
        return null;
    }

    /**
     * Deletes a ticket of a board the partner may write to.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board and the ticket number
     * @return nothing
     */
    public Void serveDeletion(ServingPartner partner, PathParams params) {
        ticketService.deleteTicket(guards.writableTicketId(partner, params));
        return null;
    }

    /**
     * Lists all tickets of a federated board.
     *
     * @param partnerId the partner record id
     * @param boardKey  the board short key
     * @return the ticket summaries
     */
    public List<TicketSummary> proxyListTickets(int partnerId, String boardKey) {
        return transport.getList(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.LIST_TICKETS.at(boardKey),
                TicketSummary.class);
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
        var request = RemoteBoardTicketRoutes.SEARCH_TICKETS.at(boardKey);
        if (query != null && !query.isBlank()) {
            request = request.query("q", query);
        }
        return transport.getList(locator.requirePartner(partnerId), request, TicketSummary.class);
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
        return transport.get(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.GET_TICKET.at(boardKey, ticketNumber),
                BoardTicket.class);
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
        return transport.getList(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.GET_TRANSITIONS.at(boardKey, ticketNumber),
                BoardTicketTransitionResponse.class);
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
        return transport.getList(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.GET_HISTORY.at(boardKey, ticketNumber),
                BoardTicketHistoryResponse.class);
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
        log.info("Federated ticket creation on partner {} board {} by member {}", partnerId, boardKey, remoteMemberId);
        var body = new RemoteCreateTicketRequest(
                remoteMemberId, laneId, title, description, nameOf(priority), textOf(dueDate));
        return transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.CREATE_TICKET.at(boardKey),
                body,
                BoardTicket.class);
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
        log.info(
                "Federated ticket update on partner {} board {} ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberUid);
        var body = new RemoteUpdateTicketRequest(
                title, description, assignedMemberId, nameOf(priority), textOf(dueDate), remoteMemberUid, displayName);
        return transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.UPDATE_TICKET.at(boardKey, ticketNumber),
                body,
                BoardTicket.class);
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
        log.info(
                "Federated ticket move on partner {} board {} ticket {} to lane {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                toLaneId,
                remoteMemberUid);
        return transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.MOVE_TICKET.at(boardKey, ticketNumber),
                new RemoteMoveTicketRequest(toLaneId, position, remoteMemberUid, displayName),
                BoardTicket.class);
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
        transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.REORDER_TICKETS.at(boardKey),
                new RemoteReorderRequest(laneId, orderedIds),
                Void.class);
    }

    /**
     * Deletes a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     */
    public void proxyDeleteTicket(int partnerId, String boardKey, int ticketNumber) {
        log.info("Federated ticket deletion on partner {} board {} ticket {}", partnerId, boardKey, ticketNumber);
        transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.DELETE_TICKET.at(boardKey, ticketNumber),
                null,
                Void.class);
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

    private static String nameOf(TicketPriority priority) {
        return priority != null ? priority.name() : null;
    }

    private static String textOf(LocalDate date) {
        return date != null ? date.toString() : null;
    }
}
