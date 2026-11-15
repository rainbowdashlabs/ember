/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentFilter;
import dev.chojo.ember.feature.comment.entity.CommentWriter;
import dev.chojo.ember.feature.comment.entity.NewComment;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupComment;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteCommentDeleteRequest;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteCommentUpdateRequest;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * The comments on an event group shared between stations, from both ends.
 *
 * <p>Serving, every request is held to the event groups shared with the partner it arrived on, so a
 * partner cannot read or write under another event group by its id, and a comment is only changed
 * or removed by the partner's member who wrote it. Whom a partner's comment tells here the event
 * group's comment target decides: a new top-level comment reaches the appointment managers, nothing
 * else does.
 *
 * <p>Asking, this station's members read and write the thread at the station holding the event
 * group; nobody here moderates it.
 */
@Singleton
public class EventGroupCommentFederation implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(EventGroupCommentFederation.class);

    private final CommentService comments;
    private final EventGroupPartnerService partners;
    private final MemberNameResolver names;
    private final EventFederationRepository nameCache;
    private final FederationTransport transport;
    private final FederationEntityResolver entityResolver;

    @Inject
    public EventGroupCommentFederation(
            CommentService comments,
            EventGroupPartnerService partners,
            MemberNameResolver names,
            EventFederationRepository nameCache,
            FederationTransport transport,
            FederationEntityResolver entityResolver) {
        this.comments = comments;
        this.partners = partners;
        this.names = names;
        this.nameCache = nameCache;
        this.transport = transport;
        this.entityResolver = entityResolver;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(
                RemoteEventGroupRoutes.LIST_COMMENTS,
                (partner, params, body) -> serveComments(partner, params.integer("id")));
        endpoints.<RemoteEventGroupComment, CommentResponse>serve(
                RemoteEventGroupRoutes.CREATE_COMMENT,
                (partner, params, body) -> serveNewComment(partner, params.integer("id"), body));
        endpoints.<RemoteCommentUpdateRequest, CommentResponse>serve(
                RemoteEventGroupRoutes.UPDATE_COMMENT,
                (partner, params, body) ->
                        serveCommentEdit(partner, params.integer("id"), params.integer("commentId"), body));
        endpoints.<RemoteCommentDeleteRequest, Void>serve(
                RemoteEventGroupRoutes.DELETE_COMMENT, (partner, params, body) -> {
                    serveCommentDeletion(partner, params.integer("id"), params.integer("commentId"), body);
                    return null;
                });
    }

    private List<CommentResponse> serveComments(ServingPartner partner, int eventGroupId) {
        partners.requireSharedWith(partner, eventGroupId);
        return comments.list(CommentEntityType.EVENT_GROUP, eventGroupId, CommentFilter.ALL).stream()
                .map(this::toResponse)
                .toList();
    }

    private CommentResponse serveNewComment(ServingPartner partner, int eventGroupId, RemoteEventGroupComment request) {
        partners.requireSharedWith(partner, eventGroupId);
        if (request.content() == null || request.content().isBlank()) {
            throw EventRefusal.PARTNER_COMMENT_NEEDS_TEXT.raise();
        }
        var target = comments.target(CommentEntityType.EVENT_GROUP, eventGroupId)
                .orElseThrow(EventRefusal.EVENT_GROUP_NOT_HERE::raise);
        var author = new MemberIdentity(partner.askingStationUid(), request.memberUid());
        var comment = comments.createOn(
                target,
                CommentWriter.partner(author, request.displayName()),
                new NewComment(request.parentId(), null, request.content()));
        nameCache.cacheName(partner.partnerId(), request.memberUid(), request.displayName());
        log.info("Comment {} created on event group {} (partner {})", comment.id(), eventGroupId, partner.partnerId());
        return toResponse(comment);
    }

    private CommentResponse serveCommentEdit(
            ServingPartner partner, int eventGroupId, int commentId, RemoteCommentUpdateRequest request) {
        partners.requireSharedWith(partner, eventGroupId);
        if (request.content() == null || request.content().isBlank()) {
            throw EventRefusal.PARTNER_COMMENT_CHANGE_NEEDS_TEXT.raise();
        }
        var comment = requireAuthor(
                partner,
                eventGroupId,
                commentId,
                request.remoteMemberUid(),
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_HERE_ON_UPDATE,
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_YOURS_TO_EDIT);
        var author = new MemberIdentity(partner.askingStationUid(), request.remoteMemberUid());
        var updated = comments.update(comment, CommentWriter.partner(author, ""), request.content())
                .orElseThrow(EventRefusal.REMOTE_EVENT_COMMENT_NOT_HERE_AFTER_UPDATE::raise);
        log.info("Comment {} on event group {} edited (partner {})", commentId, eventGroupId, partner.partnerId());
        return toResponse(updated);
    }

    private void serveCommentDeletion(
            ServingPartner partner, int eventGroupId, int commentId, RemoteCommentDeleteRequest request) {
        partners.requireSharedWith(partner, eventGroupId);
        var comment = requireAuthor(
                partner,
                eventGroupId,
                commentId,
                request.remoteMemberUid(),
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_HERE_ON_DELETE,
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_YOURS_TO_DELETE);
        if (!comments.delete(comment)) throw EventRefusal.PARTNER_COMMENT_NOT_DELETED.raise();
        log.info("Comment {} on event group {} deleted (partner {})", commentId, eventGroupId, partner.partnerId());
    }

    /**
     * The comment under the event group, written by the partner's member who asks.
     *
     * @param missing  what to refuse with when the comment is not under that event group
     * @param notYours what to refuse with when somebody else wrote it
     */
    private Comment requireAuthor(
            ServingPartner partner,
            int eventGroupId,
            int commentId,
            UUID memberUid,
            Refusal missing,
            Refusal notYours) {
        var comment = comments.findById(CommentEntityType.EVENT_GROUP, commentId)
                .filter(found -> found.targetId() == eventGroupId)
                .orElseThrow(missing::raise);
        var author = comment.author();
        if (author == null || !author.sameMember(new MemberIdentity(partner.askingStationUid(), memberUid))) {
            throw notYours.raise();
        }
        return comment;
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponseMapper.fromEventGroup(names, comment);
    }

    /**
     * The comments on a partner's event group, as the partner answers them.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId      the event group, by its id at the partner
     * @return the comments, oldest first
     */
    public List<CommentResponse> listAtPartner(int stationId, UUID partnerStationUid, int eventGroupId) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        return transport.getList(partner, RemoteEventGroupRoutes.LIST_COMMENTS.at(eventGroupId), CommentResponse.class);
    }

    /**
     * Comments on a partner's event group as one of this station's members.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId      the event group, by its id at the partner
     * @param memberUid         the member writing, by the id partners know them by
     * @param displayName       the name the partner shows for them
     * @param parentId          the comment answered, or null
     * @param content           the comment
     * @return the comment as the partner stored it
     */
    public CommentResponse createAtPartner(
            int stationId,
            UUID partnerStationUid,
            int eventGroupId,
            UUID memberUid,
            String displayName,
            @Nullable Integer parentId,
            String content) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var created = transport.send(
                partner,
                RemoteEventGroupRoutes.CREATE_COMMENT.at(eventGroupId),
                new RemoteEventGroupComment(memberUid, displayName, parentId, content),
                CommentResponse.class);
        log.info("Station {} commented on event group {} at partner {}", stationId, eventGroupId, partner.id());
        return created;
    }

    /**
     * Changes a comment one of this station's members wrote on a partner's event group.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId      the event group, by its id at the partner
     * @param commentId         the comment
     * @param memberUid         the member who wrote it, by the id partners know them by
     * @param content           the new text
     * @return the comment as the partner now holds it
     */
    public CommentResponse updateAtPartner(
            int stationId, UUID partnerStationUid, int eventGroupId, int commentId, UUID memberUid, String content) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var updated = transport.send(
                partner,
                RemoteEventGroupRoutes.UPDATE_COMMENT.at(eventGroupId, commentId),
                new RemoteCommentUpdateRequest(memberUid, content),
                CommentResponse.class);
        log.info("Station {} edited its comment {} at partner {}", stationId, commentId, partner.id());
        return updated;
    }

    /**
     * Removes a comment one of this station's members wrote on a partner's event group.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId      the event group, by its id at the partner
     * @param commentId         the comment
     * @param memberUid         the member who wrote it, by the id partners know them by
     */
    public void deleteAtPartner(
            int stationId, UUID partnerStationUid, int eventGroupId, int commentId, UUID memberUid) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        transport.deliver(
                partner,
                RemoteEventGroupRoutes.DELETE_COMMENT.at(eventGroupId, commentId),
                new RemoteCommentDeleteRequest(memberUid));
        log.info("Station {} deleted its comment {} at partner {}", stationId, commentId, partner.id());
    }
}
