/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-server event routes. They serve this station's shared events, their registrations and
 * their comments to a federation partner, authenticated by the RSA signature that
 * {@code AccessManager} already verified, through the serving functions of
 * {@code EventFederationService}.
 */
@Singleton
public class RemoteEventRoutes implements Routes {

    public static final FederationEndpoint LIST_EVENTS =
            FederationEndpoint.getList(FederationSurface.EVENT_SHARE, "/remote/events", SharedEvent.class);
    public static final FederationEndpoint GET_EVENT =
            FederationEndpoint.get(FederationSurface.EVENT_SHARE, "/remote/events/{id}", RemoteEventDetail.class);
    public static final FederationEndpoint REGISTER = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/events/{id}/register",
            RemoteRegistrationRequest.class,
            EventFederationRegistration.class);
    public static final FederationEndpoint WITHDRAW = FederationEndpoint.delete(
            FederationSurface.EVENT_SHARE, "/remote/events/{id}/register", RemoteRegistrationRequest.class, Void.class);

    /**
     * Putting a member back after a withdrawal, which this station allows for a few minutes.
     *
     * <p>A partner that has never heard of this endpoint simply never calls it, and its members keep
     * the behaviour they had: a withdrawal that stands. Nothing older breaks for want of it.
     */
    public static final FederationEndpoint UNDO_WITHDRAWAL = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/events/{id}/register/undo",
            RemoteRegistrationRequest.class,
            Void.class);

    /**
     * A partner confirming one of its own members, where this station has handed it that decision.
     *
     * <p>Refused where no such arrangement stands, and refused again where the places it was given are
     * already filled. The count is this station's either way, because the places are.
     */
    public static final FederationEndpoint CONFIRM_OWN = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/events/{id}/register/confirm",
            RemoteRegistrationRequest.class,
            Void.class);

    public static final FederationEndpoint LIST_REGISTRATIONS = FederationEndpoint.getList(
            FederationSurface.EVENT_SHARE, "/remote/events/{id}/registrations", EventFederationRegistration.class);
    public static final FederationEndpoint LIST_MEMBER_REGISTRATIONS = FederationEndpoint.getList(
            FederationSurface.EVENT_SHARE, "/remote/registrations/{memberUid}", RemoteMemberRegistration.class);
    public static final FederationEndpoint REGISTRATION_STATUS_WEBHOOK = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/webhook/event-registration-status",
            Void.class,
            FederatedEventRoutes.StatusResponse.class);
    public static final FederationEndpoint LIST_COMMENTS = FederationEndpoint.getList(
            FederationSurface.EVENT_SHARE, "/remote/events/{eventId}/comments", CommentResponse.class);
    public static final FederationEndpoint CREATE_COMMENT = FederationEndpoint.post(
            FederationSurface.EVENT_SHARE,
            "/remote/events/{eventId}/comments",
            RemoteCommentRequest.class,
            CommentResponse.class);
    public static final FederationEndpoint UPDATE_COMMENT = FederationEndpoint.put(
            FederationSurface.EVENT_SHARE,
            "/remote/events/comments/{commentId}",
            RemoteCommentUpdateRequest.class,
            CommentResponse.class);
    public static final FederationEndpoint DELETE_COMMENT = FederationEndpoint.delete(
            FederationSurface.EVENT_SHARE,
            "/remote/events/comments/{commentId}",
            RemoteCommentDeleteRequest.class,
            Void.class);

    /**
     * The files a shared event hands over, as the partner may have them.
     *
     * <p>Only the open ones travel. A file marked internal is kept back from the room, and a partner
     * station is further out than the room rather than closer to it.
     */
    public static final FederationEndpoint LIST_ATTACHMENTS = FederationEndpoint.getList(
            FederationSurface.EVENT_SHARE, "/remote/events/{eventId}/attachments", RemoteAttachment.class);

    /**
     * One such file, bytes and all.
     *
     * <p>The contract speaks JSON, so the bytes travel encoded in it. What may cross is what this
     * instance would accept as an upload in the first place, which is the one size anybody has
     * already configured.
     */
    public static final FederationEndpoint GET_ATTACHMENT_CONTENT = FederationEndpoint.get(
            FederationSurface.EVENT_SHARE,
            "/remote/events/{eventId}/attachments/{attachmentId}/content",
            RemoteAttachmentContent.class);

    public static final List<FederationEndpoint> CONTRACT = List.of(
            LIST_EVENTS,
            GET_EVENT,
            LIST_ATTACHMENTS,
            GET_ATTACHMENT_CONTENT,
            REGISTER,
            WITHDRAW,
            UNDO_WITHDRAWAL,
            CONFIRM_OWN,
            LIST_REGISTRATIONS,
            LIST_MEMBER_REGISTRATIONS,
            REGISTRATION_STATUS_WEBHOOK,
            LIST_COMMENTS,
            CREATE_COMMENT,
            UPDATE_COMMENT,
            DELETE_COMMENT);

    private final FederationEndpoints endpoints;

    @Inject
    public RemoteEventRoutes(FederationEndpoints endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(routes, prefix, CONTRACT, endpoints, binder -> binder.serve(LIST_EVENTS)
                .serve(GET_EVENT)
                .serve(LIST_ATTACHMENTS)
                .serve(GET_ATTACHMENT_CONTENT)
                .serveCreated(REGISTER)
                .serve(WITHDRAW)
                .serve(UNDO_WITHDRAWAL)
                .serve(CONFIRM_OWN)
                .serve(LIST_REGISTRATIONS)
                .serve(LIST_MEMBER_REGISTRATIONS)
                .serve(REGISTRATION_STATUS_WEBHOOK)
                .serve(LIST_COMMENTS)
                .serveCreated(CREATE_COMMENT)
                .serve(UPDATE_COMMENT)
                .serve(DELETE_COMMENT));
    }

    /**
     * @param places what this partner may do here, or {@code null} where the host decides as it always
     *         did. A peer that has never heard of this field ignores it and behaves as before
     */
    public record RemoteEventDetail(
            SharedEvent event,
            List<EventField> publicFields,
            @Nullable RemotePlaces places) {}

    /**
     * What a partner station has been given on one appointment, in its own terms.
     *
     * @param slotBudget how many places it may fill on a date, or {@code null} for no cap
     * @param decidesItself whether it confirms its own members rather than the host doing it
     */
    public record RemotePlaces(@Nullable Integer slotBudget, boolean decidesItself) {}

    /**
     * A file a shared event hands over, without its bytes: enough to list it and to ask for it.
     *
     * @param id       the attachment, which is what a request for the bytes names
     * @param name     what the partner's reader sees, which is the label where one was written
     * @param mimeType what kind of file it is
     * @param fileSize how big it is, so a reader knows what they are asking for
     */
    public record RemoteAttachment(int id, String name, String fileName, String mimeType, long fileSize) {
        public static RemoteAttachment of(EventAttachment attachment) {
            return new RemoteAttachment(
                    attachment.id(),
                    attachment.displayName(),
                    attachment.fileName(),
                    attachment.mimeType(),
                    attachment.fileSize());
        }
    }

    /**
     * One such file with its bytes, encoded because the contract between two instances speaks JSON.
     * The name is what the partner shows, the file name what a reader saving it ends up with.
     */
    public record RemoteAttachmentContent(
            int attachmentId, String name, String fileName, String mimeType, String base64) {}

    public record RemoteMemberRegistration(
            int eventId, String remoteMemberId, String eventDate, RegistrationStatus status, int partnerId) {}

    public record RemoteRegistrationRequest(UUID remoteMemberId, LocalDate eventDate) {}

    public record RemoteCommentRequest(
            UUID remoteMemberUid, String displayName, Integer parentId, String content, String eventDate) {}

    public record RemoteCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    public record RemoteCommentDeleteRequest(UUID remoteMemberUid) {}
}
