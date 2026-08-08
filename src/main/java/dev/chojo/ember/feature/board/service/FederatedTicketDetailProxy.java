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
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Proxies everything that hangs off a single ticket of a federated board: comments, checklist
 * items, links, label assignments, watchers and attachments.
 */
@Singleton
public class FederatedTicketDetailProxy {
    private static final Logger log = LoggerFactory.getLogger(FederatedTicketDetailProxy.class);

    private final BoardService boardService;
    private final BoardTicketService ticketService;
    private final MemberNameResolver memberNameResolver;
    private final FederatedBoardRemoteGateway gateway;
    private final FederatedBoardLocator locator;

    @Inject
    public FederatedTicketDetailProxy(
            BoardService boardService,
            BoardTicketService ticketService,
            MemberNameResolver memberNameResolver,
            FederatedBoardRemoteGateway gateway,
            FederatedBoardLocator locator) {
        this.boardService = boardService;
        this.ticketService = ticketService;
        this.memberNameResolver = memberNameResolver;
        this.gateway = gateway;
        this.locator = locator;
    }

    /**
     * Returns the comments of a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the comments with resolved authors
     */
    public List<BoardComment> proxyGetComments(int partnerId, String boardKey, int ticketNumber) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(partner, ticketPath(boardKey, ticketNumber) + "/comments", BoardComment.class);
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        return ticketService.findComments(ticketId).stream()
                .map(c -> c.author() != null ? c.withAuthor(memberNameResolver.enrichDisplay(c.author())) : c)
                .toList();
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
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated comment added on partner {} board {} ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberId);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("remoteMemberId", remoteMemberId);
            body.put("displayName", displayName != null ? displayName : "");
            body.put("parentId", parentId != null ? parentId : "");
            body.put("content", content != null ? content : "");
            return gateway.post(partner, ticketPath(boardKey, ticketNumber) + "/comments", body, BoardComment.class);
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        var authorIdentity = new MemberIdentity(partner.partnerStationId(), remoteMemberId);
        var comment = ticketService.createComment(ticketId, parentId, authorIdentity, content);
        locator.cacheName(partnerId, remoteMemberId, displayName);
        return comment;
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
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(
                    partner, ticketPath(boardKey, ticketNumber) + "/checklist", BoardChecklistItem.class);
        }
        return ticketService.findChecklistItems(locator.resolveTicketId(partner, boardKey, ticketNumber));
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
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated checklist item added on partner {} board {} ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberUid);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("title", title);
            putActor(body, remoteMemberUid, displayName);
            return gateway.post(
                    partner, ticketPath(boardKey, ticketNumber) + "/checklist", body, BoardChecklistItem.class);
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        locator.cacheNameIfPresent(partnerId, remoteMemberUid, displayName);
        return ticketService.addChecklistItem(ticketId, title, 0);
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
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated checklist item {} updated on partner {} board {} ticket {} by member {}",
                itemId,
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberUid);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("title", title);
            body.put("checked", checked);
            putActor(body, remoteMemberUid, displayName);
            gateway.put(partner, ticketPath(boardKey, ticketNumber) + "/checklist/" + itemId, body);
            return;
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        locator.cacheNameIfPresent(partnerId, remoteMemberUid, displayName);
        ticketService.updateChecklistItem(itemId, ticketId, title, checked, 0);
    }

    /**
     * Deletes a checklist item of a ticket on a federated board.
     *
     * @param partnerId       the partner record id
     * @param boardKey        the board short key
     * @param ticketNumber    the board relative ticket number
     * @param itemId          the checklist item id
     * @param remoteMemberUid the acting member on the partner station
     * @param displayName     the display name of the acting member
     */
    public void proxyDeleteChecklistItem(
            int partnerId, String boardKey, int ticketNumber, int itemId, UUID remoteMemberUid, String displayName) {
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated checklist item {} deleted on partner {} board {} ticket {} by member {}",
                itemId,
                partnerId,
                boardKey,
                ticketNumber,
                remoteMemberUid);
        if (partner.isRemote()) {
            gateway.delete(partner, ticketPath(boardKey, ticketNumber) + "/checklist/" + itemId);
            return;
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        locator.cacheNameIfPresent(partnerId, remoteMemberUid, displayName);
        ticketService.deleteChecklistItem(itemId, ticketId, 0);
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
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(partner, ticketPath(boardKey, ticketNumber) + "/links", BoardTicketLink.class);
        }
        return ticketService.findLinks(locator.resolveTicketId(partner, boardKey, ticketNumber));
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
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated ticket link on partner {} board {} from ticket {} to ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                linkedTicketNumber,
                remoteMemberUid);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            body.put("linkedTicketNumber", linkedTicketNumber);
            body.put("linkType", linkType);
            putActor(body, remoteMemberUid, displayName);
            gateway.post(partner, ticketPath(boardKey, ticketNumber) + "/links", body);
            return;
        }
        var link = resolveLinkContext(
                partnerId, partner, boardKey, ticketNumber, linkedTicketNumber, remoteMemberUid, displayName);
        ticketService.linkTickets(link.ticketId(), link.linkedTicketId(), linkType, link.actorIdentity());
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
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated ticket unlink on partner {} board {} from ticket {} to ticket {} by member {}",
                partnerId,
                boardKey,
                ticketNumber,
                linkedTicketNumber,
                remoteMemberUid);
        if (partner.isRemote()) {
            var body = new HashMap<String, Object>();
            putActor(body, remoteMemberUid, displayName);
            gateway.delete(partner, ticketPath(boardKey, ticketNumber) + "/links/" + linkedTicketNumber, body);
            return;
        }
        var link = resolveLinkContext(
                partnerId, partner, boardKey, ticketNumber, linkedTicketNumber, remoteMemberUid, displayName);
        ticketService.unlinkTickets(link.ticketId(), link.linkedTicketId(), link.actorIdentity());
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
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(partner, ticketPath(boardKey, ticketNumber) + "/labels", BoardLabel.class);
        }
        return boardService.findLabelsForTicket(locator.resolveTicketId(partner, boardKey, ticketNumber));
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
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated label {} added to ticket {} on partner {} board {} by member {}",
                labelId,
                ticketNumber,
                partnerId,
                boardKey,
                remoteMemberId);
        if (partner.isRemote()) {
            return gateway.postList(
                    partner,
                    ticketPath(boardKey, ticketNumber) + "/labels/" + labelId,
                    new LabelActionBody(remoteMemberId, displayName),
                    BoardLabel.class);
        }
        int boardId = locator.resolveBoardId(boardKey, partner);
        int ticketId = locator.resolveTicketId(boardId, ticketNumber);
        boardService.addLabelToTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId,
                BoardTicketHistoryAction.LABEL_ADDED,
                labelName(boardId, labelId),
                new MemberIdentity(partner.partnerStationId(), remoteMemberId));
        if (displayName != null) locator.cacheName(partnerId, remoteMemberId, displayName);
        return boardService.findLabelsForTicket(ticketId);
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
        var partner = locator.requirePartner(partnerId);
        log.info(
                "Federated label {} removed from ticket {} on partner {} board {} by member {}",
                labelId,
                ticketNumber,
                partnerId,
                boardKey,
                remoteMemberId);
        if (partner.isRemote()) {
            gateway.post(
                    partner,
                    ticketPath(boardKey, ticketNumber) + "/labels/" + labelId + "/remove",
                    new LabelActionBody(remoteMemberId, displayName));
            return;
        }
        int boardId = locator.resolveBoardId(boardKey, partner);
        int ticketId = locator.resolveTicketId(boardId, ticketNumber);
        String labelName = labelName(boardId, labelId);
        boardService.removeLabelFromTicket(ticketId, labelId);
        ticketService.logHistory(
                ticketId,
                BoardTicketHistoryAction.LABEL_REMOVED,
                labelName,
                new MemberIdentity(partner.partnerStationId(), remoteMemberId));
        if (displayName != null) locator.cacheName(partnerId, remoteMemberId, displayName);
    }

    /**
     * Returns the watchers of a ticket on a federated board.
     *
     * @param partnerId    the partner record id
     * @param boardKey     the board short key
     * @param ticketNumber the board relative ticket number
     * @return the local and federated watchers
     */
    public FederatedWatcherData proxyGetWatchers(int partnerId, String boardKey, int ticketNumber) {
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.get(partner, ticketPath(boardKey, ticketNumber) + "/watchers", FederatedWatcherData.class);
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        return new FederatedWatcherData(ticketService.findWatchers(ticketId), List.of());
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
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            gateway.post(partner, ticketPath(boardKey, ticketNumber) + "/watch", new RemoteMemberBody(remoteMemberId));
            return;
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        ticketService.addWatcher(ticketId, new MemberIdentity(partner.partnerStationId(), remoteMemberId));
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
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            gateway.delete(partner, ticketPath(boardKey, ticketNumber) + "/watch");
            return;
        }
        int ticketId = locator.resolveTicketId(partner, boardKey, ticketNumber);
        ticketService.removeWatcher(ticketId, new MemberIdentity(partner.partnerStationId(), remoteMemberId));
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
        var partner = locator.requirePartner(partnerId);
        if (partner.isRemote()) {
            return gateway.getList(
                    partner, ticketPath(boardKey, ticketNumber) + "/attachments", BoardTicketAttachment.class);
        }
        return ticketService.findAttachments(locator.resolveTicketId(partner, boardKey, ticketNumber));
    }

    private String labelName(int boardId, int labelId) {
        return boardService.findLabels(boardId).stream()
                .filter(l -> l.id() == labelId)
                .findFirst()
                .map(BoardLabel::name)
                .orElse("?");
    }

    private LinkContext resolveLinkContext(
            int partnerId,
            FederationPartner partner,
            String boardKey,
            int ticketNumber,
            int linkedTicketNumber,
            UUID remoteMemberUid,
            String displayName) {
        int boardId = locator.resolveBoardId(boardKey, partner);
        int ticketId = locator.resolveTicketId(boardId, ticketNumber);
        int linkedTicketId = locator.resolveTicketId(boardId, linkedTicketNumber);
        var actorIdentity = locator.remoteIdentity(partner, remoteMemberUid);
        locator.cacheNameIfPresent(partnerId, remoteMemberUid, displayName);
        return new LinkContext(ticketId, linkedTicketId, actorIdentity);
    }

    private void putActor(HashMap<String, Object> body, UUID remoteMemberUid, String displayName) {
        if (remoteMemberUid != null) body.put("remoteMemberUid", remoteMemberUid.toString());
        if (displayName != null) body.put("displayName", displayName);
    }

    private static String ticketPath(String boardKey, int ticketNumber) {
        return "/remote/boards/" + boardKey + "/tickets/" + ticketNumber;
    }

    private record LinkContext(int ticketId, int linkedTicketId, MemberIdentity actorIdentity) {}

    record LabelActionBody(UUID remoteMemberId, String displayName) {}

    record RemoteMemberBody(UUID remoteMemberId) {}

    /**
     * The watchers of a federated ticket, split into this station's members and the partner's.
     */
    public record FederatedWatcherData(List<Integer> local, List<Object> federated) {}
}
