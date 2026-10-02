/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.entity.TicketLabelMapping;
import dev.chojo.ember.feature.board.service.FederatedBoardDiscoveryService;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.members.entity.MemberCompletion;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Board-level server-to-server endpoints served to federation partners: which boards are shared,
 * their lanes, labels, custom fields, access configuration and member completions. The ticket
 * surface lives in {@link RemoteBoardTicketRoutes}, {@link RemoteBoardTicketDetailRoutes} and
 * {@link RemoteBoardTicketLinkRoutes}, the notification receivers in
 * {@link RemoteBoardWebhookRoutes}. The local proxy calling all of them is
 * {@link FederatedBoardRoutes}.
 * <p>
 * This class also holds the request and response records shared across the whole
 * {@code /remote/boards} surface. The contract hash follows the types reachable from the
 * declared endpoints, so their location is a code-organisation choice, not a protocol one.
 */
@Singleton
public class RemoteBoardRoutes implements Routes {

    static final String TICKETS_PATH = "/remote/boards/{boardKey}/tickets";
    static final String TICKET_PATH = TICKETS_PATH + "/{ticketNumber}";

    public static final FederationEndpoint LIST_SHARED_BOARDS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards", RemoteSharedBoardResponse.class);
    public static final FederationEndpoint GET_BOARD = FederationEndpoint.get(
            FederationSurface.BOARD_SHARE,
            "/remote/boards/{boardKey}",
            FederatedBoardDiscoveryService.FederatedBoardDetail.class);
    public static final FederationEndpoint GET_LANES = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/lanes", BoardLane.class);
    public static final FederationEndpoint GET_LABELS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/labels", BoardLabel.class);
    public static final FederationEndpoint CREATE_LABEL = FederationEndpoint.post(
            FederationSurface.BOARD_SHARE,
            "/remote/boards/{boardKey}/labels",
            RemoteCreateLabelRequest.class,
            BoardLabel.class);
    public static final FederationEndpoint GET_ALL_TICKET_LABELS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/ticket-labels", TicketLabelMapping.class);
    public static final FederationEndpoint GET_FIELDS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/fields", BoardField.class);
    public static final FederationEndpoint GET_ACCESS = FederationEndpoint.get(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/access", RemoteAccessResponse.class);
    public static final FederationEndpoint GET_MEMBERS = FederationEndpoint.getList(
            FederationSurface.BOARD_SHARE, "/remote/boards/{boardKey}/members", MemberCompletion.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            LIST_SHARED_BOARDS,
            GET_BOARD,
            GET_LANES,
            GET_LABELS,
            CREATE_LABEL,
            GET_ALL_TICKET_LABELS,
            GET_FIELDS,
            GET_ACCESS,
            GET_MEMBERS);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteBoardRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, endpoints, binder -> binder.serve(LIST_SHARED_BOARDS)
                        .serve(GET_BOARD)
                        .serve(GET_LANES)
                        .serve(GET_LABELS)
                        .serve(CREATE_LABEL)
                        .serve(GET_ALL_TICKET_LABELS)
                        .serve(GET_FIELDS)
                        .serve(GET_ACCESS)
                        .serve(GET_MEMBERS));
    }

    public record RemoteSharedBoardResponse(
            UUID uid,
            String name,
            String description,
            String shortKey,
            BoardShareMode shareMode,
            StationUserType requiredUserType) {}

    public record RemoteCreateTicketRequest(
            UUID remoteMemberId,
            Integer laneId,
            String title,
            String description,
            @Nullable String priority,
            @Nullable String dueDate) {}

    public record RemoteUpdateTicketRequest(
            String title,
            @Nullable String description,
            @Nullable Integer assignedMemberId,
            @Nullable String priority,
            @Nullable String dueDate,
            UUID remoteMemberUid,
            String displayName) {}

    public record RemoteMoveTicketRequest(
            int toLaneId, int position, @Nullable UUID remoteMemberUid, String displayName) {}

    public record RemoteReorderRequest(int laneId, List<Integer> orderedIds) {}

    public record RemoteCommentRequest(UUID remoteMemberId, String displayName, Integer parentId, String content) {}

    /**
     * A partner member rewriting a comment they wrote on a ticket of a shared board.
     *
     * @param remoteMemberId the member on the partner station, who has to be the comment's author
     * @param displayName    what the member is called, for the notifications the edit raises
     * @param content        the new text
     */
    public record RemoteEditCommentRequest(UUID remoteMemberId, String displayName, String content) {}

    /**
     * A partner member removing a comment they wrote on a ticket of a shared board.
     *
     * @param remoteMemberId the member on the partner station, who has to be the comment's author
     */
    public record RemoteDeleteCommentRequest(UUID remoteMemberId) {}

    public record RemoteChecklistItemRequest(String title, UUID remoteMemberUid, String displayName) {}

    public record RemoteUpdateChecklistItemRequest(
            String title, boolean checked, UUID remoteMemberUid, String displayName) {}

    public record RemoteLinkRequest(
            int linkedTicketNumber, LinkType linkType, UUID remoteMemberUid, String displayName) {}

    public record RemoteDeleteLinkRequest(UUID remoteMemberUid, String displayName) {}

    public record RemoteCreateLabelRequest(String name, String color) {}

    public record RemoteLabelActionRequest(UUID remoteMemberId, String displayName) {}

    public record RemoteWatchRequest(UUID remoteMemberId) {}

    public record RemoteBoardRenamedWebhook(UUID boardUid, String newName, String newShortKey) {}

    public record RemoteBoardUnsharedWebhook(UUID boardUid) {}

    public record RemoteShareModeChangedWebhook(UUID boardUid, BoardShareMode shareMode) {}

    /**
     * The watchers of a shared ticket, split into the serving station's members and its partners'.
     *
     * <p>TODO: drop the always empty {@code federated} with the next change to the board sharing contract.
     */
    public record WatcherResponse(List<Integer> local, List<Object> federated) {}

    /**
     * How a partner may use a shared board, and which of its user types may edit.
     */
    public record RemoteAccessResponse(BoardShareMode shareMode, List<StationUserType> editUserTypes) {}
}
