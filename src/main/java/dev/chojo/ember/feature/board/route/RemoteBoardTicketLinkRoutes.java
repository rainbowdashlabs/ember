/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Server-to-server endpoints for what a ticket on a shared board points at: links to other
 * tickets of the same board and the labels applied to it.
 */
@Singleton
public class RemoteBoardTicketLinkRoutes implements Routes {

    private static final String TICKET = RemoteBoardRoutes.TICKET_PATH;

    public static final FederationEndpoint GET_LINKS =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKET + "/links", BoardTicketLink.class);
    public static final FederationEndpoint CREATE_LINK = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE, TICKET + "/links", RemoteBoardRoutes.RemoteLinkRequest.class, Void.class);
    public static final FederationEndpoint DELETE_LINK = FederationEndpoint.delete(
            FederationSurface.BOARD_SHARE,
            TICKET + "/links/{linkedNumber}",
            RemoteBoardRoutes.RemoteDeleteLinkRequest.class,
            Void.class);
    public static final FederationEndpoint GET_TICKET_LABELS =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKET + "/labels", BoardLabel.class);
    public static final FederationEndpoint ADD_TICKET_LABEL = FederationEndpoint.postList(
            FederationSurface.BOARD_SHARE,
            TICKET + "/labels/{labelId}",
            RemoteBoardRoutes.RemoteLabelActionRequest.class,
            BoardLabel.class);
    public static final FederationEndpoint REMOVE_TICKET_LABEL = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE,
            TICKET + "/labels/{labelId}/remove",
            RemoteBoardRoutes.RemoteLabelActionRequest.class,
            Void.class);

    public static final List<FederationEndpoint> CONTRACT =
            List.of(GET_LINKS, CREATE_LINK, DELETE_LINK, GET_TICKET_LABELS, ADD_TICKET_LABEL, REMOVE_TICKET_LABEL);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteBoardTicketLinkRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(GET_LINKS)
                .serve(CREATE_LINK)
                .serve(DELETE_LINK)
                .serve(GET_TICKET_LABELS)
                .serve(ADD_TICKET_LABEL)
                .serve(REMOVE_TICKET_LABEL));
    }
}
