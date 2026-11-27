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
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldValue;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-server event routes. They serve this station's shared events, their registrations and
 * their comments to a federation partner, authenticated by the RSA signature that
 * {@code AccessManager} already verified, through the serving functions of
 * {@code EventPartnerRegistrationService} and {@code EventCommentFederation}.
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
            RemoteConfirmedOwn.class);

    public static final FederationEndpoint LIST_REGISTRATIONS = FederationEndpoint.getList(
            FederationSurface.EVENT_SHARE, "/remote/events/{id}/registrations", EventFederationRegistration.class);
    public static final FederationEndpoint LIST_MEMBER_REGISTRATIONS = FederationEndpoint.getList(
            FederationSurface.EVENT_SHARE, "/remote/registrations/{memberUid}", RemoteMemberRegistration.class);
    /**
     * The station holding an appointment notifying a partner that it decided about one of the partner's
     * members, or that the member moved up from the waiting list.
     */
    public static final FederationEndpoint REGISTRATION_STATUS_WEBHOOK = FederationEndpoint.post(
                    FederationSurface.EVENT_SHARE,
                    "/remote/webhook/event-registration-status",
                    RemoteRegistrationStatus.class,
                    Void.class)
            .keepingInStep();

    public static final FederationEndpoint EVENT_CHANGED_WEBHOOK = FederationEndpoint.post(
                    FederationSurface.EVENT_SHARE, "/remote/webhook/event-changed", RemoteEventChange.class, Void.class)
            .keepingInStep();
    public static final FederationEndpoint EVENT_CONTINUED_WEBHOOK = FederationEndpoint.post(
                    FederationSurface.EVENT_SHARE,
                    "/remote/webhook/event-continued",
                    RemoteEventHandover.class,
                    Void.class)
            .keepingInStep();
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
            EVENT_CHANGED_WEBHOOK,
            EVENT_CONTINUED_WEBHOOK,
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
                .serve(EVENT_CHANGED_WEBHOOK)
                .serve(EVENT_CONTINUED_WEBHOOK)
                .serve(LIST_COMMENTS)
                .serveCreated(CREATE_COMMENT)
                .serve(UPDATE_COMMENT)
                .serve(DELETE_COMMENT));
    }

    /**
     * @param places     what this partner may do here, or {@code null} where the host decides as it always
     *                   did. A peer that has never heard of this field ignores it and behaves as before
     * @param questions  the registration questions a partner's member answers on signing up: every one
     *                   the appointment asks except those naming members of the host, which mean nothing
     *                   at a partner
     * @param datePlaces how the places of the date in view stand, or {@code null} where the appointment
     *                   falls on no date
     */
    public record RemoteEventDetail(
            SharedEvent event,
            List<EventField> publicFields,
            @Nullable RemotePlaces places,
            List<RemoteQuestion> questions,
            @Nullable RemoteDatePlaces datePlaces) {}

    /**
     * A registration question as a partner station asks it of its member, under the type names partner
     * stations read.
     *
     * @param id           the question, which an answer names
     * @param name         what it asks
     * @param fieldType    the kind of answer it takes
     * @param options      the values a choice offers, or null
     * @param required     whether the sign-up is refused without an answer
     * @param defaultValue what an unanswered question is taken to mean, or null
     * @param min          the lowest number or shortest text it takes, or null
     * @param max          the highest number or longest text it takes, or null
     * @param description  what it explains below its label, or null
     * @param position     where it stands among the questions
     */
    public record RemoteQuestion(
            int id,
            String name,
            EventFieldType fieldType,
            @Nullable List<String> options,
            boolean required,
            @Nullable String defaultValue,
            @Nullable Integer min,
            @Nullable Integer max,
            @Nullable String description,
            int position) {

        /** The wire shape of a question of this station's appointment. */
        public static RemoteQuestion of(EventRegistrationField field) {
            var config = field.config();
            return new RemoteQuestion(
                    field.id(),
                    field.name(),
                    EventFieldType.of(field.fieldType()),
                    config.options(),
                    config.required(),
                    config.defaultValue(),
                    config.min(),
                    config.max(),
                    config.description(),
                    field.position());
        }

        /**
         * This question as the partner station's own sign-up form reads one.
         *
         * @param eventId the appointment at the station that asks it
         */
        public EventRegistrationField asField(int eventId) {
            var settings = new EventQuestionSettings(
                    options,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    required,
                    defaultValue,
                    min,
                    max,
                    false,
                    description);
            return new EventRegistrationField(
                    id, eventId, null, name, fieldType.fieldType(), settings, position, false);
        }
    }

    /**
     * How the places of one date of a shared appointment stand, so a partner knows before signing up
     * whether the sign-up lands on the waiting list.
     *
     * @param date    the date counted
     * @param limit   how many places there are, or null for no limit
     * @param taken   how many places accepted sign-ups hold, partners' members included
     * @param waiting how many sign-ups wait on the waiting list
     */
    public record RemoteDatePlaces(LocalDate date, @Nullable Integer limit, int taken, int waiting) {}

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

    /**
     * A partner's member and date, for signing up, withdrawing, putting back and confirming.
     *
     * @param answers the answers to the appointment's questions, sent with a sign-up only. Absent from a
     *                partner that never asks them; such a sign-up is taken where no question is required
     */
    public record RemoteRegistrationRequest(
            UUID remoteMemberId,
            LocalDate eventDate,
            @Nullable List<EventRegistrationFieldValue> answers) {

        /** A request about a member's place that carries no answers. */
        public RemoteRegistrationRequest(UUID remoteMemberId, LocalDate eventDate) {
            this(remoteMemberId, eventDate, null);
        }
    }

    /**
     * Where a partner's member stands after the partner confirmed them, as the station holding the
     * appointment answers.
     *
     * @param status    the status the registration holds now: accepted, or waiting on the waiting list
     * @param eventName the appointment's name, for the notice the member gets
     */
    public record RemoteConfirmedOwn(RegistrationStatus status, String eventName) {}

    /**
     * A decision about a partner's member on one date of a shared appointment, as the station holding it
     * sends it to the partner.
     *
     * @param eventId       the appointment at the station holding it
     * @param eventName     its name, for the notice the partner's member gets
     * @param memberUid     the partner's member
     * @param eventDate     the date
     * @param status        the status the registration holds now
     * @param queuePosition the place in line where it now waits on the waiting list, or null
     * @param movedUp       whether it moved up from the waiting list into a place that fell free
     * @param changedAt     when the station holding the appointment decided, on its own clock
     */
    public record RemoteRegistrationStatus(
            int eventId,
            String eventName,
            UUID memberUid,
            LocalDate eventDate,
            RegistrationStatus status,
            @Nullable Integer queuePosition,
            boolean movedUp,
            Instant changedAt) {}

    /**
     * A shared appointment changed, sent to the partner station whose members hold a place on it, so
     * the partner notifies them.
     *
     * @param eventId      the appointment at the station that runs it
     * @param eventName    its name after the change
     * @param changedParts the parts that changed, as the names the change notice uses joined by commas
     * @param note         what the editor wrote to go with it, or null
     * @param startTime    the time of day it now starts at where the start moved, or null
     * @param told         whether the editor chose to tell about the change; otherwise only the moved
     *                     start is announced
     * @param members      the partner's members holding a place, each with the first date they hold
     */
    public record RemoteEventChange(
            int eventId,
            String eventName,
            String changedParts,
            @Nullable String note,
            @Nullable String startTime,
            boolean told,
            List<RemoteChangedMember> members) {}

    /**
     * A member of the partner station a change concerns.
     *
     * @param memberUid the member, as the partner station knows them
     * @param eventDate the first date they hold a place on that the change touches
     */
    public record RemoteChangedMember(UUID memberUid, LocalDate eventDate) {}

    /**
     * A shared appointment handed some of its dates to another appointment: one date taken out on its
     * own, or a series gone on as a new one from a date. The partner's records of its members' places
     * on those dates follow.
     *
     * @param fromEventId the appointment the dates were taken from
     * @param toEventId   the appointment that holds them now
     * @param from        the first date handed over
     * @param until       the last date handed over, or null for every later one
     */
    public record RemoteEventHandover(
            int fromEventId,
            int toEventId,
            LocalDate from,
            @Nullable LocalDate until) {}

    /**
     * A comment one of the partner's members writes on a shared appointment or event group.
     *
     * @param remoteMemberUid the member, by the id their own station gave them
     * @param displayName     the name to show for them
     * @param parentId        the comment answered, or null for a new thread
     * @param content         the comment
     * @param eventDate       the date of the appointment the comment is about, or null for the whole
     *                        appointment and always for an event group
     */
    public record RemoteCommentRequest(
            UUID remoteMemberUid,
            String displayName,
            @Nullable Integer parentId,
            String content,
            @Nullable String eventDate) {}

    public record RemoteCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    public record RemoteCommentDeleteRequest(UUID remoteMemberUid) {}
}
