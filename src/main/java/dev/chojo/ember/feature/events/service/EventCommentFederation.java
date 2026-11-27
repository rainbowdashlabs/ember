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
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The comments on an appointment shared between stations, from both ends.
 *
 * <p>Serving, every request is held to the appointments shared with the partner it arrived on, so a
 * partner cannot read or write under another appointment by its id, and a comment is only changed or
 * removed by the partner's member who wrote it. Whom a partner's comment notifies here the appointment's
 * comment target decides: a new top-level comment reaches the appointment managers, nothing else does.
 *
 * <p>Asking, this station's members read and write the thread at the station holding the appointment;
 * nobody here moderates it. The twin for event groups is {@link EventGroupCommentFederation}.
 */
@Singleton
public class EventCommentFederation implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(EventCommentFederation.class);

    private final CommentService commentService;
    private final EventShareService shares;
    private final MemberNameResolver memberNameResolver;
    private final FederationTransport transport;
    private final FederationEntityResolver entityResolver;

    @Inject
    public EventCommentFederation(
            CommentService commentService,
            EventShareService shares,
            MemberNameResolver memberNameResolver,
            FederationTransport transport,
            FederationEntityResolver entityResolver) {
        this.commentService = commentService;
        this.shares = shares;
        this.memberNameResolver = memberNameResolver;
        this.transport = transport;
        this.entityResolver = entityResolver;
    }

    /**
     * Registers the serving functions of the comments on the appointments this station shares.
     */
    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(RemoteEventRoutes.LIST_COMMENTS, (partner, params, body) -> {
            shares.requireSharedWith(partner, params.integer("eventId"));
            return listComments(params.integer("eventId"));
        });
        endpoints.<RemoteEventRoutes.RemoteCommentRequest, CommentResponse>serve(
                RemoteEventRoutes.CREATE_COMMENT,
                (partner, params, body) -> serveNewComment(partner, params.integer("eventId"), body));
        endpoints.<RemoteEventRoutes.RemoteCommentUpdateRequest, CommentResponse>serve(
                RemoteEventRoutes.UPDATE_COMMENT,
                (partner, params, body) -> serveCommentEdit(partner, params.integer("commentId"), body));
        endpoints.<RemoteEventRoutes.RemoteCommentDeleteRequest, Void>serve(
                RemoteEventRoutes.DELETE_COMMENT, (partner, params, body) -> {
                    serveCommentDeletion(partner, params.integer("commentId"), body);
                    return null;
                });
    }

    private CommentResponse serveNewComment(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteCommentRequest request) {
        shares.requireSharedWith(partner, eventId);
        if (request.content() == null || request.content().isBlank()) {
            throw EventRefusal.PARTNER_COMMENT_NEEDS_TEXT.raise();
        }
        return createRemoteComment(
                partner.row(),
                eventId,
                request.remoteMemberUid(),
                request.displayName(),
                request.parentId(),
                request.content(),
                commentDay(request.eventDate()));
    }

    /**
     * Reads the optional occurrence date a comment is scoped to. Older peers omit the field
     * entirely, which keeps the comment attached to the whole event rather than one date.
     */
    private static @Nullable LocalDate commentDay(@Nullable String eventDate) {
        if (eventDate == null || eventDate.isBlank()) return null;
        try {
            return LocalDate.parse(eventDate);
        } catch (DateTimeParseException e) {
            throw EventRefusal.PARTNER_COMMENT_DAY_NOT_A_DATE.raise();
        }
    }

    private CommentResponse serveCommentEdit(
            ServingPartner partner, int commentId, RemoteEventRoutes.RemoteCommentUpdateRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw EventRefusal.PARTNER_COMMENT_CHANGE_NEEDS_TEXT.raise();
        }
        return updateRemoteComment(partner.row(), commentId, request.remoteMemberUid(), request.content());
    }

    private void serveCommentDeletion(
            ServingPartner partner, int commentId, RemoteEventRoutes.RemoteCommentDeleteRequest request) {
        if (!deleteRemoteComment(partner.row(), commentId, request.remoteMemberUid())) {
            throw EventRefusal.PARTNER_COMMENT_NOT_DELETED.raise();
        }
    }

    /**
     * Converts a comment to an enriched response with federated author information.
     */
    public CommentResponse toCommentResponse(Comment comment) {
        return CommentResponseMapper.fromEvent(memberNameResolver, comment);
    }

    /**
     * Lists comments for an event, enriched with federated author info.
     */
    public List<CommentResponse> listComments(int eventId) {
        return commentService.list(CommentEntityType.EVENT, eventId, CommentFilter.ALL).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    /**
     * Creates a comment from a remote federated partner. Whom it notifies here the appointment's comment
     * target decides: a new top-level comment reaches the appointment managers, and nothing else does.
     */
    public CommentResponse createRemoteComment(
            FederationPartner partner,
            int eventId,
            UUID remoteMemberUid,
            String displayName,
            @Nullable Integer parentId,
            String content,
            @Nullable LocalDate eventDate) {
        var target = commentService
                .target(CommentEntityType.EVENT, eventId)
                .orElseThrow(EventRefusal.EVENT_NOT_SHARED_WITH_PARTNER::raise);
        var author = new MemberIdentity(partner.partnerStationId(), remoteMemberUid);
        var comment = commentService.createOn(
                target, CommentWriter.partner(author, displayName), new NewComment(parentId, eventDate, content));
        shares.cacheName(partner.id(), remoteMemberUid, displayName);
        log.info("Comment {} created on event {} (partner {})", comment.id(), eventId, partner.id());
        return toCommentResponse(comment);
    }

    /**
     * Updates a comment from a remote federated partner after verifying ownership.
     */
    public CommentResponse updateRemoteComment(
            FederationPartner partner, int commentId, UUID remoteMemberUid, String content) {
        var comment = requireCommentAuthor(
                commentId,
                partner,
                remoteMemberUid,
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_HERE_ON_UPDATE,
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_YOURS_TO_EDIT);
        var author = Objects.requireNonNull(comment.author(), "a comment without an author is refused as not theirs");
        var updated = commentService
                .update(comment, CommentWriter.partner(author, ""), content)
                .orElseThrow(EventRefusal.REMOTE_EVENT_COMMENT_NOT_HERE_AFTER_UPDATE::raise);
        log.info("Comment {} on an event edited (partner {})", commentId, partner.id());
        return toCommentResponse(updated);
    }

    /**
     * Deletes a comment from a remote federated partner after verifying ownership.
     */
    public boolean deleteRemoteComment(FederationPartner partner, int commentId, UUID remoteMemberUid) {
        var comment = requireCommentAuthor(
                commentId,
                partner,
                remoteMemberUid,
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_HERE_ON_DELETE,
                EventRefusal.REMOTE_EVENT_COMMENT_NOT_YOURS_TO_DELETE);
        boolean deleted = commentService.delete(comment);
        if (deleted) log.info("Comment {} on an event deleted (partner {})", commentId, partner.id());
        return deleted;
    }

    /**
     * Lists the comments on a partner's appointment, as the partner answers them.
     */
    public List<CommentResponse> listFederatedComments(int stationId, UUID partnerStationUid, int eventId) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        return transport.getList(partner, RemoteEventRoutes.LIST_COMMENTS.at(eventId), CommentResponse.class);
    }

    /**
     * Comments on a partner's appointment as one of this station's members.
     */
    public CommentResponse createFederatedComment(
            int stationId,
            UUID partnerStationUid,
            int eventId,
            UUID memberUid,
            String displayName,
            Integer parentId,
            String content,
            LocalDate eventDate) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var body = new RemoteEventRoutes.RemoteCommentRequest(
                memberUid, displayName, parentId, content, eventDate != null ? eventDate.toString() : null);
        var created =
                transport.send(partner, RemoteEventRoutes.CREATE_COMMENT.at(eventId), body, CommentResponse.class);
        log.info("Station {} commented on event {} at partner {}", stationId, eventId, partner.id());
        return created;
    }

    /**
     * Edits a comment one of this station's members wrote on a partner's appointment.
     */
    public CommentResponse updateFederatedComment(
            int stationId, UUID partnerStationUid, int commentId, UUID memberUid, String content) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var updated = transport.send(
                partner,
                RemoteEventRoutes.UPDATE_COMMENT.at(commentId),
                new RemoteEventRoutes.RemoteCommentUpdateRequest(memberUid, content),
                CommentResponse.class);
        log.info("Station {} edited its comment {} at partner {}", stationId, commentId, partner.id());
        return updated;
    }

    /**
     * Deletes a comment one of this station's members wrote on a partner's appointment.
     */
    public void deleteFederatedComment(int stationId, UUID partnerStationUid, int commentId, UUID memberUid) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        transport.deliver(
                partner,
                RemoteEventRoutes.DELETE_COMMENT.at(commentId),
                new RemoteEventRoutes.RemoteCommentDeleteRequest(memberUid));
        log.info("Station {} deleted its comment {} at partner {}", stationId, commentId, partner.id());
    }

    /**
     * Verifies the comment exists and was authored by the given federated member.
     *
     * @param missing  what to refuse with when the comment is not here
     * @param notYours what to refuse with when somebody else wrote it
     */
    private Comment requireCommentAuthor(
            int commentId, FederationPartner partner, UUID memberUid, Refusal missing, Refusal notYours) {
        var comment =
                commentService.findById(CommentEntityType.EVENT, commentId).orElseThrow(missing::raise);
        var expectedIdentity = new MemberIdentity(partner.partnerStationId(), memberUid);
        var author = comment.author();
        if (author == null || !author.sameMember(expectedIdentity)) {
            throw notYours.raise();
        }
        return comment;
    }
}
