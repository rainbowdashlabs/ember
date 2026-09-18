/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.BadGatewayResponse;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * User-facing federated event routes: the aggregated view over every federation partner plus the
 * per-partner event detail, registration and comment proxies. Partners on this instance are served
 * from the database and remote ones over signed HTTP - the service decides, never these handlers.
 */
@Singleton
public class FederatedEventRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(FederatedEventRoutes.class);

    private final EventFederationService eventFederationService;
    private final EventFieldService eventFieldService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public FederatedEventRoutes(
            EventFederationService eventFederationService,
            EventFieldService eventFieldService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository) {
        this.eventFederationService = eventFederationService;
        this.eventFieldService = eventFieldService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/federated/events", this::federatedListEvents, StationPermission.USER);
        routes.get(prefix + "/federated/{stationuid}/events/{id}", this::federatedGetEvent, StationPermission.USER);
        routes.post(
                prefix + "/federated/{stationuid}/events/{id}/register",
                this::federatedRegister,
                StationPermission.USER);
        routes.delete(
                prefix + "/federated/{stationuid}/events/{id}/register",
                this::federatedWithdraw,
                StationPermission.USER);
        routes.post(
                prefix + "/federated/{stationuid}/events/{id}/register/undo",
                this::federatedUndoWithdrawal,
                StationPermission.USER);
        routes.post(
                prefix + "/federated/{stationuid}/events/{id}/register/confirm",
                this::federatedConfirmOwn,
                StationPermission.EVENT_REGISTRATION);
        routes.get(
                prefix + "/federated/{stationuid}/events/{id}/attachments",
                this::federatedListAttachments,
                StationPermission.USER);
        routes.get(
                prefix + "/federated/{stationuid}/events/{id}/attachments/{attachmentId}/file",
                this::federatedDownloadAttachment,
                StationPermission.USER);
        routes.get(prefix + "/federated/my-registrations", this::federatedMyRegistrations, StationPermission.USER);

        routes.get(
                prefix + "/federated/{stationuid}/events/{eventId}/comments",
                this::federatedListComments,
                StationPermission.LOGIN);
        routes.post(
                prefix + "/federated/{stationuid}/events/{eventId}/comments",
                this::federatedCreateComment,
                StationPermission.LOGIN);
        routes.put(
                prefix + "/federated/{stationuid}/events/comments/{commentId}",
                this::federatedUpdateComment,
                StationPermission.LOGIN);
        routes.delete(
                prefix + "/federated/{stationuid}/events/comments/{commentId}",
                this::federatedDeleteComment,
                StationPermission.LOGIN);
    }

    private void federatedListEvents(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventFederationService.browseFederatedEvents(session.stationId()));
    }

    /**
     * A partner's appointment as that partner describes it, handed on without being rebuilt here.
     *
     * <p>This used to put the answer together itself, reading the questions out of this instance's
     * own tables, which are not where a remote partner's live, and never asking about the places at
     * all. The station holding the appointment is the one that knows all three, so it says all three.
     */
    private void federatedGetEvent(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int eventId = pathInt(ctx, "id");
        ctx.json(eventFederationService.getFederatedEvent(session.stationId(), stationUid, eventId));
    }

    /** The files a partner's event hands over, as that partner is willing to hand them over. */
    private void federatedListAttachments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventFederationService.listFederatedAttachments(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "id")));
    }

    /**
     * One of those files, fetched from the station that owns it and handed to the reader here.
     *
     * <p>The bytes travel encoded between the two instances, because that is what the contract
     * between them speaks, and are decoded here so a browser is handed an ordinary download.
     */
    private void federatedDownloadAttachment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var content = eventFederationService.getFederatedAttachment(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "id"), pathInt(ctx, "attachmentId"));
        if (content == null || content.base64() == null) throw new NotFoundResponse();

        byte[] data;
        try {
            data = Base64.getDecoder().decode(content.base64());
        } catch (IllegalArgumentException e) {
            log.warn("Partner station answered with a file this instance cannot read", e);
            throw new BadGatewayResponse("The station holding this file answered with something unreadable");
        }
        ctx.contentType(SafeInlineMime.safeContentType(content.mimeType()));
        ctx.header(
                "Content-Disposition",
                SafeContentDisposition.build(
                        SafeInlineMime.isInlineSafe(content.mimeType())
                                ? SafeContentDisposition.Disposition.INLINE
                                : SafeContentDisposition.Disposition.ATTACHMENT,
                        content.fileName()));
        ctx.result(data);
    }

    /**
     * Signing one of our members up for a partner's appointment.
     *
     * <p>What comes back is the status the other station actually recorded. It used to say pending
     * whatever happened, so a member accepted at once by an appointment that asks for no confirmation
     * was told they were waiting on one that nobody would ever give.
     */
    private void federatedRegister(Context ctx) {
        var fed = resolveFederatedRegContext(ctx);
        var partner = fed.partner();
        var status = partner.isRemote()
                ? eventFederationService
                        .registerForFederatedEvent(
                                partner.remoteHost(),
                                partner.partnerStationId(),
                                fed.eventId(),
                                fed.remoteMemberId(),
                                fed.req().eventDate(),
                                fed.station().id(),
                                fed.station().federationPrivateKey())
                        .orElseThrow(() -> new BadRequestResponse("Registration failed"))
                : eventFederationService
                        .registerFederated(
                                fed.eventId(),
                                fed.hostPartner().id(),
                                fed.remoteMemberId(),
                                LocalDate.parse(fed.req().eventDate()))
                        .status();
        ctx.status(HttpStatus.CREATED).json(new StatusResponse(status.name()));
    }

    private void federatedWithdraw(Context ctx) {
        var fed = resolveFederatedRegContext(ctx);
        var partner = fed.partner();
        if (partner.isRemote()) {
            eventFederationService.withdrawFederatedRegistration(
                    partner.remoteHost(),
                    partner.partnerStationId(),
                    fed.eventId(),
                    fed.remoteMemberId(),
                    fed.req().eventDate(),
                    fed.station().id(),
                    fed.station().federationPrivateKey());
        } else {
            eventFederationService.withdrawRegistration(
                    fed.eventId(),
                    fed.hostPartner().id(),
                    fed.remoteMemberId(),
                    LocalDate.parse(fed.req().eventDate()));
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Asking the station that holds the appointment to put a place back.
     *
     * <p>Whether it is still possible is theirs to answer, because they hold the row and the clock
     * that measures the few minutes. A refusal is not a failure here: it means the time has passed,
     * and the member is told so rather than left wondering whether the press landed.
     */
    private void federatedUndoWithdrawal(Context ctx) {
        var fed = resolveFederatedRegContext(ctx);
        var partner = fed.partner();
        boolean restored = partner.isRemote()
                ? eventFederationService.undoFederatedWithdrawal(
                        partner.remoteHost(),
                        partner.partnerStationId(),
                        fed.eventId(),
                        fed.remoteMemberId(),
                        fed.req().eventDate(),
                        fed.station().id(),
                        fed.station().federationPrivateKey())
                : eventFederationService.undoWithdrawal(
                        fed.eventId(),
                        fed.hostPartner().id(),
                        fed.remoteMemberId(),
                        LocalDate.parse(fed.req().eventDate()));
        if (!restored) {
            throw new BadRequestResponse("This can no longer be taken back");
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Giving one of our own members a place at a partner's appointment, where they handed us the
     * choosing and a number of places to make it with.
     *
     * <p>Whoever keeps this station's registrations decides, which is the same right they hold for
     * this station's own appointments. The places belong to the other station and so does the
     * counting, so a refusal here means either that they never handed it over or that the places are
     * full: both are answers, not failures.
     */
    private void federatedConfirmOwn(Context ctx) {
        var fed = resolveFederatedRegContext(ctx);
        var partner = fed.partner();
        boolean confirmed = partner.isRemote()
                ? eventFederationService.confirmOwnFederatedMember(
                        partner.remoteHost(),
                        partner.partnerStationId(),
                        fed.eventId(),
                        fed.remoteMemberId(),
                        fed.req().eventDate(),
                        fed.station().id(),
                        fed.station().federationPrivateKey())
                : confirmOnThisInstance(fed);
        if (!confirmed) {
            throw new BadRequestResponse("No places left");
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The same, where the other station happens to live on this instance.
     *
     * <p>It still asks whether the decision was handed over, because a partner on the same instance is
     * no more entitled to confirm its own than one across the network.
     */
    private boolean confirmOnThisInstance(FederatedRegContext fed) {
        var day = LocalDate.parse(fed.req().eventDate());
        int hostPartnerId = fed.hostPartner().id();
        if (!eventFederationService.partnerPlaces(fed.eventId(), hostPartnerId).partnerConfirms()) {
            throw new ForbiddenResponse("That station decides its own registrations for this event");
        }
        var registration = eventFederationService
                .findRegistration(fed.eventId(), hostPartnerId, fed.remoteMemberId(), day)
                .orElseThrow(NotFoundResponse::new);
        return eventFederationService.acceptWithinBudget(registration.id(), fed.eventId(), hostPartnerId, day);
    }

    /**
     * Resolves the shared inputs for a federated register or withdraw: the caller's station, the
     * addressed partner, the target event, the request body, and the effective remote member id.
     */
    private FederatedRegContext resolveFederatedRegContext(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int eventId = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(FederatedRegBody.class);
        UUID remoteMemberId =
                req.memberId() != null ? req.memberId() : session.member().uid();
        var hostPartner = partner.isRemote() ? null : eventFederationService.hostPartnerOf(partner);
        return new FederatedRegContext(station, partner, hostPartner, eventId, req, remoteMemberId);
    }

    private FederationPartner resolvePartner(Context ctx, int stationId) {
        var partnerUid = pathUuid(ctx, "stationuid");
        return federationRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"));
    }

    private void federatedMyRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(List.of());
            return;
        }
        var memberUids = new ArrayList<UUID>();
        memberUids.add(session.member().uid());
        var managed = stationMemberRepository.findManaged(session.member().id());
        for (var m : managed) {
            if (m.uid() != null) memberUids.add(m.uid());
        }
        ctx.json(eventFederationService.findMyRegistrations(session.stationId(), memberUids));
    }

    private void federatedListComments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int eventId = pathInt(ctx, "eventId");
        var result = eventFederationService.listFederatedComments(session.stationId(), partnerUid, eventId);
        switch (result) {
            case EventFederationService.FederatedCommentResult.ListResult r -> ctx.json(r.comments());
            case EventFederationService.FederatedCommentResult.SingleResult r -> ctx.json(r.comment());
        }
    }

    private void federatedCreateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int eventId = pathInt(ctx, "eventId");
        var req = ctx.bodyAsClass(EventCommentRoutes.CreateCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var result = eventFederationService.createFederatedComment(
                session.stationId(),
                partnerUid,
                eventId,
                session.member().uid(),
                NameParts.of(session.account()).called(),
                req.parentId(),
                req.content(),
                req.eventDate());
        switch (result) {
            case EventFederationService.FederatedCommentResult.SingleResult r ->
                ctx.status(HttpStatus.CREATED).json(r.comment());
            case EventFederationService.FederatedCommentResult.ListResult r ->
                ctx.status(HttpStatus.CREATED).json(r.comments());
        }
    }

    private void federatedUpdateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(EventCommentRoutes.UpdateCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var result = eventFederationService.updateFederatedComment(
                session.stationId(), partnerUid, commentId, session.member().uid(), req.content());
        switch (result) {
            case EventFederationService.FederatedCommentResult.SingleResult r -> ctx.json(r.comment());
            case EventFederationService.FederatedCommentResult.ListResult r -> ctx.json(r.comments());
        }
    }

    private void federatedDeleteComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        eventFederationService.deleteFederatedComment(
                session.stationId(), partnerUid, commentId, session.member().uid());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record FederatedRegBody(String eventDate, UUID memberId) {}

    public record StatusResponse(String status) {}

    /**
     * Shared inputs for a federated register or withdraw request.
     */
    /**
     * @param partner     this station's record of the one holding the appointment
     * @param hostPartner the holder's record of this station, which is what its rows hang off, and
     *                    null where the holder is on another instance and keeps its own
     */
    private record FederatedRegContext(
            Station station,
            FederationPartner partner,
            FederationPartner hostPartner,
            int eventId,
            FederatedRegBody req,
            UUID remoteMemberId) {}
}
