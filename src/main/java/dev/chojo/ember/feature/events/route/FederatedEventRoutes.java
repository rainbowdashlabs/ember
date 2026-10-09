/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.MemberLookupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.util.SafeContentDisposition;
import dev.chojo.ember.util.SafeInlineMime;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * User-facing federated event routes: the aggregated view over every federation partner plus the
 * per-partner event detail, registration and comment proxies. Every partner is asked through the
 * federation transport, wherever it lives; these handlers never know which.
 */
@Singleton
public class FederatedEventRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(FederatedEventRoutes.class);

    private final EventFederationService eventFederationService;
    private final FederationService federationService;
    private final StationMemberService stationMemberService;
    private final MemberLookupService memberLookup;
    private final GuardianPolicy guardianPolicy;

    @Inject
    public FederatedEventRoutes(
            EventFederationService eventFederationService,
            FederationService federationService,
            StationMemberService stationMemberService,
            MemberLookupService memberLookup,
            GuardianPolicy guardianPolicy) {
        this.eventFederationService = eventFederationService;
        this.federationService = federationService;
        this.stationMemberService = stationMemberService;
        this.memberLookup = memberLookup;
        this.guardianPolicy = guardianPolicy;
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

    @OpenApi(
            path = "/api/v1/federated/events",
            methods = HttpMethod.GET,
            summary = "List the events partner stations share with this one",
            tags = {"Federation"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = EventFederationService.FederatedEventItem[].class)))
    private void federatedListEvents(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(eventFederationService.browseFederatedEvents(session.stationId()));
    }

    /**
     * A partner's appointment as that partner describes it, handed on without being rebuilt here.
     *
     * <p>This used to put the answer together itself, reading the questions out of this instance's
     * own tables, which are not where a remote partner's live, and never asking about the places at
     * all. The station holding the appointment is the one that knows all three, so it says all three.
     */
    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/{id}",
            methods = HttpMethod.GET,
            summary = "A partner station's shared event",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PartnerEventDetail.class)))
    private void federatedGetEvent(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int eventId = pathInt(ctx, "id");
        ctx.json(PartnerEventDetail.of(
                eventFederationService.getFederatedEvent(session.stationId(), stationUid, eventId)));
    }

    /**
     * A partner's appointment as this station's screens read it: what the partner sent, with its
     * questions under the shared type names.
     *
     * @param places what the partner set aside for this station, or {@code null} where it decides as
     *               it always did
     */
    public record PartnerEventDetail(
            SharedEvent event, List<AppointmentField> publicFields, RemoteEventRoutes.@Nullable RemotePlaces places) {

        static PartnerEventDetail of(RemoteEventRoutes.RemoteEventDetail detail) {
            return new PartnerEventDetail(
                    detail.event(),
                    detail.publicFields().stream()
                            .map(EventField::appointmentField)
                            .toList(),
                    detail.places());
        }
    }

    /** The files a partner's event hands over, as that partner is willing to hand them over. */
    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/{id}/attachments",
            methods = HttpMethod.GET,
            summary = "List the files a partner station's event hands over",
            tags = {"Federation"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = RemoteEventRoutes.RemoteAttachment[].class)))
    private void federatedListAttachments(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(eventFederationService.listFederatedAttachments(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "id")));
    }

    /**
     * One of those files, fetched from the station that owns it and handed to the reader here.
     *
     * <p>The bytes travel encoded between the two instances, because that is what the contract
     * between them speaks, and are decoded here so a browser is handed an ordinary download.
     */
    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/{id}/attachments/{attachmentId}/file",
            methods = HttpMethod.GET,
            summary = "Download a file a partner station's event hands over",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "200"))
    private void federatedDownloadAttachment(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var content = eventFederationService.getFederatedAttachment(
                session.stationId(), pathUuid(ctx, "stationuid"), pathInt(ctx, "id"), pathInt(ctx, "attachmentId"));
        if (content == null || content.base64() == null) throw EventRefusal.FEDERATED_FILE_NOT_HERE.raise();

        byte[] data;
        try {
            data = Base64.getDecoder().decode(content.base64());
        } catch (IllegalArgumentException e) {
            log.warn("Partner station answered with a file this instance cannot read", e);
            throw EventRefusal.FEDERATED_FILE_UNREADABLE.raise();
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
    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/{id}/register",
            methods = HttpMethod.POST,
            summary = "Register a member for a partner station's event",
            tags = {"Federation"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FederatedRegBody.class)),
            responses =
                    @OpenApiResponse(
                            status = "201",
                            content = @OpenApiContent(from = FederatedRegistrationAnswer.class)))
    private void federatedRegister(Context ctx) {
        var fed = answeredFor(ctx);
        var status = eventFederationService.registerForFederatedEvent(
                fed.stationId(), fed.partnerUid(), fed.eventId(), fed.remoteMemberId(), fed.eventDate());
        ctx.status(HttpStatus.CREATED).json(new FederatedRegistrationAnswer(status));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/{id}/register",
            methods = HttpMethod.DELETE,
            summary = "Withdraw a member's registration for a partner station's event",
            tags = {"Federation"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FederatedRegBody.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedWithdraw(Context ctx) {
        var fed = answeredFor(ctx);
        eventFederationService.withdrawFederatedRegistration(
                fed.stationId(), fed.partnerUid(), fed.eventId(), fed.remoteMemberId(), fed.eventDate());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Asking the station that holds the appointment to put a place back.
     *
     * <p>Whether it is still possible is theirs to answer, because they hold the row and the clock
     * that measures the few minutes. A refusal is not a failure here: it means the time has passed,
     * and the member is told so rather than left wondering whether the press landed.
     */
    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/{id}/register/undo",
            methods = HttpMethod.POST,
            summary = "Take back a withdrawal from a partner station's event",
            tags = {"Federation"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FederatedRegBody.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedUndoWithdrawal(Context ctx) {
        var fed = answeredFor(ctx);
        eventFederationService.undoFederatedWithdrawal(
                fed.stationId(), fed.partnerUid(), fed.eventId(), fed.remoteMemberId(), fed.eventDate());
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
    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/{id}/register/confirm",
            methods = HttpMethod.POST,
            summary = "Give one of this station's members a place at a partner station's event",
            tags = {"Federation"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FederatedRegBody.class)),
            responses = @OpenApiResponse(status = "204"))
    private void federatedConfirmOwn(Context ctx) {
        var fed = resolveFederatedRegContext(ctx);
        eventFederationService.confirmOwnFederatedMember(
                fed.stationId(), fed.partnerUid(), fed.eventId(), fed.remoteMemberId(), fed.eventDate());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * The inputs of a sign-up the caller gives for themselves or somebody in their care, or for any
     * member of the station where they run its appointments: the same rule as for the station's own
     * appointments. The member used to be taken from the request as it came, so anybody signed in
     * could sign up, withdraw or reinstate any other member at a partner's appointment.
     */
    private FederatedRegContext answeredFor(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var fed = resolveFederatedRegContext(ctx);
        if (!guardianPolicy.mayActFor(session.user(), fed.memberId())
                && !session.hasPermission(StationPermission.EVENT_MANAGER)) {
            throw EventRefusal.MEMBER_NOT_YOURS_TO_REGISTER.raise();
        }
        return fed;
    }

    /**
     * Resolves the shared inputs for a federated register or withdraw: the caller's station, the
     * addressed partner, the target event, the date, and the member, who has to be one of this
     * station's.
     */
    private FederatedRegContext resolveFederatedRegContext(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var partner = resolvePartner(ctx, session.stationId());
        int eventId = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(FederatedRegBody.class);
        UUID named = req.memberId();
        UUID remoteMemberId = named != null ? named : session.member().uid();
        int memberId = memberLookup
                .resolveId(session.stationId(), remoteMemberId)
                .orElseThrow(EventRefusal.MEMBER_NOT_YOURS_TO_REGISTER::raise);
        return new FederatedRegContext(
                session.stationId(),
                partner.partnerStationId(),
                eventId,
                eventDate(req.eventDate()),
                remoteMemberId,
                memberId);
    }

    private static LocalDate eventDate(@Nullable String value) {
        if (value == null) throw FederationRefusal.FEDERATED_REGISTRATION_DAY_NOT_A_DAY.raise();
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw FederationRefusal.FEDERATED_REGISTRATION_DAY_NOT_A_DAY.raise();
        }
    }

    private FederationPartner resolvePartner(Context ctx, int stationId) {
        var partnerUid = pathUuid(ctx, "stationuid");
        return federationService
                .findPartnerByRemoteUid(stationId, partnerUid)
                .orElseThrow(EventRefusal.PARTNER_NOT_HERE::raise);
    }

    @OpenApi(
            path = "/api/v1/federated/my-registrations",
            methods = HttpMethod.GET,
            summary = "List the reader's household's registrations at partner stations",
            tags = {"Federation"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = RemoteEventRoutes.RemoteMemberRegistration[].class)))
    private void federatedMyRegistrations(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var memberUids = new ArrayList<UUID>();
        memberUids.add(session.member().uid());
        var managed = stationMemberService.findManaged(session.member().id());
        for (var m : managed) {
            if (m.uid() != null) memberUids.add(m.uid());
        }
        ctx.json(eventFederationService.findMyRegistrations(session.stationId(), memberUids));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/{eventId}/comments",
            methods = HttpMethod.GET,
            summary = "List the comments on a partner station's event",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse[].class)))
    private void federatedListComments(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int eventId = pathInt(ctx, "eventId");
        ctx.json(eventFederationService.listFederatedComments(session.stationId(), partnerUid, eventId));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/{eventId}/comments",
            methods = HttpMethod.POST,
            summary = "Comment on a partner station's event",
            tags = {"Federation"},
            requestBody =
                    @OpenApiRequestBody(
                            content = @OpenApiContent(from = EventCommentRoutes.CreateCommentRequest.class)),
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = CommentResponse.class)))
    private void federatedCreateComment(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int eventId = pathInt(ctx, "eventId");
        var req = ctx.bodyAsClass(EventCommentRoutes.CreateCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw EventRefusal.FEDERATED_COMMENT_NEEDS_TEXT.raise();
        }
        var created = eventFederationService.createFederatedComment(
                session.stationId(),
                partnerUid,
                eventId,
                session.member().uid(),
                NameParts.of(session.user().account()).called(),
                req.parentId(),
                req.content(),
                req.eventDate());
        ctx.status(HttpStatus.CREATED).json(created);
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/comments/{commentId}",
            methods = HttpMethod.PUT,
            summary = "Change a comment on a partner station's event",
            tags = {"Federation"},
            requestBody =
                    @OpenApiRequestBody(
                            content = @OpenApiContent(from = EventCommentRoutes.UpdateCommentRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = CommentResponse.class)))
    private void federatedUpdateComment(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(EventCommentRoutes.UpdateCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw EventRefusal.FEDERATED_COMMENT_CHANGE_NEEDS_TEXT.raise();
        }
        ctx.json(eventFederationService.updateFederatedComment(
                session.stationId(), partnerUid, commentId, session.member().uid(), req.content()));
    }

    @OpenApi(
            path = "/api/v1/federated/{stationuid}/events/comments/{commentId}",
            methods = HttpMethod.DELETE,
            summary = "Delete a comment on a partner station's event",
            tags = {"Federation"},
            responses = @OpenApiResponse(status = "204"))
    private void federatedDeleteComment(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        eventFederationService.deleteFederatedComment(
                session.stationId(), partnerUid, commentId, session.member().uid());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record FederatedRegBody(
            String eventDate, @Nullable UUID memberId) {}

    public record StatusResponse(String status) {}

    /** What the station holding the appointment recorded for a registration sent to it. */
    public record FederatedRegistrationAnswer(RegistrationStatus status) {}

    /**
     * Shared inputs for a federated register or withdraw request.
     *
     * @param stationId  the station asking
     * @param partnerUid the station holding the appointment
     */
    private record FederatedRegContext(
            int stationId, UUID partnerUid, int eventId, LocalDate eventDate, UUID remoteMemberId, int memberId) {}
}
