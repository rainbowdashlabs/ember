/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteChecklistItemRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteCommentRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteEditCommentRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteUpdateChecklistItemRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteWatchRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.WatcherResponse;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Server-to-server detail endpoints for a ticket on a shared board: comments, checklist items and
 * watchers.
 */
@Singleton
public class RemoteBoardTicketDetailRoutes implements Routes {

    private static final String TICKET = RemoteBoardRoutes.TICKET_PATH;

    public static final FederationEndpoint GET_COMMENTS =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKET + "/comments", CommentResponse.class);
    public static final FederationEndpoint ADD_COMMENT = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE, TICKET + "/comments", RemoteCommentRequest.class, BoardComment.class);
    public static final FederationEndpoint EDIT_COMMENT = FederationEndpoint.put(
            FederationSurface.BOARD_SHARE,
            TICKET + "/comments/{commentId}",
            RemoteEditCommentRequest.class,
            Void.class);
    public static final FederationEndpoint DELETE_COMMENT = FederationEndpoint.delete(
            FederationSurface.BOARD_SHARE, TICKET + "/comments/{commentId}", Void.class, Void.class);
    public static final FederationEndpoint GET_CHECKLIST =
            FederationEndpoint.getList(FederationSurface.BOARD_SHARE, TICKET + "/checklist", BoardChecklistItem.class);
    public static final FederationEndpoint ADD_CHECKLIST_ITEM = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE,
            TICKET + "/checklist",
            RemoteChecklistItemRequest.class,
            BoardChecklistItem.class);
    public static final FederationEndpoint UPDATE_CHECKLIST_ITEM = FederationEndpoint.put(
            FederationSurface.BOARD_SHARE,
            TICKET + "/checklist/{itemId}",
            RemoteUpdateChecklistItemRequest.class,
            Void.class);
    public static final FederationEndpoint DELETE_CHECKLIST_ITEM = FederationEndpoint.delete(
            FederationSurface.BOARD_SHARE, TICKET + "/checklist/{itemId}", Void.class, Void.class);
    public static final FederationEndpoint GET_WATCHERS =
            FederationEndpoint.get(FederationSurface.BOARD_SHARE, TICKET + "/watchers", WatcherResponse.class);
    public static final FederationEndpoint WATCH_TICKET = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE, TICKET + "/watch", RemoteWatchRequest.class, Void.class);
    public static final FederationEndpoint UNWATCH_TICKET = FederationEndpoint.delete(
            FederationSurface.BOARD_SHARE, TICKET + "/watch", RemoteWatchRequest.class, Void.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            GET_COMMENTS,
            ADD_COMMENT,
            EDIT_COMMENT,
            DELETE_COMMENT,
            GET_CHECKLIST,
            ADD_CHECKLIST_ITEM,
            UPDATE_CHECKLIST_ITEM,
            DELETE_CHECKLIST_ITEM,
            GET_WATCHERS,
            WATCH_TICKET,
            UNWATCH_TICKET);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteBoardTicketDetailRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(GET_COMMENTS)
                .serve(ADD_COMMENT)
                .serve(EDIT_COMMENT)
                .serve(DELETE_COMMENT)
                .serve(GET_CHECKLIST)
                .serve(ADD_CHECKLIST_ITEM)
                .serve(UPDATE_CHECKLIST_ITEM)
                .serve(DELETE_CHECKLIST_ITEM)
                .serve(GET_WATCHERS)
                .serve(WATCH_TICKET)
                .serve(UNWATCH_TICKET));
    }
}
