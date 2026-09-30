/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardTicketAttachment;
import dev.chojo.ember.feature.board.entity.BoardTicketHistoryAction;
import dev.chojo.ember.feature.board.entity.BoardTicketLink;
import dev.chojo.ember.feature.board.entity.LinkType;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteChecklistItemRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteCommentRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteDeleteLinkRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteEditCommentRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteLabelActionRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteLinkRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteUpdateChecklistItemRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.RemoteWatchRequest;
import dev.chojo.ember.feature.board.route.RemoteBoardRoutes.WatcherResponse;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketDetailRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketLinkRoutes;
import dev.chojo.ember.feature.board.route.RemoteBoardTicketRoutes;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.PathParams;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Everything that hangs off a single ticket of a federated board: comments, checklist items, links,
 * label assignments, watchers and attachments. Asks the owning station for it, and answers partners
 * asking about the tickets of this station's shared boards.
 */
@Singleton
public class FederatedTicketDetailProxy implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(FederatedTicketDetailProxy.class);

    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final MemberNameResolver memberNameResolver;
    private final FederatedBoardLocator locator;
    private final FederationTransport transport;
    private final FederatedBoardGuards guards;

    @Inject
    public FederatedTicketDetailProxy(
            BoardService boardService,
            BoardTicketService ticketService,
            MemberNameResolver memberNameResolver,
            FederatedBoardLocator locator,
            FederationTransport transport,
            FederatedBoardGuards guards) {
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.memberNameResolver = memberNameResolver;
        this.locator = locator;
        this.transport = transport;
        this.guards = guards;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        serveComments(endpoints);
        serveChecklist(endpoints);
        serveWatchers(endpoints);
        serveLinks(endpoints);
        serveLabels(endpoints);
    }

    private void serveComments(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteBoardTicketDetailRoutes.GET_COMMENTS,
                (partner, params, body) -> ticketService.findComments(guards.viewableTicketId(partner, params)).stream()
                        .map(comment -> CommentResponseMapper.fromBoard(memberNameResolver, comment))
                        .toList());
        endpoints.<RemoteCommentRequest, BoardComment>serve(
                RemoteBoardTicketDetailRoutes.ADD_COMMENT, this::serveNewComment);
        endpoints.<RemoteEditCommentRequest, Void>serve(
                RemoteBoardTicketDetailRoutes.EDIT_COMMENT, (partner, params, body) -> {
                    int ticketId = guards.writableTicketId(partner, params);
                    ticketService.updateComment(
                            ticketId, guards.commentOnTicket(ticketId, params.integer("commentId")), body.content());
                    return null;
                });
        endpoints.serve(RemoteBoardTicketDetailRoutes.DELETE_COMMENT, (partner, params, body) -> {
            int ticketId = guards.writableTicketId(partner, params);
            ticketService.deleteComment(ticketId, guards.commentOnTicket(ticketId, params.integer("commentId")));
            return null;
        });
    }

    private void serveChecklist(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteBoardTicketDetailRoutes.GET_CHECKLIST,
                (partner, params, body) -> ticketService.findChecklistItems(guards.viewableTicketId(partner, params)));
        endpoints.<RemoteChecklistItemRequest, BoardChecklistItem>serve(
                RemoteBoardTicketDetailRoutes.ADD_CHECKLIST_ITEM, (partner, params, body) -> {
                    int ticketId = guards.writableTicketId(partner, params);
                    guards.cacheName(partner, body.remoteMemberUid(), body.displayName());
                    return ticketService.addChecklistItem(ticketId, body.title(), 0);
                });
        endpoints.<RemoteUpdateChecklistItemRequest, Void>serve(
                RemoteBoardTicketDetailRoutes.UPDATE_CHECKLIST_ITEM, (partner, params, body) -> {
                    int ticketId = guards.writableTicketId(partner, params);
                    int itemId = guards.checklistItemOnTicket(ticketId, params.integer("itemId"));
                    guards.cacheName(partner, body.remoteMemberUid(), body.displayName());
                    ticketService.updateChecklistItem(itemId, ticketId, body.title(), body.checked(), 0);
                    return null;
                });
        endpoints.serve(RemoteBoardTicketDetailRoutes.DELETE_CHECKLIST_ITEM, (partner, params, body) -> {
            int ticketId = guards.writableTicketId(partner, params);
            ticketService.deleteChecklistItem(
                    guards.checklistItemOnTicket(ticketId, params.integer("itemId")), ticketId, 0);
            return null;
        });
    }

    private void serveWatchers(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteBoardTicketDetailRoutes.GET_WATCHERS,
                (partner, params, body) -> new WatcherResponse(
                        ticketService.findWatchers(guards.viewableTicketId(partner, params)), List.of()));
        endpoints.<RemoteWatchRequest, Void>serve(
                RemoteBoardTicketDetailRoutes.WATCH_TICKET, (partner, params, body) -> {
                    ticketService.addWatcher(
                            guards.writableTicketId(partner, params),
                            new MemberIdentity(partner.askingStationUid(), body.remoteMemberId()));
                    return null;
                });
        endpoints.<RemoteWatchRequest, Void>serve(
                RemoteBoardTicketDetailRoutes.UNWATCH_TICKET, (partner, params, body) -> {
                    ticketService.removeWatcher(
                            guards.writableTicketId(partner, params),
                            new MemberIdentity(partner.askingStationUid(), body.remoteMemberId()));
                    return null;
                });
    }

    private void serveLinks(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteBoardTicketLinkRoutes.GET_LINKS,
                (partner, params, body) -> ticketService.findLinks(guards.viewableTicketId(partner, params)));
        endpoints.<RemoteLinkRequest, Void>serve(RemoteBoardTicketLinkRoutes.CREATE_LINK, (partner, params, body) -> {
            int boardId = guards.writableBoardId(partner, params);
            guards.cacheName(partner, body.remoteMemberUid(), body.displayName());
            ticketService.linkTickets(
                    guards.ticketId(boardId, params.integer("ticketNumber")),
                    guards.linkedTicketId(boardId, body.linkedTicketNumber()),
                    body.linkType(),
                    guards.actor(partner, body.remoteMemberUid()));
            return null;
        });
        endpoints.<RemoteDeleteLinkRequest, Void>serve(
                RemoteBoardTicketLinkRoutes.DELETE_LINK, (partner, params, body) -> {
                    int boardId = guards.writableBoardId(partner, params);
                    UUID actor = body != null ? body.remoteMemberUid() : null;
                    guards.cacheName(partner, actor, body != null ? body.displayName() : null);
                    ticketService.unlinkTickets(
                            guards.ticketId(boardId, params.integer("ticketNumber")),
                            guards.linkedTicketId(boardId, params.integer("linkedNumber")),
                            guards.actor(partner, actor));
                    return null;
                });
    }

    private void serveLabels(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteBoardTicketLinkRoutes.GET_TICKET_LABELS,
                (partner, params, body) -> boardService.findLabelsForTicket(guards.viewableTicketId(partner, params)));
        endpoints.<RemoteLabelActionRequest, List<BoardLabel>>serve(
                RemoteBoardTicketLinkRoutes.ADD_TICKET_LABEL, (partner, params, body) -> {
                    int ticketId = labelTicket(partner, params, body, BoardTicketHistoryAction.LABEL_ADDED);
                    return boardService.findLabelsForTicket(ticketId);
                });
        endpoints.<RemoteLabelActionRequest, Void>serve(
                RemoteBoardTicketLinkRoutes.REMOVE_TICKET_LABEL, (partner, params, body) -> {
                    labelTicket(partner, params, body, BoardTicketHistoryAction.LABEL_REMOVED);
                    return null;
                });
    }

    /**
     * Adds a comment by one of the partner's members to a ticket of a board the partner may write to.
     * A reply names a parent comment, which has to be on the same ticket.
     *
     * @param partner the partnership the request arrived on
     * @param params  names the board and the ticket number
     * @param request the comment and its author
     * @return the new comment
     */
    public BoardComment serveNewComment(ServingPartner partner, PathParams params, RemoteCommentRequest request) {
        int ticketId = guards.writableTicketId(partner, params);
        Integer parentId = request.parentId() != null ? guards.commentOnTicket(ticketId, request.parentId()) : null;
        var comment = ticketService.createComment(
                ticketId,
                parentId,
                new MemberIdentity(partner.askingStationUid(), request.remoteMemberId()),
                request.content());
        guards.cacheName(partner, request.remoteMemberId(), request.displayName());
        return comment;
    }

    private int labelTicket(
            ServingPartner partner,
            PathParams params,
            RemoteLabelActionRequest request,
            BoardTicketHistoryAction action) {
        int boardId = guards.writableBoardId(partner, params);
        int ticketId = guards.ticketId(boardId, params.integer("ticketNumber"));
        var label = guards.labelOnBoard(boardId, params.integer("labelId"));
        if (action == BoardTicketHistoryAction.LABEL_ADDED) {
            boardService.addLabelToTicket(ticketId, label.id());
        } else {
            boardService.removeLabelFromTicket(ticketId, label.id());
        }
        ticketService.logHistory(
                ticketId,
                action,
                label.name(),
                new MemberIdentity(partner.askingStationUid(), request.remoteMemberId()));
        guards.cacheName(partner, request.remoteMemberId(), request.displayName());
        return ticketId;
    }

    /**
     * Returns the comments of a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the comments with resolved authors
     */
    public List<CommentResponse> proxyGetComments(int partnerId, String boardKey, int ticketNumber) {
        return transport.getList(
                locator.requirePartner(partnerId),
                RemoteBoardTicketDetailRoutes.GET_COMMENTS.at(boardKey, ticketNumber),
                CommentResponse.class);
    }

    /**
     * Adds a comment to a ticket on a federated board.
     *
     * @param partnerId      the partner record id
     * @param boardKey       the board short key
     * @param ticketNumber   the board relative ticket number
     * @param parentId       the parent comment for replies
     * @param content        the comment content
     * @param remoteMemberId the authoring member on the partner station
     * @param displayName    the display name of the authoring member
     * @return the created comment
     */
    public BoardComment proxyAddComment(
            int partnerId,
            String boardKey,
            int ticketNumber,
            Integer parentId,
            String content,
            UUID remoteMemberId,
            String displayName) {
        log.info(
                "Federated comment added on partner {} board {} ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberId);
        return transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketDetailRoutes.ADD_COMMENT.at(boardKey, ticketNumber),
                new RemoteCommentRequest(remoteMemberId, displayName, parentId, content),
                BoardComment.class);
    }

    /**
     * Returns the checklist of a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the checklist items
     */
    public List<BoardChecklistItem> proxyGetChecklist(int partnerId, String boardKey, int ticketNumber) {
        return transport.getList(
                locator.requirePartner(partnerId),
                RemoteBoardTicketDetailRoutes.GET_CHECKLIST.at(boardKey, ticketNumber),
                BoardChecklistItem.class);
    }

    /**
     * Adds a checklist item to a ticket on a federated board.
     *
     * @param partnerId       the partner record id
     * @param boardKey        the board short key
     * @param ticketNumber    the board relative ticket number
     * @param title           the item title
     * @param remoteMemberUid the acting member on the partner station
     * @param displayName     the display name of the acting member
     * @return the created item
     */
    public BoardChecklistItem proxyAddChecklistItem(
            int partnerId, String boardKey, int ticketNumber, String title, UUID remoteMemberUid, String displayName) {
        log.info(
                "Federated checklist item added on partner {} board {} ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberUid);
        return transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketDetailRoutes.ADD_CHECKLIST_ITEM.at(boardKey, ticketNumber),
                new RemoteChecklistItemRequest(title, remoteMemberUid, displayName),
                BoardChecklistItem.class);
    }

    /**
     * Updates a checklist item of a ticket on a federated board.
     *
     * @param partnerId       the partner record id
     * @param boardKey        the board short key
     * @param ticketNumber    the board relative ticket number
     * @param itemId          the checklist item id
     * @param title           the new title
     * @param checked         whether the item is checked
     * @param remoteMemberUid the acting member on the partner station
     * @param displayName     the display name of the acting member
     */
    public void proxyUpdateChecklistItem(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int itemId,
            String title,
            boolean checked,
            UUID remoteMemberUid,
            String displayName) {
        log.info(
                "Federated checklist item {} updated on partner {} board {} ticket {} by member {}",
                itemId,
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberUid);
        transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketDetailRoutes.UPDATE_CHECKLIST_ITEM.at(boardKey, ticketNumber, itemId),
                new RemoteUpdateChecklistItemRequest(title, checked, remoteMemberUid, displayName),
                Void.class);
    }

    /**
     * Deletes a checklist item of a ticket on a federated board.
     *
     * @param partnerId       the partner record id
     * @param boardKey        the board short key
     * @param ticketNumber    the board relative ticket number
     * @param itemId          the checklist item id
     * @param remoteMemberUid the acting member on the partner station
     */
    public void proxyDeleteChecklistItem(
            int partnerId, String boardKey, int ticketNumber, int itemId, UUID remoteMemberUid) {
        log.info(
                "Federated checklist item {} deleted on partner {} board {} ticket {} by member {}",
                itemId,
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberUid);
        transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketDetailRoutes.DELETE_CHECKLIST_ITEM.at(boardKey, ticketNumber, itemId),
                null,
                Void.class);
    }

    /**
     * Returns the links of a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the links
     */
    public List<BoardTicketLink> proxyGetLinks(int partnerId, String boardKey, int ticketNumber) {
        return transport.getList(
                locator.requirePartner(partnerId),
                RemoteBoardTicketLinkRoutes.GET_LINKS.at(boardKey, ticketNumber),
                BoardTicketLink.class);
    }

    /**
     * Links two tickets of a federated board.
     *
     * @param partnerId          the partner record id
     * @param boardKey           the board short key
     * @param ticketNumber       the board relative ticket number
     * @param linkedTicketNumber the board relative number of the linked ticket
     * @param linkType           the link type
     * @param remoteMemberUid    the acting member on the partner station
     * @param displayName        the display name of the acting member
     */
    public void proxyCreateLink(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int linkedTicketNumber,
            LinkType linkType,
            UUID remoteMemberUid,
            String displayName) {
        log.info(
                "Federated ticket link on partner {} board {} from ticket {} to ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                linkedTicketNumber,
                remoteMemberUid);
        transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketLinkRoutes.CREATE_LINK.at(boardKey, ticketNumber),
                new RemoteLinkRequest(linkedTicketNumber, linkType, remoteMemberUid, displayName),
                Void.class);
    }

    /**
     * Removes the link between two tickets of a federated board.
     *
     * @param partnerId          the partner record id
     * @param boardKey           the board short key
     * @param ticketNumber       the board relative ticket number
     * @param linkedTicketNumber the board relative number of the linked ticket
     * @param remoteMemberUid    the acting member on the partner station
     * @param displayName        the display name of the acting member
     */
    public void proxyDeleteLink(
            int partnerId,
            String boardKey,
            int ticketNumber,
            int linkedTicketNumber,
            UUID remoteMemberUid,
            String displayName) {
        log.info(
                "Federated ticket unlink on partner {} board {} from ticket {} to ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                linkedTicketNumber,
                remoteMemberUid);
        transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketLinkRoutes.DELETE_LINK.at(boardKey, ticketNumber, linkedTicketNumber),
                new RemoteDeleteLinkRequest(remoteMemberUid, displayName),
                Void.class);
    }

    /**
     * Returns the labels assigned to a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the assigned labels
     */
    public List<BoardLabel> proxyGetTicketLabels(int partnerId, String boardKey, int ticketNumber) {
        return transport.getList(
                locator.requirePartner(partnerId),
                RemoteBoardTicketLinkRoutes.GET_TICKET_LABELS.at(boardKey, ticketNumber),
                BoardLabel.class);
    }

    /**
     * Assigns a label to a ticket on a federated board.
     *
     * @param partnerId      the partner record id
     * @param boardKey       the board short key
     * @param ticketNumber   the board relative ticket number
     * @param labelId        the label id
     * @param remoteMemberId the acting member on the partner station
     * @param displayName    the display name of the acting member
     * @return the labels assigned to the ticket afterwards
     */
    public List<BoardLabel> proxyAddTicketLabel(
            int partnerId, String boardKey, int ticketNumber, int labelId, UUID remoteMemberId, String displayName) {
        log.info(
                "Federated label {} added to ticket {} on partner {} board {} by member {}",
                labelId,
                ticketNumber,
                partnerId,
                boardKey,
                remoteMemberId);
        return transport.sendList(
                locator.requirePartner(partnerId),
                RemoteBoardTicketLinkRoutes.ADD_TICKET_LABEL.at(boardKey, ticketNumber, labelId),
                new RemoteLabelActionRequest(remoteMemberId, displayName),
                BoardLabel.class);
    }

    /**
     * Removes a label from a ticket on a federated board.
     *
     * @param partnerId      the partner record id
     * @param boardKey       the board short key
     * @param ticketNumber   the board relative ticket number
     * @param labelId        the label id
     * @param remoteMemberId the acting member on the partner station
     * @param displayName    the display name of the acting member
     */
    public void proxyRemoveTicketLabel(
            int partnerId, String boardKey, int ticketNumber, int labelId, UUID remoteMemberId, String displayName) {
        log.info(
                "Federated label {} removed from ticket {} on partner {} board {} by member {}",
                labelId,
                ticketNumber,
                partnerId,
                boardKey,
                remoteMemberId);
        transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketLinkRoutes.REMOVE_TICKET_LABEL.at(boardKey, ticketNumber, labelId),
                new RemoteLabelActionRequest(remoteMemberId, displayName),
                Void.class);
    }

    /**
     * Returns the watchers of a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the local and federated watchers
     */
    public WatcherResponse proxyGetWatchers(int partnerId, String boardKey, int ticketNumber) {
        return transport.get(
                locator.requirePartner(partnerId),
                RemoteBoardTicketDetailRoutes.GET_WATCHERS.at(boardKey, ticketNumber),
                WatcherResponse.class);
    }

    /**
     * Subscribes a partner member to a ticket on a federated board.
     *
     * @param partnerId      the partner record id
     * @param boardKey       the board short key
     * @param ticketNumber   the board relative ticket number
     * @param remoteMemberId the member on the partner station
     */
    public void proxyWatchTicket(int partnerId, String boardKey, int ticketNumber, UUID remoteMemberId) {
        transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketDetailRoutes.WATCH_TICKET.at(boardKey, ticketNumber),
                new RemoteWatchRequest(remoteMemberId),
                Void.class);
    }

    /**
     * Unsubscribes a partner member from a ticket on a federated board.
     *
     * @param partnerId      the partner record id
     * @param boardKey       the board short key
     * @param ticketNumber   the board relative ticket number
     * @param remoteMemberId the member on the partner station
     */
    public void proxyUnwatchTicket(int partnerId, String boardKey, int ticketNumber, UUID remoteMemberId) {
        transport.send(
                locator.requirePartner(partnerId),
                RemoteBoardTicketDetailRoutes.UNWATCH_TICKET.at(boardKey, ticketNumber),
                new RemoteWatchRequest(remoteMemberId),
                Void.class);
    }

    /**
     * Returns the attachments of a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the attachments
     */
    public List<BoardTicketAttachment> proxyGetAttachments(int partnerId, String boardKey, int ticketNumber) {
        return transport.getList(
                locator.requirePartner(partnerId),
                RemoteBoardTicketRoutes.GET_ATTACHMENTS.at(boardKey, ticketNumber),
                BoardTicketAttachment.class);
    }
}
