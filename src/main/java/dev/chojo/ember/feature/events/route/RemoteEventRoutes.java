/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.events.entity.EventAttachment;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.events.service.EventAttachmentService;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventDateResolver;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.federation.contract.FederationContractBinder;
import dev.chojo.ember.feature.federation.contract.FederationEndpoint;
import dev.chojo.ember.feature.federation.contract.FederationSurface;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.OpenApiName;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Server-to-server event routes. They serve this station's shared events, their registrations and
 * their comments to a federation partner, authenticated by the RSA signature that
 * {@code AccessManager} already verified.
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

    private final EventCrudService crudService;
    private final EventFieldService eventFieldService;
    private final EventDateResolver dateResolver;
    private final EventFederationService eventFederationService;
    private final EventAttachmentService attachmentService;
    private final MediaLibraryService media;
    private final Api apiConfig;

    @Inject
    public RemoteEventRoutes(
            EventCrudService crudService,
            EventFieldService eventFieldService,
            EventDateResolver dateResolver,
            EventFederationService eventFederationService,
            EventAttachmentService attachmentService,
            MediaLibraryService media,
            Api apiConfig) {
        this.crudService = crudService;
        this.eventFieldService = eventFieldService;
        this.dateResolver = dateResolver;
        this.eventFederationService = eventFederationService;
        this.attachmentService = attachmentService;
        this.media = media;
        this.apiConfig = apiConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        FederationContractBinder.register(
                routes, prefix, CONTRACT, binder -> binder.handle(LIST_EVENTS, this::remoteListEvents)
                        .handle(GET_EVENT, this::remoteGetEvent)
                        .handle(LIST_ATTACHMENTS, this::remoteListAttachments)
                        .handle(GET_ATTACHMENT_CONTENT, this::remoteGetAttachmentContent)
                        .handle(REGISTER, this::remoteRegister)
                        .handle(WITHDRAW, this::remoteWithdraw)
                        .handle(UNDO_WITHDRAWAL, this::remoteUndoWithdrawal)
                        .handle(CONFIRM_OWN, this::remoteConfirmOwn)
                        .handle(LIST_REGISTRATIONS, this::remoteListRegistrations)
                        .handle(LIST_MEMBER_REGISTRATIONS, this::remoteListMemberRegistrations)
                        .handle(REGISTRATION_STATUS_WEBHOOK, this::remoteOnRegistrationStatus)
                        .handle(LIST_COMMENTS, this::remoteListComments)
                        .handle(CREATE_COMMENT, this::remoteCreateComment)
                        .handle(UPDATE_COMMENT, this::remoteUpdateComment)
                        .handle(DELETE_COMMENT, this::remoteDeleteComment));
    }

    private void remoteListEvents(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        var events = eventIds.stream()
                .map(id -> crudService.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(SharedEvent::of)
                .toList();
        ctx.json(events);
    }

    private void remoteGetEvent(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "id");
        requireSharedEvent(partner, eventId);
        var event = crudService.findById(eventId).orElseThrow(Refusal.SHARED_EVENT_NOT_HERE::raise);
        var fields = eventFieldService
                .findByEvent(eventId, dateResolver.nextDate(event).orElse(null))
                .stream()
                .filter(EventField::isPublic)
                .toList();
        var places = eventFederationService.partnerPlaces(eventId, partner.id());
        ctx.json(new RemoteEventDetail(
                SharedEvent.of(event),
                fields,
                places.partnerConfirms() ? new RemotePlaces(places.slotBudget(), true) : null));
    }

    /** The open files of a shared event, named for a partner that may ask about it. */
    private void remoteListAttachments(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireSharedEvent(partner, eventId);
        ctx.json(attachmentService.listOpen(eventId).stream()
                .map(RemoteAttachment::of)
                .toList());
    }

    /**
     * One open file of a shared event, encoded into the answer.
     *
     * <p>The same two questions are asked here as at home: is this event shared with the partner
     * asking, and is the file one the event hands out at all. A file kept back is answered as absent
     * rather than refused, so asking for one by id says no more than asking for a file that is gone.
     */
    private void remoteGetAttachmentContent(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireSharedEvent(partner, eventId);

        var attachment = attachmentService
                .find(pathInt(ctx, "attachmentId"))
                .filter(found -> found.eventId() == eventId)
                .filter(found -> !found.internal())
                .orElseThrow(Refusal.SHARED_EVENT_FILE_NOT_HERE::raise);

        EventAttachmentService.requireSizeToTravel(attachment.fileSize(), apiConfig.maxUploadSizeBytes());
        var event = crudService.findById(eventId).orElseThrow(Refusal.EVENT_NOT_HERE_BEHIND_SHARED_FILE::raise);
        var file = media.read(event.stationId(), attachment.contentHash())
                .orElseThrow(Refusal.SHARED_EVENT_FILE_CONTENT_NOT_HERE::raise);
        ctx.json(new RemoteAttachmentContent(
                attachment.id(),
                attachment.displayName(),
                attachment.fileName(),
                file.contentType(),
                Base64.getEncoder().encodeToString(file.data())));
    }

    private void remoteRegister(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "id");
        requireSharedEvent(partner, eventId);
        var req = ctx.bodyAsClass(RemoteRegistrationRequest.class);
        var reg =
                eventFederationService.registerFederated(eventId, partner.id(), req.remoteMemberId(), req.eventDate());
        ctx.status(HttpStatus.CREATED).json(reg);
    }

    private void remoteWithdraw(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "id");
        requireSharedEvent(partner, eventId);
        var req = ctx.bodyAsClass(RemoteRegistrationRequest.class);
        eventFederationService.withdrawRegistration(eventId, partner.id(), req.remoteMemberId(), req.eventDate());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * A partner confirming one of its own members, where this station handed it that decision.
     *
     * <p>Two refusals, and they say different things. Without an arrangement the partner is asking for
     * something it was never given, which is forbidden. With one, but with its places already filled,
     * the answer is that there is no room, which is an ordinary thing to be told and not a fault.
     */
    private void remoteConfirmOwn(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "id");
        requireSharedEvent(partner, eventId);
        var req = ctx.bodyAsClass(RemoteRegistrationRequest.class);

        if (!eventFederationService.partnerPlaces(eventId, partner.id()).partnerConfirms()) {
            throw Refusal.PARTNER_DOES_NOT_CONFIRM_ITS_OWN.raise();
        }
        var registration = eventFederationService
                .findRegistration(eventId, partner.id(), req.remoteMemberId(), req.eventDate())
                .orElseThrow(Refusal.PARTNER_REGISTRATION_NOT_HERE::raise);
        if (!eventFederationService.acceptWithinBudget(registration.id(), eventId, partner.id(), req.eventDate())) {
            throw Refusal.NO_PLACES_LEFT_FOR_PARTNER.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * A partner asking for one of its members to be put back after a withdrawal.
     *
     * <p>Whether it is still possible is this station's to answer, because this station holds the
     * row and the clock that measures the window. A refusal here is not a failure: it means the
     * few minutes have passed, and the partner tells its member so.
     */
    private void remoteUndoWithdrawal(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "id");
        requireSharedEvent(partner, eventId);
        var req = ctx.bodyAsClass(RemoteRegistrationRequest.class);
        if (!eventFederationService.undoWithdrawal(eventId, partner.id(), req.remoteMemberId(), req.eventDate())) {
            throw Refusal.PARTNER_WITHDRAWAL_NO_LONGER_UNDONE.raise();
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Who from this partner is coming, which is not the same as who has a row.
     *
     * <p>A withdrawal used to delete its row and now keeps it, so the refusals are filtered out here
     * rather than sent. A partner reading this list treats a row as somebody coming, and an older one
     * has never heard of a withdrawn status at all: sending them would put people back on a list they
     * have left, on every instance that has not been updated.
     */
    private void remoteListRegistrations(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "id");
        requireSharedEvent(partner, eventId);
        var registrations = eventFederationService.findRegistrationsByPartner(partner.id()).stream()
                .filter(r -> r.eventId() == eventId)
                .filter(EventFederationRegistration::isStanding)
                .toList();
        ctx.json(registrations);
    }

    private void remoteListMemberRegistrations(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        var memberUid = pathUuid(ctx, "memberUid");
        var registrations = eventFederationService.findRegistrationsByRemoteMember(memberUid).stream()
                .filter(r -> r.partnerId() == partner.id())
                .filter(EventFederationRegistration::isStanding)
                .toList();
        ctx.json(registrations.stream()
                .map(r -> new RemoteMemberRegistration(
                        r.eventId(),
                        r.remoteMemberId().toString(),
                        r.eventDate().toString(),
                        r.status(),
                        r.partnerId()))
                .toList());
    }

    private void remoteOnRegistrationStatus(Context ctx) {
        FederationSession.requirePartner(ctx);
        ctx.json(new FederatedEventRoutes.StatusResponse("ok"));
    }

    private void remoteListComments(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireSharedEvent(partner, eventId);
        ctx.json(eventFederationService.listComments(eventId));
    }

    private void remoteCreateComment(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireSharedEvent(partner, eventId);
        var req = ctx.bodyAsClass(RemoteCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw Refusal.PARTNER_COMMENT_NEEDS_TEXT.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(eventFederationService.createRemoteComment(
                        partner,
                        eventId,
                        req.remoteMemberUid(),
                        req.displayName(),
                        req.parentId(),
                        req.content(),
                        parseCommentDate(req.eventDate())));
    }

    /**
     * Reads the optional occurrence date a comment is scoped to. Older peers omit the field
     * entirely, which keeps the comment attached to the whole event rather than one date.
     */
    private LocalDate parseCommentDate(String eventDate) {
        if (eventDate == null || eventDate.isBlank()) return null;
        try {
            return LocalDate.parse(eventDate);
        } catch (Exception e) {
            throw Refusal.PARTNER_COMMENT_DAY_NOT_A_DATE.raise();
        }
    }

    private void remoteUpdateComment(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteCommentUpdateRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw Refusal.PARTNER_COMMENT_CHANGE_NEEDS_TEXT.raise();
        }
        ctx.json(eventFederationService.updateRemoteComment(partner, commentId, req.remoteMemberUid(), req.content()));
    }

    private void remoteDeleteComment(Context ctx) {
        var partner = FederationSession.requirePartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteCommentDeleteRequest.class);
        if (eventFederationService.deleteRemoteComment(partner, commentId, req.remoteMemberUid())) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw Refusal.PARTNER_COMMENT_NOT_DELETED.raise();
        }
    }

    /**
     * Confirms the partner is allowed to see the given event, i.e. it is in the set
     * this station shares with that partner. Guards every {@code /remote/events}
     * read/write so a partner cannot address never-federated events by enumerating
     * ids.
     */
    private void requireSharedEvent(FederationPartner partner, int eventId) {
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        if (!eventIds.contains(eventId)) {
            throw Refusal.EVENT_NOT_SHARED_WITH_PARTNER.raise();
        }
    }

    /**
     * @param places what this partner may do here, or {@code null} where the host decides as it always
     *         did. A peer that has never heard of this field ignores it and behaves as before
     */
    public record RemoteEventDetail(SharedEvent event, List<EventField> publicFields, RemotePlaces places) {}

    /**
     * What a partner station has been given on one appointment, in its own terms.
     *
     * @param slotBudget how many places it may fill on a date, or {@code null} for no cap
     * @param decidesItself whether it confirms its own members rather than the host doing it
     */
    public record RemotePlaces(Integer slotBudget, boolean decidesItself) {}

    /**
     * A file a shared event hands over, without its bytes: enough to list it and to ask for it.
     *
     * @param id       the attachment, which is what a request for the bytes names
     * @param name     what the partner's reader sees, which is the label where one was written
     * @param mimeType what kind of file it is
     * @param fileSize how big it is, so a reader knows what they are asking for
     */
    @OpenApiName("RemoteEventAttachment")
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
    @OpenApiName("RemoteEventAttachmentContent")
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
