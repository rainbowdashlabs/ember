/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryResponse;
import dev.chojo.ember.feature.board.entity.BoardTicketTransitionResponse;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteCreateTicketRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteMoveTicketRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteReorderRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteUpdateTicketRequest;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Server-to-server ticket endpoints on a shared board: the ticket list, search, the lifecycle of
 * a single ticket and its read-only audit trail. Requests carry an RSA-signed envelope instead of
 * a user session.
 */
@Singleton
public class RemoteBoardTicketRoutes implements Routes {

    private static final String TICKETS = RemoteBoardRoutes.TICKETS_PATH;

    public static final FederationEndpoint LIST_TICKETS =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKETS, TicketSummary.class);
    public static final FederationEndpoint SEARCH_TICKETS =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKETS + "/search", TicketSummary.class);
    public static final FederationEndpoint CREATE_TICKET = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE, TICKETS, RemoteCreateTicketRequest.class, BoardTicket.class);
    public static final FederationEndpoint REORDER_TICKETS = FederationEndpoint.put(
            FederationSurface.BOARD_SHARE, TICKETS + "/reorder", RemoteReorderRequest.class, Void.class);
    public static final FederationEndpoint GET_TICKET =
            FederationEndpoint.get(FederationSurface.BOARD_SHARE, TICKETS + "/{ticketNumber}", BoardTicket.class);
    public static final FederationEndpoint UPDATE_TICKET = FederationEndpoint.put(
            FederationSurface.BOARD_SHARE,
            TICKETS + "/{ticketNumber}",
            RemoteUpdateTicketRequest.class,
            BoardTicket.class);
    public static final FederationEndpoint DELETE_TICKET = FederationEndpoint.delete(
            FederationSurface.BOARD_SHARE, TICKETS + "/{ticketNumber}", Void.class, Void.class);
    public static final FederationEndpoint MOVE_TICKET = FederationEndpoint.put(
            FederationSurface.BOARD_SHARE,
            TICKETS + "/{ticketNumber}/move",
            RemoteMoveTicketRequest.class,
            BoardTicket.class);
    public static final FederationEndpoint GET_TRANSITIONS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE,
            TICKETS + "/{ticketNumber}/transitions",
            BoardTicketTransitionResponse.class);
    public static final FederationEndpoint GET_HISTORY = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, TICKETS + "/{ticketNumber}/history", BoardTicketHistoryResponse.class);
    public static final FederationEndpoint GET_ATTACHMENTS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, TICKETS + "/{ticketNumber}/attachments", BoardTicketAttachment.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            LIST_TICKETS,
            SEARCH_TICKETS,
            CREATE_TICKET,
            REORDER_TICKETS,
            GET_TICKET,
            UPDATE_TICKET,
            DELETE_TICKET,
            MOVE_TICKET,
            GET_TRANSITIONS,
            GET_HISTORY,
            GET_ATTACHMENTS);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteBoardTicketRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(LIST_TICKETS)
                .serve(SEARCH_TICKETS)
                .serve(CREATE_TICKET)
                .serve(REORDER_TICKETS)
                .serve(GET_TICKET)
                .serve(UPDATE_TICKET)
                .serve(DELETE_TICKET)
                .serve(MOVE_TICKET)
                .serve(GET_TRANSITIONS)
                .serve(GET_HISTORY)
                .serve(GET_ATTACHMENTS));
    }
}
